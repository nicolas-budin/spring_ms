package com.example.frontend.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * URLs des backends, surchargeables par variables d'environnement (SERVICES_USER_URL, ...).
 */
@ConfigurationProperties("services")
public record ServicesProperties(Service user, Service notification) {

    public record Service(String url, String swaggerUrl) {
    }
}
