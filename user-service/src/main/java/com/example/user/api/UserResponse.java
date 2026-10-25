package com.example.user.api;

import java.time.Instant;

import com.example.user.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Utilisateur")
public record UserResponse(
        @Schema(description = "Identifiant", example = "1") Long id,
        @Schema(description = "Nom", example = "Alice") String name,
        @Schema(description = "Adresse email", example = "alice@example.com") String email,
        @Schema(description = "Date de création") Instant createdAt,
        @Schema(description = "Nombre de notifications (obtenu via gRPC) ; absent si notification-service est indisponible "
                + "ou pour les listes", example = "1", nullable = true) Long notificationCount) {

    public static UserResponse from(User user, Long notificationCount) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getCreatedAt(), notificationCount);
    }
}
