package org.fartmans.buttbotdatacollection;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;

public class DatabaseManager {
    private static Connection connection;
    private static Logger logger;
    private static String jdbcUrl="jdbc:mysql://%s:%d/%s?SSL=true&serverTimezone=UTC&rewriteBatchedStatements=true";  //&logger=Slf4JLogger&profileSQL=true

    public static void init(Logger logInstance) {
        logger = logInstance;
        String url = String.format(jdbcUrl,
                DataSecrets.HOST, DataSecrets.getPort(), DataSecrets.DATABASE);

        try {
            connection = DriverManager.getConnection(url, DataSecrets.USER, DataSecrets.PASSWORD);
            logger.info("Buttbotdatacollection: Successfully connected to MySQL database at {}", DataSecrets.HOST);
        } catch (SQLException e) {
            logger.error("Buttbotdatacollection: MySQL connection failed! Check your credentials.");
            e.printStackTrace();
        }
    }

    public static void executeUpdateAsync(String sql, Object... params) {
        CompletableFuture.runAsync(() -> {
            try {
                if (connection == null || connection.isClosed()) {
                    // Re-init if connection dropped
                    String url = String.format(jdbcUrl,
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
                if (logger != null) logger.error("executeUpdateAsync: Database write failed: {}", e.getMessage());
            }
        });
    }

    public static void executeUpdateBlocking(String sql, Object... params) {
            try {
                if (connection == null || connection.isClosed()) {
                    // Re-init if connection dropped
                    String url = String.format(jdbcUrl,
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
                if (logger != null) logger.error("executeUpdateBlocking: Database write failed: {}", e.getMessage());
            }
    }

    public static void insertBulkPlayLocation(List<ButtbotDataCollection.PlayerData> dataList) {
        if (dataList == null || dataList.isEmpty()) return;
        CompletableFuture.runAsync(() -> {
            try {
                if (connection == null || connection.isClosed()) {
                    String url = String.format(jdbcUrl,
                            DataSecrets.HOST, DataSecrets.getPort(), DataSecrets.DATABASE);
                    connection = DriverManager.getConnection(url, DataSecrets.USER, DataSecrets.PASSWORD);
                }

                String sql = "INSERT INTO progress_NSA_module(`datetime`,`player_name`,`dimension`, `x`, `y`, `z`) VALUES (?, ?, ?, ?, ?, ?)";

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
                    String url = String.format(jdbcUrl,
                            DataSecrets.HOST, DataSecrets.getPort(), DataSecrets.DATABASE);
                    connection = DriverManager.getConnection(url, DataSecrets.USER, DataSecrets.PASSWORD);
                }

                String sql = "INSERT INTO mobkills(`player_name`, `target`, `datetime`) VALUES (?, ?, ?)";

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

    public static void insertBulkPickupLog(ConcurrentSkipListMap<ButtbotDataCollection.LogKey, ButtbotDataCollection.ActionDataBlock> dataList) {
        if (dataList == null || dataList.isEmpty()) return;
        CompletableFuture.runAsync(() -> {
            try {
                if (connection == null || connection.isClosed()) {
                    String url = String.format(jdbcUrl,
                            DataSecrets.HOST, DataSecrets.getPort(), DataSecrets.DATABASE);
                    connection = DriverManager.getConnection(url, DataSecrets.USER, DataSecrets.PASSWORD);
                }

                String sql = "INSERT INTO pickuplog(`player_name`, `item`, `datetime`, `quantity`) VALUES (?, ?, ?, ?)";
                logger.info(dataList.toString());
                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    connection.setAutoCommit(false); // Start transaction for speed

                    int count = 0;
                    for (Map.Entry<ButtbotDataCollection.LogKey, ButtbotDataCollection.ActionDataBlock> entry : dataList.entrySet()) {
                        ButtbotDataCollection.LogKey key = entry.getKey();
                        ButtbotDataCollection.ActionDataBlock data = entry.getValue();

                        pstmt.setString(1, data.playerName());
                        pstmt.setString(2, data.target());
                        pstmt.setTimestamp(3, Timestamp.valueOf(key.timestamp()));
                        pstmt.setInt(4,data.quantity());
                        pstmt.addBatch();

                        if (++count % 1000 == 0) {
                            pstmt.executeBatch();
                        }
                    }
                    pstmt.executeBatch(); // Finalize remaining records

                } catch (SQLException e) {
                    if (logger != null) logger.error("BulkPickup write failed, rolled back: {}", e.getMessage());
                    connection.rollback(); // Undo if something went wrong
                } finally {
                    connection.setAutoCommit(true);
                }
            } catch (SQLException e) {
                if (logger != null) logger.error("BulkPickup: Database connection error: {}", e.getMessage());
            }
        });
    }

    public static void insertBulkDropLog(ConcurrentSkipListMap<ButtbotDataCollection.LogKey, ButtbotDataCollection.ActionDataBlock> dataList) {
        if (dataList == null || dataList.isEmpty()) return;
        CompletableFuture.runAsync(() -> {
            try {
                if (connection == null || connection.isClosed()) {
                    String url = String.format(jdbcUrl,
                            DataSecrets.HOST, DataSecrets.getPort(), DataSecrets.DATABASE);
                    connection = DriverManager.getConnection(url, DataSecrets.USER, DataSecrets.PASSWORD);
                }

                String sql = "INSERT INTO droplog(`player_name`, `item`, `datetime`, `quantity`) VALUES (?, ?, ?, ?)";

                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    int count = 0;
                    for (Map.Entry<ButtbotDataCollection.LogKey, ButtbotDataCollection.ActionDataBlock> entry : dataList.entrySet()) {
                        ButtbotDataCollection.LogKey key = entry.getKey();
                        ButtbotDataCollection.ActionDataBlock data = entry.getValue();

                        pstmt.setString(1, data.playerName());
                        pstmt.setString(2, data.target());
                        pstmt.setTimestamp(3, Timestamp.valueOf(key.timestamp()));
                        pstmt.setInt(4,data.quantity());
                        pstmt.addBatch();

                        if (++count % 1000 == 0) {
                            pstmt.executeBatch();
                        }
                    }

                    pstmt.executeBatch(); // Finalize remaining records

                } catch (SQLException e) {
                    if (logger != null) logger.error("BulkDrop write failed, rolled back: {}", e.getMessage());
                    connection.rollback(); // Undo if something went wrong
                }
            } catch (SQLException e) {
                if (logger != null) logger.error("BulkDrop: Database connection error: {}", e.getMessage());
            }
        });
    }

    public static void insertBulkBlockPlaceLog(ConcurrentSkipListMap<ButtbotDataCollection.LogKey, ButtbotDataCollection.ActionData> dataList) {
        if (dataList == null || dataList.isEmpty()) return;
        CompletableFuture.runAsync(() -> {
            try {
                if (connection == null || connection.isClosed()) {
                    String url = String.format(jdbcUrl,
                            DataSecrets.HOST, DataSecrets.getPort(), DataSecrets.DATABASE);
                    connection = DriverManager.getConnection(url, DataSecrets.USER, DataSecrets.PASSWORD);
                }

                String sql = "INSERT INTO blockplacelog(`player_name`, `block`, `datetime`) VALUES (?, ?, ?)";

                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {

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

                } catch (SQLException e) {
                    if (logger != null) logger.error("BulkPlace write failed, rolled back: {}", e.getMessage());
                    connection.rollback(); // Undo if something went wrong
                }
            } catch (SQLException e) {
                if (logger != null) logger.error("BulkPlace: Database connection error: {}", e.getMessage());
            }
        });
    }

    public static void storePlayerSnapshot(ButtbotDataCollection.PlayerSnapshot ps) {
        //composite function to store player snapshot information
        CompletableFuture.runAsync(() -> {
            // run this method as single thread. this ensures the reference table is populated before child tables.
            // we will likely have db insertion failures due to schema consistency rules if not done synchronously

            String uuid = UUID.randomUUID().toString();
            Timestamp ts = Timestamp.valueOf(LocalDateTime.now());

            //reference table insertion
            executeUpdateBlocking("insert into inventory_update(`player_name`,`datetime`,`UUID`) values (?, ?, ?)",
                    ps.name(),
                    ts,
                    uuid
                    );

            //built-in statistics
            executeUpdateBlocking("insert into playerstatistics (UUID, damageblocked, damagedealt, damagetaken, distanceclimbed, distancecrouched, " +
                            "distancefallen, distancesprinted, jumps, playerkills, sneaktime, targetshit, timeslept) " +
                            "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);",
                    uuid,
                    ps.damageBlocked(),
                    ps.damageDealt(),
                    ps.damageTaken(),
                    ps.distanceClimbed(),
                    ps.distanceCrouched(),
                    ps.distanceFallen(),
                    ps.distanceSprinted(),
                    ps.jumps(),
                    ps.playerKills(),
                    ps.sneakTime(),
                    ps.targetsHit(), //the most important one
                    ps.timeSlept()
                    );

            //item (inventory, hotbar) insertion
            try {
                if (connection == null || connection.isClosed()) {
                    String url = String.format(jdbcUrl,
                            DataSecrets.HOST, DataSecrets.getPort(), DataSecrets.DATABASE);
                    connection = DriverManager.getConnection(url, DataSecrets.USER, DataSecrets.PASSWORD);
                }

                String sql = "insert into inventory_items(`UUID`, `type`, `slot`,`item`, `display_name`) values (?, ?, ?, ?, ?)";

                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {

                    int count = 0;
                    for (ButtbotDataCollection.ItemData entry : ps.inventory()) {
                        pstmt.setString(1, uuid);
                        pstmt.setInt(2,entry.type());
                        pstmt.setInt(3, entry.slot());
                        pstmt.setString(4, entry.name());
                        pstmt.setString(5, entry.displayName());
                        pstmt.addBatch();

                    }
                    String statement = pstmt.toString();
                    pstmt.executeBatch(); // Finalize remaining records

                } catch (SQLException e) {
                    if (logger != null) logger.error("InventoryItems write failed, rolled back: {}", e.getMessage());
                    logger.error(e.getSQLState());
                    logger.error(e.getStackTrace().toString());
                    connection.rollback(); // Undo if something went wrong
                }
            } catch (SQLException e) {
                if (logger != null) logger.error("InventoryItems: Database connection error: {}", e.getMessage());
            }

            //item curio insertion
            try {
                if (connection == null || connection.isClosed()) {
                    String url = String.format(jdbcUrl,
                            DataSecrets.HOST, DataSecrets.getPort(), DataSecrets.DATABASE);
                    connection = DriverManager.getConnection(url, DataSecrets.USER, DataSecrets.PASSWORD);
                }
                String sql = "insert into curios(`UUID`,`slot`,`item`, `display_name`) values (?, ?, ?, ?)";

                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                int count = 0;
                for (ButtbotDataCollection.CuriosItemData entry : ps.curios()) {
                    pstmt.clearParameters();
                    pstmt.setString(1, uuid);
                    pstmt.setString(2, entry.slot());
                    pstmt.setString(3, entry.name());
                    pstmt.setString(4, entry.displayName());
                    pstmt.addBatch();
                }
                pstmt.executeBatch(); // Finalize remaining records

            } catch (SQLException e) {
                if (logger != null) logger.error("InventoryItems write failed, rolled back: {}", e.getMessage());
                connection.rollback(); // Undo if something went wrong
            }
            } catch (SQLException e) {
                if (logger != null) logger.error("Curios write failed, rolled back: {} message: {}", e.getSQLState(), e.getMessage());
            }
        });
    }
}
