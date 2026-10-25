package com.example.notification.api;

import java.util.List;

import com.example.notification.NotificationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "Consultation des notifications envoyées")
public class NotificationController {

    private final NotificationRepository repository;

    public NotificationController(NotificationRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @Operation(summary = "Lister les notifications",
            description = "Retourne toutes les notifications, ou celles d'un utilisateur si userId est fourni")
    @ApiResponse(responseCode = "200", description = "Liste des notifications (plus récentes en premier)")
    public List<NotificationResponse> list(
            @Parameter(description = "Filtrer par identifiant d'utilisateur", example = "1")
            @RequestParam(required = false) Long userId) {
        var notifications = (userId == null) ? repository.findAllByOrderByCreatedAtDesc()
                : repository.findByUserIdOrderByCreatedAtDesc(userId);
        return notifications.stream().map(NotificationResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Récupérer une notification")
    @ApiResponse(responseCode = "200", description = "Notification trouvée")
    @ApiResponse(responseCode = "404", description = "Notification inconnue")
    public ResponseEntity<NotificationResponse> get(
            @Parameter(description = "Identifiant de la notification", example = "1") @PathVariable Long id) {
        return ResponseEntity.of(repository.findById(id).map(NotificationResponse::from));
    }
}
