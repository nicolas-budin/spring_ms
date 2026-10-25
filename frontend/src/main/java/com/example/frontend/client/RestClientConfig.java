package com.example.frontend.client;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    RestClient userRestClient(RestClient.Builder builder, ServicesProperties services) {
        return builder.clone().baseUrl(services.user().url()).build();
    }

    @Bean
    RestClient notificationRestClient(RestClient.Builder builder, ServicesProperties services) {
        return builder.clone().baseUrl(services.notification().url()).build();
    }
}
