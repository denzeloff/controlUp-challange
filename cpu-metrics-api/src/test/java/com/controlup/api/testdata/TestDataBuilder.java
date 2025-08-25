package com.controlup.api.testdata;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

/**
 * Smart test data builder for CPU metrics integration tests.
 * Provides fluent API for creating realistic and manageable test scenarios.
 */
public class TestDataBuilder {
    
    private final List<CpuMetricTestData> testData = new ArrayList<>();
    private final Random random = ThreadLocalRandom.current();
    
    // Default values for smart data generation
    private static final String[] DEVICE_TYPES = {"server", "workstation", "laptop", "container", "vm"};
    private static final String[] ENVIRONMENTS = {"prod", "staging", "dev", "test"};
    private static final Map<String, PercentileRange> USAGE_PATTERNS = Map.of(
        "low", new PercentileRange(5.0, 30.0),
        "medium", new PercentileRange(30.0, 70.0), 
        "high", new PercentileRange(70.0, 95.0),
        "critical", new PercentileRange(95.0, 100.0)
    );
    
    private record PercentileRange(double min, double max) {}
    
    /**
     * Create a builder for generating realistic test scenarios
     */
    public static TestDataBuilder scenario() {
        return new TestDataBuilder();
    }
    
    /**
     * Add devices with low CPU usage (5-30%)
     */
    public TestDataBuilder withLowUsageDevices(int count) {
        return addDevicesWithPattern(count, "low", "server");
    }
    
    /**
     * Add devices with medium CPU usage (30-70%)
     */
    public TestDataBuilder withMediumUsageDevices(int count) {
        return addDevicesWithPattern(count, "medium", "workstation");
    }
    
    /**
     * Add devices with high CPU usage (70-95%)
     */
    public TestDataBuilder withHighUsageDevices(int count) {
        return addDevicesWithPattern(count, "high", "server");
    }
    
    /**
     * Add devices with critical CPU usage (95-100%)
     */
    public TestDataBuilder withCriticalUsageDevices(int count) {
        return addDevicesWithPattern(count, "critical", "server");
    }
    
    /**
     * Add a specific device with exact percentile value
     */
    public TestDataBuilder withDevice(String deviceId, double percentile95) {
        return withDevice(deviceId, percentile95, Instant.now());
    }
    
    /**
     * Add a specific device with exact percentile and timestamp
     */
    public TestDataBuilder withDevice(String deviceId, double percentile95, Instant timestamp) {
        long epochMillis = timestamp.toEpochMilli();
        testData.add(new CpuMetricTestData(
            UUID.randomUUID().toString(),
            deviceId,
            epochMillis - 60000, // 1 minute window
            epochMillis,
            percentile95,
            epochMillis
        ));
        return this;
    }
    
    /**
     * Add devices for testing time-based queries (latest window)
     */
    public TestDataBuilder withTimeBasedScenario() {
        Instant now = Instant.now();
        Instant oneHourAgo = now.minus(1, ChronoUnit.HOURS);
        Instant twoHoursAgo = now.minus(2, ChronoUnit.HOURS);
        
        // Old data - should not appear in "latest" queries
        withDevice("device-old-1", 45.0, twoHoursAgo);
        withDevice("device-old-2", 55.0, twoHoursAgo);
        
        // Intermediate data
        withDevice("device-mid-1", 65.0, oneHourAgo);
        withDevice("device-latest-1", 75.0, oneHourAgo); // This device also has newer data
        
        // Latest data - should appear in "latest" queries  
        withDevice("device-latest-1", 85.0, now); // Newer data for same device
        withDevice("device-latest-2", 95.0, now);
        withDevice("device-latest-3", 25.0, now);
        
        return this;
    }
    
    /**
     * Add devices for testing edge cases
     */
    public TestDataBuilder withEdgeCases() {
        Instant now = Instant.now();
        
        return this
            .withDevice("device-zero", 0.0, now)           // Minimum value
            .withDevice("device-max", 100.0, now)          // Maximum value  
            .withDevice("device-precision", 99.999, now)   // High precision
            .withDevice("device-low-precision", 0.001, now) // Low precision
            .withDevice("device-special-chars", 50.0, now) // Will be renamed to test special chars
            .withDevice("device-long-name-" + "x".repeat(200), 75.0, now); // Long device name
    }
    
    /**
     * Add multiple windows for the same device (historical data)
     */
    public TestDataBuilder withHistoricalDataForDevice(String deviceId, int windowCount) {
        Instant now = Instant.now();
        
        for (int i = windowCount - 1; i >= 0; i--) {
            Instant windowTime = now.minus(i, ChronoUnit.MINUTES);
            double percentile = 30.0 + (random.nextDouble() * 40.0); // Random between 30-70
            withDevice(deviceId + (i == 0 ? "" : "-window-" + i), percentile, windowTime);
        }
        
        return this;
    }
    
    /**
     * Generate data for performance testing
     */
    public TestDataBuilder withLargeDataset(int deviceCount) {
        Instant now = Instant.now();
        
        IntStream.range(0, deviceCount)
            .forEach(i -> {
                String deviceType = DEVICE_TYPES[i % DEVICE_TYPES.length];
                String environment = ENVIRONMENTS[i % ENVIRONMENTS.length];
                String deviceId = String.format("%s-%s-%04d", deviceType, environment, i);
                
                // Generate realistic percentile distribution
                double percentile = generateRealisticPercentile();
                
                withDevice(deviceId, percentile, now);
            });
            
        return this;
    }
    
    /**
     * Create identical percentile values for testing sorting edge cases
     */
    public TestDataBuilder withIdenticalPercentiles(int count, double percentile) {
        IntStream.range(0, count)
            .forEach(i -> withDevice("device-identical-" + i, percentile, Instant.now()));
        return this;
    }
    
    /**
     * Build the test data list
     */
    public List<CpuMetricTestData> build() {
        return new ArrayList<>(testData);
    }
    
    /**
     * Build and shuffle the data for randomized testing
     */
    public List<CpuMetricTestData> buildShuffled() {
        List<CpuMetricTestData> data = build();
        Collections.shuffle(data, random);
        return data;
    }
    
    /**
     * Get predictable device ID for a given index (useful for testing)
     */
    public static String getPredictableDeviceId(int index) {
        String deviceType = DEVICE_TYPES[index % DEVICE_TYPES.length];
        String environment = ENVIRONMENTS[index % ENVIRONMENTS.length];
        return String.format("%s-%s-%04d", deviceType, environment, index);
    }
    
    /**
     * Get summary statistics of the generated data
     */
    public TestDataSummary summary() {
        if (testData.isEmpty()) {
            return new TestDataSummary(0, 0.0, 0.0, 0.0, Set.of());
        }
        
        DoubleSummaryStatistics stats = testData.stream()
            .mapToDouble(CpuMetricTestData::percentile95)
            .summaryStatistics();
            
        Set<String> uniqueDevices = new HashSet<>();
        testData.forEach(data -> uniqueDevices.add(data.deviceId()));
        
        return new TestDataSummary(
            testData.size(),
            stats.getMin(),
            stats.getMax(), 
            stats.getAverage(),
            uniqueDevices
        );
    }
    
    // Private helper methods
    
    private TestDataBuilder addDevicesWithPattern(int count, String pattern, String deviceType) {
        PercentileRange range = USAGE_PATTERNS.get(pattern);
        Instant now = Instant.now();
        
        IntStream.range(0, count)
            .forEach(i -> {
                String deviceId = String.format("%s-%s-%03d", deviceType, pattern, i);
                double percentile = range.min + (random.nextDouble() * (range.max - range.min));
                withDevice(deviceId, percentile, now);
            });
            
        return this;
    }
    
    private double generateRealisticPercentile() {
        // Realistic CPU usage distribution:
        // 40% low (0-30), 30% medium (30-60), 20% high (60-90), 10% critical (90-100)
        double rand = random.nextDouble();
        
        if (rand < 0.4) {
            return random.nextDouble() * 30.0; // 0-30%
        } else if (rand < 0.7) {
            return 30.0 + (random.nextDouble() * 30.0); // 30-60%
        } else if (rand < 0.9) {
            return 60.0 + (random.nextDouble() * 30.0); // 60-90%
        } else {
            return 90.0 + (random.nextDouble() * 10.0); // 90-100%
        }
    }
    
    /**
     * Summary of generated test data
     */
    public record TestDataSummary(
        int totalRecords,
        double minPercentile,
        double maxPercentile,
        double avgPercentile,
        Set<String> uniqueDevices
    ) {}
}