package com.example.user.grpc;

import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;

import com.example.grpc.notification.CountNotificationsRequest;
import com.example.grpc.notification.NotificationServiceGrpc.NotificationServiceBlockingStub;
import com.example.grpc.notification.SendNotificationRequest;
import com.example.user.User;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

/**
 * Encapsule les appels gRPC vers notification-service. Une indisponibilité de notification-service
 * n'empêche pas user-service de fonctionner : l'erreur est journalisée et un résultat vide est renvoyé.
 */
@Component
public class NotificationClient {

    private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);

    private static final long TIMEOUT_SECONDS = 3;

    private final NotificationServiceBlockingStub stub;

    public NotificationClient(NotificationServiceBlockingStub stub) {
        this.stub = stub;
    }

    public boolean sendWelcome(User user) {
        try {
            var response = stub.withDeadlineAfter(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .sendNotification(SendNotificationRequest.newBuilder()
                    .setUserId(user.getId())
                    .setEmail(user.getEmail())
                    .setMessage("Bienvenue " + user.getName() + " !")
                    .build());
            log.info("Notification de bienvenue {} pour l'utilisateur {}", response.getNotificationId(), user.getId());
            return true;
        }
        catch (StatusRuntimeException ex) {
            log.warn("Envoi de la notification impossible pour l'utilisateur {} : {}", user.getId(), ex.getStatus());
            return false;
        }
    }

    public OptionalLong countNotifications(Long userId) {
        try {
            return OptionalLong.of(stub.withDeadlineAfter(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .countNotifications(CountNotificationsRequest.newBuilder().setUserId(userId).build())
                .getCount());
        }
        catch (StatusRuntimeException ex) {
            log.warn("Comptage des notifications impossible pour l'utilisateur {} : {}", userId, ex.getStatus());
            return OptionalLong.empty();
        }
    }
}
