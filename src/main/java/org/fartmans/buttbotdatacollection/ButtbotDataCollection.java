package org.fartmans.buttbotdatacollection;

import com.mojang.logging.LogUtils;
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
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod("buttbotdatacollection")
public class ButtbotDataCollection {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static int ticks = 0;
    private static final List<PlayerData> buffer = new ArrayList<>();

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
            for (PlayerData d : buffer) {
                DatabaseManager.executeUpdate("INSERT INTO progress_NSA_module VALUES (?, ?, ?, ?, ?, ?)",
                        Timestamp.valueOf(d.dt), d.name, d.world, d.x, d.y, d.z);
            }
            buffer.clear();
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

    private record PlayerData(LocalDateTime dt, String name, double x, double y, double z, String world) {}
}