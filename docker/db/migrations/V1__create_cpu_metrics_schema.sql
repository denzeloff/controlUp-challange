-- Create schema for CPU metrics
CREATE SCHEMA IF NOT EXISTS "cpu-metrics";

-- Create table optimized for latest percentile queries and top N rankings
CREATE TABLE "cpu-metrics".cpu_usage_95_percentile (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(255) UNIQUE NOT NULL,
    device_id VARCHAR(255) NOT NULL,
    window_start BIGINT NOT NULL,
    window_end BIGINT NOT NULL,
    percentile_95 DOUBLE PRECISION NOT NULL,
    last_updated BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- PRIMARY INDEX: Optimized for GET /metrics/devices/{deviceId}
-- This allows fast lookup of latest percentile for a specific device based on last_updated
CREATE INDEX idx_device_latest_updated
ON "cpu-metrics".cpu_usage_95_percentile (device_id, last_updated DESC);

-- SECONDARY INDEX: Optimized for GET /metrics/top/{n}
-- This allows fast ranking by percentile_95 for the latest complete windows
CREATE INDEX idx_percentile_ranking
ON "cpu-metrics".cpu_usage_95_percentile (last_updated DESC, percentile_95 DESC, device_id);

-- Add comments for documentation
COMMENT ON SCHEMA "cpu-metrics" IS 'Schema for CPU usage processing and analytics';
COMMENT ON TABLE "cpu-metrics".cpu_usage_95_percentile IS 'Stores 95th percentile CPU usage calculations optimized for latest and top-N queries';
COMMENT ON COLUMN "cpu-metrics".cpu_usage_95_percentile.device_id IS 'Unique identifier for the device/host';
COMMENT ON COLUMN "cpu-metrics".cpu_usage_95_percentile.window_start IS 'Window start time in milliseconds (epoch)';
COMMENT ON COLUMN "cpu-metrics".cpu_usage_95_percentile.window_end IS 'Window end time in milliseconds (epoch)';
COMMENT ON COLUMN "cpu-metrics".cpu_usage_95_percentile.percentile_95 IS '95th percentile of CPU usage within the time window';
COMMENT ON COLUMN "cpu-metrics".cpu_usage_95_percentile.last_updated IS 'Timestamp when this record was last updated (epoch milliseconds)';

-- Sample optimized queries that this schema supports:

-- Query 1: GET /metrics/devices/{deviceId} - Latest percentile for specific device by last_updated
-- SELECT device_id, percentile_95, window_start, window_end, last_updated
-- FROM "cpu-metrics".cpu_usage_95_percentile
-- WHERE device_id = ?
-- ORDER BY last_updated DESC
-- LIMIT 1;

-- Query 2: GET /metrics/top/{n} - Top N devices by 95th percentile from most recent updates
-- WITH latest_update AS (
--   SELECT MAX(last_updated) as max_last_updated
--   FROM "cpu-usage-processor".processed_cpu_events
-- )
-- SELECT device_id, percentile_95, window_start, window_end, last_updated
-- FROM "cpu-metrics".cpu_usage_95_percentile
-- WHERE last_updated = (SELECT max_last_updated FROM latest_update)
-- ORDER BY percentile_95 DESC
-- LIMIT ?;
--
-- Alternative: Top N devices by percentile from latest batch of updates (within time window)
-- SELECT DISTINCT ON (device_id) device_id, percentile_95, window_start, window_end, last_updated
-- FROM "cpu-metrics".cpu_usage_95_percentile
-- WHERE last_updated >= (SELECT MAX(last_updated) - 60000 FROM "cpu-usage-processor".processed_cpu_events) -- within last minute
-- ORDER BY device_id, last_updated DESC, percentile_95 DESC
-- LIMIT ?;
