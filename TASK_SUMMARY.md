## Disclaimer

The task was implemented to meet the requirements, some implementations have been simplified and require refinement. Due to time constraints, not all edge cases and optimizations were addressed. Further improvements are needed for production readiness.
Architecture and design decisions were made by me, however, the code was mainly developed using claude code generation, with manual adjustments to ensure correctness.

## Next Steps:
1. Implement monitoring and alerting for the Flink job and Kafka cluster to ensure system reliability and performance.
### Key Metrics to Monitor

#### Flink Metrics:

* Checkpoint duration and size
* Records lag per subtask
* Task manager CPU/memory usage
* Backpressure indicators
* Window operator latency

#### Kafka Metrics:

* Consumer lag per partition
* Producer/consumer throughput
* Broker disk usage
* Under-replicated partitions
* Request latency percentiles

#### Application Metrics:

* API response time (p50, p95, p99)
* Database query performance
* Cache hit ratio
* Error rates by endpoint

2. Based on the monitoring data, optimize the Flink job configuration and Kafka settings to improve performance and reduce latency.
4. Fault tolerance:
* Kafka Fault Tolerance:
  * Ensure Kafka is configured with appropriate replication factors and in-sync replicas (ISR) settings.
  * Use Kafka's built-in consumer groups to handle failover and load balancing.
* Flink Fault Tolerance:
  * Implement state backend for Flink (e.g., RocksDB) to handle larger state sizes and ensure durability.
  * Use checkpointing and savepoints for Flink jobs to recover from failures.
* Database: 
  * Primary-secondary replication with automatic failover
  * Regular backups and point-in-time recovery
* Application:
    * Implement retry mechanisms and circuit breakers in API calls
    * Use health checks and monitoring to detect and recover from failures
    * Implement logging and alerting for critical failures
5. Scalability (10x Growth):
* Kafka:
  * Scale to 50-100 partitions, 9-12 brokers
* Flink:
  * Increase parallelism to 20-30
  * Optimize state management and windowing strategies
* Database:
  * Scale PostgreSQL vertically (more CPU/RAM) or horizontally (read replicas)
  * Implement connection pooling and optimize queries
  * Indexing strategies for faster lookups
* API:
  * Implement caching strategies (e.g., Redis) to reduce database load
  * Use load balancers to distribute traffic across multiple API instances

6. Implement performance testing using gatling or jmeter to simulate high load and identify bottlenecks.
7. Code improvements:
* Think of creating shared lib for cpu-metrics common models
* Restructure code to organization standards
* Implement continuous integration and deployment (CI/CD) pipelines for automated testing and deployment
* Implement contract testing for APIs
* Consider adding kafka schema registry for event schemas
* Upgrade dependencies(kafka, flink) to latest stable versions
