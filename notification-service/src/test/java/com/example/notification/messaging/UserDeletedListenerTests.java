package com.example.notification.messaging;

import com.example.notification.Notification;
import com.example.notification.NotificationRepository;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Appelle directement le listener : pas besoin de broker RabbitMQ (le conteneur d'écoute n'est pas démarré).
 */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@AutoConfigureTestGrpcTransport
class UserDeletedListenerTests {

    @Autowired
    private UserDeletedListener listener;

    @Autowired
    private NotificationRepository repository;

    @Test
    void deletesOnlyNotificationsOfDeletedUser() {
        repository.save(new Notification(100L, "a@example.com", "Bienvenue A"));
        repository.save(new Notification(100L, "a@example.com", "Autre message"));
        repository.save(new Notification(200L, "b@example.com", "Bienvenue B"));

        listener.onUserDeleted(new UserDeletedEvent(100L));

        assertThat(repository.countByUserId(100L)).isZero();
        assertThat(repository.countByUserId(200L)).isEqualTo(1);
    }
}
