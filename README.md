# Distributed Lock Prototype

A simple Java prototype demonstrating distributed lock implementation using Redis. This project shows how multiple consumers can safely acquire and release locks in a distributed environment.

## Overview

This is a **simple lock and release use case** where:
- Multiple threads compete to acquire a lock
- Once acquired, the thread holds the lock briefly (500ms)
- The lock is released before the TTL expires (1 second)
- No race conditions occur because locks are always released in time

## Prerequisites

- **Docker** and **Docker Compose** (for containerized execution)
- **Java 11** and **Maven** (for local development)
- **Redis** (for local development)

## How to Run

### Using Docker Compose

```bash
# Run in background and verify logs
docker compose up --build -d & sleep 3 && docker compose logs --tail=50
```

This command:
- Builds the Docker image
- Starts Redis and Java application in background
- Waits 3 seconds for startup
- Displays the last 50 lines of logs

### Running Locally (without Docker)

```bash
# 1. Start Redis server
redis-server --daemonize yes

# 2. Build the project with Maven
mvn clean package

# 3. Run the application
java -jar target/distributed-lock-example-1.0-SNAPSHOT.jar
```

## How It Works

### Concurrent Execution with Thread Pool & Connection Pooling

The code uses:
- **ExecutorService** - manages 5 consumer threads concurrently
- **JedisPool** - reuses Redis connections across threads (thread-safe)

```java
JedisPool pool = new JedisPool(REDIS_HOST, 6379);
ExecutorService executor = Executors.newFixedThreadPool(NUM_CONSUMERS);
executor.submit(() -> {
    try (Jedis jedis = pool.getResource()) {
        // Consumer logic runs in a separate thread
    }
});
```

**Benefits:**
- Thread pool manages 5 threads efficiently without creating/destroying new ones
- Connection pooling reuses connections instead of creating new ones for each consumer
- `executor.awaitTermination()` waits for all consumers to finish

### Lock Acquisition & Release

- **Lock Key**: `my-distributed-lock`
- **Lock TTL**: 1 second (auto-expires if not released)
- **Hold Duration**: 500ms (always released before TTL)
- **Lock Mechanism**: Redis `SETNX` (atomic "set if not exists")
- **Lock Release**: Simple `del` command (no value checking needed)
- **Retry Strategy**: Infinite retries with 200ms backoff (guarantees all consumers acquire lock)

## Expected Output

```
REDIS_HOST: localhost
Application starting...
Consumer 2 acquired the lock.
Consumer 4 waiting to retry (attempt 1)...
...
Consumer 2 attempting to release the lock.
Lock released successfully.
Consumer 4 acquired the lock.
...
Application finished.
```

All 5 consumers successfully acquire and release the lock.

## Project Structure

```
├── src/main/java/com/example/
│   └── DistributedLock.java      # Main application with lock logic
├── pom.xml                         # Maven build configuration
├── Dockerfile                      # Multi-stage Docker build
├── docker-compose.yml              # Service orchestration
└── README.md                       # This file
```

## Technologies

- **Language**: Java 11
- **Build Tool**: Maven
- **Database**: Redis (for distributed locking)
- **Concurrency**: ExecutorService with CountDownLatch
- **Containerization**: Docker & Docker Compose

## Notes

- This is a **simple lock implementation** suitable for scenarios where locks are always released before TTL
- For production use cases with strict timing requirements, consider more sophisticated algorithms (e.g., Redlock)
