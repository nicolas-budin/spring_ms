package com.example.user.api;

import java.net.URI;
import java.util.List;

import com.example.user.User;
import com.example.user.UserRepository;
import com.example.user.grpc.NotificationClient;
import com.example.user.messaging.UserEventPublisher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Utilisateurs", description = "Gestion des utilisateurs")
public class UserController {

    private final UserRepository repository;

    private final NotificationClient notificationClient;

    private final UserEventPublisher events;

    public UserController(UserRepository repository, NotificationClient notificationClient,
            UserEventPublisher events) {
        this.repository = repository;
        this.notificationClient = notificationClient;
        this.events = events;
    }

    @GetMapping
    @Operation(summary = "Lister les utilisateurs")
    @ApiResponse(responseCode = "200", description = "Liste des utilisateurs")
    public List<UserResponse> list() {
        return repository.findAll().stream().map(user -> UserResponse.from(user, null)).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Récupérer un utilisateur",
            description = "Retourne l'utilisateur enrichi du nombre de notifications obtenu via gRPC")
    @ApiResponse(responseCode = "200", description = "Utilisateur trouvé")
    @ApiResponse(responseCode = "404", description = "Utilisateur inconnu")
    public ResponseEntity<UserResponse> get(
            @Parameter(description = "Identifiant de l'utilisateur", example = "1") @PathVariable Long id) {
        return ResponseEntity.of(repository.findById(id).map(user -> {
            var count = notificationClient.countNotifications(user.getId());
            return UserResponse.from(user, count.isPresent() ? count.getAsLong() : null);
        }));
    }

    @PostMapping
    @Operation(summary = "Créer un utilisateur",
            description = "Crée l'utilisateur puis demande à notification-service, via gRPC, "
                    + "l'envoi d'une notification de bienvenue")
    @ApiResponse(responseCode = "201", description = "Utilisateur créé")
    @ApiResponse(responseCode = "400", description = "Données invalides")
    @ApiResponse(responseCode = "409", description = "Email déjà utilisé")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        if (repository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email déjà utilisé : " + request.email());
        }
        User user = repository.save(new User(request.name(), request.email()));
        boolean sent = notificationClient.sendWelcome(user);
        return ResponseEntity.created(URI.create("/api/users/" + user.getId()))
            .body(UserResponse.from(user, sent ? 1L : null));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un utilisateur",
            description = "Supprime l'utilisateur puis publie l'événement user.deleted dans RabbitMQ : "
                    + "notification-service supprime alors ses notifications")
    @ApiResponse(responseCode = "204", description = "Utilisateur supprimé")
    @ApiResponse(responseCode = "404", description = "Utilisateur inconnu")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Identifiant de l'utilisateur", example = "1") @PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        events.userDeleted(id);
        return ResponseEntity.noContent().build();
    }
}
