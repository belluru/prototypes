package com.example.paxos;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simplified Paxos Prototype
 * Consolidated into one file for readability.
 */
public class PaxosDemo {

    public static void main(String[] args) {
        System.out.println("=== Simplified Paxos Lock Demo ===");

        // 1. Setup 5 Paxos Nodes
        List<Node> cluster = new ArrayList<>();
        for (int i = 0; i < 5; i++) cluster.add(new Node());
        PaxosProtocol protocol = new PaxosProtocol(cluster);

        // 2. Client A acquires lock
        System.out.println("\n[Client A] Acquiring lock...");
        long tokenA = protocol.tryAcquire("Client-A");
        System.out.println("[Client A] SUCCESS! Token: " + tokenA);

        // 3. Client B takes over while A is "paused"
        System.out.println("\n[Client B] Acquiring lock (Takeover)...");
        long tokenB = protocol.tryAcquire("Client-B");
        System.out.println("[Client B] SUCCESS! Token: " + tokenB);

        // 4. Storage Validation
        Storage storage = new Storage();
        System.out.println("\n[Client B] Writing with Token " + tokenB);
        storage.write("Data B", tokenB);

        System.out.println("[Client A] Waking up, trying to write with Token " + tokenA);
        try {
            storage.write("Data A (Stale)", tokenA);
        } catch (Exception e) {
            System.err.println("[Client A] REJECTED: " + e.getMessage());
        }
    }

    // --- Core Logic ---

    static class Node {
        long promisedId = -1;
        String val = null;
        long token = 0;

        synchronized PrepareMsg prepare(long id) {
            if (id > promisedId) {
                promisedId = id;
                return new PrepareMsg(true, token);
            }
            return new PrepareMsg(false, -1);
        }

        synchronized boolean accept(long id, String client, long t) {
            if (id >= promisedId) {
                promisedId = id;
                val = client;
                token = t;
                return true;
            }
            return false;
        }
    }

    static class PaxosProtocol {
        List<Node> nodes;
        AtomicLong counter = new AtomicLong(0);

        PaxosProtocol(List<Node> nodes) { this.nodes = nodes; }

        long tryAcquire(String client) {
            long proposalId = counter.incrementAndGet();
            long maxToken = 0;
            int votes = 0;

            // Phase 1: Prepare
            for (Node n : nodes) {
                PrepareMsg res = n.prepare(proposalId);
                if (res.ok) {
                    votes++;
                    maxToken = Math.max(maxToken, res.token);
                }
            }

            if (votes < 3) return -1; // No quorum

            // Phase 2: Accept
            long newToken = maxToken + 1;
            votes = 0;
            for (Node n : nodes) {
                if (n.accept(proposalId, client, newToken)) votes++;
            }

            return (votes >= 3) ? newToken : -1;
        }
    }

    static class PrepareMsg {
        boolean ok;
        long token;
        PrepareMsg(boolean o, long t) { ok = o; token = t; }
    }

    static class Storage {
        long lastToken = -1;
        void write(String data, long token) {
            if (token <= lastToken) throw new RuntimeException("Stale Token: " + token);
            lastToken = token;
            System.out.println("[Storage] Saved: " + data);
        }
    }
}
