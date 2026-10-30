# OpenShift avec Argo CD : déploiement GitOps

Dans les guides [impératif](openshift-imperatif.md) et [déclaratif](openshift-declaratif.md), c'est toi qui appliques les changements au cluster (`oc new-app`, `oc apply`). Ici, c'est **Argo CD** qui s'en charge, en lisant le dépôt Git.

```mermaid
flowchart LR
    toi["toi<br/>git push"]
    github["GitHub<br/>nicolas-budin/spring_ms<br/>dossier openshift/"]
    argo["Argo CD<br/>(openshift-gitops)"]
    cluster["projet spring-ms<br/>sur CRC"]

    toi --> github
    argo -- "lit toutes les ~3 min" --> github
    argo -- "applique les différences" --> cluster
    argo -- "surveille" --> cluster
```

C'est le principe du **GitOps** : **Git est la source de vérité**.

- **Pour changer quelque chose,** on modifie un fichier, on commite et on pousse. Argo CD applique le changement.
- **Si quelqu'un modifie le cluster à la main,** Argo CD remet l'état décrit dans Git (*selfHeal*).
- **Si on supprime un fichier de Git,** Argo CD supprime l'objet correspondant du cluster (*prune*).

## Ce qui est en place

| Fichier | Rôle |
|---|---|
| [argocd/application.yaml](../argocd/application.yaml) | l'**Application** Argo CD : « déploie le dossier `openshift/` du dépôt dans le projet `spring-ms` ». C'est le seul fichier appliqué à la main, une seule fois. |
| [openshift/*.yaml](../openshift/) | les manifests : RabbitMQ, les 3 services, Prometheus, Grafana, OpenSearch et Fluent Bit. Argo CD applique tous les `.yaml` du dossier. |

L'opérateur **Red Hat OpenShift GitOps** (Argo CD) est déjà installé sur le CRC : son instance tourne dans le namespace `openshift-gitops`. Les applications `hello-world-*` et `ollama-agent` l'utilisent déjà.

### L'Application en détail

```yaml
source:
  repoURL: https://github.com/nicolas-budin/spring_ms.git
  targetRevision: main          # la branche suivie
  path: openshift               # le dossier lu
destination:
  namespace: spring-ms          # où déployer
syncPolicy:
  automated:
    prune: true                 # objet retiré de Git → supprimé du cluster
    selfHeal: true              # modification à la main → annulée
```

- **`managedNamespaceMetadata`** pose le label `argocd.argoproj.io/managed-by: openshift-gitops` sur le namespace. Sans ce label, l'instance Argo CD d'OpenShift n'a pas le droit d'y créer des objets.
- **`ignoreDifferences`** est expliqué plus bas, à l'étape 5.

### Les ConfigMaps de Prometheus et Grafana

Argo CD n'applique que des fichiers présents dans Git. Les commandes `oc create configmap --from-file=...` ne peuvent donc plus servir. Les ConfigMaps sont maintenant écrites **en entier** en tête de [prometheus.yaml](../openshift/prometheus.yaml) et de [grafana.yaml](../openshift/grafana.yaml).

Leur contenu est une **copie** des fichiers de `observability/`, que docker-compose utilise aussi. Si tu modifies l'un, il faut modifier l'autre. Un commentaire le rappelle en tête de chaque ConfigMap.

## Étapes

### 1. Pousser les manifests dans Git

Argo CD lit GitHub, pas ton disque. Tout changement doit donc être poussé :

```bash
cd ~/github/spring_ms
git add openshift/ argocd/ docs/openshift-argocd.md
git commit -m "Argo CD : ConfigMaps Prometheus/Grafana et Application"
git push
```

### 2. Ouvrir la console Argo CD

```bash
oc get route openshift-gitops-server -n openshift-gitops
```

L'adresse est <https://openshift-gitops-server-openshift-gitops.apps-crc.testing>. Clique sur **Log in via OpenShift** et connecte-toi en kubeadmin. Tu y vois déjà tes applications `hello-world-*` et `ollama-agent`.

### 3. Créer l'Application

```bash
oc apply -f argocd/application.yaml
```

C'est la **seule** commande `oc apply` de ce guide. Ensuite, c'est Argo CD qui applique.

Dans la console Argo CD, la tuile **spring-ms** apparaît. Clique dessus pour voir l'arbre des ressources : Deployments, Services, Routes, ConfigMaps, BuildConfigs…

**Les objets déjà présents ne sont pas recréés.** Ceux que tu as créés avec `oc apply -f openshift/` portent les mêmes noms qu'Argo CD les a dans Git : il les **reprend** et les met simplement à jour. Il ajoute ses labels de suivi, et les ConfigMaps passent sous gestion déclarative.

Statut attendu au bout de quelques secondes : **Synced** (le cluster est identique à Git) et **Healthy** (les pods sont prêts).

En ligne de commande :

```bash
oc get application spring-ms -n openshift-gitops
```

### 4. Construire les images (inchangé)

Les builds restent **binaires** : le code Java est envoyé depuis ton poste, pas lu dans Git.

```bash
oc start-build notification-service --from-dir=. --follow
oc start-build user-service         --from-dir=. --follow
oc start-build frontend             --from-dir=. --follow
```

Argo CD gère les BuildConfigs et les ImageStreams, mais il ne **lance pas** les builds. Quand tu modifies le code Java, il faut donc toujours lancer `oc start-build` toi-même.

### 5. Pourquoi `ignoreDifferences`

Dans Git, chaque Deployment demande `image: notification-service:latest`. Après un build, le déclencheur `image.openshift.io/triggers` remplace ce champ par l'adresse exacte de l'image construite :

```
image-registry.openshift-image-registry.svc:5000/spring-ms/notification-service@sha256:...
```

Sans règle particulière, Argo CD verrait que le cluster ne correspond plus à Git. Avec `selfHeal`, il remettrait `notification-service:latest`, puis le déclencheur réécrirait le champ, et ainsi de suite, **en boucle**.

La règle `ignoreDifferences` sur `.spec.template.spec.containers[].image` dit à Argo CD : « ce champ appartient à OpenShift, ne le compare pas ».

## Tester le GitOps

### Une modification passe par Git

Dans [openshift/frontend.yaml](../openshift/frontend.yaml), passe `replicas: 1` à `replicas: 2` (le front n'a pas de base H2, il peut avoir 2 pods). Puis :

```bash
git commit -am "front : 2 replicas" && git push
```

Argo CD vérifie le dépôt **toutes les 3 minutes environ**. Pour ne pas attendre, clique sur **Refresh** dans la console. Un 2ᵉ pod frontend apparaît alors.

### selfHeal : le cluster revient à l'état de Git

```bash
oc scale deploy/frontend --replicas=5
oc get pods -l app=frontend -w
```

Les pods supplémentaires démarrent, puis Argo CD remet le nombre de replicas indiqué dans Git, en quelques secondes.

Même chose si tu supprimes un objet : `oc delete route frontend`, et la Route est recréée.

**Conséquence :** `oc edit`, `oc patch` et `oc scale` ne servent plus à rien sur les objets gérés par Argo CD. La seule façon de changer durablement le cluster, c'est Git.

### prune : retirer un objet

Si un fichier est supprimé de `openshift/`, ou un objet retiré d'un fichier, Argo CD supprime l'objet du cluster au prochain push.

## Modifier Prometheus ou Grafana

1. Modifier le fichier dans `observability/`, pour docker-compose, **et** sa copie dans la ConfigMap de `openshift/prometheus.yaml` ou `openshift/grafana.yaml`.
2. `git commit` puis `git push` : Argo CD met la ConfigMap à jour.
3. Prometheus et Grafana ne relisent leur configuration qu'au démarrage. Il faut donc les redémarrer :

   ```bash
   oc rollout restart deploy/grafana
   ```

   Cette commande ne modifie pas le Deployment décrit dans Git : elle recrée seulement les pods. Argo CD ne la contredit donc pas.

## Logs centralisés (OpenSearch)

Les logs des 3 services Spring sont rassemblés dans OpenSearch, comme avec docker-compose. Les fichiers sont [opensearch.yaml](../openshift/opensearch.yaml) et [fluent-bit.yaml](../openshift/fluent-bit.yaml).

```mermaid
flowchart LR
    pods["pods Spring<br/>logs JSON (ECS)"]
    fichiers["nœud CRC<br/>/var/log/pods/…/0.log"]
    fluent["Fluent Bit<br/>DaemonSet"]
    opensearch["OpenSearch<br/>opensearch:9200"]
    dashboards["OpenSearch Dashboards<br/>(Route)"]

    pods -- "sortie standard, écrite par CRI-O" --> fichiers
    fluent -- "lit les fichiers" --> fichiers
    fluent --> opensearch
    dashboards -- requêtes --> opensearch
```

### Ce qui change par rapport à docker-compose

| | docker-compose | OpenShift |
|---|---|---|
| Collecte des logs | Docker **pousse** chaque ligne vers Fluent Bit (driver `fluentd`) | CRI-O écrit les logs dans des **fichiers du nœud**, et Fluent Bit **les lit** |
| Fluent Bit | un conteneur | un **DaemonSet** : un pod par nœud (un seul sur CRC) |
| Format des lignes | JSON envoyé tel quel | chaque ligne est préfixée par CRI-O (`<heure> stdout F …`), et le parser `cri` retire ce préfixe |
| Préparation de Dashboards | conteneur `opensearch-init` | Job `opensearch-init`, lancé par Argo CD après chaque synchronisation (hook `PostSync`) |

Les services Spring écrivent leurs logs en JSON grâce à la variable `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`, ajoutée dans leurs Deployments. Fluent Bit ne lit **que** les pods Spring. Il ignore ses propres logs et ceux d'OpenSearch, qu'il relirait en boucle, ainsi que ceux des pods de build.

### Les autorisations (SCC)

OpenShift lance normalement les conteneurs avec un utilisateur aléatoire, sans accès au nœud. Deux composants ont besoin de plus :

| Composant | SCC | Pourquoi |
|---|---|---|
| OpenSearch, Dashboards | `anyuid` | leurs images tournent avec l'utilisateur 1000, propriétaire de leurs dossiers |
| Fluent Bit | `privileged` | lire `/var/log/pods` sur le nœud demande d'être root, un volume `hostPath` et le passage des contrôles SELinux |

Chaque autorisation est un **RoleBinding** dans Git, qui donne à un ServiceAccount (`opensearch`, `fluent-bit`) le droit d'utiliser la SCC. Argo CD peut les créer lui-même : dans le projet, il a le verbe `bind`, qui permet de transmettre ces droits.

Pendant la synchronisation, tu verras peut-être l'avertissement `would violate PodSecurity "baseline"` pour Fluent Bit. Ce n'est qu'un **avertissement**, parce que le namespace est en `warn=baseline`.

### Déployer

Comme le reste, en passant par Git :

```bash
git add openshift/ && git commit -m "OpenSearch + Fluent Bit" && git push
```

Puis clique sur **Refresh** dans Argo CD. Après la synchronisation :

1. les services Spring redémarrent, puisque leur variable d'environnement a changé, et écrivent désormais en JSON ;
2. `oc get pods` doit afficher `opensearch`, `opensearch-dashboards` et `fluent-bit-xxxxx` en `1/1 Running` ;
3. le Job `opensearch-init` passe à `Completed` : il a créé l'index pattern `logs-*`.

### Vérifier

```bash
oc exec deploy/opensearch -- curl -s 'localhost:9200/_cat/indices/logs-*?v'   # un index du jour, green
oc logs ds/fluent-bit | grep -i error                                         # rien
```

Ouvre OpenSearch Dashboards sur <https://opensearch-dashboards-spring-ms.apps-crc.testing>, puis **Discover** :

| Recherche | Résultat |
|---|---|
| `service.name : "user-service"` | les logs d'un service |
| `log.level : "WARN"` | les avertissements |
| `message : "supprimé"` | les suppressions traitées par notification-service via RabbitMQ |

Pour provoquer un warning gRPC, supprime le pod de notification-service : `oc delete pod -l app=notification-service`. Crée tout de suite un utilisateur : pendant que le nouveau pod démarre, user-service écrit « Envoi de la notification impossible … UNAVAILABLE ».

On ne peut pas utiliser `oc scale --replicas=0` pour ce test : *selfHeal* le remettrait aussitôt à 1.

### Dépannage

| Symptôme | Où regarder |
|---|---|
| aucun pod `fluent-bit` | `oc describe ds fluent-bit` (section *Events*) : une erreur `unable to validate against any security context constraint` veut dire que le RoleBinding `fluent-bit-privileged` manque |
| `opensearch` redémarre en boucle | `oc logs deploy/opensearch --previous` : mémoire (limite à 1536 Mi) ou droits sur `/usr/share/opensearch/data` |
| pas d'index `logs-*` | `oc logs ds/fluent-bit` : connexion à `opensearch:9200`, ou fichiers non trouvés |
| Discover vide alors que l'index existe | élargir la plage de temps en haut à droite (par exemple « Last 1 hour ») |

### Limites

- **Les logs sont stockés dans un `emptyDir`** : ils sont perdus si le pod OpenSearch est recréé.
- **Le fichier de configuration de Fluent Bit est différent de celui de docker-compose** : seule l'entrée change, les filtres sont les mêmes. Le script `init-dashboards.sh`, lui, est une copie : si tu modifies l'un, modifie l'autre.
- **OpenShift a sa propre solution de logs**, l'opérateur *OpenShift Logging*. Ici, on reprend la chaîne de docker-compose pour garder la même configuration.

## Ce qui reste hors d'Argo CD

| Élément | Pourquoi |
|---|---|
| l'Application elle-même (`argocd/application.yaml`) | elle est appliquée une seule fois, à la main. C'est le point de départ. |
| la construction des images | builds binaires depuis ton poste (`oc start-build --from-dir=.`) |

Pour aller plus loin :

- passer les BuildConfigs en source `Git` (voir le [guide déclaratif](openshift-declaratif.md#pour-aller-plus-loin--construire-depuis-git)), pour que les images soient construites depuis le même dépôt ;
- ou utiliser **OpenShift Pipelines** (Tekton, déjà installé sur le CRC), pour construire automatiquement à chaque push.

## Supprimer

```bash
oc delete application spring-ms -n openshift-gitops
```

Par défaut, supprimer l'Application **laisse les objets** dans le cluster. Pour tout supprimer, supprime aussi le projet : `oc delete project spring-ms`.
