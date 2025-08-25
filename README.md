# ControlUp CPU Metrics Challenge

A real-time CPU usage monitoring and analytics system built with Apache Flink, Kafka, Spring Boot, and PostgreSQL. The system processes CPU usage events in real-time, calculates 95th percentile metrics, and provides REST APIs for retrieving metrics data.

## System Architecture

```
Test Producer → Kafka → Flink Processor → Kafka → Metrics Handler → PostgreSQL
                                                                           ↑
                                                                    REST API
```

## Components

### 1. [Test Producer](./test/) 
**Kafka producer for generating realistic CPU usage test data**
- Simulates CPU usage events for multiple devices
- Configurable event rates and patterns
- Built with Java 21 + Kafka 3.9.1

### 2. [CPU Usage Processor](./cpu-usage-processor/)
**Real-time stream processing with Apache Flink** 
- Processes CPU usage events in 1-minute time windows
- Calculates 95th percentile CPU usage per device
- Built with Java 21 + Apache Flink 1.20.2

### 3. [CPU Metrics Handler](./cpu-metrics-handler/)
**Spring Boot service for persisting processed metrics**
- Consumes processed events from Kafka
- Stores metrics in PostgreSQL with optimized schema
- Built with Java 21 + Spring Boot 3.2.0

### 4. [CPU Metrics API](./cpu-metrics-api/)
**REST API for retrieving CPU metrics**
- GET `/api/metrics/devices/{deviceId}` - Latest 95th percentile for a device
- GET `/api/metrics/top/{n}` - Top N devices by CPU usage
- Built with Java 21 + Spring Boot 3.2.0 + OpenAPI/Swagger

## Code
Code structure follows a modular approach with separate directories for each component. Each component has its own `build.gradle` file for dependency management and build configuration.
Each module was built for a simplicity with layered architecture principles in mind, separating concerns into controllers, services, repositories, and models.

## Quick Start

### Prerequisites
- Docker & Docker Compose
- Java 21+ (for local development)

### Run the Complete System
```bash
# Start all services with Docker Compose
cd docker
docker-compose up

# The following services will be available:
# - Kafka: localhost:9092
# - PostgreSQL: localhost:5432
# - CPU Metrics API: http://localhost:8080/api
# - Swagger UI: http://localhost:8080/api/swagger-ui.html
```

### Manual Testing
```bash
# Check API health
curl http://localhost:8080/api/actuator/health

# Get metrics for a specific device
curl http://localhost:8080/api/metrics/devices/device-001

# Get top 10 devices by CPU usage
curl http://localhost:8080/api/metrics/top/10
```

## Data Flow

1. **Test Producer** generates CPU usage events:
   ```json
   {"deviceId": "device-001", "timestamp": 1692617400000, "cpuUsage": 75.5}
   ```

2. **CPU Usage Processor** (Flink) aggregates events in 1-minute windows and calculates 95th percentile

3. **Processed events** are published back to Kafka:
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

4. **CPU Metrics Handler** persists processed metrics to PostgreSQL

5. **CPU Metrics API** provides REST endpoints to query the stored metrics

## Development

### Build All Services
```bash
# Build all Java services
./cpu-usage-processor/gradlew -p cpu-usage-processor build
./cpu-metrics-handler/gradlew -p cpu-metrics-handler build  
./cpu-metrics-api/gradlew -p cpu-metrics-api build
./test/gradlew -p test build
```

### Run Individual Services Locally
```bash
# 1. Start infrastructure
cd docker
docker-compose up kafka postgres

# 2. Start services (in separate terminals)
cd cpu-usage-processor && ./gradlew run
cd cpu-metrics-handler && ./gradlew bootRun  
cd cpu-metrics-api && ./gradlew bootRun

# 3. Generate test data
cd test && ./gradlew run --args="10 100 5"
```

## Configuration

### Environment Variables (Docker)
- `KAFKA_BOOTSTRAP_SERVERS`: Kafka broker address
- `SPRING_DATASOURCE_URL`: PostgreSQL connection URL
- `SPRING_DATASOURCE_USERNAME/PASSWORD`: Database credentials

### Key Properties
- **Processing Window**: 1 minute (configurable)
- **High Usage Threshold**: 80% CPU (configurable)
- **Checkpoint Interval**: 30 seconds
- **Database Schema**: `cpu-metrics`
- **Kafka Topics**: `events` (input), `processed-events` (output)

## Monitoring

- **Health Checks**: Available via Spring Boot Actuator endpoints
- **Logs**: Structured logging for all components
- **Metrics**: Flink built-in metrics + Spring Boot metrics
- **Database Indexes**: Optimized for device queries and top-N rankings

## Technology Stack

- **Stream Processing**: Apache Flink 1.20.2
- **Message Queue**: Apache Kafka 3.9.1
- **Database**: PostgreSQL 15 with HikariCP
- **Backend**: Spring Boot 3.2.0 + Java 21
- **API Documentation**: OpenAPI/Swagger
- **Build Tool**: Gradle 8.x
- **Containerization**: Docker + Docker Compose
