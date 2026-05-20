package org.fartmans.buttbotdatacollection;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
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
import net.minecraft.stats.Stats;
import net.minecraft.world.item.ItemStack;

import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentSkipListMap;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.*;

import static org.fartmans.buttbotdatacollection.DatabaseManager.storePlayerSnapshot;


@Mod("buttbotdatacollection")
public class ButtbotDataCollection {
    private final Comparator<LogKey> logComparator = Comparator
            .comparing(LogKey::timestamp)
            .thenComparingLong(LogKey::nanoId);

    private static final Logger logger = LogUtils.getLogger();
    private static int ticks = 0;
    public static final List<PlayerData> buffer = new ArrayList<>();
    private static final Map<UUID, LocalDateTime> loginTimes = new HashMap<>();
    private final ConcurrentSkipListMap<LogKey, ActionData> mobKillLog = new ConcurrentSkipListMap<>(logComparator);
    private final ConcurrentSkipListMap<LogKey, ActionDataBlock> pickupLog = new ConcurrentSkipListMap<>(logComparator);
    private final ConcurrentSkipListMap<LogKey, ActionDataBlock> dropLog = new ConcurrentSkipListMap<>(logComparator);
    private final ConcurrentSkipListMap<LogKey, ActionData> blockPlaceLog = new ConcurrentSkipListMap<>(logComparator);
    public static final List<CuriosItemData> curios = new ArrayList<>();
    public static final List<ItemData> hotbarAndArmor = new ArrayList<>(); //1= hotbar, 2= inventory

    public ButtbotDataCollection(IEventBus modEventBus) {
        Runtime.getRuntime().addShutdownHook(new Thread(this::commitPlayedStatistics, "buttbot-data-collection-shutdown-hook"));
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        if (DataSecrets.isConfigured()) {
            logger.info("Environment variables found. Attempting database connection...");
            DatabaseManager.init(logger); // Pass logger to handle DB-specific logs
        } else {
            logger.error("Missing ENV variables: MC_DB_HOST: {}, MC_DB_NAME: {}, MC_DB_USER: {}",
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
            commitPlayedStatistics();

        }
    }

    public void commitPlayedStatistics() {
        final List<PlayerData> dataSnapshot = List.copyOf(buffer);
        DatabaseManager.insertBulkPlayLocation(dataSnapshot);
        buffer.clear();

        //insert kills data
        final ConcurrentSkipListMap<LogKey, ActionData> kl = cloneMonsterKillSnapshot();
        final ConcurrentSkipListMap<LogKey, ActionDataBlock> pl = clonePickupLogSnapshot();
        final ConcurrentSkipListMap<LogKey, ActionDataBlock> dl = cloneDropLogSnapshot();
        final ConcurrentSkipListMap<LogKey, ActionData> bpl = cloneBlockPlaceLogSnapshot();
        DatabaseManager.insertBulkMonsterKills(kl);
        DatabaseManager.insertBulkPickupLog(pl);
        DatabaseManager.insertBulkDropLog(dl);
        DatabaseManager.insertBulkBlockPlaceLog(bpl);
    }

    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DatabaseManager.executeUpdateAsync("INSERT INTO progress_deaths(`datetime`, `player_name`, `message`, `world`, `x`, `y`, `z`) VALUES (?, ?, ?, ?, ?, ?, ?)",
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
            DatabaseManager.executeUpdateAsync("INSERT INTO progres_cheevos(`datetime`, `player_name`, `cheevo_text` VALUES (?, ?, ?)",
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
        DatabaseManager.executeUpdateAsync("INSERT IGNORE INTO minecraft_players VALUES (?, ?, ?)",
                username,
                uuid.toString(),
                Timestamp.valueOf(LocalDateTime.now())
        );

        // Store the login time in memory
        loginTimes.put(uuid, LocalDateTime.now());

        logger.info("Playtime session started for {}", username);
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        //flush all information to disk.
        commitPlayedStatistics();
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        //capture snapshot
        if (event.getEntity() instanceof ServerPlayer sp) {
            PlayerSnapshot ps = collectPlayerData(sp);
            storePlayerSnapshot(ps);
        }

        UUID uuid = event.getEntity().getUUID();
        String username = event.getEntity().getName().getString();
        LocalDateTime loginTime = loginTimes.get(uuid);
        Duration playedTime = Duration.between(loginTime, LocalDateTime.now());
        if (playedTime.toSeconds() > 0) {
            DatabaseManager.executeUpdateAsync("INSERT progress_playertracker_v2 (`player_name`, `datetime`,`timedelta`) values (?, ?, ?)",
                    username,
                    Timestamp.valueOf(LocalDateTime.now()),
                    playedTime.toSeconds()
            );
            loginTimes.remove(uuid);
        }

    }

    private static PlayerSnapshot collectPlayerData(ServerPlayer player) {
        //gather hotbar items
        List<ItemData> hotbar = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                //1= hotbar, 2= inventory
                hotbarAndArmor.add(new ItemData(1,
                        i,
                        BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),  //internal item name
                        stack.getHoverName().getString()) //display name for item
                );
            } else {
                hotbarAndArmor.add(new ItemData(1,
                        i,
                        "Empty",  //internal item name
                        "Empty") //display name for item
                );
            }
        }

        //gather inventory slots
        List<ItemData> armor = new ArrayList<>();
        for (int i = 0; i < player.getInventory().armor.size(); i++) {
            ItemStack stack = player.getInventory().armor.get(i);
            if (!stack.isEmpty()) {
                String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                String name = stack.getHoverName().getString();
                //1= hotbar, 2= inventory
                hotbarAndArmor.add(new ItemData(2,
                        i,
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                        stack.getHoverName().getString())
                );
            } else {
                hotbarAndArmor.add(new ItemData(2,
                        i,
                        "Empty",
                        "Empty"
                ));
            }
        }

        Optional<ICuriosItemHandler> curiosInventoryOpt = CuriosApi.getCuriosInventory(player);

        if (curiosInventoryOpt.isPresent()) {
            ICuriosItemHandler inventoryHandler = curiosInventoryOpt.get();
            //iterate all slots
            inventoryHandler.getCurios().forEach((slotId, stacksHandler) -> {

                for (int i = 0; i < stacksHandler.getSlots(); i++) {
                    ItemStack stack = stacksHandler.getStacks().getStackInSlot(i);

                    if (!stack.isEmpty()) {
                        curios.add(new CuriosItemData(
                                slotId,
                                stack.toString().split(" ")[1],
                                stack.getHoverName().getString()
                        ));
                    } else {
                        //store empty slot information
                        curios.add(new CuriosItemData(
                                slotId,
                                "Empty",
                                "Empty"
                        ));
                    }

                }
            });
        }

        var stats = player.getStats();

        return new PlayerSnapshot(
                player.getGameProfile().getName(),
                player.experienceLevel,
                player.getMaxHealth(),
                player.getArmorValue(),
                hotbarAndArmor,
                curios,
                stats.getValue(Stats.CUSTOM.get(Stats.DAMAGE_BLOCKED_BY_SHIELD)),
                stats.getValue(Stats.CUSTOM.get(Stats.DAMAGE_DEALT)),
                stats.getValue(Stats.CUSTOM.get(Stats.DAMAGE_TAKEN)),
                stats.getValue(Stats.CUSTOM.get(Stats.CLIMB_ONE_CM)),
                stats.getValue(Stats.CUSTOM.get(Stats.CROUCH_ONE_CM)),
                stats.getValue(Stats.CUSTOM.get(Stats.FALL_ONE_CM)),
                stats.getValue(Stats.CUSTOM.get(Stats.SPRINT_ONE_CM)),
                stats.getValue(Stats.CUSTOM.get(Stats.WALK_ONE_CM)),
                stats.getValue(Stats.CUSTOM.get(Stats.JUMP)),
                stats.getValue(Stats.CUSTOM.get(Stats.PLAYER_KILLS)),
                stats.getValue(Stats.CUSTOM.get(Stats.TARGET_HIT)),
                stats.getValue(Stats.CUSTOM.get(Stats.SLEEP_IN_BED))
        );
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
        ItemStack originalStack = event.getOriginalStack();
        int count = originalStack.getCount();
        if (!originalStack.isEmpty() && count > 0) {
            pickupLog.put(
                    new LogKey(LocalDateTime.now(), System.nanoTime()),
                    new ActionDataBlock(
                            event.getPlayer().getName().getString(),
                            originalStack.getHoverName().getString(),
                            count
                    )
            );
        }
    }

    @SubscribeEvent
    public void onItemDrop(ItemTossEvent event) {
        ItemStack itemStack = event.getEntity().getItem();
        if (!itemStack.isEmpty() && itemStack.getCount() > 0) {
            dropLog.put(
                    new LogKey(LocalDateTime.now(), System.nanoTime()),
                    new ActionDataBlock(event.getPlayer().getName().getString(),
                            itemStack.getHoverName().getString(),
                            itemStack.getCount()
                    )
            );
        }
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
    public record ItemData(int type, int slot, String name, String displayName) {}  //1= hotbar, 2= inventory
    public record CuriosItemData(String slot, String name, String displayName) {}
    public record PlayerSnapshot(
            //general stats
            String name,
            int level,
            float hp,
            double defense,

            //inventory (hotbar, armor) and curios
            List<ItemData> inventory,
            List<CuriosItemData> curios,

            //statistics data
            int damageBlocked,
            int damageDealt,
            int damageTaken,
            int distanceClimbed,
            int distanceCrouched,
            int distanceFallen,
            int distanceSprinted,
            int jumps,
            int playerKills,
            int sneakTime,
            int targetsHit,
            int timeSlept
    ) {}
}