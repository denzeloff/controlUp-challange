package com.controlup.handler.entity;

import com.controlup.handler.model.ProcessedCpuEvent;

import java.time.Instant;
import java.util.Objects;

public class CpuMetric {

    private Long id;
    private String eventId;
    private String deviceId;
    private Long windowStart;
    private Long windowEnd;
    private Double percentile95;
    private Long lastUpdated;
    private Instant createdAt;

    public CpuMetric() {
    }

    public CpuMetric(String eventId, String deviceId, Long windowStart, Long windowEnd,
                     Double percentile95, Long lastUpdated) {
        this.eventId = eventId;
        this.deviceId = deviceId;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.percentile95 = percentile95;
        this.lastUpdated = lastUpdated;
        this.createdAt = Instant.now();
    }

    // Factory method from ProcessedCpuEvent
    public static CpuMetric fromProcessedEvent(ProcessedCpuEvent event) {
        return new CpuMetric(
                event.getId(),
                event.getDeviceId(),
                event.getWindowStart(),
                event.getWindowEnd(),
                event.getPercentile95(),
                event.getLastUpdated()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventId() {
        return eventId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public Long getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(Long windowStart) {
        this.windowStart = windowStart;
    }

    public Long getWindowEnd() {
        return windowEnd;
    }

    public void setWindowEnd(Long windowEnd) {
        this.windowEnd = windowEnd;
    }

    public Double getPercentile95() {
        return percentile95;
    }

    public void setPercentile95(Double percentile95) {
        this.percentile95 = percentile95;
    }

    public Long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CpuMetric that = (CpuMetric) o;
        return Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }

    @Override
    public String toString() {
        return "CpuMetric{" +
                "id=" + id +
                ", deviceId='" + deviceId + '\'' +
                ", windowStart=" + windowStart +
                ", windowEnd=" + windowEnd +
                ", percentile95=" + percentile95 +
                ", lastUpdated=" + lastUpdated +
                ", createdAt=" + createdAt +
                '}';
    }
}
