package com.core.beautyshop.shared.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name="app.spa.notifications.enabled", havingValue="true")
public class SpaNotificationKafkaConfiguration {
    @Bean
    public NewTopic spaNotificationTopic() {
        return TopicBuilder.name("spa.notification").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic spaNotificationDltTopic() {
        return TopicBuilder.name("spa.notification.DLT").partitions(3).replicas(1).build();
    }
}
