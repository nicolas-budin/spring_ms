package com.example.user.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publie les événements utilisateur dans RabbitMQ. Comme pour les appels gRPC, une indisponibilité du broker
 * n'empêche pas user-service de fonctionner : l'erreur est journalisée.
 */
@Component
public class UserEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(UserEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public UserEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void userDeleted(Long userId) {
        try {
            rabbitTemplate.convertAndSend(MessagingConfig.USERS_EXCHANGE, MessagingConfig.USER_DELETED_KEY,
                    new UserDeletedEvent(userId));
            log.info("Événement {} publié pour l'utilisateur {}", MessagingConfig.USER_DELETED_KEY, userId);
        }
        catch (AmqpException ex) {
            log.warn("Publication impossible pour l'utilisateur {} : {}", userId, ex.getMessage());
        }
    }
}
