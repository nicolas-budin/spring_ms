package com.example.user.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Données de création d'un utilisateur")
public record CreateUserRequest(
        @Schema(description = "Nom de l'utilisateur", example = "Alice") @NotBlank @Size(max = 100) String name,
        @Schema(description = "Adresse email (unique)", example = "alice@example.com") @NotBlank @Email String email) {
}
