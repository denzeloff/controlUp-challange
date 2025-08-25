package com.controlup.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Kafka producer for generating CPU usage test events
 */
public class CpuUsageProducer {

    private static final Logger LOG = LoggerFactory.getLogger(CpuUsageProducer.class);

    private static final String KAFKA_BOOTSTRAP_SERVERS = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
    private static final String TOPIC = System.getenv().getOrDefault("KAFKA_TOPIC", "events");

    private final KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper;
    private final Random random;

    public CpuUsageProducer() {
        this.producer = createProducer();
        this.objectMapper = new ObjectMapper();
        this.random = new Random();
    }

    private KafkaProducer<String, String> createProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 1);

        return new KafkaProducer<>(props);
    }

    public void generateEvents(int numDevices, int eventsPerSecond, int durationMinutes) {
        LOG.info("Starting to generate events: {} devices, {} events/sec, {} minutes",
                numDevices, eventsPerSecond, durationMinutes);

        String[] deviceIds = generateDeviceIds(numDevices);
        long endTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(durationMinutes);
        long intervalMs = 1000 / eventsPerSecond;

        long eventCount = 0;

        while (System.currentTimeMillis() < endTime) {
            try {
                for (int i = 0; i < eventsPerSecond; i++) {
                    String deviceId = deviceIds[random.nextInt(deviceIds.length)];
                    double cpuUsage = generateCpuUsage();
                    long timestamp = System.currentTimeMillis() - 30000;

                    CpuUsageEvent event = new CpuUsageEvent(deviceId, timestamp, cpuUsage);
                    String eventJson = objectMapper.writeValueAsString(event);

                    ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, deviceId, eventJson);

                    producer.send(record, (metadata, exception) -> {
                        if (exception != null) {
                            LOG.error("Failed to send event for device {}", deviceId, exception);
                        }
                    });

                    eventCount++;

                    if (eventCount % 1000 == 0) {
                        LOG.info("Sent {} events", eventCount);
                    }
                }

                Thread.sleep(intervalMs);

            } catch (Exception e) {
                LOG.error("Error generating events", e);
                break;
            }
        }

        LOG.info("Finished generating {} events", eventCount);
        producer.flush();
        producer.close();
    }

    private String[] generateDeviceIds(int numDevices) {
        String[] deviceIds = new String[numDevices];
        for (int i = 0; i < numDevices; i++) {
            deviceIds[i] = "device-" + String.format("%03d", i + 1);
        }
        return deviceIds;
    }

    private double generateCpuUsage() {
        // Generate realistic CPU usage patterns
        if (random.nextDouble() < 0.1) {
            // 10% chance of high CPU usage (80-100%)
            return 80.0 + random.nextDouble() * 20.0;
        } else if (random.nextDouble() < 0.3) {
            // 30% chance of medium CPU usage (40-80%)
            return 40.0 + random.nextDouble() * 40.0;
        } else {
            // 60% chance of low CPU usage (0-40%)
            return random.nextDouble() * 40.0;
        }
    }

    public static void main(String[] args) {
        int numDevices = args.length > 0 ? Integer.parseInt(args[0]) : 
            Integer.parseInt(System.getenv().getOrDefault("NUM_DEVICES", "10"));
        int eventsPerSecond = args.length > 1 ? Integer.parseInt(args[1]) : 
            Integer.parseInt(System.getenv().getOrDefault("PRODUCER_RATE", "100"));
        int durationMinutes = args.length > 2 ? Integer.parseInt(args[2]) : 
            Integer.parseInt(System.getenv().getOrDefault("DURATION_MINUTES", "60"));

        LOG.info("CPU Usage Producer starting with parameters:");
        LOG.info("  Number of devices: {}", numDevices);
        LOG.info("  Events per second: {}", eventsPerSecond);
        LOG.info("  Duration: {} minutes", durationMinutes);
        LOG.info("  Kafka servers: {}", KAFKA_BOOTSTRAP_SERVERS);
        LOG.info("  Topic: {}", TOPIC);

        CpuUsageProducer producer = new CpuUsageProducer();

        // Add shutdown hook for graceful termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down producer...");
            producer.producer.close();
        }));

        producer.generateEvents(numDevices, eventsPerSecond, durationMinutes);
    }
}
