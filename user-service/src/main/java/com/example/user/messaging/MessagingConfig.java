package com.example.user.messaging;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessagingConfig {

    public static final String USERS_EXCHANGE = "users";

    public static final String USER_DELETED_KEY = "user.deleted";

    /**
     * Exchange où user-service publie ses événements ; il est déclaré au premier envoi s'il n'existe pas.
     */
    @Bean
    TopicExchange usersExchange() {
        return new TopicExchange(USERS_EXCHANGE);
    }

    /**
     * Les messages sont envoyés en JSON plutôt qu'en sérialisation Java.
     */
    @Bean
    MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
