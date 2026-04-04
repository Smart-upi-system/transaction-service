package com.uws.transaction_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaTopicStarter {
    @Bean
    public NewTopic transactionTopic(){
        return new NewTopic("transaction.events",3,(short) 1);
    }
}
