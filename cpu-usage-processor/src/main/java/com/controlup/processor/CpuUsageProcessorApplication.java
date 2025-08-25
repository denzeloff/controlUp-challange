package com.controlup.processor;

import com.controlup.processor.function.CpuUsageAggregator;
import com.controlup.processor.model.CpuUsageEvent;
import com.controlup.processor.model.ProcessedCpuEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.formats.json.JsonDeserializationSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.OutputTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * Apache Flink application that processes CPU usage events from Kafka
 * and calculates 95th percentile over 30-minute sliding windows
 */
public class CpuUsageProcessorApplication {

    private static final Logger LOG = LoggerFactory.getLogger(CpuUsageProcessorApplication.class);

    private static final String KAFKA_BOOTSTRAP_SERVERS = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
    private static final String INPUT_TOPIC = System.getenv().getOrDefault("KAFKA_INPUT_TOPIC", "events");
    private static final String OUTPUT_TOPIC = System.getenv().getOrDefault("KAFKA_OUTPUT_TOPIC", "processed-events");
    private static final String CONSUMER_GROUP = System.getenv().getOrDefault("KAFKA_CONSUMER_GROUP", "cpu-usage-processor");

    public static void main(String[] args) throws Exception {
        LOG.info("Starting CPU Usage Processor Application - 95th Percentile Calculator");

        // Set up the execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        final OutputTag<CpuUsageEvent> lateEventsTag = new OutputTag<>("late-events") {
        };

        //TODO: Requires testing
        //Disable checkpointing for testing - state is too large for memory-backed storage
        //env.enableCheckpointing(10000, CheckpointingMode.EXACTLY_ONCE);
        //env.getCheckpointConfig().setMinPauseBetweenCheckpoints(500);
        //env.getCheckpointConfig().setCheckpointTimeout(60000);
        //env.setStateBackend(new RocksDBStateBackend("s3://my-bucket/flink-checkpoints"));

        // Create Kafka source
        KafkaSource<CpuUsageEvent> source = KafkaSource.<CpuUsageEvent>builder()
                .setBootstrapServers(KAFKA_BOOTSTRAP_SERVERS)
                .setTopics(INPUT_TOPIC)
                .setGroupId(CONSUMER_GROUP)
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new JsonDeserializationSchema<>(CpuUsageEvent.class))
                .build();

        // Create Kafka sink for processed events
        KafkaSink<String> sink = KafkaSink.<String>builder()
                .setBootstrapServers(KAFKA_BOOTSTRAP_SERVERS)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(OUTPUT_TOPIC)
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build())
                .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
                .build();

        ObjectMapper objectMapper = new ObjectMapper();

        // Create data processing pipeline
        DataStream<CpuUsageEvent> kafkaStream = env.fromSource(
                source,
                WatermarkStrategy.<CpuUsageEvent>forBoundedOutOfOrderness(Duration.ofSeconds(35))
                        .withIdleness(Duration.ofSeconds(10))
                        .withTimestampAssigner((event, timestamp) -> {
                            long eventTime = event.getTimestamp();
                            long currentTime = System.currentTimeMillis();
                            LOG.debug("Event time: {}, current time: {}, lag: {}ms",
                                    eventTime, currentTime, currentTime - eventTime);
                            return eventTime;
                        }),
                "Kafka Source"
        );

        SingleOutputStreamOperator<ProcessedCpuEvent> processedEvents = kafkaStream
                // Filter out invalid events
                .filter(event -> event != null && event.getDeviceId() != null)
                .name("Filter Valid Events")

                // Key by deviceId for windowing
                .keyBy(CpuUsageEvent::getDeviceId)

                // Apply sliding event-time window of 30 seconds, sliding every 5 seconds
                .window(SlidingEventTimeWindows.of(Duration.of(30, ChronoUnit.SECONDS), Duration.of(5, ChronoUnit.SECONDS)))
                .allowedLateness(Duration.of(35, ChronoUnit.SECONDS))
                .sideOutputLateData(lateEventsTag)

                // Aggregate events within the window
                .aggregate(new CpuUsageAggregator())
                .name("Aggregate CPU Usage");

        // Convert processed events to JSON and send to output topic
        processedEvents
                .map(event -> {
                    try {
                        String json = objectMapper.writeValueAsString(event);
                        LOG.info("Processed event: {}", event);
                        return json;
                    } catch (Exception e) {
                        LOG.error("Failed to serialize processed event: {}", event, e);
                        return "{}";
                    }
                })
                .name("Serialize to JSON")
                .sinkTo(sink)
                .name("Kafka Sink");

        // Log late events for monitoring
        processedEvents.getSideOutput(lateEventsTag)
                .map(ev -> "Late event: " + ev.getDeviceId() + " at " + ev.getTimestamp())
                .print();

        // Also log percentile results
        processedEvents
                .map(event -> {
                    LOG.info("95th Percentile Result - Device: {}, Percentile95: {}%, Window: {} - {}",
                            event.getDeviceId(), event.getPercentile95(),
                            event.getWindowStart(), event.getWindowEnd());
                    return event;
                })
                .name("Log Percentile Results");

        // Execute the job
        LOG.info("Executing CPU Usage Processor job...");
        env.execute("CPU Usage Processor");
    }
}
