package com.example.user.grpc;

import com.example.grpc.notification.NotificationServiceGrpc;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

@Configuration
public class GrpcClientConfig {

    /**
     * Stub bloquant vers notification-service. L'adresse du canal "notification" est définie par
     * {@code spring.grpc.client.channel.notification.target}.
     */
    @Bean
    NotificationServiceGrpc.NotificationServiceBlockingStub notificationStub(GrpcChannelFactory channels) {
        return NotificationServiceGrpc.newBlockingStub(channels.createChannel("notification"));
    }
}
