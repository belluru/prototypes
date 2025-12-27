# Distributed Lock Prototype

A simple Java prototype demonstrating distributed lock implementation using Redis. This project shows how multiple consumers can safely acquire and release locks in a distributed environment.

## Overview

This is a **simple lock and release use case** where:
- Multiple threads compete to acquire a lock
- Once acquired, the thread holds the lock briefly (500ms)
- The lock is released before the TTL expires (1 second)
- No race conditions occur because locks are always released in time

## How to Run

```bash
docker compose up --build -d & sleep 3 && docker compose logs --tail=50
```

## Expected Output

```
REDIS_HOST: localhost
Application starting...
Consumer 2 acquired the lock.
Consumer 4 waiting to retry...
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

## Technical Details

- **Lock Key**: `my-distributed-lock`
- **Lock TTL**: 1 second (auto-expires if not released)
- **Hold Duration**: 500ms (always released before TTL)
- **Lock Mechanism**: Redis SETNX (atomic "set if not exists")

## Notes

- This is a **simple lock implementation** suitable for scenarios where locks are always released before TTL
- For production use cases with strict timing requirements, consider more sophisticated algorithms (e.g., Redlock)
