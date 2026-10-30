# OpenShift en déclaratif : déployer avec des manifests YAML

Ce guide déploie l'application sur OpenShift Local (CRC) à partir des fichiers du dossier [openshift/](../openshift/). On n'y donne pas une suite d'ordres : on **décrit l'état voulu** dans des fichiers YAML, et on demande à OpenShift de s'y conformer.

Pour comprendre ce que crée chaque objet, commande par commande, voir le guide [impératif](openshift-imperatif.md).

## Impératif ou déclaratif

| Impératif (`oc new-app`, `oc create route`…) | Déclaratif (`oc apply -f`) |
|---|---|
| rapide pour apprendre et tester | fichiers relisibles, versionnables dans Git |
| aucune trace de ce qui a été tapé | le fichier **est** la documentation |
| refaire l'environnement = retaper toutes les commandes | refaire l'environnement = `oc apply -f openshift/` |

Les commandes `oc` impératives ne font en réalité que **fabriquer du YAML** et l'envoyer au cluster. Les manifests écrivent ce YAML directement.

## Les fichiers

| Fichier | Objets | Remplace |
|---|---|---|
| [rabbitmq.yaml](../openshift/rabbitmq.yaml) | Deployment, Service, Route `rabbitmq-console` | `oc new-app` + `oc create route` |
| [notification-service.yaml](../openshift/notification-service.yaml) | BuildConfig, ImageStream, Deployment, Service (8082, 9090), Route | `oc new-build` + `oc patch` + `oc new-app` + `oc create route` |
| [user-service.yaml](../openshift/user-service.yaml) | idem, port 8081, adresse gRPC de notification-service | idem |
| [frontend.yaml](../openshift/frontend.yaml) | idem, port 8080, adresses des backends | idem |
| [prometheus.yaml](../openshift/prometheus.yaml) | ConfigMap `prometheus-config`, Deployment, Service, Route | `oc create configmap --from-file` + `oc new-app` + `oc create route` |
| [opensearch.yaml](../openshift/opensearch.yaml) | ServiceAccount + autorisation SCC `anyuid`, OpenSearch, Dashboards, Route, Job de préparation | — |
| [fluent-bit.yaml](../openshift/fluent-bit.yaml) | ServiceAccount + autorisation SCC `privileged`, configuration, DaemonSet qui lit les logs des pods sur le nœud | — |
| [grafana.yaml](../openshift/grafana.yaml) | 3 ConfigMaps (source de données, emplacement des dashboards, dashboard), Deployment, Service, Route | idem |

Les ConfigMaps de Prometheus et Grafana sont des **copies** des fichiers de `observability/`, que docker-compose utilise aussi : si tu modifies l'un, modifie l'autre. Pour que ce déploiement soit appliqué automatiquement depuis Git, voir le guide [Argo CD](openshift-argocd.md).

Chaque fichier contient plusieurs objets séparés par `---`. Les commentaires indiquent la commande impérative que chaque bloc remplace.

### Ce qu'il faut comprendre dans ces fichiers

- **Les labels relient les objets entre eux.** Les objets ne se trouvent pas par leur nom, mais par leurs labels :
  - le Deployment gère les pods qui portent `app: notification-service` (`selector.matchLabels`) ;
  - le Service envoie le trafic aux pods qui portent ce même label (`selector`).

  Si les labels ne correspondent pas, rien ne se connecte, et **aucune erreur ne s'affiche**.
- **Les ports ont des noms.** Le Deployment déclare `http` (8082) et `grpc` (9090). Le Service et la Route les désignent par leur nom (`targetPort: http`). Si un numéro de port change, seul le Deployment est à modifier. Avec ces fichiers, la ligne `EXPOSE` du Dockerfile ne sert plus.
- **Les fichiers ne précisent pas de namespace.** Ils s'appliquent au projet courant (`oc project`).
- **Le Deployment n'utilise qu'un seul pod** (`replicas: 1`), parce que H2 est en mémoire : avec deux pods, chacun aurait ses propres données.
- **Des probes surveillent les services Spring.** La *readiness* (`/actuator/health/readiness`) n'envoie du trafic au pod qu'une fois Spring démarré. La *liveness* (`/actuator/health/liveness`) redémarre le conteneur s'il ne répond plus.
- **L'annotation `image.openshift.io/triggers`** redéploie le service à chaque nouvelle image construite.

## Étapes

### 1. Préparer le terminal et le projet

```bash
eval $(crc oc-env)
oc login -u kubeadmin https://api.crc.testing:6443   # mot de passe : crc console --credentials
oc new-project spring-ms                             # ou : oc project spring-ms s'il existe déjà
cd ~/github/spring_ms
```

**Si le projet contient déjà des objets créés en impératif**, il faut repartir d'un projet vide. `oc new-app` donne aux Deployments le selector `deployment: <nom>`, et un selector **ne peut pas être modifié**. `oc apply` échouerait donc avec `field is immutable`.

```bash
oc delete project spring-ms
# attendre quelques secondes que la suppression se termine
oc new-project spring-ms
```

### 2. Vérifier sans rien créer

```bash
oc apply -f openshift/ --dry-run=server
```

Le serveur valide chaque objet sans l'enregistrer. Chaque ligne doit se terminer par `(server dry run)`, sans erreur.

### 3. Appliquer

```bash
oc apply -f openshift/
```

`oc apply` crée les objets absents et met à jour ceux qui ont changé. On peut le relancer autant de fois qu'on veut.

Pour déployer **un seul composant**, on applique son fichier : `oc apply -f openshift/notification-service.yaml`.

### 4. Constater que les pods attendent leur image

```bash
oc get pods
```

RabbitMQ démarre. Les 3 services Spring sont en `ErrImagePull` ou `ImagePullBackOff`. **C'est normal :** leurs images n'ont pas encore été construites, et les ImageStreams sont vides. Les pods réessaient en boucle sans conséquence.

### 5. Construire les images

```bash
mvn clean                                  # retire les target/ : l'envoi est plus léger
oc start-build notification-service --from-dir=. --follow
oc start-build user-service         --from-dir=. --follow
oc start-build frontend             --from-dir=. --follow
```

- **`--from-dir=.`** : `oc` envoie le dossier du projet au cluster, qui exécute le Dockerfile indiqué dans chaque BuildConfig. Une ligne `Push successful` en fin de build indique que l'image est prête.
- **Avec une archive préparée à la main** : le tar de macOS doit produire un format standard, sinon le build échoue :

  ```bash
  COPYFILE_DISABLE=1 tar --no-mac-metadata --no-xattrs --format=ustar \
    --exclude='./*/target' --exclude=./.git -czf /tmp/spring-ms.tgz .
  oc start-build notification-service --from-archive=/tmp/spring-ms.tgz --follow
  ```

Dès qu'une image est poussée, l'annotation `image.openshift.io/triggers` met à jour le Deployment, et un nouveau pod démarre.

### 6. Suivre le déploiement

```bash
oc rollout status deploy/notification-service   # attend que le nouveau pod soit prêt
oc get pods                                     # 1/1 Running pour les 4 pods
oc logs deploy/notification-service | grep -E "Started|rabbitConnectionFactory"
```

### 7. Vérifier les Services

```bash
oc get endpoints
```

Chaque Service doit afficher l'adresse IP d'un pod, par exemple `10.217.0.45:8082,10.217.0.45:9090`. Si un Service affiche `<none>`, son `selector` ne trouve aucun pod **prêt** : soit les labels ne correspondent pas, soit la readiness probe échoue encore.

### 8. Tester

```bash
oc get routes
```

| URL | Quoi |
|---|---|
| <https://frontend-spring-ms.apps-crc.testing> | le front |
| <https://user-service-spring-ms.apps-crc.testing/swagger-ui.html> | Swagger de user-service |
| <https://notification-service-spring-ms.apps-crc.testing/swagger-ui.html> | Swagger de notification-service |
| <https://rabbitmq-console-spring-ms.apps-crc.testing> | console RabbitMQ (`guest` / `guest`) |

Le certificat est auto-signé par CRC : il faut accepter l'alerte du navigateur.

Trois vérifications :

- **gRPC** : créer un utilisateur depuis le front ou Swagger. La réponse doit contenir `"notificationCount": 1`.
- **RabbitMQ** : le supprimer, puis lancer `oc logs deploy/notification-service | grep supprimé`.
- **gRPC depuis le Mac** : lancer `oc port-forward svc/notification-service 9090`, puis utiliser Postman sur `localhost:9090`. Le fonctionnement du tunnel est expliqué dans le [guide impératif](openshift-imperatif.md#5-tester-grpc-depuis-le-mac--oc-port-forward).

## Faire évoluer le déploiement

| Besoin | Commande |
|---|---|
| le code Java a changé | `oc start-build <service> --from-dir=. --follow` : le redéploiement est automatique |
| un YAML a changé | `oc diff -f openshift/` pour voir l'écart avec le cluster, puis `oc apply -f openshift/` |
| retirer un composant | `oc delete -f openshift/notification-service.yaml` : le fichier qui a servi à créer sert aussi à supprimer |
| tout supprimer | `oc delete project spring-ms` |

On ne modifie plus le cluster avec `oc patch` ou `oc edit`. On modifie **le fichier**, puis on fait `oc apply`. Sinon, le fichier et le cluster divergent, et le prochain `oc apply` écrase la modification faite à la main.

Attention : `oc apply -f openshift/` applique **tous** les fichiers du dossier. Un composant supprimé avec `oc delete -f` sera recréé au prochain `oc apply -f openshift/` si son fichier est resté dans le dossier.

## Pour aller plus loin : construire depuis Git

Avec la source `Binary`, le code est envoyé depuis le poste à chaque build. Quand le projet sera dans un dépôt Git, le BuildConfig pourra cloner le code lui-même :

```yaml
spec:
  source:
    type: Git
    git:
      uri: https://github.com/<compte>/spring-ms.git
      ref: main
```

Le build se lance alors sans rien envoyer : `oc start-build notification-service --follow`. Chaque build part d'un commit précis, et un webhook peut relancer le build à chaque `git push`.

Le dépôt doit être accessible depuis CRC : public, ou privé avec un Secret qui contient les identifiants.
