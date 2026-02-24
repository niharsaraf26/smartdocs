package com.smartdocs.documents.service;

import com.smartdocs.common.config.RabbitMQConfig;
import com.smartdocs.common.entity.Document;
import com.smartdocs.common.message.DocumentProcessingMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class DocumentProcessingPublisher {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * Queue document for AI processing
     */
    public void queueForProcessing(Document document) {
        DocumentProcessingMessage message = DocumentProcessingMessage.from(document);

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.SMARTDOCS_EXCHANGE,
                RabbitMQConfig.DOCUMENT_PROCESS_KEY,
                message
        );

        log.info("Queued document for processing: {}", document.getId());
    }
}
