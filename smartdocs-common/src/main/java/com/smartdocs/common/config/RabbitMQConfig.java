package com.smartdocs.common.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
public class RabbitMQConfig {

    // Queue names
    public static final String DOCUMENT_PROCESSING_QUEUE = "document.processing";

    // Exchange names
    public static final String SMARTDOCS_EXCHANGE = "smartdocs.exchange";

    // Routing keys
    public static final String DOCUMENT_PROCESS_KEY = "document.process";

    /**
     * Main exchange for routing messages
     */
    @Bean
    public DirectExchange smartDocsExchange() {
        return new DirectExchange(SMARTDOCS_EXCHANGE, true, false);
    }

    /**
     * Queue for document processing (AI extraction)
     */
    @Bean
    public Queue documentProcessingQueue() {
        return QueueBuilder.durable(DOCUMENT_PROCESSING_QUEUE)
                .withArgument("x-dead-letter-exchange", "smartdocs.dlx")
                .build();
    }

    /**
     * Binding for document processing
     */
    @Bean
    public Binding documentProcessingBinding() {
        return BindingBuilder
                .bind(documentProcessingQueue())
                .to(smartDocsExchange())
                .with(DOCUMENT_PROCESS_KEY);
    }

    /**
     * JSON message converter
     */
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitTemplate with JSON converter
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}
