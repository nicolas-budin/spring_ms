# Déployer sur OpenShift (CRC)

L'application (RabbitMQ, les 3 services, Prometheus, Grafana et OpenSearch) se déploie sur OpenShift Local (CRC) de trois façons. Chacune a son guide :

| Guide | Méthode | Pour quoi faire |
|---|---|---|
| [Impératif](docs/openshift-imperatif.md) | commandes `oc new-build`, `oc new-app`, `oc create route`… une par une | apprendre et voir ce que chaque commande crée |
| [Déclaratif](docs/openshift-declaratif.md) | manifests YAML de [openshift/](openshift/) + `oc apply` | déploiement que l'on peut rejouer, relire et versionner |
| [Argo CD (GitOps)](docs/openshift-argocd.md) | les mêmes manifests, appliqués par Argo CD depuis GitHub | Git est la source de vérité : un `git push` met le cluster à jour |

Les trois guides aboutissent au même résultat. Le guide Argo CD prend la suite du guide déclaratif : il reprend les objets créés par `oc apply`. En revanche, ne pas mélanger l'impératif avec les deux autres dans un même projet : les Deployments créés par `oc new-app` ne peuvent pas être repris par `oc apply` ni par Argo CD. Il faut d'abord supprimer le projet.

## Prérequis

```bash
crc start                                           # démarre la VM (24 Go configurés)
eval $(crc oc-env)                                  # met oc dans le PATH
oc login -u kubeadmin https://api.crc.testing:6443  # mot de passe : crc console --credentials
```

Console web : <https://console-openshift-console.apps-crc.testing>

## URLs une fois déployé

| URL | Quoi |
|---|---|
| <https://frontend-spring-ms.apps-crc.testing> | le front |
| <https://user-service-spring-ms.apps-crc.testing/swagger-ui.html> | Swagger de user-service |
| <https://notification-service-spring-ms.apps-crc.testing/swagger-ui.html> | Swagger de notification-service |
| <https://rabbitmq-console-spring-ms.apps-crc.testing> | console RabbitMQ (`guest` / `guest`) |
| <https://prometheus-spring-ms.apps-crc.testing/targets> | Prometheus : état des cibles |
| <https://grafana-spring-ms.apps-crc.testing> | Grafana, dashboard « Spring MS » (`admin` / `admin` pour modifier) |
| <https://opensearch-dashboards-spring-ms.apps-crc.testing> | OpenSearch Dashboards : logs des services (menu Discover) |
| <https://openshift-gitops-server-openshift-gitops.apps-crc.testing> | console Argo CD (« Log in via OpenShift ») |

Le certificat est auto-signé par CRC : il faut accepter l'alerte du navigateur.

gRPC n'a pas de Route. Pour l'atteindre depuis le poste : `oc port-forward svc/notification-service 9090`.

## Différences avec docker-compose

- **Ports :** au lieu d'un port publié par service, chaque Route a son propre nom de domaine, sur le port 443.
- **Noms internes :** les mêmes que dans docker-compose (`rabbitmq`, `user-service`, `notification-service`), car ce sont les noms des Services.
- **H2 en mémoire :** un seul pod par service, sinon chaque pod aurait ses propres données.
- **RabbitMQ :** ses données sont dans un `emptyDir`, et sont perdues si le pod est recréé.
- **Observabilité :** Prometheus et Grafana sont déployés, avec leur configuration dans des ConfigMaps (copies des fichiers de `observability/`). Les logs vont dans OpenSearch, mais Fluent Bit les lit dans les fichiers du nœud : sur OpenShift, il n'y a pas de driver de logs Docker.

## Tout supprimer

```bash
oc delete project spring-ms   # supprime tout ce qui a été créé dans le projet
crc stop                      # arrête la VM
```
