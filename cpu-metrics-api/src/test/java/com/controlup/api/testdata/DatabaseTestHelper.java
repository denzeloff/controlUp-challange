package com.controlup.api.testdata;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.jdbc.JdbcTestUtils;

import java.util.List;

/**
 * Helper class for managing test data in integration tests
 */
public class DatabaseTestHelper {
    
    private final JdbcTemplate jdbcTemplate;
    private static final String TABLE_NAME = "\"cpu-metrics\".cpu_usage_95_percentile";
    
    public DatabaseTestHelper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * Clean all test data from the database
     */
    public void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE " + TABLE_NAME + " RESTART IDENTITY CASCADE");
    }
    
    /**
     * Insert test data into the database
     */
    public void insertTestData(List<CpuMetricTestData> testData) {
        testData.forEach(this::insertTestData);
    }
    
    /**
     * Insert single test data record
     */
    public void insertTestData(CpuMetricTestData data) {
        jdbcTemplate.update(
            "INSERT INTO " + TABLE_NAME + " " +
            "(event_id, device_id, window_start, window_end, percentile_95, last_updated) " +
            "VALUES (?, ?, ?, ?, ?, ?)",
            data.eventId(),
            data.deviceId(),
            data.windowStart(),
            data.windowEnd(),
            data.percentile95(),
            data.lastUpdated()
        );
    }
    
    /**
     * Count total records in the table
     */
    public int countRecords() {
        return JdbcTestUtils.countRowsInTable(jdbcTemplate, TABLE_NAME);
    }
    
    /**
     * Count records for specific device
     */
    public int countRecordsForDevice(String deviceId) {
        return JdbcTestUtils.countRowsInTableWhere(
            jdbcTemplate, 
            TABLE_NAME, 
            "device_id = '" + deviceId + "'"
        );
    }
    
    /**
     * Get all device IDs from the database
     */
    public List<String> getAllDeviceIds() {
        return jdbcTemplate.queryForList(
            "SELECT DISTINCT device_id FROM " + TABLE_NAME + " ORDER BY device_id",
            String.class
        );
    }
    
    /**
     * Get the latest timestamp in the database
     */
    public Long getLatestTimestamp() {
        List<Long> results = jdbcTemplate.queryForList(
            "SELECT MAX(last_updated) FROM " + TABLE_NAME,
            Long.class
        );
        return results.isEmpty() ? null : results.get(0);
    }
    
    /**
     * Get count of records with latest timestamp
     */
    public int countLatestRecords() {
        Long latestTimestamp = getLatestTimestamp();
        if (latestTimestamp == null) {
            return 0;
        }
        
        return JdbcTestUtils.countRowsInTableWhere(
            jdbcTemplate,
            TABLE_NAME,
            "last_updated = " + latestTimestamp
        );
    }
    
    /**
     * Verify database schema exists
     */
    public boolean schemaExists() {
        try {
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + TABLE_NAME, Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Get maximum percentile value in database
     */
    public Double getMaxPercentile() {
        List<Double> results = jdbcTemplate.queryForList(
            "SELECT MAX(percentile_95) FROM " + TABLE_NAME,
            Double.class
        );
        return results.isEmpty() ? null : results.get(0);
    }
    
    /**
     * Get minimum percentile value in database  
     */
    public Double getMinPercentile() {
        List<Double> results = jdbcTemplate.queryForList(
            "SELECT MIN(percentile_95) FROM " + TABLE_NAME,
            Double.class
        );
        return results.isEmpty() ? null : results.get(0);
    }
    
    /**
     * Execute the exact same query as the repository to verify results
     */
    public List<String> getTopNDeviceIds(int n) {
        String sql = """
            WITH latest_window AS (
                SELECT MAX(window_end) as max_window_end
                FROM "cpu-metrics".cpu_usage_95_percentile
            ),
            latest_metrics AS (
                SELECT DISTINCT ON (device_id) 
                       device_id, percentile_95, window_start, window_end, last_updated
                FROM "cpu-metrics".cpu_usage_95_percentile
                WHERE window_end = (SELECT max_window_end FROM latest_window)
                ORDER BY device_id, last_updated DESC
            )
            SELECT device_id
            FROM latest_metrics
            ORDER BY percentile_95 DESC
            LIMIT ?
            """;
            
        return jdbcTemplate.queryForList(sql, String.class, n);
    }
}