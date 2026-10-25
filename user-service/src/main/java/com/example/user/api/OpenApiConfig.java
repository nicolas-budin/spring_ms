package com.example.user.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI userServiceOpenApi() {
        return new OpenAPI().info(new Info()
            .title("User Service API")
            .description("API REST de gestion des utilisateurs. "
                    + "Chaque création déclenche un appel gRPC vers notification-service.")
            .version("v1"));
    }
}
