package com.controlup.api.testdata;

/**
 * Test data record representing a CPU metric entry for testing
 */
public record CpuMetricTestData(
    String eventId,
    String deviceId,
    long windowStart,
    long windowEnd,
    double percentile95,
    long lastUpdated
) {
    
    /**
     * Create test data with current timestamp
     */
    public static CpuMetricTestData create(String deviceId, double percentile95) {
        long now = System.currentTimeMillis();
        return new CpuMetricTestData(
            java.util.UUID.randomUUID().toString(),
            deviceId,
            now - 60000, // 1 minute window
            now,
            percentile95,
            now
        );
    }
    
    /**
     * Create test data with specific timestamps
     */
    public static CpuMetricTestData create(String deviceId, double percentile95, 
                                          long windowStart, long windowEnd, long lastUpdated) {
        return new CpuMetricTestData(
            java.util.UUID.randomUUID().toString(),
            deviceId,
            windowStart,
            windowEnd,
            percentile95,
            lastUpdated
        );
    }
    
    /**
     * SQL for inserting this test data
     */
    public String toInsertSql() {
        return String.format(
            "INSERT INTO \"cpu-metrics\".cpu_usage_95_percentile " +
            "(event_id, device_id, window_start, window_end, percentile_95, last_updated) " +
            "VALUES ('%s', '%s', %d, %d, %.3f, %d)",
            eventId, deviceId, windowStart, windowEnd, percentile95, lastUpdated
        );
    }
}