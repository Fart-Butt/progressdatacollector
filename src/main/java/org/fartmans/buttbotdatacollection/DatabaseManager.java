package org.fartmans.buttbotdatacollection;

import java.sql.*;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;

public class DatabaseManager {
    private static Connection connection;
    private static Logger logger;

    public static void init(Logger logInstance) {
        logger = logInstance;
        String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=true&serverTimezone=UTC",
                DataSecrets.HOST, DataSecrets.getPort(), DataSecrets.DATABASE);

        try {
            connection = DriverManager.getConnection(url, DataSecrets.USER, DataSecrets.PASSWORD);
            logger.info("Buttbotdatacollection: Successfully connected to MySQL database at {}", DataSecrets.HOST);
        } catch (SQLException e) {
            logger.error("Buttbotdatacollection: MySQL connection failed! Check your credentials.");
            e.printStackTrace();
        }
    }

    public static void executeUpdate(String sql, Object... params) {
        CompletableFuture.runAsync(() -> {
            try {
                if (connection == null || connection.isClosed()) {
                    // Re-init if connection dropped
                    String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=true&serverTimezone=UTC",
                            DataSecrets.HOST, DataSecrets.getPort(), DataSecrets.DATABASE);
                    connection = DriverManager.getConnection(url, DataSecrets.USER, DataSecrets.PASSWORD);
                }

                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    for (int i = 0; i < params.length; i++) {
                        pstmt.setObject(i + 1, params[i]);
                    }
                    pstmt.executeUpdate();
                }
            } catch (SQLException e) {
                if (logger != null) logger.error("Buttbotdatacollection: Database write failed: {}", e.getMessage());
            }
        });
    }

}