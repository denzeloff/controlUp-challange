# CPU Usage Processor

A real-time stream processing application built with Apache Flink and Kafka to process CPU usage events and calculate 95th percentile metrics.

## Features

- **Real-time Processing**: Processes CPU usage events from Kafka in real-time using Apache Flink
- **Time Windows**: Aggregates events in configurable time windows (default: 1 minute)
- **Percentile Analytics**: Calculates 95th percentile CPU usage per device
- **High Usage Detection**: Configurable threshold for high CPU usage alerts (default: 80%)
- **Fault Tolerance**: Built-in checkpointing every 30 seconds for reliability
- **Scalable**: Can be deployed on Flink cluster for horizontal scaling

## Architecture

```
Kafka (events topic) → Flink Processing → Kafka (processed-events topic)
```

### Data Models

#### Input: CpuUsageEvent
```json
{
  "deviceId": "device-001",
  "timestamp": 1692617400000,
  "cpuUsage": 75.5
}
```

#### Output: ProcessedCpuEvent
```json
{
  "deviceId": "device-001",
  "windowStart": 1692617400000,
  "windowEnd": 1692617460000,
  "percentile95": 85.1,
  "eventCount": 12,
  "lastUpdated": 1692617460000
}
```

## Requirements

- Java 21+
- Gradle 8.x+
- Apache Flink 1.20.2
- Kafka 3.6+ (running on localhost:9092)
- Topics: `events` (input), `processed-events` (output)

## Quick Start

### 1. Build the application
```bash
./gradlew build
```

### 2. Run the application
```bash
./gradlew run
```

### 3. Create a fat JAR for deployment
```bash
./gradlew shadowJar
```

### 4. Run on Flink cluster
```bash
flink run build/libs/cpu-usage-processor.jar
```

## Configuration

Edit `src/main/resources/application.properties`:

```properties
# Kafka Configuration
kafka.bootstrap.servers=localhost:9092
kafka.input.topic=events
kafka.output.topic=processed-events
kafka.consumer.group.id=cpu-usage-processor

# Processing Configuration
processor.window.size.minutes=1
processor.high.usage.threshold.percent=80.0
processor.checkpoint.interval.ms=30000

# Flink Configuration
flink.parallelism=1
flink.out.of.orderness.seconds=5
```

## Testing

### Send test events to Kafka
```bash
# Sample CPU usage event
echo '{"deviceId":"device-001","timestamp":1692617400000,"cpuUsage":85.5}' | \
kafka-console-producer.sh --bootstrap-server localhost:9092 --topic events
```

### Use the test producer
```bash
# Run the included test producer to generate realistic data
cd ../test
./gradlew run --args="10 100 5"  # 10 devices, 100 events/sec, 5 minutes
```

### Monitor processed events
```bash
kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic processed-events --from-beginning
```

## Deployment

### Docker Compose (Recommended)
```bash
# Run the entire system with Docker Compose
cd ../docker
docker-compose up cpu-usage-processor
```

### Docker (standalone)
```bash
# Build application
./gradlew shadowJar

# Build and run Docker container
docker build -t cpu-usage-processor .
docker run --network=host -e KAFKA_BOOTSTRAP_SERVERS=kafka:9092 cpu-usage-processor
```

### Flink Cluster
```bash
# Build fat JAR
./gradlew shadowJar

# Submit to Flink cluster
flink run -c com.controlup.processor.CpuUsageProcessorApplication \
  build/libs/cpu-usage-processor.jar
```

## Testing

### Run Unit Tests
```bash
./gradlew test
```

### Test Coverage
The project includes comprehensive unit tests for the core aggregation logic:

- **CpuUsageAggregatorTest**: Tests the 95th percentile calculation and window aggregation
  - Accumulator functionality (value storage, array resizing)
  - Event aggregation and window boundary tracking
  - 95th percentile calculation with Apache Commons Math
  - Merge operations for distributed processing
  - Edge cases (empty data, identical values, large datasets)

### Test Results
```bash
# View detailed test results
./gradlew test --info

# Generate test report
./gradlew test jacocoTestReport
```

## Monitoring

The application provides:
- **Checkpointing** every 30 seconds for fault tolerance
- **Logging** of processed events and high usage alerts
- **Metrics** through Flink's built-in metrics system

## Development

### Project Structure
```
src/main/java/com/controlup/processor/
├── CpuUsageProcessorApplication.java  # Main application
├── model/
│   ├── CpuUsageEvent.java            # Input event model
│   └── ProcessedCpuEvent.java        # Output event model
└── function/
    └── CpuUsageAggregator.java       # Window aggregation logic

src/test/java/com/controlup/processor/
└── function/
    └── CpuUsageAggregatorTest.java   # Comprehensive unit tests
```

### Adding New Features
1. Modify models in `model/` package
2. Update processing logic in `function/` package
3. Adjust main application in `CpuUsageProcessorApplication.java`
4. Update configuration in `application.properties`
5. Add corresponding unit tests in `src/test/java/`