package com.uws.transaction_service.config;

import com.uws.transaction_service.events.CreditConfirmedEvent;
import com.uws.transaction_service.events.DebitConfirmedEvent;
import com.uws.transaction_service.events.FraudCheckFailedEvent;
import com.uws.transaction_service.events.FraudCheckPassedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Consumes events from wallet.events and fraud.events topics
 */
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    private Map<String, Object> baseConsumerConfig(String groupId) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // Manual commit for reliability
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);
        return props;
    }

    // ========== WALLET EVENTS CONSUMERS ==========

    /**
     * Consumer for DebitConfirmedEvent from Wallet Service
     */
    @Bean
    public ConsumerFactory<String, DebitConfirmedEvent> debitConfirmedConsumerFactory() {
        Map<String, Object> props = baseConsumerConfig("transaction-service-wallet-group");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, DebitConfirmedEvent.class);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, DebitConfirmedEvent>
    debitConfirmedKafkaListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, DebitConfirmedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(debitConfirmedConsumerFactory());
        factory.getContainerProperties().setAckMode(
                org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL);
        return factory;
    }

    /**
     * Consumer for CreditConfirmedEvent from Wallet Service
     */
    @Bean
    public ConsumerFactory<String, CreditConfirmedEvent> creditConfirmedConsumerFactory() {
        Map<String, Object> props = baseConsumerConfig("transaction-service-wallet-group");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, CreditConfirmedEvent.class);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CreditConfirmedEvent>
    creditConfirmedKafkaListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, CreditConfirmedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(creditConfirmedConsumerFactory());
        factory.getContainerProperties().setAckMode(
                org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL);
        return factory;
    }

    // ========== FRAUD EVENTS CONSUMERS ==========

    /**
     * Consumer for FraudCheckPassedEvent from Fraud Service
     */
    @Bean
    public ConsumerFactory<String, FraudCheckPassedEvent> fraudPassedConsumerFactory() {
        Map<String, Object> props = baseConsumerConfig("transaction-service-fraud-group");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, FraudCheckPassedEvent.class);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, FraudCheckPassedEvent>
    fraudPassedKafkaListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, FraudCheckPassedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(fraudPassedConsumerFactory());
        factory.getContainerProperties().setAckMode(
                org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL);
        return factory;
    }

    /**
     * Consumer for FraudCheckFailedEvent from Fraud Service
     */
    @Bean
    public ConsumerFactory<String, FraudCheckFailedEvent> fraudFailedConsumerFactory() {
        Map<String, Object> props = baseConsumerConfig("transaction-service-fraud-group");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, FraudCheckFailedEvent.class);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, FraudCheckFailedEvent>
    fraudFailedKafkaListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, FraudCheckFailedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(fraudFailedConsumerFactory());
        factory.getContainerProperties().setAckMode(
                org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL);
        return factory;
    }
}