package org.fartmans.buttbotdatacollection;

import java.sql.*;
import java.util.List;
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

    public static void bulkPlayLocation(List<ButtbotDataCollection.PlayerData> pd) {
        logger.info("bulkPlayLocation: Starting bulkplaylocation insert");
        logger.info(pd.toString());
        String sql = "INSERT INTO progress_NSA_module VALUES (?, ?, ?, ?, ?, ?)";
        CompletableFuture.runAsync(() -> {
            try {
                if (connection == null || connection.isClosed()) {
                    // Re-init if connection dropped
                    String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=true&serverTimezone=UTC",
                            DataSecrets.HOST, DataSecrets.getPort(), DataSecrets.DATABASE);
                    connection = DriverManager.getConnection(url, DataSecrets.USER, DataSecrets.PASSWORD);
                }
                logger.info("pre PS");
                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    logger.info("post PS");
                    for (ButtbotDataCollection.PlayerData d : pd) {
                        logger.info("for loop running");
                        pstmt.setString(1, String.valueOf(d.dt()));
                        pstmt.setString(2, d.name);
                        pstmt.setString(3, d.world());
                        pstmt.setInt(4, (int) d.x());
                        pstmt.setInt(5, (int) d.y());
                        pstmt.setInt(6, (int) d.z());
                        pstmt.addBatch();
                    }
                    logger.info("committing");
                    // commit
                    pstmt.executeBatch();
                } catch (Exception e) {
                    logger.info(e.getMessage());
                }
            } catch (SQLException e) {
                if (logger != null) logger.error("Buttbotdatacollection: Database write failed: {}", e.getMessage());
            }
        });
    }
}
