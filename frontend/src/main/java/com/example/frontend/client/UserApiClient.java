package com.example.frontend.client;

import java.util.List;

import com.example.frontend.dto.CreateUserRequest;
import com.example.frontend.dto.UserDto;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class UserApiClient {

    private final RestClient restClient;

    public UserApiClient(@Qualifier("userRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public List<UserDto> findAll() {
        return restClient.get().uri("/api/users").retrieve().body(new ParameterizedTypeReference<>() {
        });
    }

    public UserDto findById(Long id) {
        return restClient.get().uri("/api/users/{id}", id).retrieve().body(UserDto.class);
    }

    public UserDto create(CreateUserRequest request) {
        return restClient.post().uri("/api/users").body(request).retrieve().body(UserDto.class);
    }
}
