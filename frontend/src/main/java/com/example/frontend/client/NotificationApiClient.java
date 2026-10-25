package com.example.frontend.client;

import java.util.List;

import com.example.frontend.dto.NotificationDto;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class NotificationApiClient {

    private final RestClient restClient;

    public NotificationApiClient(@Qualifier("notificationRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public List<NotificationDto> findAll() {
        return restClient.get().uri("/api/notifications").retrieve().body(new ParameterizedTypeReference<>() {
        });
    }

    public List<NotificationDto> findByUser(Long userId) {
        return restClient.get()
            .uri(uri -> uri.path("/api/notifications").queryParam("userId", userId).build())
            .retrieve()
            .body(new ParameterizedTypeReference<>() {
            });
    }
}
