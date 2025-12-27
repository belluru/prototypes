package com.example;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;


/**
 * Simple Distributed Lock Implementation
 *
 * This is a straightforward lock and release use case where:
 * - Locks are always released before TTL expires (500ms work < 1 second TTL)
 * - No value checking needed during release (controlled timing)
 * - Multiple consumers safely take turns holding the lock
 * - Uses Redis SETNX for atomic lock acquisition
 */
public class DistributedLock {

    private static final String LOCK_KEY = "my-distributed-lock";
    private static final int LOCK_TTL_SECONDS = 1;
    private static final int NUM_CONSUMERS = 5;
    private static final String REDIS_HOST = System.getenv().getOrDefault("REDIS_HOST", "localhost");
    private static long workDurationMs = 500;  // Default: no race condition

    public static void main(String[] args) {
        // Accept work duration as command line argument
        if (args.length > 0) {
            workDurationMs = Long.parseLong(args[0]);
        }
        
        System.out.println("Application starting...");
        System.out.println("Work duration: " + workDurationMs + "ms, Lock TTL: " + LOCK_TTL_SECONDS + "s");
        System.out.flush();
        
        // Create a connection pool (thread-safe, reuses connections)
        JedisPool pool = new JedisPool(REDIS_HOST, 6379);
        
        // Create a thread pool with NUM_CONSUMERS threads
        ExecutorService executor = Executors.newFixedThreadPool(NUM_CONSUMERS);

        for (int i = 0; i < NUM_CONSUMERS; i++) {
            int consumerId = i;

            // Submit consumer task to thread pool for concurrent execution
            executor.submit(() -> {
                String lockValue = "consumer-" + consumerId;
                try (Jedis jedis = pool.getResource()) {
                    // Keep retrying until lock is acquired
                    while (true) {
                        if (acquireLock(jedis, lockValue)) {
                            System.out.println("Consumer " + consumerId + " acquired the lock.");
                            // Simulate work with configurable duration
                            Thread.sleep(workDurationMs);
                            System.out.println("Consumer " + consumerId + " attempting to release the lock.");
                            releaseLock(jedis);
                            break;
                        } else {
                            // Retry with backoff
                            Thread.sleep(200);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Consumer " + consumerId + " Error: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }

        // Wait for all submitted tasks to complete
        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Main thread interrupted while waiting for consumers.");
        }
        
        // Close the connection pool
        pool.close();
        System.out.println("Application finished.");
        System.out.flush();
    }

    /**
     * Attempt to acquire distributed lock using Redis SETNX
     * 
     * SetParams.nx() = SET if Not eXists (atomic operation)
     * SetParams.ex(1) = EXpire after 1 second (TTL safety)
     * 
     * Returns true only if lock was successfully acquired (first consumer)
     */
    private static boolean acquireLock(Jedis jedis, String lockValue) throws InterruptedException {
        SetParams setParams = new SetParams().nx().ex(LOCK_TTL_SECONDS);
        String result = jedis.set(LOCK_KEY, lockValue, setParams);
        return "OK".equals(result);
    }

    /**
     * Release the distributed lock by deleting it from Redis
     * 
     * Simple release (no value checking):
     * - When work duration < TTL: safe, no race condition
     * - When work duration > TTL: can cause race condition where lock gets deleted by wrong consumer
     */
    private static void releaseLock(Jedis jedis) {
        jedis.del(LOCK_KEY);
        System.out.println("Lock released successfully.");
        System.out.flush();
    }
}
