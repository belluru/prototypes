# Distributed Lock Prototype

A Java prototype demonstrating the evolution of distributed locks, from simple Redis implementations to strictly-safe consensus-based systems.

## 🎓 Learning Path

For a progressive understanding of distributed locking, follow the documentation in this order:

1.  **[Single-Node Redis Locks](#implementations--scenarios)**: Basics of `SETNX`, atomicity issues (TOCTOU), and the modern Lua solution.
2.  **[5-Node Redis (Redlock)](#5-redlock-version-multi-node-high-availability)**: Achieving High Availability for the lock itself.
3.  **[Redlock's Vulnerabilities](#redlock-vulnerability-demos-controlled-simulations)**: Hands-on demos of Clock Drift and GC Pauses that break Redlock.
4.  **[The Paxos Solution (Consensus)](#6-paxos-version-consensus-based-production-grade)**: How logical time and fencing tokens provide strict safety. **[Deep Dive here](file:///Users/ellurubharath/Documents/Coding/prototypes/distributed-locks/PAXOS_README.md)**.

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
docker compose --profile toctou down && WORK_DURATION=950 docker compose --profile toctou up --build -d && docker compose logs -f app-toctou
```
*Evidence: Logs will show Consumer 2 acquiring the lock while Consumer 1 is "sleeping" inside the release method.*

---

### 4. Lua Script Version (Production-Ready)
The correct implementation using Redis Lua scripting to ensure the check-and-delete operation is atomic.

#### Code Snippet (Lua)
```lua
-- DistributedLockWithLuaScript.java
-- Demonstrates atomicity using a deliberate busy-wait loop
for i = 1, 10000000 do 
    -- This blocks the entire Redis server while active
end; 
if redis.call('get', KEYS[1]) == ARGV[1] then 
    return redis.call('del', KEYS[1]) 
else 
    return 0 
end
```

#### Run Scenario
```bash
# Clean state, start, and follow logs
docker compose --profile lua down && WORK_DURATION=950 docker compose --profile lua up --build -d && docker compose logs -f app-lua
```
*Result: Lock safely released. The busy-wait loop blocks Redis, making it impossible for another consumer to grab the lock during that window.*

---

### 5. Redlock Version (Multi-Node, High Availability)
A distributed lock implementation designed for high availability using 5 independent Redis nodes.

**Quorum Logic:** The lock is acquired successfully only if the consumer can set the lock key in at least **3 out of 5** nodes. This ensures that the system is resilient to up to 2 node failures.

#### Code Snippet (Quorum Acquisition)
```java
// Redlock.java
private static boolean acquireRedlock(String lockValue) {
    int acquiredCount = 0;
    SetParams setParams = new SetParams().nx().ex(LOCK_TTL_SECONDS);

    for (JedisPool pool : POOLS) {
        try (Jedis jedis = pool.getResource()) {
            String result = jedis.set(LOCK_KEY, lockValue, setParams);
            if ("OK".equals(result)) acquiredCount++;
        } catch (Exception e) { /* Node down */ }
    }

    if (acquiredCount >= 3) return true; // Quorum reached
    
    releaseRedlock(lockValue); // Rollback partial locks
    return false;
}
```

#### Run Scenario
```bash
# Clean state, start 5 Redis nodes, and follow Redlock logs
docker compose --profile redlock down && WORK_DURATION=950 docker compose --profile redlock up --build -d && docker compose logs -f app-redlock
```
*Result: Consumers acquire the lock only when they reach a quorum. High availability is achieved by distributing the lock state across multiple independent Redis instances.*

Play around with duration more than TTL such as 1500ms to see the effect of quorum. Delete is atomic, so we should not encounter race conditions.

---

### Redlock Vulnerability Demos (Controlled Simulations)

These demos are specifically designed to replicate the safety failures discussed in the [Paxos Guide](file:///Users/ellurubharath/Documents/Coding/prototypes/distributed-locks/PAXOS_README.md).

#### 1. GC Pause Vulnerability
Simulates Client A acquiring the lock and then hitting a long pause. Client B takes over, and when Client A wakes up, both are in the critical section!
```bash
docker compose --profile redlock-gc up --build
```

#### 2. Clock Drift Vulnerability
Simulates a quorum of nodes (3/5) having their clocks jump forward, causing keys to expire early. Client B acquires the lock while Client A still thinks it holds it.
```bash
docker compose --profile redlock-drift up --build
```

---

### 6. Paxos Version (Consensus-based, Production Grade)
A distributed lock implementation using a simplified Paxos consensus algorithm to address Redlock's limitations regarding clock synchronization and process pauses.

**Key Features:**
- **Logical Time**: Uses sequence numbers instead of physical clocks.
- **Fencing Tokens**: Provides monotonically increasing tokens to prevent stale writes.
- **Quorum-based Agreement**: Ensures consistency across 5 nodes.

#### Run Scenario
```bash
docker compose --profile paxos up --build
```

**[Read the Detailed Paxos Guide](file:///Users/ellurubharath/Documents/Coding/prototypes/distributed-locks/PAXOS_README.md)** for a deep dive into Redlock limitations and the Paxos solution.

> [!NOTE]
> For a theoretical deep dive into why Redlock is often considered insufficient for strict safety, see Martin Kleppmann's seminal article: [How to do distributed locking](https://martin.kleppmann.com/2016/02/08/how-to-do-distributed-locking.html).

---

## Project Guide

### Project Structure
```
├── src/main/java/com/example/
│   ├── DistributedLock.java              # 1. Unsafe
│   ├── DistributedLockWithValueCheck.java# 2. Safe (Non-atomic)
│   ├── DistributedLockWithTOCTOU.java    # 3. TOCTOU Demo
│   ├── DistributedLockWithLuaScript.java # 4. Lua (Atomic)
│   ├── Redlock.java                      # 5. Redlock HA
│   ├── RedlockGCPauseDemo.java           # 6a. GC Pause Demo
│   ├── RedlockClockDriftDemo.java        # 6b. Clock Drift Demo
│   └── paxos/                            # 7. Paxos Implementation
│       └── PaxosDemo.java                # Consolidated Protocol & Demo
├── PAXOS_README.md                       # Detailed Paxos & Safety docs
├── docker-compose.yml                    # Service profiles for all demos
├── Dockerfile                            # Multi-stage build
└── pom.xml                               # Maven config
```

### Docker Profiles
Run specific versions using profiles:
```bash
docker compose --profile [unsafe|safe|toctou|lua|redlock|redlock-gc|redlock-drift|paxos] up --build
```

---

## Key Lessons

1.  **Simple delete is dangerous**: Never delete a lock without verifying you still own it.
2.  **Atomicity is requirements**: Even with value checking, non-atomic operations are vulnerable (TOCTOU).
3.  **Lua scripts are the standard**: For single-instance Redis, Lua scripts are the simplest way to achieve atomicity for complex operations.
4.  **Production Readiness**: In production, consider robust libraries like Redisson (which implements Redlock) for distributed locks.
5.  **Fencing Tokens for Safety**: Note that Redis does not have a native way to generate monotonically increasing fencing tokens. While fencing tokens are the standard way to resolve GC pause vulnerabilities, you must implement a system (like the Paxos prototype) to generate and validate them.
