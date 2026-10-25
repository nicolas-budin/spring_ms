package com.example.notification.api;

import java.time.Instant;

import com.example.notification.Notification;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Notification envoyée à un utilisateur")
public record NotificationResponse(
        @Schema(description = "Identifiant de la notification", example = "1") Long id,
        @Schema(description = "Identifiant de l'utilisateur destinataire", example = "1") Long userId,
        @Schema(description = "Email du destinataire", example = "alice@example.com") String email,
        @Schema(description = "Contenu de la notification", example = "Bienvenue Alice !") String message,
        @Schema(description = "Date de création") Instant createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getUserId(), notification.getEmail(),
                notification.getMessage(), notification.getCreatedAt());
    }
}
