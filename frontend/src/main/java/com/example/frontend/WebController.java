package com.example.frontend;

import java.util.List;
import java.util.function.Supplier;

import com.example.frontend.client.NotificationApiClient;
import com.example.frontend.client.ServicesProperties;
import com.example.frontend.client.UserApiClient;
import com.example.frontend.dto.CreateUserRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class WebController {

    private static final Logger log = LoggerFactory.getLogger(WebController.class);

    private final UserApiClient users;

    private final NotificationApiClient notifications;

    private final ServicesProperties services;

    public WebController(UserApiClient users, NotificationApiClient notifications, ServicesProperties services) {
        this.users = users;
        this.notifications = notifications;
        this.services = services;
    }

    @ModelAttribute("services")
    ServicesProperties services() {
        return services;
    }

    @GetMapping("/")
    String index(Model model) {
        if (!model.containsAttribute("user")) {
            model.addAttribute("user", CreateUserRequest.empty());
        }
        model.addAttribute("users", call(users::findAll, model, "user-service"));
        model.addAttribute("notifications", call(notifications::findAll, model, "notification-service"));
        return "index";
    }

    @PostMapping("/users")
    String createUser(@Valid @ModelAttribute("user") CreateUserRequest user, BindingResult binding, Model model,
            RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return index(model);
        }
        try {
            var created = users.create(user);
            redirect.addFlashAttribute("success", "Utilisateur " + created.name() + " créé");
        }
        catch (RestClientResponseException ex) {
            redirect.addFlashAttribute("error", "Création refusée (" + ex.getStatusCode().value() + ")");
        }
        catch (RestClientException ex) {
            redirect.addFlashAttribute("error", "user-service indisponible");
        }
        return "redirect:/";
    }

    @PostMapping("/users/{id}/delete")
    String deleteUser(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            users.delete(id);
            redirect.addFlashAttribute("success", "Utilisateur " + id + " supprimé");
        }
        catch (RestClientResponseException ex) {
            redirect.addFlashAttribute("error", "Suppression refusée (" + ex.getStatusCode().value() + ")");
        }
        catch (RestClientException ex) {
            redirect.addFlashAttribute("error", "user-service indisponible");
        }
        return "redirect:/";
    }

    @GetMapping("/users/{id}")
    String userDetail(@PathVariable Long id, Model model, RedirectAttributes redirect) {
        try {
            model.addAttribute("user", users.findById(id));
        }
        catch (HttpClientErrorException.NotFound ex) {
            redirect.addFlashAttribute("error", "Utilisateur " + id + " introuvable");
            return "redirect:/";
        }
        catch (RestClientException ex) {
            log.warn("Appel à user-service impossible : {}", ex.getMessage());
            redirect.addFlashAttribute("error", "user-service indisponible");
            return "redirect:/";
        }
        model.addAttribute("notifications", call(() -> notifications.findByUser(id), model, "notification-service"));
        return "user";
    }

    private <T> List<T> call(Supplier<List<T>> supplier, Model model, String service) {
        try {
            return supplier.get();
        }
        catch (RestClientException ex) {
            log.warn("Appel à {} impossible : {}", service, ex.getMessage());
            model.addAttribute("error", service + " indisponible");
            return List.of();
        }
    }
}
