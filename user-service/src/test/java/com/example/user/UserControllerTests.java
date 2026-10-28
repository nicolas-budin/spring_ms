package com.example.user;

import java.util.ArrayList;
import java.util.List;

import com.example.grpc.notification.CountNotificationsRequest;
import com.example.grpc.notification.CountNotificationsResponse;
import com.example.grpc.notification.NotificationServiceGrpc;
import com.example.grpc.notification.SendNotificationRequest;
import com.example.grpc.notification.SendNotificationResponse;
import com.example.user.messaging.UserDeletedEvent;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vérifie l'API REST et l'appel gRPC sortant, en remplaçant notification-service par un faux service
 * gRPC exposé sur un transport in-process. RabbitMQ est remplacé par un mock de {@link RabbitTemplate}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestGrpcTransport
@AutoConfigureMetrics
class UserControllerTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FakeNotificationService notifications;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void resetNotifications() {
        notifications.received.clear();
    }

    @Test
    void createUserSendsWelcomeNotification() throws Exception {
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Alice", "email": "alice@example.com"}"""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Alice"))
            .andExpect(jsonPath("$.notificationCount").value(1));

        assertThat(notifications.received).singleElement()
            .satisfies(request -> assertThat(request.getMessage()).isEqualTo("Bienvenue Alice !"));

        mvc.perform(get("/api/users/{id}", notifications.received.getFirst().getUserId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notificationCount").value(1));
    }

    @Test
    void deleteUserPublishesEvent() throws Exception {
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Carol", "email": "carol@example.com"}"""))
            .andExpect(status().isCreated());
        long id = notifications.received.getFirst().getUserId();

        mvc.perform(delete("/api/users/{id}", id)).andExpect(status().isNoContent());

        verify(rabbitTemplate).convertAndSend("users", "user.deleted", new UserDeletedEvent(id));
        mvc.perform(get("/api/users/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void deleteUnknownUserReturns404() throws Exception {
        mvc.perform(delete("/api/users/{id}", 9999)).andExpect(status().isNotFound());

        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    @Test
    void invalidUserIsRejected() throws Exception {
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "", "email": "pas-un-email"}"""))
            .andExpect(status().isBadRequest());
    }

    @Test
    void openApiDocumentIsExposed() throws Exception {
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.info.title").value("User Service API"))
            .andExpect(jsonPath("$.paths['/api/users'].post").exists());
    }

    @Test
    void prometheusEndpointExposesMetrics() throws Exception {
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Bob", "email": "bob@example.com"}"""))
            .andExpect(status().isCreated());

        mvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("http_server_requests_seconds_count{application=\"user-service\"")))
            .andExpect(content().string(containsString("grpc_client_seconds_count{application=\"user-service\"")));
    }

    @TestConfiguration
    static class FakeNotificationConfig {

        @Bean
        FakeNotificationService fakeNotificationService() {
            return new FakeNotificationService();
        }
    }

    static class FakeNotificationService extends NotificationServiceGrpc.NotificationServiceImplBase {

        final List<SendNotificationRequest> received = new ArrayList<>();

        @Override
        public void sendNotification(SendNotificationRequest request,
                StreamObserver<SendNotificationResponse> responseObserver) {
            received.add(request);
            responseObserver.onNext(SendNotificationResponse.newBuilder()
                .setNotificationId(received.size())
                .setStatus("SENT")
                .build());
            responseObserver.onCompleted();
        }

        @Override
        public void countNotifications(CountNotificationsRequest request,
                StreamObserver<CountNotificationsResponse> responseObserver) {
            long count = received.stream().filter(r -> r.getUserId() == request.getUserId()).count();
            responseObserver.onNext(CountNotificationsResponse.newBuilder().setCount(count).build());
            responseObserver.onCompleted();
        }
    }
}
