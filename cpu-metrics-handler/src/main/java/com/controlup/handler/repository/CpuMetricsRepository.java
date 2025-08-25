package com.controlup.handler.repository;

import com.controlup.handler.entity.CpuMetric;
import com.controlup.handler.model.ProcessedCpuEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class CpuMetricsRepository {

    private static final Logger logger = LoggerFactory.getLogger(CpuMetricsRepository.class);

    private final JdbcTemplate jdbcTemplate;

    private static final String INSERT_SQL = """
        INSERT INTO "cpu-metrics".cpu_usage_95_percentile 
        (event_id, device_id, window_start, window_end, percentile_95, last_updated, created_at) 
        VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
        ON CONFLICT (event_id) DO NOTHING
        """;

    @Autowired
    public CpuMetricsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsertMetric(CpuMetric metric) {
        try {
            int rowsAffected = jdbcTemplate.update(INSERT_SQL,
                metric.getEventId(),
                metric.getDeviceId(),
                metric.getWindowStart(),
                metric.getWindowEnd(),
                metric.getPercentile95(),
                metric.getLastUpdated());

            logger.trace("Upserted metric for device {}: {} rows affected", metric.getDeviceId(), rowsAffected);

        } catch (DataIntegrityViolationException e) {
            logger.debug("Data integrity violation for device {}: {} - likely duplicate processing",
                        metric.getEventId(), e.getMessage());
            // Don't rethrow - this is expected in high-concurrency scenarios
        }
    }

    public void batchUpsertMetrics(List<CpuMetric> metrics) {
        if (metrics.isEmpty()) {
            return;
        }

        try {
            List<Object[]> batchArgs = metrics.stream()
                .map(metric -> new Object[]{
                    metric.getEventId(),
                    metric.getDeviceId(),
                    metric.getWindowStart(),
                    metric.getWindowEnd(),
                    metric.getPercentile95(),
                    metric.getLastUpdated()
                })
                .collect(Collectors.toList());

            int[] rowsAffected = jdbcTemplate.batchUpdate(INSERT_SQL, batchArgs);

            logger.debug("Batch upserted {} metrics, total rows affected: {}",
                        metrics.size(), java.util.Arrays.stream(rowsAffected).sum());

        } catch (DataIntegrityViolationException e) {
            logger.debug("Data integrity violation in batch upsert: {} - likely duplicate processing",
                        e.getMessage());
            // Fallback to individual upserts for better error isolation
            logger.info("Falling back to individual upserts for batch of {} metrics", metrics.size());
            for (CpuMetric metric : metrics) {
                upsertMetric(metric);
            }
        }
    }
}
