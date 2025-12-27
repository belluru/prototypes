# Distributed Lock Prototype

A Java prototype demonstrating distributed lock implementation with Redis. This project explores the progression from simple, unsafe locks to production-ready atomic operations.

## Core Technical Details

All implementations share these common characteristics:

- **Lock Key**: `my-distributed-lock`
- **Lock TTL**: 1 second (prevents deadlocks if a consumer crashes)
- **Work Duration**: Configurable via `WORK_DURATION` (default: 500ms)
- **Acquisition**: Uses Redis `SETNX` (Atomic "Set if Not Exists")
- **Identification**: Each consumer uses a unique identifier to claim ownership.

---

## Implementations & Scenarios

### 1. Unsafe Version (No Value Checking)
The most basic implementation. It simply deletes the lock key without verifying ownership.

**Vulnerability:** If a lock expires while work is still in progress, a new consumer can grab it. When the original consumer finishes, it will delete the *new* consumer's lock.

#### Code Snippet
```java
// DistributedLock.java
jedis.del(LOCK_KEY);  // Simple delete, no checking
```

#### Run Scenarios
**Scenario A: Success (Work < TTL)**
```bash
# Clean state, start, and follow logs
docker compose --profile unsafe down && WORK_DURATION=500 docker compose --profile unsafe up --build -d && docker compose logs -f app-unsafe
```

**Scenario B: Race Condition (Work > TTL)**
```bash
# Clean state, start, and follow logs
docker compose --profile unsafe down && WORK_DURATION=1500 docker compose --profile unsafe up --build -d && docker compose logs -f app-unsafe
```
*Result: Consumer 1 deletes Consumer 2's lock.*

---

### 2. Safe Version (Value Checking, Non-Atomic)
Attempts to fix the unsafe version by checking if the lock value matches the consumer's ID before deleting.

**Vulnerability:** It has a "Time of Check to Time of Use" (TOCTOU) issue. A race condition can still occur between the `get` and the `del`.

#### Code Snippet
```java
// DistributedLockWithValueCheck.java
if (lockValue.equals(jedis.get(LOCK_KEY))) {  // Check
    jedis.del(LOCK_KEY);                      // Delete (race condition window!)
}
```

#### Run Scenario
```bash
# Clean state, start, and follow logs
docker compose --profile safe down && WORK_DURATION=1500 docker compose --profile safe up --build -d && docker compose logs -f app-safe
```
*Result: Consumer 1 may still delete Consumer 2's lock if the TTL expires exactly between the check and delete, which is a TOCTOU vulnerability shown in case 3.*

---

### 3. TOCTOU Version (Demonstrating the Gap)
Explicitly demonstrates the TOCTOU vulnerability by introducing a deliberate delay between the check and the delete.

#### Code Snippet
```java
// DistributedLockWithTOCTOU.java
String currentValue = jedis.get(LOCK_KEY);
if (lockValue.equals(currentValue)) {
    Thread.sleep(100);    // Deliberate window for race condition
    jedis.del(LOCK_KEY);  // Deletes someone else's lock!
}
```

#### Run Scenario
```bash
# Clean state, start, and follow logs
docker compose --profile toctou down && WORK_DURATION=1500 docker compose --profile toctou up --build -d && docker compose logs -f app-toctou
```
*Evidence: Logs will show Consumer 2 acquiring the lock while Consumer 1 is "sleeping" inside the release method.*

---

### 4. Lua Script Version (Production-Ready)
The correct implementation using Redis Lua scripting to ensure the check-and-delete operation is atomic.

#### Code Snippet (Lua)
```lua
-- DistributedLockWithLuaScript.java
if redis.call('get', KEYS[1]) == ARGV[1] then
    return redis.call('del', KEYS[1])
else
    return 0
end
```

#### Run Scenario
```bash
# Clean state, start, and follow logs
docker compose --profile lua down && WORK_DURATION=1500 docker compose --profile lua up --build -d && docker compose logs -f app-lua
```
*Result: Lock safely released. No race conditions possible because Redis executes the script as a single atomic unit.*

---

## Project Guide

### Troubleshooting Logs
If you don't see logs after running a command:
1.  **Remove `-d`**: Run without the detached flag to see output in real-time:
    `WORK_DURATION=1500 docker compose --profile unsafe up --build`
2.  **Check Service Names**: Ensure you are following the correct service (e.g., `app-unsafe`, `app-safe`, etc.).
3.  **Wait for Redis**: On first run, Redis might take a few seconds to start. The apps are configured to retry acquisition, but logs might take a moment to appear.

### Project Structure
```
├── src/main/java/com/example/
│   ├── DistributedLock.java              # Unsafe
│   ├── DistributedLockWithValueCheck.java# Non-atomic check
│   ├── DistributedLockWithTOCTOU.java    # Vulnerability demo
│   └── DistributedLockWithLuaScript.java # Atomic (Lua)
├── Dockerfile                            # Multi-stage build
├── docker-compose.yml                    # Service profiles
└── pom.xml                               # Maven config
```

### Docker Profiles
Run specific versions using profiles:
```bash
docker compose --profile [unsafe|safe|toctou|lua] up --build & sleep 5 && docker compose logs
```

---

## Key Lessons

1.  **Simple delete is dangerous**: Never delete a lock without verifying you still own it.
2.  **Atomicity is requirements**: Even with value checking, non-atomic operations are vulnerable (TOCTOU).
3.  **Lua scripts are the standard**: For single-instance Redis, Lua scripts are the simplest way to achieve atomicity for complex operations.
4.  **Production Readiness**: In production, consider robust libraries like Redisson (which implements Redlock) for distributed locks.
