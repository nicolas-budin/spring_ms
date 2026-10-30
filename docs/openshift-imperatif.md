# OpenShift en impératif : déployer avec des commandes `oc`

Ce guide déploie l'application sur OpenShift Local (CRC) **commande par commande**. Chaque commande dit à OpenShift « fais ceci », et on voit à chaque étape ce qu'elle crée.

C'est la meilleure façon d'apprendre. Pour un déploiement que l'on peut rejouer, voir le guide [déclaratif](openshift-declaratif.md).

Ordre de déploiement : RabbitMQ, puis notification-service, puis user-service, puis le frontend. Chaque service a besoin de ceux qui le précèdent.

## 0. Préparer le terminal et créer le projet

```bash
eval $(crc oc-env)                                  # met la commande oc dans le PATH
oc login -u kubeadmin https://api.crc.testing:6443  # mot de passe : crc console --credentials
oc new-project spring-ms
cd ~/github/spring_ms
```

- **`oc`** est l'outil en ligne de commande d'OpenShift. `eval $(crc oc-env)` l'ajoute au `PATH` du terminal en cours seulement : il faut le relancer dans chaque nouveau terminal.
- **`https://api.crc.testing:6443`** est l'adresse de l'**API** du cluster, celle qu'utilise `oc`. La console web a une autre adresse : <https://console-openshift-console.apps-crc.testing>.
- **Un projet** est un espace isolé (un *namespace* Kubernetes, avec des droits en plus). Tout ce qu'on crée ensuite va dedans. Supprimer le projet supprime tout ce qu'il contient.

Pour vérifier : `oc whoami` doit répondre `kubeadmin`, et `oc project` doit répondre `spring-ms`.

## 1. RabbitMQ

### 1a. Créer l'application

```bash
oc new-app docker.io/library/rabbitmq:4.3.7-management --name=rabbitmq
```

`oc new-app` est un raccourci : à partir d'une image, il crée plusieurs objets d'un coup.

- **Deployment** : la « recette ». Elle dit « je veux 1 pod avec cette image ; s'il meurt, recrée-le ».
- **Pod** : le conteneur qui tourne. C'est le Deployment qui le crée, jamais toi.
- **Service** : un **nom DNS interne stable**, ici `rabbitmq`, placé devant le pod. L'adresse IP d'un pod change à chaque redémarrage, le nom du Service ne change pas.

Pour savoir quels ports ouvrir, `oc new-app` lit les lignes `EXPOSE` de l'image (5672, 15672…).

### 1b. Suivre le démarrage

```bash
oc get pods -w     # attendre 1/1 Running, puis Ctrl+C
```

Si ça bloque : `oc logs deploy/rabbitmq` montre les logs de RabbitMQ. `oc describe pod -l deployment=rabbitmq` montre la section *Events*, en bas, avec les éventuels problèmes.

OpenShift lance les conteneurs avec un **utilisateur aléatoire, jamais root**. L'image RabbitMQ le supporte, parce que son dossier de données est déclaré comme volume.

### 1c. Exposer la console

```bash
oc create route edge rabbitmq-console --service=rabbitmq --port=15672-tcp
oc get route rabbitmq-console
```

Une **Route** donne une URL HTTPS publique à un Service : <https://rabbitmq-console-spring-ms.apps-crc.testing>, avec les identifiants `guest` / `guest`. Le certificat est auto-signé par CRC : accepte l'alerte du navigateur.

- **`edge`** : la Route gère le HTTPS. Elle parle ensuite en HTTP au pod.
- **`--port=15672-tcp`** : le port **du Service** à viser, désigné par le nom que `new-app` lui a donné.

### Un même port, trois niveaux

Un port apparaît à trois endroits, et chaque objet ne connaît que le niveau juste en dessous de lui.

```
navigateur ── https://rabbitmq-console-spring-ms.apps-crc.testing (port 443)
   ▼
router OpenShift   ← un seul point d'entrée (80/443) pour toutes les Routes : il les distingue par le nom de domaine
   │  port du Service : 15672-tcp          (écrit dans la Route)
   ▼
Service rabbitmq
   │  targetPort : 15672                   (écrit dans le Service)
   ▼
pod rabbitmq       ← RabbitMQ écoute sur 15672 (EXPOSE dans l'image)
```

Docker publie un **port** de l'hôte par service, par exemple `"15672:15672"`. OpenShift donne plutôt un **nom de domaine** par Route, toujours sur le port 443, donc deux applications ne peuvent pas se disputer un port.

Une Route ne transporte que du HTTP. Le port AMQP 5672 n'a donc pas de Route. Il reste interne, et les services Spring le joignent par `rabbitmq:5672`.

## 2. notification-service

### 2a. Déclarer les ports dans le Dockerfile

Pour connaître les ports, `oc new-app` lit les lignes `EXPOSE` de l'image. Sans elles, il crée le Deployment mais **aucun Service**.

[notification-service/Dockerfile](../notification-service/Dockerfile) contient déjà la ligne suivante, dans la deuxième partie (l'image finale), juste avant `ENTRYPOINT` :

```dockerfile
EXPOSE 8082 9090
```

8082 est le port REST + Swagger, 9090 le port gRPC.

### 2b. Créer la recette de build

```bash
oc new-build --name=notification-service --binary --strategy=docker
```

Cette commande crée deux objets :

- un **BuildConfig** : la recette (« construis avec un Dockerfile ») ;
- un **ImageStream** : l'endroit où l'image construite sera rangée, dans le registry interne d'OpenShift.

`--binary` veut dire que le code n'est pas récupéré dans un dépôt Git : c'est toi qui l'envoies, au moment du build.

### 2c. Indiquer quel Dockerfile utiliser

```bash
oc patch bc/notification-service -p '{"spec":{"strategy":{"dockerStrategy":{"dockerfilePath":"notification-service/Dockerfile"}}}}'
```

Le build reçoit **tout le projet**, parce que le Dockerfile a besoin du `pom.xml` parent et de `grpc-api`. Par défaut, OpenShift cherche un `Dockerfile` à la racine. Or ici il y en a un par service, dans chaque sous-dossier. C'est l'équivalent de `docker build -f notification-service/Dockerfile .`.

`oc patch` modifie **un seul champ** d'un objet existant. Le JSON suit simplement le chemin dans le YAML de l'objet :

```yaml
spec:
  strategy:
    dockerStrategy:
      dockerfilePath: notification-service/Dockerfile   # ← le champ ajouté
```

Autre façon de faire : `oc edit bc/notification-service` ouvre le YAML dans `vi`. On y ajoute la ligne à la main, puis on quitte avec `:wq`.

### 2d. Envoyer le code et construire l'image

**Méthode simple** : `oc` fabrique l'archive lui-même.

```bash
mvn clean                                         # retire les target/ : l'envoi est plus léger
oc start-build notification-service --from-dir=. --follow
```

**Méthode avec une archive préparée** :

```bash
COPYFILE_DISABLE=1 tar --no-mac-metadata --no-xattrs --format=ustar \
  --exclude='./*/target' --exclude=./.git -czf /tmp/spring-ms.tgz .
oc start-build notification-service --from-archive=/tmp/spring-ms.tgz --follow
```

Le `tar` de macOS ajoute des métadonnées propres à macOS (attributs étendus). Le build, qui tourne sous Linux, les refuse : il échoue avec `unable to extract binary build input`, et le pod de build reste en `Init:Error`. Les options `--no-mac-metadata --no-xattrs --format=ustar` produisent un tar standard qui évite ce problème. `COPYFILE_DISABLE=1` évite en plus les fichiers cachés `._*`.

Le build lance un **pod de build** temporaire, qui exécute le Dockerfile puis pousse l'image dans l'ImageStream. La première fois, compte plusieurs minutes, le temps que Maven télécharge les dépendances. La fin attendue est `Push successful`.

Pour vérifier : `oc get builds` doit afficher `Complete`, et `oc get is notification-service` doit montrer un tag `latest`.

### 2e. Créer l'application

```bash
oc new-app --image-stream=notification-service:latest --name=notification-service \
  -e SPRING_RABBITMQ_HOST=rabbitmq \
  -e SERVER_FORWARD_HEADERS_STRATEGY=framework
```

- **`--image-stream`** prend l'image construite dans le registry interne, et non sur Docker Hub. OpenShift ajoute aussi un **déclencheur** : à chaque nouveau build, le service est redéployé automatiquement.
- **`SPRING_RABBITMQ_HOST=rabbitmq`** : `rabbitmq` est le nom du Service créé à l'étape 1.
- **`SERVER_FORWARD_HEADERS_STRATEGY=framework`** : la Route parle en HTTP au pod. Sans ce réglage, Spring croit être en HTTP, et Swagger génère des URLs `http://` que le navigateur bloque. Avec ce réglage, Spring lit l'en-tête `X-Forwarded-Proto: https` ajouté par la Route.

### 2f. Vérifier

```bash
oc get pods -w                                     # notification-service-xxxx en 1/1 Running
oc logs deploy/notification-service | grep -E "Started|rabbitConnectionFactory"
oc get svc notification-service                    # 8082/TCP,9090/TCP (lus dans EXPOSE)
```

Le pod `notification-service-N-build` en `Completed` est le pod de build. C'est normal qu'il soit là.

### 2g. Exposer Swagger

```bash
oc create route edge notification-service --service=notification-service --port=8082-tcp
```

Swagger est ensuite sur <https://notification-service-spring-ms.apps-crc.testing/swagger-ui.html>.

Le port gRPC 9090 n'a pas de Route : seul user-service l'utilise, à l'intérieur du cluster. Pour le tester depuis le Mac, voir l'étape 5.

## 3. user-service

On reprend le même parcours que pour notification-service.

1. Ajouter `EXPOSE 8081` dans [user-service/Dockerfile](../user-service/Dockerfile), juste avant `ENTRYPOINT`. user-service est seulement **client** gRPC : il n'ouvre pas de port gRPC.
2. Créer la recette, la patcher et construire :

   ```bash
   oc new-build --name=user-service --binary --strategy=docker
   oc patch bc/user-service -p '{"spec":{"strategy":{"dockerStrategy":{"dockerfilePath":"user-service/Dockerfile"}}}}'
   oc start-build user-service --from-dir=. --follow
   ```

3. Créer l'application :

   ```bash
   oc new-app --image-stream=user-service:latest --name=user-service \
     -e SPRING_RABBITMQ_HOST=rabbitmq \
     -e SERVER_FORWARD_HEADERS_STRATEGY=framework \
     -e SPRING_GRPC_CLIENT_CHANNEL_NOTIFICATION_TARGET=static://notification-service:9090
   ```

   En local, le canal gRPC pointe vers `localhost:9090`. Dans le cluster, notification-service tourne dans un **autre pod**, qu'on joint par le nom de son Service. Spring traduit la variable d'environnement en propriété : `SPRING_GRPC_CLIENT_CHANNEL_NOTIFICATION_TARGET` devient `spring.grpc.client.channel.notification.target`.

4. Exposer Swagger :

   ```bash
   oc create route edge user-service --service=user-service --port=8081-tcp
   ```

5. Tester, sur <https://user-service-spring-ms.apps-crc.testing/swagger-ui.html> :
   - **`POST /api/users`** doit répondre avec `"notificationCount": 1`. C'est la preuve que **gRPC** fonctionne.
   - **`DELETE /api/users/{id}`** doit répondre 204. Ensuite, `oc logs deploy/notification-service | grep supprimé` doit afficher une ligne, preuve que **RabbitMQ** fonctionne.

## 4. frontend

1. Ajouter `EXPOSE 8080` dans [frontend/Dockerfile](../frontend/Dockerfile), juste avant `ENTRYPOINT`.
2. Créer la recette, la patcher et construire :

   ```bash
   oc new-build --name=frontend --binary --strategy=docker
   oc patch bc/frontend -p '{"spec":{"strategy":{"dockerStrategy":{"dockerfilePath":"frontend/Dockerfile"}}}}'
   oc start-build frontend --from-dir=. --follow
   ```

3. Créer l'application :

   ```bash
   oc new-app --image-stream=frontend:latest --name=frontend \
     -e SERVER_FORWARD_HEADERS_STRATEGY=framework \
     -e SERVICES_USER_URL=http://user-service:8081 \
     -e SERVICES_NOTIFICATION_URL=http://notification-service:8082 \
     -e SERVICES_USER_SWAGGERURL=https://user-service-spring-ms.apps-crc.testing/swagger-ui.html \
     -e SERVICES_NOTIFICATION_SWAGGERURL=https://notification-service-spring-ms.apps-crc.testing/swagger-ui.html
   ```

   Il y a deux sortes d'adresses, comme dans docker-compose :
   - les variables `*_URL` servent aux appels **de serveur à serveur**. Ce sont les noms des Services, internes au cluster ;
   - les variables `*_SWAGGERURL` sont des liens **affichés dans le navigateur**. Ce sont donc les URLs des Routes.

4. Exposer le front :

   ```bash
   oc create route edge frontend --service=frontend --port=8080-tcp
   ```

   Le front est ensuite sur <https://frontend-spring-ms.apps-crc.testing>.

## 5. Tester gRPC depuis le Mac : `oc port-forward`

```bash
oc port-forward svc/notification-service 9090
```

La commande ouvre un **tunnel temporaire** :

```
Mac : Postman → localhost:9090 ── tunnel oc (via l'API du cluster) ──► pod notification-service :9090
```

- La commande reste au premier plan. Pendant ce temps, on utilise `localhost:9090` depuis un autre terminal ou Postman (par exemple `grpcurl -plaintext localhost:9090 list`). On ferme le tunnel avec `Ctrl+C`.
- Pour utiliser un autre port local, la syntaxe est `local:distant`. Par exemple, `oc port-forward svc/notification-service 19090:9090` si docker-compose occupe déjà 9090.
- Le tunnel vise **un seul pod**. Il se coupe si le pod redémarre. C'est un outil de développement, pas une exposition permanente.

## 6. Dépannage

| Symptôme | Cause et solution |
|---|---|
| pod `…-build` en `Init:Error`, message `unable to extract binary build input` | archive créée par le tar de macOS : utiliser les options de l'étape 2d, ou `--from-dir=.` |
| `oc new-app` ne crée pas de Service | pas de ligne `EXPOSE` dans le Dockerfile : l'ajouter, puis relancer le build |
| page *Whitelabel Error Page* (404) | la page vient de Spring, donc la Route, le Service et le pod fonctionnent. C'est le chemin demandé qui n'existe pas : vérifier l'URL, par exemple `/swagger-ui.html` |
| pod en `ErrImagePull` | l'image n'a pas encore été construite : lancer `oc start-build` |
| pod bloqué ou qui redémarre | `oc describe pod <nom>` (section *Events*) et `oc logs <nom>` |

### Supprimer un service

Un service se supprime objet par objet, par son nom :

```bash
oc delete deploy/notification-service svc/notification-service route/notification-service \
  bc/notification-service is/notification-service --ignore-not-found
```

Pour tout supprimer d'un coup : `oc delete project spring-ms`.

## Les limites de l'impératif

Toutes ces commandes ont créé l'état du cluster, mais **aucun fichier ne le décrit**. Pour reconstruire l'environnement ailleurs, il faut retaper toutes les commandes. Pour savoir ce qui a été configuré, il faut interroger le cluster (`oc get … -o yaml`).

Le guide [déclaratif](openshift-declaratif.md) décrit ce même déploiement dans des fichiers YAML, que l'on peut relire, versionner et rejouer.
