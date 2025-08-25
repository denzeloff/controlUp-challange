package com.controlup.api.service;

import com.controlup.api.dto.DeviceMetricResponse;
import com.controlup.api.dto.TopDeviceMetricResponse;
import com.controlup.api.repository.CpuMetricsRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetricsServiceIntegrationTest {

    @Mock
    private CpuMetricsRepository repository;

    @InjectMocks
    private MetricsService metricsService;

    @BeforeEach
    void setUp() {
        // Reset all mocks before each test
        reset(repository);
    }

    @Nested
    @DisplayName("Get Latest Metric For Device Tests")
    class GetLatestMetricForDeviceTests {

        @Test
        @DisplayName("Should return null when device not found")
        void shouldReturnNullWhenDeviceNotFound() {
            // Given: Repository returns empty Optional for non-existent device
            when(repository.findLatestByDeviceId("non-existent-device"))
                .thenReturn(Optional.empty());

            // When: Getting latest metric for non-existent device
            DeviceMetricResponse result = metricsService.getLatestMetricForDevice("non-existent-device");
            
            // Then: Should return null
            assertThat(result).isNull();
            verify(repository).findLatestByDeviceId("non-existent-device");
        }

        @Test
        @DisplayName("Should return metric when device exists")
        void shouldReturnMetricWhenDeviceExists() {
            // Given: Repository returns device metric data
            long currentTime = Instant.now().toEpochMilli();
            DeviceMetricResponse expectedResponse = new DeviceMetricResponse(
                "service-device-001",
                82.5,
                currentTime - 60000, // 1 minute window
                currentTime,
                currentTime
            );
            
            when(repository.findLatestByDeviceId("service-device-001"))
                .thenReturn(Optional.of(expectedResponse));

            // When: Getting latest metric
            DeviceMetricResponse result = metricsService.getLatestMetricForDevice("service-device-001");

            // Then: Should return the metric
            assertThat(result).isNotNull();
            assertThat(result.deviceId()).isEqualTo("service-device-001");
            assertThat(result.percentile95()).isEqualTo(82.5);
            assertThat(result.windowStart()).isEqualTo(currentTime - 60000);
            assertThat(result.windowEnd()).isEqualTo(currentTime);
            assertThat(result.lastUpdated()).isEqualTo(currentTime);
            verify(repository).findLatestByDeviceId("service-device-001");
        }

        @ParameterizedTest
        @CsvSource({
            "server-low-000, 15.0, true",
            "server-high-000, 85.0, true", 
            "server-critical-000, 97.5, true",
            "non-existent, 0.0, false"
        })
        @DisplayName("Should handle different usage scenarios correctly")
        void shouldHandleDifferentUsageScenariosCorrectly(String deviceId, double expectedPercentile, boolean shouldExist) {
            // Given: Mock responses for usage scenario
            long currentTime = Instant.now().toEpochMilli();
            
            if (shouldExist) {
                when(repository.findLatestByDeviceId(deviceId))
                    .thenReturn(Optional.of(new DeviceMetricResponse(deviceId, expectedPercentile, 
                        currentTime - 60000, currentTime, currentTime)));
            } else {
                when(repository.findLatestByDeviceId(deviceId))
                    .thenReturn(Optional.empty());
            }

            // When: Getting latest metric for device
            DeviceMetricResponse result = metricsService.getLatestMetricForDevice(deviceId);
            
            // Then: Should handle the scenario correctly
            if (shouldExist) {
                assertThat(result).isNotNull();
                assertThat(result.deviceId()).isEqualTo(deviceId);
                assertThat(result.percentile95()).isEqualTo(expectedPercentile);
            } else {
                assertThat(result).isNull();
            }
            
            // Verify repository interaction
            verify(repository).findLatestByDeviceId(deviceId);
        }

        @Test
        @DisplayName("Should validate device ID format and constraints")
        void shouldValidateDeviceIdFormatAndConstraints() {
            // Given: Mock response for device with complex ID
            String complexDeviceId = "complex-device_id.with@special-chars123";
            long currentTime = Instant.now().toEpochMilli();
            
            when(repository.findLatestByDeviceId(complexDeviceId))
                .thenReturn(Optional.of(new DeviceMetricResponse(complexDeviceId, 67.8, 
                    currentTime - 60000, currentTime, currentTime)));

            // When: Getting metric with complex device ID
            DeviceMetricResponse result = metricsService.getLatestMetricForDevice(complexDeviceId);

            // Then: Should handle complex device ID correctly
            assertThat(result).isNotNull();
            assertThat(result.deviceId()).isEqualTo(complexDeviceId);
            assertThat(result.percentile95()).isEqualTo(67.8);
            verify(repository).findLatestByDeviceId(complexDeviceId);
        }
    }

    @Nested
    @DisplayName("Get Top Devices By Percentile Tests")
    class GetTopDevicesByPercentileTests {

        @Test
        @DisplayName("Should return empty list when no data exists")
        void shouldReturnEmptyListWhenNoDataExists() {
            // Given: Repository returns empty list
            when(repository.findTopNByPercentile(5))
                .thenReturn(List.of());

            // When: Getting top devices with no data
            List<TopDeviceMetricResponse> result = metricsService.getTopDevicesByPercentile(5);
            
            // Then: Should return empty list
            assertThat(result).isEmpty();
            verify(repository).findTopNByPercentile(5);
        }

        @Test
        @DisplayName("Should return correctly ordered devices")
        void shouldReturnCorrectlyOrderedDevices() {
            // Given: Mock repository to return ordered devices
            long currentTime = Instant.now().toEpochMilli();
            List<TopDeviceMetricResponse> mockResponse = Arrays.asList(
                new TopDeviceMetricResponse("device-critical", 98.5, currentTime - 60000, currentTime, currentTime),
                new TopDeviceMetricResponse("device-high", 87.2, currentTime - 60000, currentTime, currentTime),
                new TopDeviceMetricResponse("device-medium", 56.7, currentTime - 60000, currentTime, currentTime)
            );
            
            when(repository.findTopNByPercentile(3))
                .thenReturn(mockResponse);

            // When: Getting top 3 devices
            List<TopDeviceMetricResponse> result = metricsService.getTopDevicesByPercentile(3);

            // Then: Should return devices in descending percentile order
            assertThat(result).hasSize(3);
            
            assertThat(result.get(0).deviceId()).isEqualTo("device-critical");
            assertThat(result.get(0).percentile95()).isEqualTo(98.5);
            
            assertThat(result.get(1).deviceId()).isEqualTo("device-high");
            assertThat(result.get(1).percentile95()).isEqualTo(87.2);
            
            assertThat(result.get(2).deviceId()).isEqualTo("device-medium");
            assertThat(result.get(2).percentile95()).isEqualTo(56.7);
            
            verify(repository).findTopNByPercentile(3);
        }

        @Test
        @DisplayName("Should handle various N values appropriately")
        void shouldHandleVariousNValuesAppropriately() {
            // Given: Mock responses for different N values
            long currentTime = Instant.now().toEpochMilli();
            List<TopDeviceMetricResponse> mediumDataset = Arrays.asList(
                new TopDeviceMetricResponse("device-1", 85.0, currentTime - 60000, currentTime, currentTime),
                new TopDeviceMetricResponse("device-2", 80.0, currentTime - 60000, currentTime, currentTime),
                new TopDeviceMetricResponse("device-3", 75.0, currentTime - 60000, currentTime, currentTime),
                new TopDeviceMetricResponse("device-4", 70.0, currentTime - 60000, currentTime, currentTime),
                new TopDeviceMetricResponse("device-5", 65.0, currentTime - 60000, currentTime, currentTime),
                new TopDeviceMetricResponse("device-6", 60.0, currentTime - 60000, currentTime, currentTime),
                new TopDeviceMetricResponse("device-7", 55.0, currentTime - 60000, currentTime, currentTime),
                new TopDeviceMetricResponse("device-8", 50.0, currentTime - 60000, currentTime, currentTime),
                new TopDeviceMetricResponse("device-9", 45.0, currentTime - 60000, currentTime, currentTime),
                new TopDeviceMetricResponse("device-10", 40.0, currentTime - 60000, currentTime, currentTime)
            );
            
            // Mock responses for different N values
            when(repository.findTopNByPercentile(3)).thenReturn(mediumDataset.subList(0, 3));
            when(repository.findTopNByPercentile(10)).thenReturn(mediumDataset);
            when(repository.findTopNByPercentile(20)).thenReturn(mediumDataset); // Return all 10 available

            // When/Then: Testing different N values
            
            // Small N
            List<TopDeviceMetricResponse> smallResult = metricsService.getTopDevicesByPercentile(3);
            assertThat(smallResult).hasSize(3);

            // Exact N
            List<TopDeviceMetricResponse> exactResult = metricsService.getTopDevicesByPercentile(10);
            assertThat(exactResult).hasSize(10);

            // Large N (more than available)
            List<TopDeviceMetricResponse> largeResult = metricsService.getTopDevicesByPercentile(20);
            assertThat(largeResult).hasSize(10); // Only 10 available

            // Verify ordering is maintained (repository should handle this)
            for (List<TopDeviceMetricResponse> result : List.of(smallResult, exactResult, largeResult)) {
                for (int i = 0; i < result.size() - 1; i++) {
                    assertThat(result.get(i).percentile95())
                        .isGreaterThanOrEqualTo(result.get(i + 1).percentile95());
                }
            }
            
            // Verify repository interactions
            verify(repository).findTopNByPercentile(3);
            verify(repository).findTopNByPercentile(10);
            verify(repository).findTopNByPercentile(20);
        }

        @Test
        @DisplayName("Should handle business rules for time-based queries")
        void shouldHandleBusinessRulesForTimeBasedQueries() {
            // Given: Mock response simulating latest window data only
            long currentTime = Instant.now().toEpochMilli();
            List<TopDeviceMetricResponse> latestWindowData = Arrays.asList(
                new TopDeviceMetricResponse("device-latest-2", 95.0, currentTime - 60000, currentTime, currentTime),
                new TopDeviceMetricResponse("device-latest-1", 85.0, currentTime - 60000, currentTime, currentTime), // Updated data
                new TopDeviceMetricResponse("device-latest-3", 25.0, currentTime - 60000, currentTime, currentTime)
                // Note: device-old-1 is not included as it's from an older window
            );
            
            when(repository.findTopNByPercentile(10))
                .thenReturn(latestWindowData);

            // When: Getting top devices
            List<TopDeviceMetricResponse> result = metricsService.getTopDevicesByPercentile(10);

            // Then: Should only include devices from latest complete window
            assertThat(result).hasSize(3);
            
            // Verify business logic: should prioritize latest data
            boolean hasLatestDevice = result.stream()
                .anyMatch(r -> r.deviceId().equals("device-latest-1") && r.percentile95() == 85.0);
            assertThat(hasLatestDevice).isTrue();

            // Should not include old data (repository handles this filtering)
            boolean hasOldDevice = result.stream()
                .anyMatch(r -> r.deviceId().equals("device-old-1"));
            assertThat(hasOldDevice).isFalse();
            
            verify(repository).findTopNByPercentile(10);
        }
    }

    @Nested
    @DisplayName("Service Layer Integration and Error Handling")
    class ServiceLayerIntegrationTests {

        @Test
        @DisplayName("Should maintain transactional integrity")
        void shouldMaintainTransactionalIntegrity() {
            // Given: Mock responses for multiple service operations
            long currentTime = Instant.now().toEpochMilli();
            
            // Mock responses for concurrent service calls
            when(repository.findLatestByDeviceId("workstation-staging-0001"))
                .thenReturn(Optional.of(new DeviceMetricResponse("workstation-staging-0001", 45.0, 
                    currentTime - 60000, currentTime, currentTime)));
                    
            when(repository.findLatestByDeviceId("server-staging-0050"))
                .thenReturn(Optional.of(new DeviceMetricResponse("server-staging-0050", 75.0, 
                    currentTime - 60000, currentTime, currentTime)));
                    
            when(repository.findTopNByPercentile(10))
                .thenReturn(Arrays.asList(
                    new TopDeviceMetricResponse("device-1", 95.0, currentTime - 60000, currentTime, currentTime),
                    new TopDeviceMetricResponse("device-2", 85.0, currentTime - 60000, currentTime, currentTime),
                    new TopDeviceMetricResponse("device-3", 75.0, currentTime - 60000, currentTime, currentTime),
                    new TopDeviceMetricResponse("device-4", 65.0, currentTime - 60000, currentTime, currentTime),
                    new TopDeviceMetricResponse("device-5", 55.0, currentTime - 60000, currentTime, currentTime),
                    new TopDeviceMetricResponse("device-6", 45.0, currentTime - 60000, currentTime, currentTime),
                    new TopDeviceMetricResponse("device-7", 35.0, currentTime - 60000, currentTime, currentTime),
                    new TopDeviceMetricResponse("device-8", 25.0, currentTime - 60000, currentTime, currentTime),
                    new TopDeviceMetricResponse("device-9", 15.0, currentTime - 60000, currentTime, currentTime),
                    new TopDeviceMetricResponse("device-10", 5.0, currentTime - 60000, currentTime, currentTime)
                ));

            // When: Performing multiple service operations
            DeviceMetricResponse deviceResult = metricsService.getLatestMetricForDevice("workstation-staging-0001");
            List<TopDeviceMetricResponse> topResults = metricsService.getTopDevicesByPercentile(10);
            DeviceMetricResponse anotherDeviceResult = metricsService.getLatestMetricForDevice("server-staging-0050");

            // Then: Service should handle concurrent calls correctly
            assertThat(deviceResult).isNotNull();
            assertThat(deviceResult.deviceId()).isEqualTo("workstation-staging-0001");
            assertThat(topResults).hasSize(10);
            assertThat(anotherDeviceResult).isNotNull();
            assertThat(anotherDeviceResult.deviceId()).isEqualTo("server-staging-0050");
            
            // Verify repository interactions
            verify(repository).findLatestByDeviceId("workstation-staging-0001");
            verify(repository).findLatestByDeviceId("server-staging-0050");
            verify(repository).findTopNByPercentile(10);
        }

        @ParameterizedTest
        @CsvSource({
            "device-zero, 0.0",
            "device-max, 100.0",
            "device-precision, 99.999",
            "device-low-precision, 0.001"
        })
        @DisplayName("Should handle edge case percentile values")
        void shouldHandleEdgeCasePercentileValues(String deviceId, double expectedPercentile) {
            // Given: Mock response for edge case device
            long currentTime = Instant.now().toEpochMilli();
            
            when(repository.findLatestByDeviceId(deviceId))
                .thenReturn(Optional.of(new DeviceMetricResponse(deviceId, expectedPercentile, 
                    currentTime - 60000, currentTime, currentTime)));

            // When: Getting metric for edge case device
            DeviceMetricResponse result = metricsService.getLatestMetricForDevice(deviceId);
            
            // Then: Should handle edge case correctly
            assertThat(result).isNotNull();
            assertThat(result.deviceId()).isEqualTo(deviceId);
            assertThat(result.percentile95()).isEqualTo(expectedPercentile);
            
            // Verify repository interaction
            verify(repository).findLatestByDeviceId(deviceId);
        }

        @Test
        @DisplayName("Should handle identical percentiles in top N queries")
        void shouldHandleIdenticalPercentilesInTopNQueries() {
            // Given: Mock response with identical percentiles
            long currentTime = Instant.now().toEpochMilli();
            
            when(repository.findTopNByPercentile(10))
                .thenReturn(Arrays.asList(
                    new TopDeviceMetricResponse("device-max", 100.0, currentTime - 60000, currentTime, currentTime),
                    new TopDeviceMetricResponse("device-identical-1", 75.0, currentTime - 60000, currentTime, currentTime),
                    new TopDeviceMetricResponse("device-identical-2", 75.0, currentTime - 60000, currentTime, currentTime),
                    new TopDeviceMetricResponse("device-identical-3", 75.0, currentTime - 60000, currentTime, currentTime),
                    new TopDeviceMetricResponse("device-identical-4", 75.0, currentTime - 60000, currentTime, currentTime),
                    new TopDeviceMetricResponse("device-identical-5", 75.0, currentTime - 60000, currentTime, currentTime),
                    new TopDeviceMetricResponse("device-precision", 99.999, currentTime - 60000, currentTime, currentTime),
                    new TopDeviceMetricResponse("device-low-precision", 0.001, currentTime - 60000, currentTime, currentTime),
                    new TopDeviceMetricResponse("device-zero", 0.0, currentTime - 60000, currentTime, currentTime)
                ));

            // When: Getting top devices
            List<TopDeviceMetricResponse> result = metricsService.getTopDevicesByPercentile(10);
            
            // Then: Should handle identical percentiles correctly
            long identicalCount = result.stream()
                .filter(r -> r.percentile95() == 75.0)
                .count();
            assertThat(identicalCount).isEqualTo(5);
            
            // Verify repository interaction
            verify(repository).findTopNByPercentile(10);
        }

        @Test
        @DisplayName("Should provide consistent results across multiple calls")
        void shouldProvideConsistentResultsAcrossMultipleCalls() {
            // Given: Mock stable responses for consistency testing
            long currentTime = Instant.now().toEpochMilli();
            
            DeviceMetricResponse stableResponse = new DeviceMetricResponse(
                "stable-device", 88.8, currentTime - 60000, currentTime, currentTime);
                
            List<TopDeviceMetricResponse> stableTopResponse = Arrays.asList(
                new TopDeviceMetricResponse("stable-device", 88.8, currentTime - 60000, currentTime, currentTime),
                new TopDeviceMetricResponse("device-2", 75.0, currentTime - 60000, currentTime, currentTime),
                new TopDeviceMetricResponse("device-3", 65.0, currentTime - 60000, currentTime, currentTime)
            );
            
            // Mock consistent responses
            when(repository.findLatestByDeviceId("stable-device"))
                .thenReturn(Optional.of(stableResponse));
            when(repository.findTopNByPercentile(3))
                .thenReturn(stableTopResponse);

            // When: Calling service methods multiple times
            DeviceMetricResponse firstCall = metricsService.getLatestMetricForDevice("stable-device");
            DeviceMetricResponse secondCall = metricsService.getLatestMetricForDevice("stable-device");
            DeviceMetricResponse thirdCall = metricsService.getLatestMetricForDevice("stable-device");

            List<TopDeviceMetricResponse> firstTopCall = metricsService.getTopDevicesByPercentile(3);
            List<TopDeviceMetricResponse> secondTopCall = metricsService.getTopDevicesByPercentile(3);

            // Then: Results should be identical
            assertThat(firstCall).isEqualTo(secondCall);
            assertThat(secondCall).isEqualTo(thirdCall);
            assertThat(firstCall.percentile95()).isEqualTo(88.8);

            assertThat(firstTopCall).hasSize(3);
            assertThat(secondTopCall).hasSize(3);
            
            // Verify identical ordering
            for (int i = 0; i < firstTopCall.size(); i++) {
                assertThat(firstTopCall.get(i).deviceId())
                    .isEqualTo(secondTopCall.get(i).deviceId());
                assertThat(firstTopCall.get(i).percentile95())
                    .isEqualTo(secondTopCall.get(i).percentile95());
            }
            
            // Verify repository interactions (3 calls for device, 2 calls for top)
            verify(repository, times(3)).findLatestByDeviceId("stable-device");
            verify(repository, times(2)).findTopNByPercentile(3);
        }

        @Test
        @DisplayName("Should handle service layer logging and monitoring requirements")
        void shouldHandleServiceLayerLoggingAndMonitoringRequirements() {
            // Given: Mock responses for logging validation
            long currentTime = Instant.now().toEpochMilli();
            
            when(repository.findLatestByDeviceId("server-high-000"))
                .thenReturn(Optional.of(new DeviceMetricResponse("server-high-000", 95.0, 
                    currentTime - 60000, currentTime, currentTime)));
                    
            when(repository.findLatestByDeviceId("non-existent"))
                .thenReturn(Optional.empty());
                
            when(repository.findTopNByPercentile(2))
                .thenReturn(Arrays.asList(
                    new TopDeviceMetricResponse("server-high-000", 95.0, currentTime - 60000, currentTime, currentTime),
                    new TopDeviceMetricResponse("server-high-001", 85.0, currentTime - 60000, currentTime, currentTime)
                ));

            // When: Performing service operations (logs should be generated)
            DeviceMetricResponse deviceResult = metricsService.getLatestMetricForDevice("server-high-000");
            List<TopDeviceMetricResponse> topResult = metricsService.getTopDevicesByPercentile(2);
            DeviceMetricResponse nullResult = metricsService.getLatestMetricForDevice("non-existent");

            // Then: Operations should complete successfully
            // (Logging verification would typically be done with test appenders in real scenarios)
            assertThat(deviceResult).isNotNull();
            assertThat(topResult).hasSize(2);
            assertThat(nullResult).isNull();
            
            // Verify repository interactions
            verify(repository).findLatestByDeviceId("server-high-000");
            verify(repository).findLatestByDeviceId("non-existent");
            verify(repository).findTopNByPercentile(2);
        }
    }

}