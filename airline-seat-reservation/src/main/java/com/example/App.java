package com.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class App {
    private static final String DB_URL = System.getenv("DB_URL") != null ? 
            System.getenv("DB_URL") : "jdbc:mysql://localhost:3306/airline?allowPublicKeyRetrieval=true&useSSL=false";
    private static final String DB_USER = System.getenv("DB_USER") != null ? System.getenv("DB_USER") : "root";
    private static final String DB_PASSWORD = System.getenv("DB_PASSWORD") != null ? System.getenv("DB_PASSWORD") : "rootpassword";

    private static final String SELECT_QUERY = System.getenv("SELECT_QUERY") != null ? 
            System.getenv("SELECT_QUERY") : "SELECT seat_id FROM seats WHERE passenger_id IS NULL LIMIT 1";

    public static void main(String[] args) {
        System.out.println("Starting Seat Reservation Simulation...");
        System.out.println("Using Query: " + SELECT_QUERY);

        waitForDatabase();
        
        long startTime = System.currentTimeMillis();

        ExecutorService executor = Executors.newFixedThreadPool(10);
        for (int i = 1; i <= 100; i++) {
            final int passengerId = i;
            final String passengerName = "Passenger " + i;
            executor.submit(() -> reserveSeat(passengerId, passengerName));
        }

        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        long endTime = System.currentTimeMillis();
        System.out.println("Simulation complete.");
        System.out.println("Total time taken: " + (endTime - startTime) + "ms");
        System.out.println("Printing final seat assignments...");
        printSummary();
    }

    private static void reserveSeat(int passengerId, String passengerName) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            // 1. Find an available seat using the configured query
            int seatId = -1;
            try (PreparedStatement pstmt = conn.prepareStatement(SELECT_QUERY);
                 ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    seatId = rs.getInt("seat_id");
                }
            }

            if (seatId != -1) {
                // Simulate race condition window
                Thread.sleep(100);

                // 2. Assign the seat to the passenger
                String assignSeatSql = "UPDATE seats SET passenger_id = ? WHERE seat_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(assignSeatSql)) {
                    pstmt.setInt(1, passengerId);
                    pstmt.setInt(2, seatId);
                    pstmt.executeUpdate();
                }

                // 3. Update the passenger table
                String updatePassengerSql = "UPDATE passengers SET seat_number = ? WHERE id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(updatePassengerSql)) {
                    pstmt.setInt(1, seatId);
                    pstmt.setInt(2, passengerId);
                    pstmt.executeUpdate();
                }

                System.out.println(passengerName + " reserved seat: " + seatId);
            } else {
                System.out.println("No seats available for " + passengerName);
            }
        } catch (SQLException | InterruptedException e) {
            System.err.println("Error for " + passengerName + ": " + e.getMessage());
        }
    }

    private static void printSummary() {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            System.out.println("\n--- Final Seat Assignments ---");
            String sql = "SELECT s.seat_id, p.id as passenger_id, p.name FROM seats s LEFT JOIN passengers p ON s.passenger_id = p.id ORDER BY s.seat_id";
            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int seatId = rs.getInt("seat_id");
                    int passengerId = rs.getInt("passenger_id");
                    String passengerName = rs.getString("name");
                    if (passengerName != null) {
                        System.out.printf("Seat %d: [%d] %s%n", seatId, passengerId, passengerName);
                    } else {
                        System.out.printf("Seat %d: EMPTY%n", seatId);
                    }
                }
            }

            System.out.println("\n--- Reconciliation ---");
            String countSql = "SELECT seat_id, COUNT(*) as bookings FROM seats GROUP BY seat_id HAVING bookings > 1";
            // Note: In this schema, seat_id is PRIMARY KEY, so we can't have duplicate seat_id in 'seats' table.
            // However, the race condition will show multiple passengers THINKING they booked the same seat (logs)
            // or the same passenger ID in multiple seats (if logic allowed it).
            // Actually, the request "multiple passengers can be assigned same seat" implies 
            // multiple rows in a mapping table, but here we have a 1:1 link in `seats` table (passenger_id column).
            // In the current logic:
            // 1. SELECT seat_id FROM seats WHERE passenger_id IS NULL LIMIT 1 (returns seat S for both T1 and T2)
            // 2. T1: UPDATE seats SET passenger_id = P1 WHERE seat_id = S
            // 3. T2: UPDATE seats SET passenger_id = P2 WHERE seat_id = S (OVERWRITES T1)
            // So the database will show S occupied by P2, but BOTH P1 and P2 will think they booked S.
            
            String reportSql = "SELECT p.id, p.name, p.seat_number FROM passengers p WHERE p.seat_number IS NOT NULL";
            try (PreparedStatement pstmt = conn.prepareStatement(reportSql);
                 ResultSet rs = pstmt.executeQuery()) {
                System.out.println("Passenger Intentions (From Passengers Table):");
                while (rs.next()) {
                    int pId = rs.getInt("id");
                    String name = rs.getString("name");
                    int sNo = rs.getInt("seat_number");
                    System.out.printf("Passenger %d (%s) thinks they have Seat %d%n", pId, name, sNo);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void waitForDatabase() {
        int maxRetries = 30;
        long delayMs = 2000;
        System.out.println("Waiting for database connection...");
        // Ideally we would have a DB pool of connections.
        for (int i = 0; i < maxRetries; i++) {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                System.out.println("Database connection established.");
                return;
            } catch (SQLException e) {
                System.out.println("Database not ready yet... (Attempt " + (i + 1) + "/" + maxRetries + ")");
                try { Thread.sleep(delayMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
        System.err.println("Could not connect to database after " + maxRetries + " attempts. Exiting.");
        System.exit(1);
    }
}
