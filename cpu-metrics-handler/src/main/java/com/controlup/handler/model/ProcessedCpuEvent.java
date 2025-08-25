package com.controlup.handler.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a processed CPU usage event with 95th percentile calculation
 * This model matches the structure produced by the cpu-usage-processor
 */
public class ProcessedCpuEvent {

    private String id;
    private String deviceId;
    private long windowStart;
    private long windowEnd;
    private double percentile95;
    private long lastUpdated;

    public ProcessedCpuEvent() {
    }

    @JsonCreator
    public ProcessedCpuEvent(
            @JsonProperty("id") String id,
            @JsonProperty("deviceId") String deviceId,
            @JsonProperty("windowStart") long windowStart,
            @JsonProperty("windowEnd") long windowEnd,
            @JsonProperty("percentile95") double percentile95,
            @JsonProperty("lastUpdated") long lastUpdated) {
        this.id = id;
        this.deviceId = deviceId;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.percentile95 = percentile95;
        this.lastUpdated = lastUpdated;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public void setWindowStart(long windowStart) {
        this.windowStart = windowStart;
    }

    public void setWindowEnd(long windowEnd) {
        this.windowEnd = windowEnd;
    }

    public void setPercentile95(double percentile95) {
        this.percentile95 = percentile95;
    }

    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public String getId() {
        return id;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public long getWindowStart() {
        return windowStart;
    }

    public long getWindowEnd() {
        return windowEnd;
    }

    public double getPercentile95() {
        return percentile95;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    @JsonIgnore
    public Instant getWindowStartAsInstant() {
        return Instant.ofEpochMilli(windowStart);
    }

    @JsonIgnore
    public Instant getWindowEndAsInstant() {
        return Instant.ofEpochMilli(windowEnd);
    }

    @JsonIgnore
    public Instant getLastUpdatedAsInstant() {
        return Instant.ofEpochMilli(lastUpdated);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProcessedCpuEvent that = (ProcessedCpuEvent) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ProcessedCpuEvent{" +
                "id='" + id + '\'' +
                ", deviceId='" + deviceId + '\'' +
                ", windowStart=" + windowStart +
                ", windowEnd=" + windowEnd +
                ", percentile95=" + String.format("%.2f", percentile95) +
                ", lastUpdated=" + lastUpdated +
                '}';
    }
}
