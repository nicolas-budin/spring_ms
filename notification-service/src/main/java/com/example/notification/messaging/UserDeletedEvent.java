package com.example.notification.messaging;

/**
 * Événement reçu de user-service quand un utilisateur est supprimé.
 */
public record UserDeletedEvent(Long userId) {
}
