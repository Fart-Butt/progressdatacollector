package org.fartmans.buttbotdatacollection;

import java.sql.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;

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

    public static void insertBulkPlayLocation(List<ButtbotDataCollection.PlayerData> dataList) {
        if (dataList == null || dataList.isEmpty()) return;
        CompletableFuture.runAsync(() -> {
            try {
                if (connection == null || connection.isClosed()) {
                    String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=true&serverTimezone=UTC&rewriteBatchedStatements=true",
                            DataSecrets.HOST, DataSecrets.getPort(), DataSecrets.DATABASE);
                    connection = DriverManager.getConnection(url, DataSecrets.USER, DataSecrets.PASSWORD);
                }

                String sql = "INSERT INTO progress_NSA_module VALUES (?, ?, ?, ?, ?, ?)";

                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    //connection.setAutoCommit(false); // Start transaction for speed
                    connection.setAutoCommit(false);
                    try {
                        int count = 0;
                        for (ButtbotDataCollection.PlayerData data : dataList) {
                            pstmt.setTimestamp(1, java.sql.Timestamp.valueOf(data.dt()));
                            pstmt.setString(2, data.name());
                            pstmt.setString(3, data.world());
                            pstmt.setDouble(4, data.x());
                            pstmt.setDouble(5, data.y());
                            pstmt.setDouble(6, data.z());
                            pstmt.addBatch();

                            if (++count % 1000 == 0) {
                                pstmt.executeBatch();
                            }
                        }
                    } catch (Exception e) { logger.error("error: {}", e.getMessage()); }
                    pstmt.executeBatch(); // Finalize remaining records
                    connection.commit();  // Commit the transaction

                } catch (SQLException e) {
                    connection.rollback(); // Undo if something went wrong
                    if (logger != null) logger.error("BulkPlay write failed, rolled back: {}", e.getMessage());
                } finally {
                    connection.setAutoCommit(true);
                }
            } catch (SQLException e) {
                if (logger != null) logger.error("BulkPlay: Database connection error: {}", e.getMessage());
            }
        });
    }

    public static void insertBulkMonsterKills(ConcurrentSkipListMap<ButtbotDataCollection.LogKey, ButtbotDataCollection.ActionData> dataList) {
        if (dataList == null || dataList.isEmpty()) return;
        CompletableFuture.runAsync(() -> {
            try {
                if (connection == null || connection.isClosed()) {
                    String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=true&serverTimezone=UTC&rewriteBatchedStatements=true",
                            DataSecrets.HOST, DataSecrets.getPort(), DataSecrets.DATABASE);
                    connection = DriverManager.getConnection(url, DataSecrets.USER, DataSecrets.PASSWORD);
                }

                String sql = "INSERT INTO mobkills VALUES (?, ?, ?)";

                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    connection.setAutoCommit(false); // Start transaction for speed

                    int count = 0;
                    for (Map.Entry<ButtbotDataCollection.LogKey, ButtbotDataCollection.ActionData> entry : dataList.entrySet()) {
                        ButtbotDataCollection.LogKey key = entry.getKey();
                        ButtbotDataCollection.ActionData data = entry.getValue();

                        pstmt.setString(1, data.playerName());
                        pstmt.setString(2, data.target());
                        pstmt.setTimestamp(3, Timestamp.valueOf(key.timestamp()));
                        pstmt.addBatch();

                        if (++count % 1000 == 0) {
                            pstmt.executeBatch();
                        }
                    }

                    pstmt.executeBatch(); // Finalize remaining records
                    connection.commit();  // Commit the transaction

                } catch (SQLException e) {
                    if (logger != null) logger.error("BulkMonster write failed, rolled back: {}", e.getMessage());
                    connection.rollback(); // Undo if something went wrong
                } finally {
                    connection.setAutoCommit(true);
                }
            } catch (SQLException e) {
                if (logger != null) logger.error("BulkMonster: Database connection error: {}", e.getMessage());
            }
        });
    }
}
