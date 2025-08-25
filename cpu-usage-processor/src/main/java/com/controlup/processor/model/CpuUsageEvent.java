package com.controlup.processor.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a CPU usage event from monitoring system
 */
public class CpuUsageEvent {
    
    private String deviceId;
    private long timestamp;
    private double cpuUsage;
    
    // Default constructor for Jackson deserialization
    public CpuUsageEvent() {
    }
    
    @JsonCreator
    public CpuUsageEvent(
            @JsonProperty("deviceId") String deviceId,
            @JsonProperty("timestamp") long timestamp,
            @JsonProperty("cpuUsage") double cpuUsage) {
        this.deviceId = deviceId;
        this.timestamp = timestamp;
        this.cpuUsage = cpuUsage;
    }
    
    // Setters for Jackson deserialization
    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public void setCpuUsage(double cpuUsage) {
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
    
    public Instant getTimestampAsInstant() {
        return Instant.ofEpochMilli(timestamp);
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
