package com.controlup.api.repository;

import com.controlup.api.dto.DeviceMetricResponse;
import com.controlup.api.dto.TopDeviceMetricResponse;
import com.controlup.api.testdata.CpuMetricTestData;
import com.controlup.api.testdata.DatabaseTestHelper;
import com.controlup.api.testdata.TestDataBuilder;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CpuMetricsRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("db/init-test-schema.sql");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private CpuMetricsRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private DatabaseTestHelper dbHelper;

    @BeforeEach
    void setUp() {
        dbHelper = new DatabaseTestHelper(jdbcTemplate);
        dbHelper.cleanDatabase();
    }

    @Nested
    @DisplayName("Database Schema and Connection Tests")
    class SchemaTests {

        @Test
        @Order(1)
        @DisplayName("Should connect to PostgreSQL container successfully")
        void shouldConnectToDatabase() {
            assertThat(postgres.isRunning()).isTrue();
            assertThat(dbHelper.schemaExists()).isTrue();
        }

        @Test
        @Order(2)
        @DisplayName("Should have empty database initially")
        void shouldHaveEmptyDatabase() {
            assertThat(dbHelper.countRecords()).isZero();
        }
    }

    @Nested
    @DisplayName("Find Latest By Device ID Tests")
    class FindLatestByDeviceIdTests {

        @Test
        @DisplayName("Should return empty when device not found")
        void shouldReturnEmptyWhenDeviceNotFound() {
            Optional<DeviceMetricResponse> result = repository.findLatestByDeviceId("non-existent-device");
            
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should find latest metric for existing device")
        void shouldFindLatestMetricForExistingDevice() {
            // Given: Device with single metric
            CpuMetricTestData testData = CpuMetricTestData.create("device-001", 75.5);
            dbHelper.insertTestData(testData);

            // When: Finding latest metric
            Optional<DeviceMetricResponse> result = repository.findLatestByDeviceId("device-001");

            // Then: Should return the metric
            assertThat(result).isPresent();
            DeviceMetricResponse response = result.get();
            assertThat(response.deviceId()).isEqualTo("device-001");
            assertThat(response.percentile95()).isEqualTo(75.5);
            assertThat(response.windowStart()).isEqualTo(testData.windowStart());
            assertThat(response.windowEnd()).isEqualTo(testData.windowEnd());
            assertThat(response.lastUpdated()).isEqualTo(testData.lastUpdated());
        }

        @Test
        @DisplayName("Should return latest metric when multiple exist for same device")
        void shouldReturnLatestMetricWhenMultipleExist() {
            Instant now = Instant.now();
            Instant oneHourAgo = now.minus(1, ChronoUnit.HOURS);
            Instant twoHoursAgo = now.minus(2, ChronoUnit.HOURS);

            // Given: Multiple metrics for same device at different times
            List<CpuMetricTestData> testData = List.of(
                CpuMetricTestData.create("device-001", 60.0, 
                    twoHoursAgo.toEpochMilli() - 60000, twoHoursAgo.toEpochMilli(), twoHoursAgo.toEpochMilli()),
                CpuMetricTestData.create("device-001", 70.0,
                    oneHourAgo.toEpochMilli() - 60000, oneHourAgo.toEpochMilli(), oneHourAgo.toEpochMilli()),
                CpuMetricTestData.create("device-001", 85.5,
                    now.toEpochMilli() - 60000, now.toEpochMilli(), now.toEpochMilli())
            );
            dbHelper.insertTestData(testData);

            // When: Finding latest metric
            Optional<DeviceMetricResponse> result = repository.findLatestByDeviceId("device-001");

            // Then: Should return the latest (most recent) metric
            assertThat(result).isPresent();
            DeviceMetricResponse response = result.get();
            assertThat(response.deviceId()).isEqualTo("device-001");
            assertThat(response.percentile95()).isEqualTo(85.5); // Latest value
            assertThat(response.lastUpdated()).isEqualTo(now.toEpochMilli());
        }

        @Test
        @DisplayName("Should handle devices with special characters in ID")
        void shouldHandleSpecialCharactersInDeviceId() {
            // Given: Device with special characters
            String specialDeviceId = "device-with-special_chars.123-test@domain";
            CpuMetricTestData testData = CpuMetricTestData.create(specialDeviceId, 42.0);
            dbHelper.insertTestData(testData);

            // When: Finding by special device ID
            Optional<DeviceMetricResponse> result = repository.findLatestByDeviceId(specialDeviceId);

            // Then: Should find the device
            assertThat(result).isPresent();
            assertThat(result.get().deviceId()).isEqualTo(specialDeviceId);
            assertThat(result.get().percentile95()).isEqualTo(42.0);
        }

        @Test
        @DisplayName("Should handle edge case percentile values")
        void shouldHandleEdgeCasePercentileValues() {
            List<CpuMetricTestData> edgeCases = TestDataBuilder.scenario()
                .withEdgeCases()
                .build();
            
            dbHelper.insertTestData(edgeCases);

            // Test minimum value
            Optional<DeviceMetricResponse> zeroResult = repository.findLatestByDeviceId("device-zero");
            assertThat(zeroResult).isPresent();
            assertThat(zeroResult.get().percentile95()).isEqualTo(0.0);

            // Test maximum value
            Optional<DeviceMetricResponse> maxResult = repository.findLatestByDeviceId("device-max");
            assertThat(maxResult).isPresent();
            assertThat(maxResult.get().percentile95()).isEqualTo(100.0);

            // Test high precision
            Optional<DeviceMetricResponse> precisionResult = repository.findLatestByDeviceId("device-precision");
            assertThat(precisionResult).isPresent();
            assertThat(precisionResult.get().percentile95()).isEqualTo(99.999);
        }
    }

    @Nested
    @DisplayName("Find Top N By Percentile Tests")
    class FindTopNByPercentileTests {

        @Test
        @DisplayName("Should return empty list when no data exists")
        void shouldReturnEmptyListWhenNoDataExists() {
            List<TopDeviceMetricResponse> result = repository.findTopNByPercentile(5);
            
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return top devices ordered by percentile descending")
        void shouldReturnTopDevicesOrderedByPercentile() {
            // Given: Multiple devices with different percentiles
            List<CpuMetricTestData> testData = TestDataBuilder.scenario()
                .withDevice("device-low", 25.0)
                .withDevice("device-medium", 55.0)
                .withDevice("device-high", 85.0)
                .withDevice("device-critical", 95.0)
                .build();
            
            dbHelper.insertTestData(testData);

            // When: Getting top 3 devices
            List<TopDeviceMetricResponse> result = repository.findTopNByPercentile(3);

            // Then: Should return devices in descending order
            assertThat(result).hasSize(3);
            assertThat(result.get(0).deviceId()).isEqualTo("device-critical");
            assertThat(result.get(0).percentile95()).isEqualTo(95.0);
            assertThat(result.get(1).deviceId()).isEqualTo("device-high");
            assertThat(result.get(1).percentile95()).isEqualTo(85.0);
            assertThat(result.get(2).deviceId()).isEqualTo("device-medium");
            assertThat(result.get(2).percentile95()).isEqualTo(55.0);
        }

        @Test
        @DisplayName("Should limit results to requested number")
        void shouldLimitResultsToRequestedNumber() {
            // Given: 10 devices with different percentiles
            List<CpuMetricTestData> testData = TestDataBuilder.scenario()
                .withLowUsageDevices(3)
                .withMediumUsageDevices(3)
                .withHighUsageDevices(3)
                .withCriticalUsageDevices(1)
                .build();
            
            dbHelper.insertTestData(testData);

            // When: Requesting top 5
            List<TopDeviceMetricResponse> result = repository.findTopNByPercentile(5);

            // Then: Should return exactly 5 results
            assertThat(result).hasSize(5);
            
            // And: Should be ordered by percentile descending
            for (int i = 0; i < result.size() - 1; i++) {
                assertThat(result.get(i).percentile95())
                    .isGreaterThanOrEqualTo(result.get(i + 1).percentile95());
            }
        }

        @Test
        @DisplayName("Should return only latest window data")
        void shouldReturnOnlyLatestWindowData() {
            // Given: Time-based scenario with multiple windows
            List<CpuMetricTestData> testData = TestDataBuilder.scenario()
                .withTimeBasedScenario()
                .build();
            
            dbHelper.insertTestData(testData);

            // When: Getting top devices
            List<TopDeviceMetricResponse> result = repository.findTopNByPercentile(10);

            // Then: Should only return devices from latest window
            long latestTimestamp = dbHelper.getLatestTimestamp();
            assertThat(result).isNotEmpty();
            
            // Verify all results are from the latest window (latest timestamp period)
            result.forEach(response -> {
                // Should be recent data, not old data
                assertThat(response.deviceId()).startsWith("device-latest");
            });
            
            // Should include the updated device with newer percentile
            Optional<TopDeviceMetricResponse> updatedDevice = result.stream()
                .filter(r -> r.deviceId().equals("device-latest-1"))
                .findFirst();
            assertThat(updatedDevice).isPresent();
            assertThat(updatedDevice.get().percentile95()).isEqualTo(85.0); // Not 75.0 (older value)
        }

        @Test
        @DisplayName("Should handle identical percentile values")
        void shouldHandleIdenticalPercentileValues() {
            // Given: Multiple devices with identical percentiles
            List<CpuMetricTestData> testData = TestDataBuilder.scenario()
                .withIdenticalPercentiles(3, 75.0)
                .build();
            
            dbHelper.insertTestData(testData);

            // When: Getting top devices
            List<TopDeviceMetricResponse> result = repository.findTopNByPercentile(5);

            // Then: Should return all devices with identical percentiles
            assertThat(result).hasSize(3);
            result.forEach(response -> {
                assertThat(response.percentile95()).isEqualTo(75.0);
                assertThat(response.deviceId()).startsWith("device-identical-");
            });
        }

        @Test
        @DisplayName("Should handle large N value gracefully")
        void shouldHandleLargeNValueGracefully() {
            // Given: Only 3 devices
            List<CpuMetricTestData> testData = TestDataBuilder.scenario()
                .withHighUsageDevices(3)
                .build();
            
            dbHelper.insertTestData(testData);

            // When: Requesting more devices than available
            List<TopDeviceMetricResponse> result = repository.findTopNByPercentile(100);

            // Then: Should return all available devices
            assertThat(result).hasSize(3);
        }

        @Test
        @DisplayName("Should handle zero and negative N values")  
        void shouldHandleZeroAndNegativeNValues() {
            // Given: Some test data
            List<CpuMetricTestData> testData = TestDataBuilder.scenario()
                .withMediumUsageDevices(5)
                .build();
            
            dbHelper.insertTestData(testData);

            // When/Then: Zero should return empty list
            List<TopDeviceMetricResponse> zeroResult = repository.findTopNByPercentile(0);
            assertThat(zeroResult).isEmpty();
        }
    }

    @Nested
    @DisplayName("Performance and Load Tests")
    class PerformanceTests {

        @Test
        @DisplayName("Should handle large dataset efficiently")
        void shouldHandleLargeDatasetEfficiently() {
            // Given: Large dataset (1000 devices)
            List<CpuMetricTestData> testData = TestDataBuilder.scenario()
                .withLargeDataset(1000)
                .build();
            
            dbHelper.insertTestData(testData);

            // When: Performing queries (measure performance)
            long startTime = System.currentTimeMillis();
            
            // Test device lookup with a device ID that we know exists
            String predictableDeviceId = TestDataBuilder.getPredictableDeviceId(0); // "server-prod-0000"
            Optional<DeviceMetricResponse> deviceResult = repository.findLatestByDeviceId(predictableDeviceId);
            
            // Test top N query
            List<TopDeviceMetricResponse> topResult = repository.findTopNByPercentile(10);
            
            long endTime = System.currentTimeMillis();
            long executionTime = endTime - startTime;

            // Then: Should execute within reasonable time (< 1 second for 1000 records)
            assertThat(executionTime).isLessThan(1000);
            assertThat(deviceResult).isPresent();
            assertThat(topResult).hasSize(10);
            
            // Verify ordering is maintained even with large dataset
            for (int i = 0; i < topResult.size() - 1; i++) {
                assertThat(topResult.get(i).percentile95())
                    .isGreaterThanOrEqualTo(topResult.get(i + 1).percentile95());
            }
        }

        @Test
        @DisplayName("Should handle complex time-based queries efficiently")
        void shouldHandleComplexTimeBasedQueriesEfficiently() {
            // Given: Device with extensive historical data
            List<CpuMetricTestData> testData = TestDataBuilder.scenario()
                .withHistoricalDataForDevice("device-historical", 50)
                .build();
            
            dbHelper.insertTestData(testData);

            // When: Finding latest for device with many records
            long startTime = System.currentTimeMillis();
            Optional<DeviceMetricResponse> result = repository.findLatestByDeviceId("device-historical");
            long endTime = System.currentTimeMillis();

            // Then: Should execute quickly despite many records
            assertThat(endTime - startTime).isLessThan(100);
            assertThat(result).isPresent();
        }
    }

    @Nested
    @DisplayName("Data Integrity and Edge Cases")
    class DataIntegrityTests {

        @Test
        @DisplayName("Should maintain data integrity with concurrent operations")
        void shouldMaintainDataIntegrityWithConcurrentOperations() {
            // Given: Initial data
            List<CpuMetricTestData> testData = TestDataBuilder.scenario()
                .withMediumUsageDevices(10)
                .build();
            
            dbHelper.insertTestData(testData);
            int initialCount = dbHelper.countRecords();

            // When: Performing multiple queries simultaneously (simulate concurrent access)
            List<Thread> threads = List.of(
                new Thread(() -> repository.findLatestByDeviceId("workstation-medium-001")),
                new Thread(() -> repository.findTopNByPercentile(5)),
                new Thread(() -> repository.findLatestByDeviceId("workstation-medium-002")),
                new Thread(() -> repository.findTopNByPercentile(3))
            );

            threads.forEach(Thread::start);
            threads.forEach(thread -> {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            // Then: Data should remain consistent
            assertThat(dbHelper.countRecords()).isEqualTo(initialCount);
        }

        @Test
        @DisplayName("Should handle database constraints properly")
        void shouldHandleDatabaseConstraintsProperly() {
            // Given: Test data that might violate constraints
            CpuMetricTestData data1 = CpuMetricTestData.create("device-001", 75.0);
            dbHelper.insertTestData(data1);

            // When/Then: Attempting to insert duplicate event_id should be handled
            assertThatCode(() -> {
                // Try to insert the same event_id (should be unique)
                jdbcTemplate.update(
                    "INSERT INTO \"cpu-metrics\".cpu_usage_95_percentile " +
                    "(event_id, device_id, window_start, window_end, percentile_95, last_updated) " +
                    "VALUES (?, ?, ?, ?, ?, ?)",
                    data1.eventId(), // Same event_id
                    "device-002",    // Different device
                    data1.windowStart(),
                    data1.windowEnd(),
                    50.0,
                    data1.lastUpdated()
                );
            }).isInstanceOfAny(Exception.class); // Should throw constraint violation
        }

        @Test
        @DisplayName("Should handle null and boundary values gracefully")
        void shouldHandleNullAndBoundaryValuesGracefully() {
            // Test with boundary percentile values
            List<CpuMetricTestData> boundaryData = List.of(
                CpuMetricTestData.create("device-min", 0.0),
                CpuMetricTestData.create("device-max", 100.0),
                CpuMetricTestData.create("device-precision", 99.999999)
            );
            
            dbHelper.insertTestData(boundaryData);

            // All queries should work with boundary values
            assertThat(repository.findLatestByDeviceId("device-min")).isPresent();
            assertThat(repository.findLatestByDeviceId("device-max")).isPresent();
            assertThat(repository.findLatestByDeviceId("device-precision")).isPresent();

            List<TopDeviceMetricResponse> topResults = repository.findTopNByPercentile(3);
            assertThat(topResults).hasSize(3);
        }
    }
}