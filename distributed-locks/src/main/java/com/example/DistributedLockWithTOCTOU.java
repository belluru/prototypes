package com.example;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;


/**
 * Distributed Lock with TOCTOU (Time-of-Check-Time-of-Use) Race Condition
 *
 * This version demonstrates a race condition EVEN WITH value checking:
 * - Checks lock value before deleting (like safe version)
 * - BUT adds deliberate sleep between check and delete
 * - This creates a window where another consumer can acquire the lock
 * - Result: Still deletes someone else's lock (TOCTOU vulnerability)
 * 
 * The sleep mimics real-world scheduling delays or network latency
 */
public class DistributedLockWithTOCTOU {

    private static final String LOCK_KEY = "my-distributed-lock";
    private static final int LOCK_TTL_SECONDS = 1;
    private static final int NUM_CONSUMERS = 5;
    private static final String REDIS_HOST = System.getenv().getOrDefault("REDIS_HOST", "localhost");
    private static long workDurationMs = 500;

    public static void main(String[] args) {
        if (args.length > 0) {
            workDurationMs = Long.parseLong(args[0]);
        }
        
        System.out.println("Application starting (with TOCTOU vulnerability)...");
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
     * Release with TOCTOU vulnerability:
     * 
     * Check-and-delete are NOT atomic. Between them:
     * 1. We check the value (it's ours)
     * 2. Another consumer acquires the lock (TTL expired or sleep delays us)
     * 3. We delete the lock (now it's the other consumer's lock!)
     * 
     * The deliberate 100ms sleep demonstrates this vulnerability clearly.
     */
    private static void releaseLock(Jedis jedis, String lockValue) {
        String currentValue = jedis.get(LOCK_KEY);
        
        if (lockValue.equals(currentValue)) {
            System.out.println("Value check passed, preparing to delete (TOCTOU window opens here...)");
            
            // Deliberate sleep to create race condition window
            // In real world: GC pause, context switch, network delay, etc.
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            jedis.del(LOCK_KEY);
            System.out.println("Lock deleted by " + lockValue + " (may have been someone else's!)");
        } else {
            System.out.println("Lock NOT released - value mismatch (expected: " + lockValue + ", found: " + currentValue + ")");
        }
        System.out.flush();
    }
}
