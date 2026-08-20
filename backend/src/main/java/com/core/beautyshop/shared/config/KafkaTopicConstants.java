package com.core.beautyshop.shared.config;

/**
 * Constants defining Kafka Topic names and Consumer Group IDs.
 */
public final class KafkaTopicConstants {

    private KafkaTopicConstants() {
        // Prevent instantiation
    }

    // Order Topics
    public static final String ORDER_CREATED_TOPIC = "order.created";
    public static final String ORDER_CANCELLED_TOPIC = "order.cancelled";
    public static final String ORDER_STATUS_CHANGED_TOPIC = "order.status-changed";

    // Notification Topics
    public static final String NOTIFICATION_EMAIL_TOPIC = "notification.email";

    // Consumer Groups
    public static final String NOTIFICATION_GROUP_ID = "beautyshop-notification-group";
    public static final String AUDIT_LOG_GROUP_ID = "beautyshop-audit-group";
}
