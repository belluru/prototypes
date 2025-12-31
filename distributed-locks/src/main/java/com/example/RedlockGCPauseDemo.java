package com.example;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.params.SetParams;
import java.util.ArrayList;
import java.util.List;

/**
 * Replicates the Redlock "Process Pause" vulnerability.
 * 
 * Scenario:
 * 1. Client A acquires Redlock (TTL = 1s).
 * 2. Client A immediately hits a "GC Pause" (Thread.sleep for 2s).
 * 3. The lock expires on the Redis nodes.
 * 4. Client B acquires the same Redlock.
 * 5. Client A wakes up and thinks it still has the lock.
 * 6. BOTH clients are now in the critical section!
 */
public class RedlockGCPauseDemo {
    private static final String LOCK_KEY = "redlock-vulnerability-demo";
    private static final int LOCK_TTL_SECONDS = 1;
    private static final List<JedisPool> POOLS = new ArrayList<>();

    public static void main(String[] args) throws InterruptedException {
        setupRedisPools();
        
        System.out.println("=== Redlock GC Pause Vulnerability Demo ===");

        // Thread for Client A
        Thread clientA = new Thread(() -> {
            String lockValue = "client-A";
            if (acquireRedlock(lockValue)) {
                System.out.println("[Client A] Acquired lock. Entering simulated GC pause for 2s...");
                try {
                    // Simulating a GC pause longer than TTL
                    Thread.sleep(2000); 
                } catch (InterruptedException e) {}
                
                System.out.println("[Client A] Woke up from GC. Performing critical work...");
                performCriticalWork("Client A");
                
                releaseRedlock(lockValue);
            } else {
                System.out.println("[Client A] Failed to acquire lock.");
            }
        });

        // Thread for Client B (starts after Client A's lock would have expired)
        Thread clientB = new Thread(() -> {
            try { Thread.sleep(1200); } catch (InterruptedException e) {} // Wait for TTL to expire
            
            String lockValue = "client-B";
            System.out.println("[Client B] Attempting to acquire lock...");
            if (acquireRedlock(lockValue)) {
                System.out.println("[Client B] Acquired lock! Performing critical work...");
                performCriticalWork("Client B");
                releaseRedlock(lockValue);
            } else {
                System.out.println("[Client B] Failed to acquire lock.");
            }
        });

        clientA.start();
        clientB.start();
        
        clientA.join();
        clientB.join();

        System.out.println("\nDemo Finished.");
        closePools();
    }

    private static void performCriticalWork(String clientId) {
        System.out.println(">>> [" + clientId + "] IS NOW IN THE CRITICAL SECTION!");
        try { Thread.sleep(500); } catch (InterruptedException e) {}
        System.out.println("<<< [" + clientId + "] leaving critical section.");
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
