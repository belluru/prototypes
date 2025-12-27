package com.example;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DistributedLock {

    private static final String LOCK_KEY = "my-distributed-lock";
    private static final int LOCK_TTL_SECONDS = 1; // Short TTL to demonstrate race condition
    private static final int NUM_CONSUMERS = 5; // Reduced for clarity
    private static final String REDIS_HOST = System.getenv().getOrDefault("REDIS_HOST", "localhost");

    public static void main(String[] args) {
        System.out.println("REDIS_HOST: " + REDIS_HOST);
        System.out.println("Application starting...");
        ExecutorService executor = Executors.newFixedThreadPool(NUM_CONSUMERS);
        CountDownLatch latch = new CountDownLatch(NUM_CONSUMERS);

        for (int i = 0; i < NUM_CONSUMERS; i++) {
            int consumerId = i;

            executor.submit(() -> {
                Jedis jedis = null;
                String lockValue = "consumer-" + consumerId + "-thread-" + Thread.currentThread().getId();
                try {
                    jedis = new Jedis(REDIS_HOST, 6379);
                    if (acquireLock(jedis, lockValue)) {
                        try {
                            System.out.println("Consumer " + consumerId + " acquired the lock.");
                            // Simulate work that takes longer than the lock TTL
                            Thread.sleep(2000);
                        } finally {
                            System.out.println("Consumer " + consumerId + " attempting to release the lock.");
                            releaseLock(jedis, lockValue);
                        }
                    } else {
                        System.out.println("Consumer " + consumerId + " failed to acquire the lock.");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("Consumer " + consumerId + " interrupted.");
                } finally {
                    if (jedis != null) {
                        jedis.close();
                    }
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Main thread interrupted while waiting for consumers.");
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        System.out.println("Application finished.");
    }

    private static boolean acquireLock(Jedis jedis, String lockValue) throws InterruptedException {
        SetParams setParams = new SetParams().nx().ex(LOCK_TTL_SECONDS);
        String result = jedis.set(LOCK_KEY, lockValue, setParams);
        return "OK".equals(result);
    }

    private static void releaseLock(Jedis jedis, String lockValue) {
        // We can still get the value to show the race condition
        String currentValue = jedis.get(LOCK_KEY);
        System.out.println("Releasing lock: lockValue=" + lockValue + ", currentValue=" + currentValue);
        if (lockValue.equals(currentValue)) {
            jedis.del(LOCK_KEY);
            System.out.println("Consumer with lock value " + lockValue + " released the lock.");
        } else {
            System.out.println("Consumer with lock value " + lockValue + " did NOT release the lock because the value has changed to " + currentValue);
        }
    }
}
