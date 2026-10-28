package com.example.user.messaging;

/**
 * Événement publié dans RabbitMQ quand un utilisateur est supprimé. Sérialisé en JSON : {@code {"userId": 42}}.
 */
public record UserDeletedEvent(Long userId) {
}
