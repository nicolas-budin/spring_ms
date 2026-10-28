package com.example.notification.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Déclare la file de notification-service et la relie à l'exchange "users" de user-service : chaque message
 * publié avec la clé "user.deleted" y est copié, et y attend tant que notification-service ne l'a pas consommé.
 */
@Configuration
public class MessagingConfig {

    public static final String USER_DELETED_QUEUE = "notification.user-deleted";

    @Bean
    TopicExchange usersExchange() {
        return new TopicExchange("users");
    }

    @Bean
    Queue userDeletedQueue() {
        return new Queue(USER_DELETED_QUEUE);
    }

    @Bean
    Binding userDeletedBinding(Queue userDeletedQueue, TopicExchange usersExchange) {
        return BindingBuilder.bind(userDeletedQueue).to(usersExchange).with("user.deleted");
    }

    /**
     * Lit le JSON envoyé par user-service ; le type cible est déduit du paramètre du listener.
     */
    @Bean
    MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
