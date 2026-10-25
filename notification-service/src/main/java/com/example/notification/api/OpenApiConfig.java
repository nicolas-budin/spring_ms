package com.example.notification.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI notificationServiceOpenApi() {
        return new OpenAPI().info(new Info()
            .title("Notification Service API")
            .description("API REST de consultation des notifications. "
                    + "Les notifications sont créées par user-service via gRPC (port 9090).")
            .version("v1"));
    }
}
