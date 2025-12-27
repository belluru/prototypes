# Distributed Lock Prototype

A Java prototype demonstrating distributed lock implementation with Redis. Includes four versions showing the progression from unsafe to production-ready:

1. **Unsafe** - Simple delete (basic race condition)
2. **Safe** - Value checking (but still vulnerable to TOCTOU)
3. **TOCTOU** - Demonstrates check-to-delete race condition
4. **Lua Script** - Atomic operations (production-ready)

## Four Implementations

### 1. Unsafe Version (No Value Checking)
- Simply deletes lock from Redis
- Vulnerable when lock expires during work
- File: `DistributedLock.java`

### 2. Safe Version (Value Checking, NOT Atomic)
- Checks lock value before deleting
- BUT has a window between check and delete
- Still vulnerable to TOCTOU race condition
- File: `DistributedLockWithValueCheck.java`

### 3. TOCTOU Version (Demonstrates the Vulnerability)
- Checks lock value before deleting
- Adds deliberate 100ms sleep between check and delete
- Clearly shows the TOCTOU window where other consumers can grab the lock
- File: `DistributedLockWithTOCTOU.java`

### 4. Lua Script Version (Production-Ready)
- Uses Redis Lua scripting for atomic check-and-delete
- Single atomic operation = no race condition window
- Guaranteed safe even with concurrent access
- File: `DistributedLockWithLuaScript.java`

## How to Run

### Unsafe Version (No Race Condition)
Work duration is 500ms, which is less than the 1 second lock TTL:

```bash
docker compose --profile unsafe up --build -d & sleep 3 && docker compose logs --tail=50
```

Or with custom work duration:
```bash
WORK_DURATION=500 docker compose --profile unsafe up --build -d & sleep 3 && docker compose logs --tail=50
```

**Expected behavior:**
```
Application starting...
Work duration: 500ms, Lock TTL: 1s
Consumer 3 acquired the lock.
Consumer 3 attempting to release the lock.
Lock released successfully.
...
Application finished.
```

---

### Unsafe Version (Race Condition Scenario)
Work duration exceeds TTL to trigger race condition:

```bash
WORK_DURATION=1500 docker compose --profile unsafe up --build -d & sleep 5 && docker compose logs --tail=100
```

**What happens:**
1. Consumer 1 acquires lock (1500ms work starts)
2. At ~1 second: Lock TTL expires in Redis
3. Consumer 2 acquires the lock
4. At ~1.5 seconds: Consumer 1 finishes and deletes lock
5. **Result:** Consumer 1 deleted Consumer 2's lock ❌

---

### Safe Version (Value Checking, But Not Atomic)
Same scenario but with value checking:

```bash
WORK_DURATION=1500 docker compose --profile safe up --build -d & sleep 5 && docker compose logs --tail=100
```

**What happens:**
1. Consumer 1 acquires lock (1500ms work starts)
2. At ~1 second: Lock TTL expires, Consumer 2 acquires it
3. At ~1.5 seconds: Consumer 1 checks value, it matches
4. Consumer 1 deletes the lock
5. **Result:** Consumer 1 deleted Consumer 2's lock ❌ (But with lower probability due to timing)

**Evidence in logs:**
```
Consumer 1 acquired the lock.
...
Consumer 2 acquired the lock.
...
Consumer 1 attempting to release.
Lock deleted (may have been someone else's!)
```

---

### TOCTOU Version (Demonstrating the Vulnerability)
Clearly shows the race condition window with deliberate sleep:

```bash
WORK_DURATION=1500 docker compose --profile toctou up --build -d & sleep 5 && docker compose logs --tail=100
```

**What happens:**
1. Consumer 1 acquires lock (1500ms work)
2. Consumer 1 checks: "Lock is mine" ✓
3. **100ms sleep (TOCTOU window opens)**
4. Consumer 2 acquires the same lock (TTL expired)
5. Consumer 1 wakes up and deletes the lock
6. **Result:** Consumer 1 deleted Consumer 2's lock ❌ (Happens reliably!)

**Evidence in logs:**
```
Consumer 1 acquired the lock.
...
Consumer 1 attempting to release the lock.
Value check passed, preparing to delete (TOCTOU window opens here...)
Consumer 2 acquired the lock.     ← Got lock while Consumer 1 was sleeping!
...
Consumer 1 deletes lock            ← Deletes Consumer 2's lock!
Lock deleted (may have been someone else's!)
Consumer 2 attempting to release.
Lock NOT released - value mismatch
```

---

### Lua Script Version (Production-Ready, Atomic)
The proper solution using Lua scripting:

```bash
WORK_DURATION=1500 docker compose --profile lua up --build -d & sleep 5 && docker compose logs --tail=100
```

**What happens:**
1. Consumer 1 acquires lock (1500ms work)
2. Consumer 1 calls Lua script for atomic check-and-delete
3. **Single atomic Redis operation:** Check value AND delete happen together
4. Redis guarantees: Either both succeed or neither does
5. **Result:** Lock safely released, no race condition ✓

**Evidence in logs:**
```
Consumer 1 acquired the lock.
...
Consumer 1 attempting to release.
Lock released successfully (atomic check-and-delete).
Consumer 2 acquired the lock.
...
Consumer 2 attempting to release.
Lock released successfully (atomic check-and-delete).
```

**Key difference:** No "value mismatch" messages because Lua makes it impossible for another consumer to grab the lock between check and delete.

---

## Project Structure

```
├── src/main/java/com/example/
│   ├── DistributedLock.java                    # Unsafe version (no value checking)
│   ├── DistributedLockWithValueCheck.java      # Safe version (non-atomic value checking)
│   ├── DistributedLockWithTOCTOU.java          # TOCTOU demo (shows vulnerability with sleep)
│   └── DistributedLockWithLuaScript.java       # Production-ready (atomic Lua script)
├── pom.xml                                     # Maven build configuration
├── Dockerfile                                  # Multi-stage Docker build (supports all 4)
├── docker-compose.yml                          # Services for all 4 implementations
└── README.md                                   # This file
```

## Docker Profiles

The project uses Docker Compose profiles to run different implementations:

```bash
# Run unsafe version
docker compose --profile unsafe up --build

# Run safe version (value checking)
docker compose --profile safe up --build

# Run TOCTOU version (demonstrates vulnerability)
docker compose --profile toctou up --build

# Run Lua script version (production-ready)
docker compose --profile lua up --build

# Run all four versions simultaneously
docker compose up --build
```

## Technical Details

- **Lock Key**: `my-distributed-lock`
- **Lock TTL**: 1 second (auto-expires if not released)
- **Work Duration**: Configurable via `WORK_DURATION` environment variable (default 500ms)
- **Lock Mechanism**: Redis SETNX (atomic "set if not exists")

### Unsafe Release
```java
jedis.del(LOCK_KEY);  // Simple delete, no checking
```

### Non-Atomic Release (Safe but Still Vulnerable)
```java
if (lockValue.equals(jedis.get(LOCK_KEY))) {  // Check
    jedis.del(LOCK_KEY);                      // Delete (race condition window!)
}
```

### TOCTOU Vulnerability Demo (With Sleep)
```java
String currentValue = jedis.get(LOCK_KEY);
if (lockValue.equals(currentValue)) {
    Thread.sleep(100);  // Creates window for another consumer to grab lock
    jedis.del(LOCK_KEY);  // Deletes someone else's lock!
}
```

### Atomic Release (Lua Script - Production-Ready)
```lua
if redis.call('get', KEYS[1]) == ARGV[1] then
    return redis.call('del', KEYS[1])
else
    return 0
end
```

---

## Key Lessons

This prototype demonstrates:

1. **Simple delete** = vulnerable to basic race conditions
2. **Value checking** = prevents wrong consumer from deleting lock, BUT vulnerable to TOCTOU
3. **TOCTOU vulnerability** = gap between check and delete where another consumer can grab lock
4. **Lua scripts** = atomic operations guarantee no race condition window
5. **Production solution** = Always use atomic operations (Lua, Redlock, or database transactions)

### When to Use Each:
- **Unsafe**: Only for learning/demo (or when work duration << TTL)
- **Safe**: When race conditions are unlikely due to timing
- **TOCTOU**: Educational demonstration of why atomicity matters
- **Lua Script**: Production environments where correctness is critical
