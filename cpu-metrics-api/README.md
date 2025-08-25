# CPU Metrics API

A RESTful API service built with Spring Boot and Java 21 for retrieving CPU usage percentile metrics.

## Features

- **GET /api/metrics/devices/{deviceId}** - Retrieve the latest 95th percentile CPU usage for a specific device
- **GET /api/metrics/top/{n}** - Retrieve top N devices by 95th percentile from the latest complete window
- Comprehensive input validation and error handling
- OpenAPI/Swagger documentation
- PostgreSQL database integration

## Prerequisites

- Java 21
- PostgreSQL database
- Gradle 8.x

## Database Setup

The API connects to the same PostgreSQL database used by the cpu-metrics-handler. The database schema is automatically created by the cpu-metrics-handler using Flyway migrations:

- Database: `controlup`  
- Schema: `cpu-metrics`
- Migration files: `/docker/db/migrations/V1__create_cpu_metrics_schema.sql`

Ensure the cpu-metrics-handler is running first to initialize the database.

## Running the Application

1. **Using Gradle:**
   ```bash
   cd cpu-metrics-api
   ./gradlew bootRun
   ```

2. **Using JAR:**
   ```bash
   ./gradlew build
   java -jar build/libs/cpu-metrics-api-1.0-SNAPSHOT.jar
   ```

## API Documentation

Once the application is running, you can access:

- **Swagger UI**: http://localhost:8080/api/swagger-ui.html
- **OpenAPI Docs**: http://localhost:8080/api/v3/api-docs

## API Endpoints

### Get Device Metric

Retrieves the latest 95th percentile CPU usage metric for a specific device.

**Request:**
```
GET /api/metrics/devices/{deviceId}
```

**Response:**
```json
{
  "deviceId": "device-123",
  "percentile95": 85.5,
  "windowStart": 1734567890000,
  "windowEnd": 1734567920000,
  "lastUpdated": 1734567890000
}
```

### Get Top N Devices

Retrieves the top N devices ranked by their 95th percentile CPU usage from the most recent complete window.

**Request:**
```
GET /api/metrics/top/{n}
```

**Response:**
```json
[
  {
    "deviceId": "device-456",
    "percentile95": 95.2,
    "windowStart": 1734567890000,
    "windowEnd": 1734567920000,
    "lastUpdated": 1734567890000
  },
  {
    "deviceId": "device-789",
    "percentile95": 92.1,
    "windowStart": 1734567890000,
    "windowEnd": 1734567920000,
    "lastUpdated": 1734567890000
  }
]
```

## Configuration

Key configuration properties in `application.properties`:

```properties
# Server Configuration
server.port=8080
server.servlet.context-path=/api

# Database Configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/controlup
spring.datasource.username=postgres
spring.datasource.password=postgres

# HikariCP Connection Pooling
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=5

# Documentation
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.api-docs.path=/v3/api-docs

# Actuator Health Checks
management.endpoints.web.exposure.include=health,info,metrics
```

## Error Handling

The API provides comprehensive error responses:

```json
{
  "status": 404,
  "message": "Device with ID 'invalid-device' not found",
  "timestamp": "2023-12-19T10:30:00Z",
  "path": "/api/metrics/devices/invalid-device"
}
```

## Testing

### Integration Tests with Testcontainers

The project includes comprehensive integration tests using **Testcontainers** to ensure reliable, isolated testing against real PostgreSQL databases.

#### Test Structure

```
src/test/java/com/controlup/api/
├── testdata/
│   ├── TestDataBuilder.java           # Smart test data generation
│   ├── CpuMetricTestData.java         # Test data model
│   └── DatabaseTestHelper.java       # Database utilities for tests
├── repository/
│   └── CpuMetricsRepositoryIntegrationTest.java  # Repository layer tests
├── service/
│   └── MetricsServiceIntegrationTest.java        # Service layer tests
└── controller/
    └── MetricsControllerIntegrationTest.java     # API endpoint tests
```

#### Key Features of Integration Tests

**🔧 Smart Test Data Management:**
- **TestDataBuilder**: Fluent API for creating realistic test scenarios
- **Predefined Patterns**: Low, medium, high, and critical CPU usage scenarios
- **Edge Cases**: Zero values, 100% usage, high precision decimals
- **Time-Based Testing**: Historical data and latest window scenarios
- **Performance Data**: Large datasets (1000+ records) for load testing

**🗄️ Database Integration:**
- **Real PostgreSQL**: Testcontainers with PostgreSQL 15
- **Schema Management**: Automatic schema creation from migration scripts
- **Data Isolation**: Clean database state for each test
- **Query Validation**: Tests verify actual SQL query execution

**📊 Comprehensive Coverage:**

*Repository Layer Tests:*
- Database schema validation
- Complex SQL query correctness (latest window, top-N ranking)
- Edge cases (empty results, boundary values)
- Performance with large datasets
- Concurrent access patterns

*Service Layer Tests:*
- Business logic validation
- Transaction integrity
- Error handling and logging
- Service-specific edge cases
- Performance benchmarks

*API Layer Tests:*
- HTTP status codes and response formats
- Request validation and parameter constraints
- JSON structure and data types
- Error responses and meaningful messages
- Performance under load

#### Running Integration Tests

```bash
# Run all integration tests
./gradlew test

# Run specific test class
./gradlew test --tests "CpuMetricsRepositoryIntegrationTest"

# Run tests with detailed output
./gradlew test --info

# Performance testing with large datasets
./gradlew test --tests "*PerformanceTest*"
```

#### Test Data Examples

```java
// Smart test data generation
List<CpuMetricTestData> testData = TestDataBuilder.scenario()
    .withLowUsageDevices(5)      // 5-30% CPU usage
    .withHighUsageDevices(3)     // 70-95% CPU usage
    .withCriticalUsageDevices(2) // 95-100% CPU usage
    .withEdgeCases()             // 0%, 100%, precision values
    .withTimeBasedScenario()     // Historical vs latest data
    .build();

// Large dataset for performance testing
TestDataBuilder.scenario()
    .withLargeDataset(1000)      // 1000 realistic devices
    .build();
```

#### Test Database Configuration

Integration tests use:
- **PostgreSQL 15** via Testcontainers
- **Schema**: Identical to production (`cpu-metrics` schema)
- **Indexes**: Same optimized indexes as production
- **Migrations**: Flyway migrations applied automatically

#### Performance Assertions

Tests validate performance requirements:
- Repository queries: < 100ms for single device lookup
- Service layer: < 2 seconds for complex operations  
- API endpoints: < 1 second per request
- Large dataset handling: < 5 seconds for 1000+ records

## Architecture

- **Controller Layer**: REST endpoints with validation
- **Service Layer**: Business logic
- **Repository Layer**: Database access with optimized queries
- **DTO Layer**: Response objects with JSON serialization
- **Exception Handling**: Global exception handler with proper HTTP status codes

## Deployment

### Docker Compose (Recommended)
```bash
# Run the entire system with Docker Compose
cd ../docker
docker-compose up cpu-metrics-api
```

### Docker (standalone)
```bash
# Build application
./gradlew build

# Build and run Docker container
docker build -t cpu-metrics-api .
docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/controlup \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD=postgres \
  cpu-metrics-api
```

## Dependencies

This API requires:
1. **PostgreSQL database** - with schema created by cpu-metrics-handler
2. **cpu-metrics-handler** - to populate the database with processed metrics

## Database Schema

The API reads from the `cpu-metrics.cpu_metrics` table which contains:
- `device_id`: Device identifier
- `window_start`/`window_end`: Time window boundaries  
- `percentile_95`: Calculated 95th percentile CPU usage
- `event_count`: Number of events in the window
- `last_updated`: Timestamp of last update

## Database Optimization

The API leverages database indexes for optimal performance:
- `idx_device_latest_updated`: For device-specific queries
- `idx_percentile_ranking`: For top-N ranking queries