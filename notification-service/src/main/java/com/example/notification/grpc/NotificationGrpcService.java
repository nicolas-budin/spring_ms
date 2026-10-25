package com.example.notification.grpc;

import com.example.grpc.notification.CountNotificationsRequest;
import com.example.grpc.notification.CountNotificationsResponse;
import com.example.grpc.notification.NotificationServiceGrpc;
import com.example.grpc.notification.SendNotificationRequest;
import com.example.grpc.notification.SendNotificationResponse;
import com.example.notification.Notification;
import com.example.notification.NotificationRepository;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

/**
 * Implémentation du service gRPC défini dans notification.proto.
 */
@Service
public class NotificationGrpcService extends NotificationServiceGrpc.NotificationServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(NotificationGrpcService.class);

    private final NotificationRepository repository;

    public NotificationGrpcService(NotificationRepository repository) {
        this.repository = repository;
    }

    @Override
    public void sendNotification(SendNotificationRequest request,
            StreamObserver<SendNotificationResponse> responseObserver) {
        if (request.getMessage().isBlank()) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                .withDescription("message must not be blank")
                .asRuntimeException());
            return;
        }
        Notification saved = repository
            .save(new Notification(request.getUserId(), request.getEmail(), request.getMessage()));
        log.info("Notification {} envoyée à {} (user {})", saved.getId(), saved.getEmail(), saved.getUserId());
        responseObserver.onNext(SendNotificationResponse.newBuilder()
            .setNotificationId(saved.getId())
            .setStatus("SENT")
            .build());
        responseObserver.onCompleted();
    }

    @Override
    public void countNotifications(CountNotificationsRequest request,
            StreamObserver<CountNotificationsResponse> responseObserver) {
        responseObserver.onNext(CountNotificationsResponse.newBuilder()
            .setCount(repository.countByUserId(request.getUserId()))
            .build());
        responseObserver.onCompleted();
    }
}
