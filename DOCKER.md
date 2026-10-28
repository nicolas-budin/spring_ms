# Lancer et arrêter les services

Toutes les commandes se lancent depuis la racine du projet.

## Ports

| Service | Port hôte (Docker) | Port en local (jar) |
|---|---|---|
| frontend | 8088 (modifiable avec `FRONTEND_PORT`) | 8080 |
| user-service | 8081 | 8081 |
| notification-service | 8082 (REST), 9090 (gRPC) | 8082, 9090 |
| RabbitMQ | 5672 (AMQP), 15672 (console, `guest` / `guest`) | 5672, 15672 |
| Prometheus | 9091 | — |
| Grafana | 3000 | — |

Dans Docker, le front est publié sur 8088 et non sur 8080, parce que 8080 est souvent déjà pris (par Keycloak, par exemple).
Pour choisir un autre port : `FRONTEND_PORT=9000 docker compose up -d`.

## Avec Docker Compose

### Démarrer

```bash
docker compose up -d            # sans reconstruire les images
docker compose up -d --build    # après une modification du code
```

Sans `-d`, les logs s'affichent dans le terminal, et Ctrl+C arrête les conteneurs.

Une fois les services démarrés :

- Front : http://localhost:8088
- Swagger user-service : http://localhost:8081/swagger-ui.html
- Swagger notification-service : http://localhost:8082/swagger-ui.html
- Grafana : http://localhost:3000 (dashboard « Spring MS »)
- Prometheus : http://localhost:9091

### Arrêter

```bash
docker compose down
```

Tous les conteneurs (les 3 services, Prometheus et Grafana) sont arrêtés et supprimés. Les bases H2 sont en mémoire : **les données sont perdues à chaque arrêt**, de même que l'historique
des métriques de Prometheus.

### Commandes utiles

```bash
docker compose ps                         # état des conteneurs
docker compose logs -f                    # logs des 3 services
docker compose logs -f user-service       # logs d'un seul service
docker compose restart user-service       # redémarrer un seul service
docker compose up -d --build --no-deps frontend   # reconstruire et relancer un seul service, sans redémarrer les autres
```

## Sans Docker (jars en local)

Il faut d'abord libérer les ports s'ils sont pris par les conteneurs : `docker compose down`.

### Démarrer

```bash
mvn clean install
java -jar notification-service/target/notification-service-0.0.1-SNAPSHOT.jar &
java -jar user-service/target/user-service-0.0.1-SNAPSHOT.jar &
java -jar frontend/target/frontend-0.0.1-SNAPSHOT.jar &
```

Il vaut mieux démarrer notification-service en premier. Si user-service démarre avant, il fonctionne quand même, mais les
utilisateurs créés pendant ce temps ne reçoivent pas de notification de bienvenue.

Si le port 8080 est déjà pris, on peut lancer le front sur un autre port :

```bash
java -jar frontend/target/frontend-0.0.1-SNAPSHOT.jar --server.port=8088 &
```

### Arrêter

```bash
pkill -f -- '-0.0.1-SNAPSHOT.jar'
```
