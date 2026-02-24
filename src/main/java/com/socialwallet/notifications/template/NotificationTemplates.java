package com.socialwallet.notifications.template;

import com.socialwallet.notifications.model.NotificationChannel;
import com.socialwallet.notifications.model.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Notification templates defined as Java constants.
 * Templates change with code, not at runtime — no need for a database table.
 * <p>
 * Lookup by (type, channel, locale). Fallback: user locale → "fr" → generic.
 */
public final class NotificationTemplates {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");
    private static final String DEFAULT_LOCALE = "fr";

    @Getter
    @AllArgsConstructor
    public static class Template {
        private final String title;
        private final String body;
    }

    /**
     * Key: "TYPE:CHANNEL:LOCALE"
     */
    private static final Map<String, Template> TEMPLATES = Map.ofEntries(
            // ===================== PUSH FR =====================
            entry(NotificationType.MESSAGE_RECEIVED, NotificationChannel.PUSH, "fr",
                    "Nouveau message", "{{senderName}} : {{messagePreview}}"),
            entry(NotificationType.CALL_INCOMING, NotificationChannel.PUSH, "fr",
                    "Appel entrant", "{{callerName}} vous appelle"),
            entry(NotificationType.CALL_MISSED, NotificationChannel.PUSH, "fr",
                    "Appel manqué", "Appel manqué de {{callerName}}"),
            entry(NotificationType.TIP_RECEIVED, NotificationChannel.PUSH, "fr",
                    "Argent reçu", "{{senderName}} vous a envoyé {{amount}} {{currency}}"),
            entry(NotificationType.DEPOSIT_CONFIRMED, NotificationChannel.PUSH, "fr",
                    "Dépôt confirmé", "Dépôt de {{amount}} {{currency}} confirmé"),
            entry(NotificationType.DEPOSIT_FAILED, NotificationChannel.PUSH, "fr",
                    "Échec du dépôt", "Votre dépôt de {{amount}} {{currency}} a échoué"),
            entry(NotificationType.PAYOUT_COMPLETED, NotificationChannel.PUSH, "fr",
                    "Retrait effectué", "Retrait de {{amount}} {{currency}} envoyé vers {{destination}}"),
            entry(NotificationType.PAYOUT_FAILED, NotificationChannel.PUSH, "fr",
                    "Échec du retrait", "Votre retrait de {{amount}} {{currency}} a échoué"),
            entry(NotificationType.PURCHASE_COMPLETED, NotificationChannel.PUSH, "fr",
                    "Achat confirmé", "Vous avez acheté {{productTitle}} pour {{amount}} {{currency}}"),
            entry(NotificationType.PRODUCT_SOLD, NotificationChannel.PUSH, "fr",
                    "Vente réalisée !", "{{buyerName}} a acheté {{productTitle}} — {{amount}} {{currency}}"),
            entry(NotificationType.KYC_APPROVED, NotificationChannel.PUSH, "fr",
                    "Identité vérifiée", "Votre identité a été vérifiée. Retraits activés."),
            entry(NotificationType.KYC_REJECTED, NotificationChannel.PUSH, "fr",
                    "Vérification refusée", "Votre vérification d'identité a été refusée : {{reason}}"),
            entry(NotificationType.ACCOUNT_LOGIN, NotificationChannel.PUSH, "fr",
                    "Nouvelle connexion", "Connexion détectée depuis {{deviceName}}"),
            entry(NotificationType.SUSPICIOUS_LOGIN, NotificationChannel.PUSH, "fr",
                    "Connexion suspecte", "Tentative de connexion suspecte depuis {{location}}"),
            entry(NotificationType.STORY_VIEWED, NotificationChannel.PUSH, "fr",
                    "Story vue", "{{viewerName}} a vu votre story"),
            entry(NotificationType.USER_BANNED, NotificationChannel.PUSH, "fr",
                    "Compte suspendu", "Votre compte a été suspendu : {{reason}}"),
            entry(NotificationType.BAN_APPEAL_RESOLVED, NotificationChannel.PUSH, "fr",
                    "Résultat de l'appel", "Votre appel a été {{resolution}}"),
            entry(NotificationType.MODERATION_WARNING, NotificationChannel.PUSH, "fr",
                    "Avertissement", "Votre contenu a été signalé : {{reason}}"),
            entry(NotificationType.WALLET_FROZEN, NotificationChannel.PUSH, "fr",
                    "Portefeuille gelé", "Votre portefeuille a été temporairement gelé"),

            // ===================== SMS FR =====================
            entry(NotificationType.OTP_REQUESTED, NotificationChannel.SMS, "fr",
                    null, "Votre code SocialWallet : {{otpCode}}. Valide {{validityMinutes}} min. Ne le partagez pas."),
            entry(NotificationType.TIP_RECEIVED, NotificationChannel.SMS, "fr",
                    null, "Vous avez reçu {{amount}} {{currency}} de {{senderName}}. Solde : {{newBalance}} {{currency}}"),
            entry(NotificationType.DEPOSIT_CONFIRMED, NotificationChannel.SMS, "fr",
                    null, "Dépôt de {{amount}} {{currency}} confirmé. Solde : {{newBalance}} {{currency}}"),
            entry(NotificationType.DEPOSIT_FAILED, NotificationChannel.SMS, "fr",
                    null, "Échec du dépôt de {{amount}} {{currency}}. Veuillez réessayer."),
            entry(NotificationType.PAYOUT_COMPLETED, NotificationChannel.SMS, "fr",
                    null, "Retrait de {{amount}} {{currency}} envoyé vers {{destination}}"),
            entry(NotificationType.PAYOUT_FAILED, NotificationChannel.SMS, "fr",
                    null, "Échec du retrait de {{amount}} {{currency}}. Fonds restitués."),
            entry(NotificationType.PRODUCT_SOLD, NotificationChannel.SMS, "fr",
                    null, "Vente ! {{buyerName}} a acheté {{productTitle}} — {{amount}} {{currency}}"),
            entry(NotificationType.KYC_APPROVED, NotificationChannel.SMS, "fr",
                    null, "SocialWallet : Identité vérifiée. Retraits activés."),
            entry(NotificationType.SUSPICIOUS_LOGIN, NotificationChannel.SMS, "fr",
                    null, "Connexion suspecte détectée sur votre compte SocialWallet. Si ce n'est pas vous, changez votre mot de passe."),
            entry(NotificationType.WALLET_FROZEN, NotificationChannel.SMS, "fr",
                    null, "Votre portefeuille SocialWallet a été temporairement gelé. Contactez le support.")
    );

    private NotificationTemplates() {}

    /**
     * Resolve a template for the given type, channel, and locale.
     * Falls back to "fr" locale, then to a generic fallback.
     */
    public static Template resolve(NotificationType type, NotificationChannel channel, String locale) {
        // Try exact locale
        String key = buildKey(type, channel, locale);
        Template template = TEMPLATES.get(key);
        if (template != null) return template;

        // Fallback to default locale
        if (!DEFAULT_LOCALE.equals(locale)) {
            key = buildKey(type, channel, DEFAULT_LOCALE);
            template = TEMPLATES.get(key);
            if (template != null) return template;
        }

        // Generic fallback
        return new Template("Notification", type.name());
    }

    /**
     * Resolve template and substitute placeholders with actual values.
     */
    public static Template resolveAndFormat(NotificationType type, NotificationChannel channel,
                                            String locale, Map<String, Object> data) {
        Template template = resolve(type, channel, locale);
        String title = substitute(template.getTitle(), data);
        String body = substitute(template.getBody(), data);
        return new Template(title, body);
    }

    /**
     * Replace {{placeholders}} with values from the data map.
     */
    static String substitute(String text, Map<String, Object> data) {
        if (text == null || data == null) return text;
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = data.get(key);
            matcher.appendReplacement(result, Matcher.quoteReplacement(value != null ? String.valueOf(value) : ""));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String buildKey(NotificationType type, NotificationChannel channel, String locale) {
        return type.name() + ":" + channel.name() + ":" + locale;
    }

    private static Map.Entry<String, Template> entry(NotificationType type, NotificationChannel channel,
                                                      String locale, String title, String body) {
        return Map.entry(buildKey(type, channel, locale), new Template(title, body));
    }
}
