package com.example;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.params.SetParams;
import java.util.ArrayList;
import java.util.List;

/**
 * Replicates the Redlock "Clock Drift" vulnerability.
 * 
 * Scenario:
 * 1. Client A acquires Redlock on nodes A, B, C, D, E (TTL = 2s).
 * 2. Simulated "Clock Drift": Nodes A, B, and C's clocks jump forward, 
 *    effectively expiring the lock immediately on those nodes.
 * 3. Client B attempts to acquire the lock. Since A, B, and C's locks are "expired",
 *    Client B successfully acquires the lock (reaching a quorum of 3).
 * 4. BOTH clients now hold the lock simultaneously!
 */
public class RedlockClockDriftDemo {
    private static final String LOCK_KEY = "redlock-drift-demo";
    private static final int LOCK_TTL_SECONDS = 2; // Longer TTL to allow for manual drift simulation
    private static final List<JedisPool> POOLS = new ArrayList<>();

    public static void main(String[] args) throws InterruptedException {
        setupRedisPools();
        
        System.out.println("=== Redlock Clock Drift Vulnerability Demo ===");

        // 1. Client A acquires the lock
        String lockValueA = "client-A";
        System.out.println("[Client A] Attempting to acquire lock with 2s TTL...");
        if (acquireRedlock(lockValueA)) {
            System.out.println("[Client A] SUCCESS! Acquired lock.");
            
            // 2. Simulate Clock Drift on 3 nodes (A, B, C)
            System.out.println("[System] Simulating clock drift on 3 nodes (A, B, C) - expiring keys early...");
            simulateClockDrift(3); 

            // 3. Client B attempts to acquire the lock
            System.out.println("\n[Client B] Attempting to acquire lock while Client A still thinks it's valid...");
            String lockValueB = "client-B";
            if (acquireRedlock(lockValueB)) {
                System.out.println("[Client B] SUCCESS! Client B also acquired the lock due to drift.");
                
                // 4. Verification
                System.out.println("\n!!! SAFETY VIOLATION !!!");
                System.out.println("Both Client A and Client B believe they hold the lock.");
            } else {
                System.out.println("[Client B] Failed to acquire lock (This shouldn't happen in this demo).");
            }
            
            releaseRedlock(lockValueA);
            releaseRedlock(lockValueB);
        } else {
            System.out.println("[Client A] Failed to acquire lock.");
        }

        System.out.println("\nDemo Finished.");
        closePools();
    }

    private static void simulateClockDrift(int nodeCount) {
        for (int i = 0; i < nodeCount; i++) {
            try (Jedis jedis = POOLS.get(i).getResource()) {
                jedis.del(LOCK_KEY); // Manual deletion simulates early expiration due to clock drift
            } catch (Exception e) {}
        }
    }

    private static boolean acquireRedlock(String lockValue) {
        int acquiredCount = 0;
        SetParams params = new SetParams().nx().ex(LOCK_TTL_SECONDS);
        for (JedisPool pool : POOLS) {
            try (Jedis jedis = pool.getResource()) {
                if ("OK".equals(jedis.set(LOCK_KEY, lockValue, params))) {
                    acquiredCount++;
                }
            } catch (Exception e) {}
        }
        return acquiredCount >= 3;
    }

    private static void releaseRedlock(String lockValue) {
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        for (JedisPool pool : POOLS) {
            try (Jedis jedis = pool.getResource()) {
                jedis.eval(script, 1, LOCK_KEY, lockValue);
            } catch (Exception e) {}
        }
    }

    private static void setupRedisPools() {
        String nodesEnv = System.getenv().getOrDefault("REDIS_NODES", "localhost:6379,localhost:6380,localhost:6381,localhost:6382,localhost:6383");
        String[] nodes = nodesEnv.split(",");
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        for (String node : nodes) {
            String[] parts = node.split(":");
            POOLS.add(new JedisPool(poolConfig, parts[0], Integer.parseInt(parts[1]), 2000));
        }
    }

    private static void closePools() {
        for (JedisPool pool : POOLS) pool.close();
    }
}
