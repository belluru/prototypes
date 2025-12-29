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
            // Enable transaction to make FOR UPDATE effective
            conn.setAutoCommit(false);
            try {
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

                    conn.commit();
                    System.out.println(passengerName + " reserved seat: " + seatId);
                } else {
                    conn.rollback();
                    System.out.println("No seats available for " + passengerName);
                }
            } catch (Exception e) {
                conn.rollback();
                throw e;
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
