# CPU Usage Test Producer

A Kafka producer for generating realistic CPU usage test events. This tool is used to simulate device CPU usage data for testing the CPU metrics processing pipeline.

## Features

- **Realistic Data Generation**: Creates varied CPU usage patterns across multiple devices
- **Configurable Parameters**: Number of devices, event rate, and duration
- **Proper Event Schema**: Uses the correct `{deviceId, timestamp, cpuUsage}` format
- **High Performance**: Built with Kafka 3.9.1 for efficient event production
- **Built with Java 21 and Gradle**

## Requirements

- Java 21+
- Gradle 8.x+
- Kafka 3.9.1+ (running on localhost:9092)
- Topic: `events` (must exist)

## Usage

### Build
```bash
./gradlew build
```

### Run with default parameters (10 devices, 100 events/sec, 60 minutes)
```bash
./gradlew run
```

### Run with custom parameters
```bash
./gradlew run --args="<numDevices> <eventsPerSecond> <durationMinutes>"
```

### Examples
```bash
# Generate events for 5 devices, 50 events/sec, for 30 minutes
./gradlew run --args="5 50 30"

# Generate events for 20 devices, 200 events/sec, for 10 minutes  
./gradlew run --args="20 200 10"

# Quick test: 3 devices, 10 events/sec, for 2 minutes
./gradlew run --args="3 10 2"
```

## Event Schema

The producer generates events with the following schema:
```json
{
  "deviceId": "device-001",
  "timestamp": 1672531200000,
  "cpuUsage": 45.67
}
```

## CPU Usage Patterns

The producer generates realistic CPU usage patterns:
- **60% low usage (0-40%)**: Normal system operation
- **30% medium usage (40-80%)**: Moderate load conditions  
- **10% high usage (80-100%)**: Peak load scenarios

This distribution creates varied data suitable for testing 95th percentile calculations and high-usage alerting.

## Deployment

### Docker Compose (Recommended)
```bash
# Run as part of the complete system
cd ../docker
docker-compose up test-producer
```

### Docker (standalone)
```bash
# Build application  
./gradlew build

# Build and run Docker container
docker build -t cpu-test-producer .
docker run \
  -e KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
  -e KAFKA_TOPIC=events \
  -e PRODUCER_RATE=100 \
  -e NUM_DEVICES=50 \
  cpu-test-producer
```

### Environment Variables (Docker)

- `KAFKA_BOOTSTRAP_SERVERS`: Kafka broker address (default: localhost:9092)
- `KAFKA_TOPIC`: Target Kafka topic (default: events)
- `PRODUCER_RATE`: Events per second (default: 100)  
- `NUM_DEVICES`: Number of device IDs to simulate (default: 50)

## Integration

This producer is designed to work with:
1. **cpu-usage-processor**: Consumes events from the `events` topic
2. **cpu-metrics-handler**: Processes the aggregated results
3. **cpu-metrics-api**: Provides API access to the processed metrics

Run the producer after starting Kafka and before running the processing pipeline for end-to-end testing.