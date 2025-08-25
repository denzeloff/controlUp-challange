package com.controlup.handler.service;

import com.controlup.handler.model.ProcessedCpuEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * High-throughput Kafka consumer service for processing CPU metrics events
 * Implements batch processing and manual offset management for optimal performance
 */
@Service
public class KafkaConsumerService {
    
    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerService.class);
    
    private final MetricsProcessor metricsProcessor;
    
    @Value("${app.processing.batch-size:100}")
    private int batchSize;
    
    @Autowired
    public KafkaConsumerService(MetricsProcessor metricsProcessor) {
        this.metricsProcessor = metricsProcessor;
    }
    
    /**
     * Consumes messages from the processed-events topic with batch processing
     * Uses manual acknowledgment for better control over offset commits
     */
    @KafkaListener(
        topics = "${app.kafka.input-topic:processed-events}",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "3"
    )
    public void consumeProcessedEvents(
            @Payload List<ProcessedCpuEvent> events,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        logger.debug("Received batch of {} events from partition {} at offset {}", 
                    events.size(), partition, offset);
        
        try {
            // Process events synchronously
            metricsProcessor.processBatch(events);
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            logger.info("Successfully processed batch of {} events from partition {} at offset {}", 
                       events.size(), partition, offset);
            
        } catch (Exception e) {
            logger.error("Failed to process batch of {} events from partition {} at offset {}: {}", 
                        events.size(), partition, offset, e.getMessage(), e);
            
            // Don't acknowledge on failure - this will cause the consumer to retry or send to DLQ
            // depending on the retry configuration
            throw new RuntimeException("Batch processing failed", e);
        }
    }
    
    /**
     * Single event fallback consumer (for non-batch scenarios)
     */
    @KafkaListener(
        topics = "${app.kafka.input-topic:processed-events}",
        containerFactory = "singleEventKafkaListenerContainerFactory"
    )
    public void consumeSingleEvent(
            @Payload ProcessedCpuEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        logger.debug("Received single event for device {} from partition {} at offset {}", 
                    event.getDeviceId(), partition, offset);
        
        try {
            metricsProcessor.processSingle(event);
            acknowledgment.acknowledge();
            
            logger.debug("Successfully processed event for device {} from partition {} at offset {}", 
                        event.getDeviceId(), partition, offset);
            
        } catch (Exception e) {
            logger.error("Failed to process event for device {} from partition {} at offset {}: {}", 
                        event.getDeviceId(), partition, offset, e.getMessage(), e);
            throw new RuntimeException("Single event processing failed", e);
        }
    }
}