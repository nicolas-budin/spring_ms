package com.example.notification;

import com.example.grpc.notification.CountNotificationsRequest;
import com.example.grpc.notification.NotificationServiceGrpc;
import com.example.grpc.notification.SendNotificationRequest;
import com.example.grpc.notification.SendNotificationResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.grpc.client.GrpcChannelFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@SpringBootTest
@AutoConfigureTestGrpcTransport
class NotificationGrpcServiceTests {

    @Autowired
    private GrpcChannelFactory channels;

    @Test
    void sendThenCountNotifications() {
        var stub = NotificationServiceGrpc.newBlockingStub(channels.createChannel("test"));

        SendNotificationResponse response = stub.sendNotification(SendNotificationRequest.newBuilder()
            .setUserId(42)
            .setEmail("bob@example.com")
            .setMessage("Bienvenue Bob !")
            .build());

        assertThat(response.getStatus()).isEqualTo("SENT");
        assertThat(response.getNotificationId()).isPositive();
        assertThat(stub.countNotifications(CountNotificationsRequest.newBuilder().setUserId(42).build()).getCount())
            .isEqualTo(1);
    }

    @Test
    void blankMessageIsRejected() {
        var stub = NotificationServiceGrpc.newBlockingStub(channels.createChannel("test"));

        assertThatExceptionOfType(StatusRuntimeException.class)
            .isThrownBy(() -> stub.sendNotification(SendNotificationRequest.newBuilder().setUserId(1).build()))
            .extracting(StatusRuntimeException::getStatus)
            .extracting(Status::getCode)
            .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }
}
