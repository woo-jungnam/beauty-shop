package com.core.beautyshop.shared.config;

public final class KafkaTopicConstants {

    private KafkaTopicConstants() {
    }

    public static final String ORDER_CREATED_TOPIC = "order.created";
    public static final String ORDER_CANCELLED_TOPIC = "order.cancelled";
    public static final String ORDER_STATUS_CHANGED_TOPIC = "order.status-changed";

    public static final String NOTIFICATION_EMAIL_TOPIC = "notification.email";

    public static final String NOTIFICATION_GROUP_ID = "beautyshop-notification-group";
    public static final String AUDIT_LOG_GROUP_ID = "beautyshop-audit-group";
}
