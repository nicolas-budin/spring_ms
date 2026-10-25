package com.example.notification;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
public class Notification {

    @Id
    @GeneratedValue
    private Long id;

    private Long userId;

    private String email;

    private String message;

    private Instant createdAt;

    protected Notification() {
    }

    public Notification(Long userId, String email, String message) {
        this.userId = userId;
        this.email = email;
        this.message = message;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
