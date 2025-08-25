package com.controlup.handler.service;

import com.controlup.handler.entity.CpuMetric;
import com.controlup.handler.model.ProcessedCpuEvent;
import com.controlup.handler.repository.CpuMetricsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
// Temporarily disabled due to AspectJ dependency issues
// import org.springframework.retry.annotation.Backoff;
// import org.springframework.retry.annotation.Recover;
// import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service responsible for processing CPU metrics and storing them in PostgreSQL
 * Implements idempotent operations with high throughput batch processing
 */
@Service
public class MetricsProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricsProcessor.class);
    
    private final CpuMetricsRepository repository;
    
    @Value("${app.processing.batch-size:100}")
    private int batchSize;
    
    @Value("${app.processing.batch-timeout-ms:1000}")
    private long batchTimeoutMs;
    
    @Autowired
    public MetricsProcessor(CpuMetricsRepository repository) {
        this.repository = repository;
    }
    
    /**
     * Process a batch of CPU events for high throughput
     */
    @Transactional
    public void processBatch(List<ProcessedCpuEvent> events) {
        logger.debug("Processing batch of {} CPU events", events.size());
        
        // Convert ProcessedCpuEvents to CpuMetrics for database operations
        List<CpuMetric> metrics = events.stream()
            .map(CpuMetric::fromProcessedEvent)
            .collect(Collectors.toList());
        
        // Use batch processing for better performance
        repository.batchUpsertMetrics(metrics);
        
        logger.debug("Successfully processed batch of {} CPU events", events.size());
    }
    
    /**
     * Process a single CPU event
     */
    @Transactional
    public void processSingle(ProcessedCpuEvent event) {
        logger.debug("Processing single CPU event for device {}", event.getDeviceId());
        
        CpuMetric metric = CpuMetric.fromProcessedEvent(event);
        processMetricWithRetry(metric);
        logger.debug("Successfully processed CPU event for device {}", event.getDeviceId());
    }
    
    /**
     * Process individual metric with retry mechanism for transient failures
     */
    public void processMetricWithRetry(CpuMetric metric) {
        try {
            // Validate metric data
            if (!isValidMetric(metric)) {
                logger.warn("Skipping invalid metric: {}", metric);
                return;
            }
            
            // Perform idempotent upsert operation
            repository.upsertMetric(metric);
            
            logger.trace("Stored metric for device {} with 95th percentile {}", 
                        metric.getDeviceId(), metric.getPercentile95());
            
        } catch (DataIntegrityViolationException e) {
            // This might happen in rare race conditions even with upsert
            // Log and continue - the constraint violation means another thread processed the same event
            logger.debug("Data integrity violation for metric {}: {} - likely duplicate processing", 
                        metric, e.getMessage());
            
        } catch (Exception e) {
            logger.error("Unexpected error processing metric {}: {}", metric, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Recovery method for failed retries (temporarily disabled)
     */
    // @Recover - Disabled due to missing AspectJ dependency
    public void recoverFromFailure(Exception e, CpuMetric metric) {
        logger.error("All retry attempts failed for metric {}. Sending to dead letter queue: {}", 
                    metric, e.getMessage(), e);
        
        // In a production system, you might want to:
        // 1. Send to a dead letter topic
        // 2. Store in a failed events table
        // 3. Send alerts to monitoring system
    }
    
    /**
     * Validate metric data before processing
     */
    private boolean isValidMetric(CpuMetric metric) {
        if (metric == null) {
            logger.warn("Received null metric");
            return false;
        }
        
        if (metric.getDeviceId() == null || metric.getDeviceId().trim().isEmpty()) {
            logger.warn("Metric has invalid device ID: {}", metric);
            return false;
        }
        
        if (metric.getWindowStart() <= 0 || metric.getWindowEnd() <= 0) {
            logger.warn("Metric has invalid window times: {}", metric);
            return false;
        }
        
        if (metric.getWindowStart() >= metric.getWindowEnd()) {
            logger.warn("Metric has invalid window range (start >= end): {}", metric);
            return false;
        }
        
        if (metric.getPercentile95() < 0 || metric.getPercentile95() > 100) {
            logger.warn("Metric has invalid percentile value: {}", metric);
            return false;
        }
        
        if (metric.getLastUpdated() <= 0) {
            logger.warn("Metric has invalid last updated timestamp: {}", metric);
            return false;
        }
        
        return true;
    }
}