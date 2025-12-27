# Distributed Lock Prototype

This project is a simple prototype of a distributed lock implementation using Java and Redis.

## Prerequisites

- Docker
- Docker Compose

## How to Run

1. **Clone the repository:**
   ```bash
   git clone <repository-url>
cd distributed-locks
   ```

2. **Build and run the application:**
   ```bash
docker compose up --build
```

This command will start a Redis container and an application container. The application will simulate 1000 consumers trying to acquire a distributed lock.

**Note:** The application is a simulation that runs and then exits. The output you see in your console is the result of this simulation. You will see messages from different consumers acquiring and releasing the lock. After a few seconds, the application will finish and the `app` container will stop.

## How it Works

The Java application uses a fixed-size thread pool to simulate multiple consumers. Each consumer tries to acquire a lock in Redis using the `SETNX` command with a TTL (Time To Live).

- If the lock is acquired, the consumer prints a success message, simulates some work, and then releases the lock.
- If the lock is not acquired, the consumer prints a failure message.

The `docker-compose.yml` file defines two services:
- `redis`: A standard Redis container.
- `app`: The Java application, which is built from the `Dockerfile`.

The application connects to Redis using the hostname `redis`, which is resolved by Docker's internal networking.