package com.controlup.test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * CPU usage event for testing
 */
public class CpuUsageEvent {
    
    private final String deviceId;
    private final long timestamp;
    private final double cpuUsage;
    
    @JsonCreator
    public CpuUsageEvent(
            @JsonProperty("deviceId") String deviceId,
            @JsonProperty("timestamp") long timestamp,
            @JsonProperty("cpuUsage") double cpuUsage) {
        this.deviceId = deviceId;
        this.timestamp = timestamp;
        this.cpuUsage = cpuUsage;
    }
    
    public String getDeviceId() {
        return deviceId;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public double getCpuUsage() {
        return cpuUsage;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CpuUsageEvent that = (CpuUsageEvent) o;
        return timestamp == that.timestamp
            && Double.compare(that.cpuUsage, cpuUsage) == 0
            && Objects.equals(deviceId, that.deviceId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(deviceId, timestamp, cpuUsage);
    }
    
    @Override
    public String toString() {
        return "CpuUsageEvent{" +
                "deviceId='" + deviceId + '\'' +
                ", timestamp=" + timestamp +
                ", cpuUsage=" + cpuUsage +
                '}';
    }
}