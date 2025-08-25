package com.controlup.api.service;

import com.controlup.api.dto.DeviceMetricResponse;
import com.controlup.api.dto.TopDeviceMetricResponse;
import com.controlup.api.repository.CpuMetricsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MetricsService {

    private static final Logger logger = LoggerFactory.getLogger(MetricsService.class);
    
    private final CpuMetricsRepository repository;

    @Autowired
    public MetricsService(CpuMetricsRepository repository) {
        this.repository = repository;
    }

    /**
     * Retrieves the latest CPU usage percentile metric for a specific device.
     * 
     * @param deviceId the unique identifier of the device
     * @return the latest percentile metric for the device, or null if not found
     */
    public DeviceMetricResponse getLatestMetricForDevice(String deviceId) {
        logger.info("Retrieving latest metric for device: {}", deviceId);
        
        return repository.findLatestByDeviceId(deviceId)
                .orElseGet(() -> {
                    logger.warn("No metrics found for device: {}", deviceId);
                    return null;
                });
    }

    /**
     * Retrieves the top N devices by 95th percentile from the latest complete window.
     * 
     * @param n the number of top devices to retrieve
     * @return list of top N devices ordered by percentile descending
     */
    public List<TopDeviceMetricResponse> getTopDevicesByPercentile(int n) {
        logger.info("Retrieving top {} devices by percentile", n);
        
        List<TopDeviceMetricResponse> topDevices = repository.findTopNByPercentile(n);
        
        logger.info("Retrieved {} top devices by percentile", topDevices.size());
        return topDevices;
    }
}