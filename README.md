# Distributed Lock Prototype

A simple Java prototype demonstrating distributed lock implementation using Redis. This project shows how multiple consumers can safely acquire and release locks in a distributed environment.

## Overview

This is a **simple lock and release use case** where:
- Multiple threads compete to acquire a lock
- Once acquired, the thread holds the lock for a configurable duration
- The lock is released by simply deleting it from Redis (no value checking)
- Race conditions can occur when work duration exceeds lock TTL

## How to Run

### Safe Scenario (No Race Condition)
Work duration is 500ms, which is less than the 1 second lock TTL:

```bash
docker compose up --build -d & sleep 3 && docker compose logs --tail=50
```

Or explicitly pass the work duration:
```bash
WORK_DURATION=500 docker compose up --build -d & sleep 3 && docker compose logs --tail=50
```

**Expected behavior:**
```
Work duration: 500ms, Lock TTL: 1s
Consumer 3 acquired the lock.
Consumer 3 attempting to release the lock.
Lock released successfully.
Consumer 1 acquired the lock.
...
Application finished.
```

All consumers safely acquire and release locks because they complete before TTL expires.

---

### Race Condition Scenario (Lock Expiry During Release)
Work duration is 1500ms, which exceeds the 1 second lock TTL.

Pass the work duration as an environment variable:
```bash
WORK_DURATION=1500 docker compose up --build -d & sleep 5 && docker compose logs --tail=100
```

**What happens:**
1. Consumer 1 acquires lock (1500ms work starts)
2. At ~1 second: Lock TTL expires in Redis (lock becomes available)
3. Consumer 2 acquires the same lock
4. At ~1.5 seconds: Consumer 1 finishes and calls `releaseLock()`
5. **Consumer 1 deletes Consumer 2's lock** ❌ (Wrong consumer deleted it!)

**Evidence in logs:**
```
Consumer 1 acquired the lock.
...
Consumer 2 acquired the lock.         ← Lock expired, Consumer 2 got it
...
Consumer 1 attempting to release.    ← Consumer 1 still finishing
Lock released successfully.           ← Deletes Consumer 2's lock!
```

This demonstrates why value checking is essential in distributed locks.

---

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
- **Default Work Duration**: 500ms (can be passed as command argument)
- **Lock Mechanism**: Redis SETNX (atomic "set if not exists")
- **Release**: Simple `del` (no value checking - vulnerable to race conditions)

## Notes

- This prototype demonstrates why **value checking is critical** in distributed locks
- When work duration < TTL: Safe, no race condition
- When work duration > TTL: Race condition occurs, wrong consumer can delete the lock
- For production: Use Redlock algorithm or value-checking with Lua scripts
