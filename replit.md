# Distributed Lock Prototype

## Overview
This project is a simple prototype demonstrating distributed lock implementation using Java and Redis. It shows how multiple consumers compete for a lock and demonstrates race conditions that can occur with distributed locks.

## Project Structure
- `src/main/java/com/example/DistributedLock.java` - Main application demonstrating distributed lock
- `pom.xml` - Maven build configuration
- `docker-compose.yml` - Docker configuration (not used in Replit environment)
- `Dockerfile` - Docker build file (not used in Replit environment)

## How to Run
The project is configured with a workflow that:
1. Starts Redis server in the background
2. Runs the Java application

To run manually:
```bash
redis-server --daemonize yes && sleep 1 && java -jar target/distributed-lock-example-1.0-SNAPSHOT.jar
```

## Build
To rebuild the project after making changes:
```bash
mvn clean package -DskipTests
```

## Environment
- Java: GraalVM 22.3
- Redis: System package
- Build: Maven with shade plugin for fat JAR

## How It Works
- Uses a fixed-size thread pool to simulate 5 consumers
- Each consumer attempts to acquire a lock using Redis SETNX with TTL
- Demonstrates race conditions when work takes longer than lock TTL
- Lock key: `my-distributed-lock`
- Lock TTL: 1 second

## Recent Changes
- 2025-12-27: Initial setup in Replit environment
  - Configured Java GraalVM and Redis
  - Set up workflow for running the demo
  - Added .gitignore for Java/Maven
