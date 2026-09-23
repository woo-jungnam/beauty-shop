package com.core.beautyshop.shared.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KafkaConfigTest {

    @Test
    void outboxPayloadUsesOneStringSerializationBoundary() {
        KafkaConfig config = new KafkaConfig();
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");
        ReflectionTestUtils.setField(config, "defaultGroupId", "test-group");

        DefaultKafkaProducerFactory<String, String> producerFactory =
                (DefaultKafkaProducerFactory<String, String>) config.producerFactory();
        DefaultKafkaConsumerFactory<String, String> consumerFactory =
                (DefaultKafkaConsumerFactory<String, String>) config.consumerFactory();

        assertEquals(StringSerializer.class,
                producerFactory.getConfigurationProperties().get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));
        assertEquals(ErrorHandlingDeserializer.class,
                consumerFactory.getConfigurationProperties().get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG));
        assertEquals(StringDeserializer.class.getName(),
                consumerFactory.getConfigurationProperties().get(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS));
    }
}
