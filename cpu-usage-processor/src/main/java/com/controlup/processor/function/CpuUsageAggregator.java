package com.controlup.processor.function;

import com.controlup.processor.model.CpuUsageEvent;
import com.controlup.processor.model.ProcessedCpuEvent;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.apache.flink.api.common.functions.AggregateFunction;

import java.io.Serializable;
import java.util.UUID;

/**
 * Aggregates CPU usage events within a sliding time window and calculates 95th percentile
 */
public class CpuUsageAggregator implements AggregateFunction<CpuUsageEvent, CpuUsageAggregator.CpuAccumulator, ProcessedCpuEvent> {

    public static class CpuAccumulator implements Serializable {
        private static final long serialVersionUID = 1L;

        public String deviceId;
        public double[] cpuUsageArray;
        public int count = 0;
        public int capacity = 1000; // Initial capacity - reduced for memory efficiency
        public long windowStart = Long.MAX_VALUE;
        public long windowEnd = Long.MIN_VALUE;

        public CpuAccumulator() {
            this.cpuUsageArray = new double[capacity];
        }

        public CpuAccumulator(String deviceId) {
            this.deviceId = deviceId;
            this.cpuUsageArray = new double[capacity];
        }

        public void addValue(double value) {
            if (count >= cpuUsageArray.length) {
                // Resize array if needed
                double[] newArray = new double[cpuUsageArray.length * 2];
                System.arraycopy(cpuUsageArray, 0, newArray, 0, count);
                cpuUsageArray = newArray;
            }
            cpuUsageArray[count++] = value;
        }

        public double[] getValues() {
            double[] result = new double[count];
            System.arraycopy(cpuUsageArray, 0, result, 0, count);
            return result;
        }

        public boolean isEmpty() {
            return count == 0;
        }
    }

    @Override
    public CpuAccumulator createAccumulator() {
        return new CpuAccumulator();
    }

    @Override
    public CpuAccumulator add(CpuUsageEvent event, CpuAccumulator accumulator) {
        if (accumulator.deviceId == null) {
            accumulator.deviceId = event.getDeviceId();
        }

        accumulator.addValue(event.getCpuUsage());

        if (event.getTimestamp() < accumulator.windowStart) {
            accumulator.windowStart = event.getTimestamp();
        }
        if (event.getTimestamp() > accumulator.windowEnd) {
            accumulator.windowEnd = event.getTimestamp();
        }

        return accumulator;
    }

    @Override
    public ProcessedCpuEvent getResult(CpuAccumulator accumulator) {
        System.out.println("DEBUG: getResult called for device: " + accumulator.deviceId + ", count: " + accumulator.count);

        if (accumulator.isEmpty()) {
            long currentTime = System.currentTimeMillis();
            System.out.println("DEBUG: Empty accumulator, returning unknown result");
            return new ProcessedCpuEvent(UUID.randomUUID().toString(), "unknown", currentTime, currentTime, 0.0, currentTime);
        }

        double percentile95 = calculatePercentile95(accumulator.getValues());
        long lastUpdated = System.currentTimeMillis();

        System.out.println("DEBUG: Calculated 95th percentile: " + percentile95 + " for device: " + accumulator.deviceId);

        return new ProcessedCpuEvent(
                UUID.randomUUID().toString(),
                accumulator.deviceId,
                accumulator.windowStart,
                accumulator.windowEnd,
                percentile95,
                lastUpdated
        );
    }

    @Override
    public CpuAccumulator merge(CpuAccumulator acc1, CpuAccumulator acc2) {
        if (acc1.isEmpty()) return acc2;
        if (acc2.isEmpty()) return acc1;

        CpuAccumulator merged = new CpuAccumulator(acc1.deviceId);

        // Add all values from both accumulators
        for (int i = 0; i < acc1.count; i++) {
            merged.addValue(acc1.cpuUsageArray[i]);
        }
        for (int i = 0; i < acc2.count; i++) {
            merged.addValue(acc2.cpuUsageArray[i]);
        }

        merged.windowStart = Math.min(acc1.windowStart, acc2.windowStart);
        merged.windowEnd = Math.max(acc1.windowEnd, acc2.windowEnd);

        return merged;
    }

    /**
     * Calculates the 95th percentile from a list of CPU usage values
     */
    private double calculatePercentile95(double[] values) {
        if (values == null || values.length == 0) {
            return 0.0;
        }
        Percentile percentile = new Percentile(95);
        return percentile.evaluate(values);
    }
}
