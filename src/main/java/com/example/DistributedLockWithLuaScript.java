package com.example;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Arrays;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;


/**
 * Distributed Lock with Lua Script (Atomic Check-and-Delete)
 *
 * This is the PRODUCTION-READY solution:
 * - Check and delete happen in a single atomic Redis operation
 * - Uses Lua scripting to eliminate TOCTOU race condition
 * - Redis guarantees: either both operations succeed or neither does
 * - No window where another consumer can snatch the lock
 * 
 * The Lua script is executed server-side in Redis, ensuring atomicity.
 */
public class DistributedLockWithLuaScript {

    private static final String LOCK_KEY = "my-distributed-lock";
    private static final int LOCK_TTL_SECONDS = 1;
    private static final int NUM_CONSUMERS = 5;
    private static final String REDIS_HOST = System.getenv().getOrDefault("REDIS_HOST", "localhost");
    private static long workDurationMs = 500;
    
    // Lua script for atomic check-and-delete
    // KEYS[1] = lock key
    // ARGV[1] = expected lock value
    // Returns: 1 if deleted (we owned it), 0 if didn't own it
    private static final String LUA_SCRIPT = 
        "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "return redis.call('del', KEYS[1]) " +
        "else " +
            "return 0 " +
        "end";

    public static void main(String[] args) {
        if (args.length > 0) {
            workDurationMs = Long.parseLong(args[0]);
        }
        
        System.out.println("Application starting (with Lua script atomicity)...");
        System.out.println("Work duration: " + workDurationMs + "ms, Lock TTL: " + LOCK_TTL_SECONDS + "s");
        System.out.flush();
        
        JedisPool pool = new JedisPool(REDIS_HOST, 6379);
        ExecutorService executor = Executors.newFixedThreadPool(NUM_CONSUMERS);

        for (int i = 0; i < NUM_CONSUMERS; i++) {
            int consumerId = i;

            executor.submit(() -> {
                String lockValue = "consumer-" + consumerId;
                try (Jedis jedis = pool.getResource()) {
                    while (true) {
                        if (acquireLock(jedis, lockValue)) {
                            System.out.println("Consumer " + consumerId + " acquired the lock.");
                            Thread.sleep(workDurationMs);
                            System.out.println("Consumer " + consumerId + " attempting to release the lock.");
                            releaseLock(jedis, lockValue);
                            break;
                        } else {
                            Thread.sleep(200);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Consumer " + consumerId + " Error: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Main thread interrupted while waiting for consumers.");
        }
        
        pool.close();
        System.out.println("Application finished.");
        System.out.flush();
    }

    private static boolean acquireLock(Jedis jedis, String lockValue) throws InterruptedException {
        SetParams setParams = new SetParams().nx().ex(LOCK_TTL_SECONDS);
        String result = jedis.set(LOCK_KEY, lockValue, setParams);
        return "OK".equals(result);
    }

    /**
     * Release using Lua script for atomic check-and-delete
     * 
     * Lua script guarantees:
     * - Check lock value
     * - Delete lock (only if value matches)
     * - Both happen in single atomic operation (no race condition window)
     * 
     * Returns 1 if we successfully deleted our lock
     * Returns 0 if the lock wasn't ours (someone else owned it)
     */
    private static void releaseLock(Jedis jedis, String lockValue) {
        // Execute Lua script atomically on Redis server
        Object result = jedis.eval(LUA_SCRIPT, 1, LOCK_KEY, lockValue);
        
        if (result != null && result.equals(1L)) {
            System.out.println("Lock released successfully (atomic check-and-delete).");
        } else {
            System.out.println("Lock NOT released - value mismatch (lock belongs to someone else).");
        }
        System.out.flush();
    }
}
