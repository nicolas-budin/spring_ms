package com.example.notification.messaging;

import com.example.notification.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consomme les événements user.deleted : les notifications de l'utilisateur supprimé sont effacées.
 */
@Component
public class UserDeletedListener {

    private static final Logger log = LoggerFactory.getLogger(UserDeletedListener.class);

    private final NotificationRepository repository;

    public UserDeletedListener(NotificationRepository repository) {
        this.repository = repository;
    }

    @RabbitListener(queues = MessagingConfig.USER_DELETED_QUEUE)
    @Transactional
    public void onUserDeleted(UserDeletedEvent event) {
        long deleted = repository.deleteByUserId(event.userId());
        log.info("Utilisateur {} supprimé : {} notification(s) effacée(s)", event.userId(), deleted);
    }
}
