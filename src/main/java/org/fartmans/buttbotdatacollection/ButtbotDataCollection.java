package org.fartmans.buttbotdatacollection;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.slf4j.Logger;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import javax.xml.crypto.Data;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentSkipListMap;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.*;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod("buttbotdatacollection")
public class ButtbotDataCollection {

    private final Comparator<LogKey> logComparator = Comparator
            .comparing(LogKey::timestamp)
            .thenComparingLong(LogKey::nanoId);

    private static final Logger LOGGER = LogUtils.getLogger();
    private static int ticks = 0;
    public static final List<PlayerData> buffer = new ArrayList<>();
    private static final Map<UUID, LocalDateTime> loginTimes = new HashMap<>();
    private final ConcurrentSkipListMap<LogKey, ActionData> mobKillLog = new ConcurrentSkipListMap<>(logComparator);
    private final ConcurrentSkipListMap<LogKey, ActionDataBlock> pickupLog = new ConcurrentSkipListMap<>(logComparator);
    private final ConcurrentSkipListMap<LogKey, ActionDataBlock> dropLog = new ConcurrentSkipListMap<>(logComparator);
    private final ConcurrentSkipListMap<LogKey, ActionData> blockPlaceLog = new ConcurrentSkipListMap<>(logComparator);

    public ButtbotDataCollection(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        if (DataSecrets.isConfigured()) {
            LOGGER.info("Environment variables found. Attempting database connection...");
            DatabaseManager.init(LOGGER); // Pass logger to handle DB-specific logs
        } else {
            LOGGER.error("Missing ENV variables: MC_DB_HOST: {}, MC_DB_NAME: {}, MC_DB_USER: {}",
                    DataSecrets.HOST == null ? "MISSING" : "OK",
                    DataSecrets.DATABASE == null ? "MISSING" : "OK",
                    DataSecrets.USER == null ? "MISSING" : "OK");
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        ticks++;

        // Collect every 5 seconds (100 ticks)
        if (ticks % 100 == 0) {
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                buffer.add(new PlayerData(
                        LocalDateTime.now(),
                        player.getScoreboardName(),
                        player.getX(), player.getY(), player.getZ(),
                        player.level().dimension().location().toString()
                ));
            }
        }

        // Commit every 60 seconds (1200 ticks)
        if (ticks >= 1200) {
            ticks = 0;
            //insert play location data
            LOGGER.info("performing bulk insert of play location");
            final List<PlayerData> dataSnapshot = List.copyOf(buffer);
            DatabaseManager.insertBulkPlayLocation(dataSnapshot);
            buffer.clear();

            //insert kills data
            LOGGER.info("performing monster kills bulk insert");
            final ConcurrentSkipListMap<LogKey, ActionData> kl = cloneMonsterKillSnapshot();
            final ConcurrentSkipListMap<LogKey, ActionDataBlock> pl = clonePickupLogSnapshot();
            final ConcurrentSkipListMap<LogKey, ActionDataBlock> dl = cloneDropLogSnapshot();
            final ConcurrentSkipListMap<LogKey, ActionData> bpl = cloneBlockPlaceLogSnapshot();
            DatabaseManager.insertBulkMonsterKills(kl);
            DatabaseManager.insertBulkPickupLog(pl);
            DatabaseManager.insertBulkDropLog(dl);
            DatabaseManager.insertBulkBlockPlaceLog(bpl);

        }
    }

    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DatabaseManager.executeUpdate("INSERT INTO progress_deaths VALUES (?, ?, ?, ?, ?, ?, ?)",
                    Timestamp.valueOf(LocalDateTime.now()),
                    player.getScoreboardName(),
                    event.getSource().getLocalizedDeathMessage(player).getString(),
                    player.level().dimension().location().toString(),
                    player.getX(), player.getY(), player.getZ()
            );
        }
    }

    @SubscribeEvent
    public void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DatabaseManager.executeUpdate("INSERT INTO progres_cheevos VALUES (?, ?, ?)",
                    Timestamp.valueOf(LocalDateTime.now()),
                    player.getScoreboardName(),
                    event.getAdvancement().id().toString()
            );
        }
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        String username = event.getEntity().getName().getString();
        UUID uuid = event.getEntity().getUUID();
        //insert uuid and player name into the users table
        //use insert ignore so we don't overwrite previous user discovery.
        DatabaseManager.executeUpdate("INSERT IGNORE INTO minecraft_players VALUES (?, ?, ?)",
                username,
                uuid.toString(),
                Timestamp.valueOf(LocalDateTime.now())
        );

        // Store the login time in memory
        loginTimes.put(uuid, LocalDateTime.now());

        LOGGER.info("Playtime session started for {}", username);
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        String username = event.getEntity().getName().getString();
        LocalDateTime loginTime = loginTimes.get(uuid);
        Duration playedTime = Duration.between(loginTime, LocalDateTime.now());
        if (playedTime.toSeconds() > 0) {
            DatabaseManager.executeUpdate("INSERT progress_playertracker_v2 (`player`, `datetime`,`timedelta`) values (?, ?, ?)",
                    username,
                    Timestamp.valueOf(LocalDateTime.now()),
                    playedTime.toSeconds()
            );
            loginTimes.remove(uuid);
        }
    }

    @SubscribeEvent
    public void onMobKill(LivingDeathEvent event) {
        if (event.getEntity() instanceof Monster monster && event.getSource().getEntity() instanceof Player player) {
            mobKillLog.put(
                    new LogKey(LocalDateTime.now(), System.nanoTime()),
                    new ActionData(player.getName().getString(), monster.getName().getString())
            );
        }
        if (event.getEntity() instanceof Animal animal && event.getSource().getEntity() instanceof Player player) {
            mobKillLog.put(
                    new LogKey(LocalDateTime.now(), System.nanoTime()),
                    new ActionData(player.getName().getString(), animal.getName().getString())
            );
        }
    }

    @SubscribeEvent
    public void onItemPickup(ItemEntityPickupEvent.Post event) {
        if (event.getItemEntity().getItem().getCount() > 0) {
            pickupLog.put(
                    new LogKey(LocalDateTime.now(), System.nanoTime()),
                    new ActionDataBlock(event.getPlayer().getName().getString(),
                            event.getItemEntity().getItem().getHoverName().getString(),
                            event.getItemEntity().getItem().getCount())
            );
        }
    }

    @SubscribeEvent
    public void onItemDrop(ItemTossEvent event) {
        if (event.getEntity().getItem().getCount() > 0) {
            dropLog.put(
                    new LogKey(LocalDateTime.now(), System.nanoTime()),
                    new ActionDataBlock(event.getPlayer().getName().getString(),
                            event.getEntity().getItem().getHoverName().getString(),
                            event.getEntity().getItem().getCount())
            );
        }
        String itemInfo = event.getEntity().getItem().getCount() + "x " +
                event.getEntity().getItem().getHoverName().getString();
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        // Ensure the entity placing the block is a player
        if (!(event.getEntity() instanceof Player player)) return;

        // Get the name of the block being placed
        String blockName = event.getPlacedBlock().getBlock().getName().getString();

        blockPlaceLog.put(
                new LogKey(LocalDateTime.now(), System.nanoTime()),
                new ActionData(player.getName().getString(), blockName)
        );
    }

    public synchronized ConcurrentSkipListMap<LogKey, ActionData> cloneMonsterKillSnapshot() {
        //clone list so we can save to database
        ConcurrentSkipListMap<LogKey, ActionData> snapshot = new ConcurrentSkipListMap<>(this.mobKillLog);
        this.mobKillLog.clear();
        return snapshot;
    }

    public synchronized ConcurrentSkipListMap<LogKey, ActionDataBlock> clonePickupLogSnapshot() {
        //clone list so we can save to database
        ConcurrentSkipListMap<LogKey, ActionDataBlock> snapshot = new ConcurrentSkipListMap<>(this.pickupLog);
        this.pickupLog.clear();
        return snapshot;
    }

    public synchronized ConcurrentSkipListMap<LogKey, ActionDataBlock> cloneDropLogSnapshot() {
        //clone list so we can save to database
        ConcurrentSkipListMap<LogKey, ActionDataBlock> snapshot = new ConcurrentSkipListMap<>(this.dropLog);
        this.dropLog.clear();
        return snapshot;
    }

    public synchronized ConcurrentSkipListMap<LogKey, ActionData> cloneBlockPlaceLogSnapshot() {
        //clone list so we can save to database
        ConcurrentSkipListMap<LogKey, ActionData> snapshot = new ConcurrentSkipListMap<>(this.blockPlaceLog);
        this.blockPlaceLog.clear();
        return snapshot;
    }

    public record PlayerData(LocalDateTime dt, String name, double x, double y, double z, String world) {}
    public record ActionData(String playerName, String target) {}
    public record ActionDataBlock(String playerName, String target, Integer quantity) {}
    public record LogKey(LocalDateTime timestamp, long nanoId) {}
}