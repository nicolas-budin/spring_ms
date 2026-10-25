package com.example.frontend.dto;

import java.time.Instant;

public record NotificationDto(Long id, Long userId, String email, String message, Instant createdAt) {
}
