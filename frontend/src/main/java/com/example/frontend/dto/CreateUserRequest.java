package com.example.frontend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateUserRequest(@NotBlank String name, @NotBlank @Email String email) {

    public static CreateUserRequest empty() {
        return new CreateUserRequest("", "");
    }
}
