/*
 * Main.java
 * Main application entry point for the Personal Expense Tracker System.
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("      PERSONAL EXPENSE TRACKER SYSTEM (OFFLINE)   ");
        System.out.println("==================================================");

        try {
            // 1. Initialize Database
            Database.initialize();

            // 2. Start HTTP Server & REST API
            ApiServer server = new ApiServer();
            server.start();

            System.out.println("\nApplication started successfully!");
            System.out.println("Access frontend at: http://localhost:8080");
            System.out.println("Press Ctrl+C to stop the server.\n");

        } catch (Exception e) {
            System.err.println("[Main] Fatal error starting application: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
