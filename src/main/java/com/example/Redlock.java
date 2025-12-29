package com.example;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.params.SetParams;

/**
 * Redlock Implementation (Multi-Node Distributed Lock)
 * 
 * This version uses 5 Redis nodes and requires a quorum (3/5) to acquire the lock.
 * This provides High Availability (HA) - the lock still works if up to 2 nodes go down.
 */
public class Redlock {

    private static final String LOCK_KEY = "my-distributed-lock";
    private static final int LOCK_TTL_SECONDS = 1;
    private static int numConsumers = 5;
    private static final int QUORUM = 3;
    private static long workDurationMs = 950;
    
    private static final String LUA_SCRIPT = 
        "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "return redis.call('del', KEYS[1]) " +
        "else " +
            "return 0 " +
        "end";

    private static final List<JedisPool> POOLS = new ArrayList<>();

    public static void main(String[] args) {
        if (args.length > 0) {
            workDurationMs = Long.parseLong(args[0]);
        }

        numConsumers = Integer.parseInt(System.getenv().getOrDefault("NUM_CONSUMERS", "5"));
        long startTime = System.currentTimeMillis();

        String nodesEnv = System.getenv().getOrDefault("REDIS_NODES", "localhost:6379");
        String[] nodes = nodesEnv.split(",");
        
        System.out.println("Application starting (Redlock Multi-Node)...");
        System.out.println("Nodes: " + nodesEnv);
        System.out.println("Consumers: " + numConsumers + ", Work duration: " + workDurationMs + "ms, Lock TTL: " + LOCK_TTL_SECONDS + "s");
        System.out.flush();

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        for (String node : nodes) {
            String[] parts = node.split(":");
            POOLS.add(new JedisPool(poolConfig, parts[0], Integer.parseInt(parts[1]), 2000));
        }

        ExecutorService executor = Executors.newFixedThreadPool(numConsumers);

        for (int i = 0; i < numConsumers; i++) {
            int consumerId = i;
            executor.submit(() -> {
                String lockValue = "consumer-" + consumerId;
                while (true) {
                    if (acquireRedlock(lockValue)) {
                        try {
                            System.out.println("Consumer " + consumerId + " acquired the lock (Quorum reached).");
                            Thread.sleep(workDurationMs);
                            System.out.println("Consumer " + consumerId + " attempting to release the lock.");
                            releaseRedlock(lockValue, true);
                            break;
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    } else {
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        for (JedisPool pool : POOLS) {
            pool.close();
        }
        long endTime = System.currentTimeMillis();
        System.out.println("Application finished.");
        System.out.println("TOTAL_TIME_MS: " + (endTime - startTime));
        System.out.flush();
    }

    private static boolean acquireRedlock(String lockValue) {
        int acquiredCount = 0;
        SetParams setParams = new SetParams().nx().ex(LOCK_TTL_SECONDS);

        for (JedisPool pool : POOLS) {
            try (Jedis jedis = pool.getResource()) {
                String result = jedis.set(LOCK_KEY, lockValue, setParams);
                if ("OK".equals(result)) {
                    acquiredCount++;
                }
            } catch (Exception e) {
                // Node might be down, continue to next node
            }
        }

        if (acquiredCount >= QUORUM) {
            return true;
        } else {
            // Failed to reach quorum, release partial locks
            releaseRedlock(lockValue, false);
            return false;
        }
    }

    private static void releaseRedlock(String lockValue, boolean printValue) {
        for (JedisPool pool : POOLS) {
            try (Jedis jedis = pool.getResource()) {
                if (printValue) {
                    System.out.println("Current lockValue on node: " + jedis.get(LOCK_KEY) + ", Consumer trying to release: " + lockValue);
                }
                Object result = jedis.eval(LUA_SCRIPT, 1, LOCK_KEY, lockValue);
                if (printValue) {
                    System.out.println("Lua Release Result: " + result + " (1=success, 0=failure)");
                }
            } catch (Exception e) {
                // Node might be down, continue to next node
            }
        }
    }
}
