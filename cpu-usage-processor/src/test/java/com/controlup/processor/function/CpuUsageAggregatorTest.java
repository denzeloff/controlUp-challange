package com.controlup.processor.function;

import com.controlup.processor.model.CpuUsageEvent;
import com.controlup.processor.model.ProcessedCpuEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

class CpuUsageAggregatorTest {

    private CpuUsageAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new CpuUsageAggregator();
    }

    @Nested
    @DisplayName("CpuAccumulator Tests")
    class CpuAccumulatorTest {

        @Test
        @DisplayName("Should create empty accumulator")
        void shouldCreateEmptyAccumulator() {
            CpuUsageAggregator.CpuAccumulator accumulator = new CpuUsageAggregator.CpuAccumulator();
            
            assertTrue(accumulator.isEmpty());
            assertEquals(0, accumulator.count);
            assertEquals(1000, accumulator.capacity);
            assertEquals(Long.MAX_VALUE, accumulator.windowStart);
            assertEquals(Long.MIN_VALUE, accumulator.windowEnd);
        }

        @Test
        @DisplayName("Should create accumulator with device ID")
        void shouldCreateAccumulatorWithDeviceId() {
            String deviceId = "device-123";
            CpuUsageAggregator.CpuAccumulator accumulator = new CpuUsageAggregator.CpuAccumulator(deviceId);
            
            assertEquals(deviceId, accumulator.deviceId);
            assertTrue(accumulator.isEmpty());
        }

        @Test
        @DisplayName("Should add single value")
        void shouldAddSingleValue() {
            CpuUsageAggregator.CpuAccumulator accumulator = new CpuUsageAggregator.CpuAccumulator();
            
            accumulator.addValue(75.5);
            
            assertFalse(accumulator.isEmpty());
            assertEquals(1, accumulator.count);
            assertArrayEquals(new double[]{75.5}, accumulator.getValues());
        }

        @Test
        @DisplayName("Should add multiple values")
        void shouldAddMultipleValues() {
            CpuUsageAggregator.CpuAccumulator accumulator = new CpuUsageAggregator.CpuAccumulator();
            double[] expectedValues = {10.0, 25.5, 50.0, 75.5, 90.0};
            
            for (double value : expectedValues) {
                accumulator.addValue(value);
            }
            
            assertEquals(5, accumulator.count);
            assertArrayEquals(expectedValues, accumulator.getValues());
        }

        @Test
        @DisplayName("Should resize array when capacity exceeded")
        void shouldResizeArrayWhenCapacityExceeded() {
            CpuUsageAggregator.CpuAccumulator accumulator = new CpuUsageAggregator.CpuAccumulator();
            // Set small initial capacity for testing
            accumulator.capacity = 2;
            accumulator.cpuUsageArray = new double[2];
            
            // Add values beyond initial capacity
            accumulator.addValue(10.0);
            accumulator.addValue(20.0);
            accumulator.addValue(30.0); // This should trigger resize
            
            assertEquals(3, accumulator.count);
            double[] values = accumulator.getValues();
            assertEquals(3, values.length);
            assertArrayEquals(new double[]{10.0, 20.0, 30.0}, values);
        }
    }

    @Nested
    @DisplayName("Aggregator Function Tests")
    class AggregatorFunctionTest {

        @Test
        @DisplayName("Should create new accumulator")
        void shouldCreateNewAccumulator() {
            CpuUsageAggregator.CpuAccumulator accumulator = aggregator.createAccumulator();
            
            assertNotNull(accumulator);
            assertTrue(accumulator.isEmpty());
        }

        @Test
        @DisplayName("Should add single event to accumulator")
        void shouldAddSingleEventToAccumulator() {
            CpuUsageEvent event = new CpuUsageEvent("device-001", 1692617400000L, 75.5);
            CpuUsageAggregator.CpuAccumulator accumulator = aggregator.createAccumulator();
            
            CpuUsageAggregator.CpuAccumulator result = aggregator.add(event, accumulator);
            
            assertSame(accumulator, result);
            assertEquals("device-001", result.deviceId);
            assertEquals(1, result.count);
            assertEquals(1692617400000L, result.windowStart);
            assertEquals(1692617400000L, result.windowEnd);
            assertArrayEquals(new double[]{75.5}, result.getValues());
        }

        @Test
        @DisplayName("Should add multiple events to accumulator")
        void shouldAddMultipleEventsToAccumulator() {
            CpuUsageAggregator.CpuAccumulator accumulator = aggregator.createAccumulator();
            
            CpuUsageEvent event1 = new CpuUsageEvent("device-001", 1692617400000L, 60.0);
            CpuUsageEvent event2 = new CpuUsageEvent("device-001", 1692617410000L, 75.5);
            CpuUsageEvent event3 = new CpuUsageEvent("device-001", 1692617420000L, 90.0);
            
            aggregator.add(event1, accumulator);
            aggregator.add(event2, accumulator);
            aggregator.add(event3, accumulator);
            
            assertEquals("device-001", accumulator.deviceId);
            assertEquals(3, accumulator.count);
            assertEquals(1692617400000L, accumulator.windowStart);
            assertEquals(1692617420000L, accumulator.windowEnd);
            assertArrayEquals(new double[]{60.0, 75.5, 90.0}, accumulator.getValues());
        }

        @Test
        @DisplayName("Should update window boundaries correctly")
        void shouldUpdateWindowBoundariesCorrectly() {
            CpuUsageAggregator.CpuAccumulator accumulator = aggregator.createAccumulator();
            
            // Add events in non-chronological order
            CpuUsageEvent event1 = new CpuUsageEvent("device-001", 1692617420000L, 60.0);
            CpuUsageEvent event2 = new CpuUsageEvent("device-001", 1692617400000L, 75.5); // Earlier
            CpuUsageEvent event3 = new CpuUsageEvent("device-001", 1692617440000L, 90.0); // Later
            
            aggregator.add(event1, accumulator);
            aggregator.add(event2, accumulator);
            aggregator.add(event3, accumulator);
            
            assertEquals(1692617400000L, accumulator.windowStart); // Earliest
            assertEquals(1692617440000L, accumulator.windowEnd);   // Latest
        }
    }

    @Nested
    @DisplayName("Result Generation Tests")
    class ResultGenerationTest {

        @Test
        @DisplayName("Should return unknown result for empty accumulator")
        void shouldReturnUnknownResultForEmptyAccumulator() {
            CpuUsageAggregator.CpuAccumulator accumulator = aggregator.createAccumulator();
            
            ProcessedCpuEvent result = aggregator.getResult(accumulator);
            
            assertNotNull(result);
            assertEquals("unknown", result.getDeviceId());
            assertEquals(0.0, result.getPercentile95());
        }

        @Test
        @DisplayName("Should calculate 95th percentile for single value")
        void shouldCalculate95thPercentileForSingleValue() {
            CpuUsageAggregator.CpuAccumulator accumulator = aggregator.createAccumulator();
            CpuUsageEvent event = new CpuUsageEvent("device-001", 1692617400000L, 75.5);
            
            aggregator.add(event, accumulator);
            ProcessedCpuEvent result = aggregator.getResult(accumulator);
            
            assertEquals("device-001", result.getDeviceId());
            assertEquals(75.5, result.getPercentile95(), 0.01);
            assertEquals(1692617400000L, result.getWindowStart());
            assertEquals(1692617400000L, result.getWindowEnd());
        }

        @Test
        @DisplayName("Should calculate 95th percentile for multiple values")
        void shouldCalculate95thPercentileForMultipleValues() {
            CpuUsageAggregator.CpuAccumulator accumulator = aggregator.createAccumulator();
            
            // Add 20 values: 5, 10, 15, ..., 100
            for (int i = 1; i <= 20; i++) {
                CpuUsageEvent event = new CpuUsageEvent("device-001", 1692617400000L + i * 1000, i * 5.0);
                aggregator.add(event, accumulator);
            }
            
            ProcessedCpuEvent result = aggregator.getResult(accumulator);
            
            assertEquals("device-001", result.getDeviceId());
            // 95th percentile of [5, 10, 15, ..., 100] using Apache Commons Math method
            // The actual result is 99.75 based on the interpolation method used
            assertEquals(99.75, result.getPercentile95(), 0.01);
        }

        @Test
        @DisplayName("Should calculate 95th percentile with edge values")
        void shouldCalculate95thPercentileWithEdgeValues() {
            CpuUsageAggregator.CpuAccumulator accumulator = aggregator.createAccumulator();
            double[] testValues = {0.0, 0.1, 50.0, 99.9, 100.0};
            
            for (int i = 0; i < testValues.length; i++) {
                CpuUsageEvent event = new CpuUsageEvent("device-test", 1692617400000L + i * 1000, testValues[i]);
                aggregator.add(event, accumulator);
            }
            
            ProcessedCpuEvent result = aggregator.getResult(accumulator);
            
            assertEquals("device-test", result.getDeviceId());
            assertTrue(result.getPercentile95() >= 50.0 && result.getPercentile95() <= 100.0);
        }
    }

    @Nested
    @DisplayName("Merge Operations Tests")
    class MergeOperationsTest {

        @Test
        @DisplayName("Should return second accumulator when first is empty")
        void shouldReturnSecondAccumulatorWhenFirstIsEmpty() {
            CpuUsageAggregator.CpuAccumulator acc1 = aggregator.createAccumulator();
            CpuUsageAggregator.CpuAccumulator acc2 = aggregator.createAccumulator();
            
            CpuUsageEvent event = new CpuUsageEvent("device-002", 1692617400000L, 80.0);
            aggregator.add(event, acc2);
            
            CpuUsageAggregator.CpuAccumulator merged = aggregator.merge(acc1, acc2);
            
            assertSame(acc2, merged); // Should return acc2 directly
            assertEquals("device-002", merged.deviceId);
            assertEquals(1, merged.count);
            assertArrayEquals(new double[]{80.0}, merged.getValues());
        }

        @Test
        @DisplayName("Should return first accumulator when second is empty")
        void shouldReturnFirstAccumulatorWhenSecondIsEmpty() {
            CpuUsageAggregator.CpuAccumulator acc1 = aggregator.createAccumulator();
            CpuUsageAggregator.CpuAccumulator acc2 = aggregator.createAccumulator();
            
            CpuUsageEvent event = new CpuUsageEvent("device-001", 1692617400000L, 70.0);
            aggregator.add(event, acc1);
            
            CpuUsageAggregator.CpuAccumulator merged = aggregator.merge(acc1, acc2);
            
            assertSame(acc1, merged);
            assertEquals("device-001", merged.deviceId);
            assertEquals(1, merged.count);
        }

        @Test
        @DisplayName("Should merge two non-empty accumulators")
        void shouldMergeTwoNonEmptyAccumulators() {
            CpuUsageAggregator.CpuAccumulator acc1 = aggregator.createAccumulator();
            CpuUsageAggregator.CpuAccumulator acc2 = aggregator.createAccumulator();
            
            // Add values to first accumulator
            aggregator.add(new CpuUsageEvent("device-001", 1692617400000L, 60.0), acc1);
            aggregator.add(new CpuUsageEvent("device-001", 1692617410000L, 70.0), acc1);
            
            // Add values to second accumulator  
            aggregator.add(new CpuUsageEvent("device-001", 1692617405000L, 65.0), acc2);
            aggregator.add(new CpuUsageEvent("device-001", 1692617415000L, 75.0), acc2);
            
            CpuUsageAggregator.CpuAccumulator merged = aggregator.merge(acc1, acc2);
            
            assertEquals("device-001", merged.deviceId);
            assertEquals(4, merged.count);
            assertEquals(1692617400000L, merged.windowStart); // Min of both
            assertEquals(1692617415000L, merged.windowEnd);   // Max of both
            
            double[] mergedValues = merged.getValues();
            assertEquals(4, mergedValues.length);
            // Values should contain all from both accumulators
            assertArrayEquals(new double[]{60.0, 70.0, 65.0, 75.0}, mergedValues);
        }

        @Test
        @DisplayName("Should handle window boundaries correctly when merging")
        void shouldHandleWindowBoundariesCorrectlyWhenMerging() {
            CpuUsageAggregator.CpuAccumulator acc1 = aggregator.createAccumulator();
            CpuUsageAggregator.CpuAccumulator acc2 = aggregator.createAccumulator();
            
            // First accumulator: earlier window
            aggregator.add(new CpuUsageEvent("device-001", 1692617400000L, 60.0), acc1);
            aggregator.add(new CpuUsageEvent("device-001", 1692617420000L, 70.0), acc1);
            
            // Second accumulator: later window
            aggregator.add(new CpuUsageEvent("device-001", 1692617440000L, 80.0), acc2);
            aggregator.add(new CpuUsageEvent("device-001", 1692617460000L, 90.0), acc2);
            
            CpuUsageAggregator.CpuAccumulator merged = aggregator.merge(acc1, acc2);
            
            assertEquals(1692617400000L, merged.windowStart); // Earliest from acc1
            assertEquals(1692617460000L, merged.windowEnd);   // Latest from acc2
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesTest {

        @Test
        @DisplayName("Should handle zero CPU usage values")
        void shouldHandleZeroCpuUsageValues() {
            CpuUsageAggregator.CpuAccumulator accumulator = aggregator.createAccumulator();
            CpuUsageEvent event = new CpuUsageEvent("device-001", 1692617400000L, 0.0);
            
            aggregator.add(event, accumulator);
            ProcessedCpuEvent result = aggregator.getResult(accumulator);
            
            assertEquals(0.0, result.getPercentile95());
        }

        @Test
        @DisplayName("Should handle maximum CPU usage values")
        void shouldHandleMaximumCpuUsageValues() {
            CpuUsageAggregator.CpuAccumulator accumulator = aggregator.createAccumulator();
            CpuUsageEvent event = new CpuUsageEvent("device-001", 1692617400000L, 100.0);
            
            aggregator.add(event, accumulator);
            ProcessedCpuEvent result = aggregator.getResult(accumulator);
            
            assertEquals(100.0, result.getPercentile95());
        }

        @Test
        @DisplayName("Should handle large number of events")
        void shouldHandleLargeNumberOfEvents() {
            CpuUsageAggregator.CpuAccumulator accumulator = aggregator.createAccumulator();
            
            // Add 2000 events (more than initial capacity of 1000)
            for (int i = 0; i < 2000; i++) {
                CpuUsageEvent event = new CpuUsageEvent("device-large", 1692617400000L + i * 100, i % 100);
                aggregator.add(event, accumulator);
            }
            
            ProcessedCpuEvent result = aggregator.getResult(accumulator);
            
            assertEquals("device-large", result.getDeviceId());
            assertEquals(2000, accumulator.count);
            assertTrue(result.getPercentile95() >= 0 && result.getPercentile95() <= 100);
        }

        @Test
        @DisplayName("Should handle identical CPU usage values")
        void shouldHandleIdenticalCpuUsageValues() {
            CpuUsageAggregator.CpuAccumulator accumulator = aggregator.createAccumulator();
            double constantValue = 42.5;
            
            // Add 10 events with the same CPU usage
            for (int i = 0; i < 10; i++) {
                CpuUsageEvent event = new CpuUsageEvent("device-constant", 1692617400000L + i * 1000, constantValue);
                aggregator.add(event, accumulator);
            }
            
            ProcessedCpuEvent result = aggregator.getResult(accumulator);
            
            assertEquals(constantValue, result.getPercentile95(), 0.01);
        }

        @Test
        @DisplayName("Should handle events with same timestamp")
        void shouldHandleEventsWithSameTimestamp() {
            CpuUsageAggregator.CpuAccumulator accumulator = aggregator.createAccumulator();
            long timestamp = 1692617400000L;
            
            aggregator.add(new CpuUsageEvent("device-001", timestamp, 60.0), accumulator);
            aggregator.add(new CpuUsageEvent("device-001", timestamp, 80.0), accumulator);
            
            assertEquals(timestamp, accumulator.windowStart);
            assertEquals(timestamp, accumulator.windowEnd);
            assertEquals(2, accumulator.count);
        }
    }
}