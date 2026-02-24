package com.socialwallet.notifications.template;

import com.socialwallet.notifications.model.NotificationChannel;
import com.socialwallet.notifications.model.NotificationType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NotificationTemplatesTest {

    @Test
    void resolve_ExistingTemplate_ReturnsTemplate() {
        NotificationTemplates.Template template = NotificationTemplates.resolve(
                NotificationType.TIP_RECEIVED, NotificationChannel.PUSH, "fr");

        assertNotNull(template);
        assertEquals("Argent reçu", template.getTitle());
        assertTrue(template.getBody().contains("{{senderName}}"));
    }

    @Test
    void resolve_UnknownLocale_FallsBackToFr() {
        NotificationTemplates.Template template = NotificationTemplates.resolve(
                NotificationType.TIP_RECEIVED, NotificationChannel.PUSH, "de");

        assertNotNull(template);
        assertEquals("Argent reçu", template.getTitle());
    }

    @Test
    void resolve_UnknownTypeChannel_ReturnsGenericFallback() {
        // CALL_INCOMING has no SMS template (it's PUSH only)
        NotificationTemplates.Template template = NotificationTemplates.resolve(
                NotificationType.CALL_INCOMING, NotificationChannel.SMS, "fr");

        assertNotNull(template);
        assertEquals("Notification", template.getTitle());
    }

    @Test
    void resolveAndFormat_SubstitutesPlaceholders() {
        Map<String, Object> data = Map.of(
                "senderName", "Alice",
                "amount", "5000",
                "currency", "XAF"
        );

        NotificationTemplates.Template template = NotificationTemplates.resolveAndFormat(
                NotificationType.TIP_RECEIVED, NotificationChannel.PUSH, "fr", data);

        assertEquals("Argent reçu", template.getTitle());
        assertEquals("Alice vous a envoyé 5000 XAF", template.getBody());
    }

    @Test
    void resolveAndFormat_SMS_OTP_FormatsCorrectly() {
        Map<String, Object> data = Map.of(
                "otpCode", "123456",
                "validityMinutes", "5"
        );

        NotificationTemplates.Template template = NotificationTemplates.resolveAndFormat(
                NotificationType.OTP_REQUESTED, NotificationChannel.SMS, "fr", data);

        assertNull(template.getTitle()); // SMS has no title
        assertTrue(template.getBody().contains("123456"));
        assertTrue(template.getBody().contains("5 min"));
    }

    @Test
    void substitute_NullText_ReturnsNull() {
        assertNull(NotificationTemplates.substitute(null, Map.of()));
    }

    @Test
    void substitute_NullData_ReturnsOriginal() {
        assertEquals("Hello {{name}}", NotificationTemplates.substitute("Hello {{name}}", null));
    }

    @Test
    void substitute_MissingVariable_ReplacesWithEmpty() {
        String result = NotificationTemplates.substitute("Hello {{name}}!", Map.of());
        assertEquals("Hello !", result);
    }
}
