package com.socialwallet.notifications.listener;

import com.socialwallet.notifications.event.chat.MessageSentEvent;
import com.socialwallet.notifications.model.NotificationType;
import com.socialwallet.notifications.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatEventListenerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private PrivacyChecker privacyChecker;

    @InjectMocks
    private ChatEventListener listener;

    private UUID senderId;
    private UUID recipientId;

    @BeforeEach
    void setUp() {
        senderId = UUID.randomUUID();
        recipientId = UUID.randomUUID();
    }

    @Test
    void onMessageSent_SenderNotBlocked_SendsNotification() {
        when(privacyChecker.isBlocked(recipientId, senderId)).thenReturn(false);

        MessageSentEvent event = MessageSentEvent.builder()
                .eventId("msg-1")
                .senderId(senderId)
                .recipientId(recipientId)
                .senderName("Alice")
                .messagePreview("Hello!")
                .conversationId(UUID.randomUUID())
                .build();

        listener.onMessageSent(event);

        verify(notificationService).send(eq(recipientId), eq(NotificationType.MESSAGE_RECEIVED), anyMap(), eq("msg-1"));
    }

    @Test
    void onMessageSent_SenderBlocked_SkipsNotification() {
        when(privacyChecker.isBlocked(recipientId, senderId)).thenReturn(true);

        MessageSentEvent event = MessageSentEvent.builder()
                .eventId("msg-2")
                .senderId(senderId)
                .recipientId(recipientId)
                .senderName("Bob")
                .messagePreview("Hi!")
                .conversationId(UUID.randomUUID())
                .build();

        listener.onMessageSent(event);

        verify(notificationService, never()).send(any(), any(), any(), any());
    }

    @Test
    void onMessageSent_ExceptionThrown_DoesNotPropagate() {
        when(privacyChecker.isBlocked(recipientId, senderId)).thenReturn(false);
        doThrow(new RuntimeException("DB down")).when(notificationService).send(any(), any(), any(), any());

        MessageSentEvent event = MessageSentEvent.builder()
                .eventId("msg-3")
                .senderId(senderId)
                .recipientId(recipientId)
                .senderName("Charlie")
                .messagePreview("Test")
                .conversationId(UUID.randomUUID())
                .build();

        // Should NOT throw
        assertDoesNotThrow(() -> listener.onMessageSent(event));
    }

    private void assertDoesNotThrow(Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            throw new AssertionError("Expected no exception but got: " + e.getMessage(), e);
        }
    }
}
