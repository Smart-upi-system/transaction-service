package com.uws.transaction_service.config;

import com.uws.transaction_service.events.CreditConfirmedEvent;
import com.uws.transaction_service.events.DebitConfirmedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    /**
     * Shared base properties to avoid repetition
     */
    private Map<String, Object> baseProperties(String groupId) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false); // Ignore sender package info
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return props;
    }

    // ========== 1. GENERIC FACTORY (For Fraud & Misc Events) ==========

    @Bean
    @Primary // Used by default for Map-based listeners
    public ConsumerFactory<String, Object> genericConsumerFactory() {
        Map<String, Object> props = baseProperties("transaction-service-main-group");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "java.util.Map");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean("kafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(genericConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }

    // ========== 2. WALLET SPECIFIC FACTORIES (Strongly Typed) ==========

    @Bean
    public ConsumerFactory<String, DebitConfirmedEvent> debitConfirmedConsumerFactory() {
        Map<String, Object> props = baseProperties("transaction-service-wallet-group");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, DebitConfirmedEvent.class);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean("debitConfirmedKafkaListenerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, DebitConfirmedEvent> debitConfirmedKafkaListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, DebitConfirmedEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(debitConfirmedConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, CreditConfirmedEvent> creditConfirmedConsumerFactory() {
        Map<String, Object> props = baseProperties("transaction-service-wallet-group");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, CreditConfirmedEvent.class);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean("creditConfirmedKafkaListenerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, CreditConfirmedEvent> creditConfirmedKafkaListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, CreditConfirmedEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(creditConfirmedConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }
}