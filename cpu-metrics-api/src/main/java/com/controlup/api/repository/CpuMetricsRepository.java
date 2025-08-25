package com.controlup.api.repository;

import com.controlup.api.dto.DeviceMetricResponse;
import com.controlup.api.dto.TopDeviceMetricResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class CpuMetricsRepository {

    private static final Logger logger = LoggerFactory.getLogger(CpuMetricsRepository.class);

    private final JdbcTemplate jdbcTemplate;

    private static final String FIND_LATEST_BY_DEVICE_SQL = """
        SELECT device_id, percentile_95, window_start, window_end, last_updated
        FROM "cpu-metrics".cpu_usage_95_percentile
        WHERE device_id = ?
        ORDER BY last_updated DESC
        LIMIT 1
        """;

    private static final String FIND_TOP_N_BY_PERCENTILE_SQL = """
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
        SELECT device_id, percentile_95, window_start, window_end, last_updated
        FROM latest_metrics
        ORDER BY percentile_95 DESC
        LIMIT ?
        """;

    @Autowired
    public CpuMetricsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<DeviceMetricResponse> findLatestByDeviceId(String deviceId) {
        logger.debug("Finding latest metric for device: {}", deviceId);

        List<DeviceMetricResponse> results = jdbcTemplate.query(
                FIND_LATEST_BY_DEVICE_SQL,
                new DeviceMetricRowMapper(),
                deviceId
        );

        if (results.isEmpty()) {
            logger.debug("No metrics found for device: {}", deviceId);
            return Optional.empty();
        }

        logger.debug("Found latest metric for device {}: percentile95={}",
                    deviceId, results.getFirst().percentile95());
        return Optional.of(results.getFirst());
    }

    public List<TopDeviceMetricResponse> findTopNByPercentile(int limit) {
        logger.debug("Finding top {} devices by percentile from latest complete window", limit);

        List<TopDeviceMetricResponse> results = jdbcTemplate.query(
                FIND_TOP_N_BY_PERCENTILE_SQL,
                new Object[]{limit},
                new TopDeviceMetricRowMapper()
        );

        logger.debug("Found {} top devices by percentile", results.size());
        return results;
    }

    private static class DeviceMetricRowMapper implements RowMapper<DeviceMetricResponse> {
        @Override
        public DeviceMetricResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new DeviceMetricResponse(
                    rs.getString("device_id"),
                    rs.getDouble("percentile_95"),
                    rs.getLong("window_start"),
                    rs.getLong("window_end"),
                    rs.getLong("last_updated")
            );
        }
    }

    private static class TopDeviceMetricRowMapper implements RowMapper<TopDeviceMetricResponse> {
        @Override
        public TopDeviceMetricResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new TopDeviceMetricResponse(
                    rs.getString("device_id"),
                    rs.getDouble("percentile_95"),
                    rs.getLong("window_start"),
                    rs.getLong("window_end"),
                    rs.getLong("last_updated")
            );
        }
    }
}
