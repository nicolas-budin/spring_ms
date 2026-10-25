# spring-ms

Exemple de 3 microservices Spring Boot 4.1 (Java 25) :

| Service | Rôle | Ports |
|---|---|---|
| `frontend` | UI Thymeleaf, appelle les 2 backends en **REST** (`RestClient`) | 8080 |
| `user-service` | API REST utilisateurs (H2/JPA), **client gRPC** de notification-service | 8081 |
| `notification-service` | **Serveur gRPC** + API REST de consultation (H2/JPA) | 8082 (REST), 9090 (gRPC) |

```
frontend ──REST──► user-service ──gRPC──► notification-service
    └──────────────────REST──────────────────────┘
```

Lors de la création d'un utilisateur, `user-service` appelle `SendNotification` en gRPC ;
`GET /api/users/{id}` appelle `CountNotifications` pour renseigner `notificationCount`.
Le contrat est dans [grpc-api/src/main/proto/notification.proto](grpc-api/src/main/proto/notification.proto),
les stubs Java sont générés au build dans le module `grpc-api`.

Si notification-service est indisponible, l'utilisateur est quand même créé
(`notificationCount` vaut `null`) et l'erreur est journalisée.

## Lancer en local

```bash
mvn clean install
java -jar notification-service/target/notification-service-0.0.1-SNAPSHOT.jar
java -jar user-service/target/user-service-0.0.1-SNAPSHOT.jar
java -jar frontend/target/frontend-0.0.1-SNAPSHOT.jar
```

Puis ouvrir http://localhost:8080.

## Lancer avec Docker

```bash
docker compose up --build
```

Le front est alors sur http://localhost:8088 (port hôte modifiable : `FRONTEND_PORT=9000 docker compose up`).
Pour arrêter, redémarrer ou consulter les logs, voir [DOCKER.md](DOCKER.md).

## Documentation des API (OpenAPI / Swagger)

| Service | Swagger UI | OpenAPI JSON |
|---|---|---|
| user-service | http://localhost:8081/swagger-ui.html | http://localhost:8081/v3/api-docs |
| notification-service | http://localhost:8082/swagger-ui.html | http://localhost:8082/v3/api-docs |

## Tester le serveur gRPC

notification-service expose gRPC en clair (sans TLS) sur `localhost:9090`. La server reflection est activée : un
client peut découvrir les méthodes sans avoir le `.proto`.

### Avec Postman

1. **New → gRPC**, URL `localhost:9090` (sans `http://`, TLS désactivé).
2. **Select a method → Using server reflection**, puis choisir
   `notification.NotificationService / SendNotification` ou `CountNotifications`.
   Si la reflection ne fonctionne pas : **Import a .proto file** avec
   [grpc-api/src/main/proto/notification.proto](grpc-api/src/main/proto/notification.proto).
3. Saisir le message (ou **Generate Example Message**), puis **Invoke**.

`SendNotification` :

```json
{ "user_id": 1, "email": "alice@example.com", "message": "Hello via gRPC" }
```

Réponse : `{ "notification_id": "…", "status": "SENT" }`. Les `int64` sont affichés entre guillemets.
Un `message` vide renvoie `3 INVALID_ARGUMENT`.

`CountNotifications` :

```json
{ "user_id": 1 }
```

La notification envoyée en gRPC apparaît ensuite en REST : `GET http://localhost:8082/api/notifications?userId=1`.

### Avec grpcurl

```bash
brew install grpcurl
grpcurl -plaintext localhost:9090 list
grpcurl -plaintext -d '{"user_id":1,"email":"alice@example.com","message":"Hello via gRPC"}' \
  localhost:9090 notification.NotificationService/SendNotification
grpcurl -plaintext -d '{"user_id":1}' localhost:9090 notification.NotificationService/CountNotifications
```

## Exemples

```bash
curl -X POST localhost:8081/api/users -H 'Content-Type: application/json' \
     -d '{"name":"Alice","email":"alice@example.com"}'
curl localhost:8081/api/users/1
curl 'localhost:8082/api/notifications?userId=1'
```

Consoles H2 : http://localhost:8081/h2-console (`jdbc:h2:mem:users`) et
http://localhost:8082/h2-console (`jdbc:h2:mem:notifications`), utilisateur `sa`, sans mot de passe.
