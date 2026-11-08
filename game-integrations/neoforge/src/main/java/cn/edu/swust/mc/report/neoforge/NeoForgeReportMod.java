package cn.edu.swust.mc.report.neoforge;

import cn.edu.swust.mc.report.OnlinePlayer;
import cn.edu.swust.mc.report.PlaytimeLedger;
import cn.edu.swust.mc.report.ReportConfig;
import cn.edu.swust.mc.report.Reporter;
import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Mod("minecraft_server_report")
public final class NeoForgeReportMod {
    private static final Logger LOGGER = LoggerFactory.getLogger("minecraft-server-report");
    private static final Map<String, OnlinePlayer> ONLINE = new ConcurrentHashMap<>();
    private static final Map<UUID, Look> LOOKS = new ConcurrentHashMap<>();

    private PlaytimeLedger ledger;
    private Reporter reporter;
    private MinecraftServer server;
    private ScheduledExecutorService ticker;

    public NeoForgeReportMod() {
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        server = event.getServer();
        try {
            Path dir = FMLPaths.CONFIGDIR.get().resolve("minecraft-server-report");
            ReportConfig config = ReportConfig.load(dir.resolve("config.properties"));
            ledger = new PlaytimeLedger(dir.resolve("playtime.properties"), config.afkTimeoutMillis());
            String version = currentMinecraftVersion();
            reporter = new Reporter(config, ledger, () -> ONLINE.values().stream().toList(),
                    server::getMaxPlayers, version, "neoforge");
            reporter.start();
            LOGGER.info("Minecraft 服务器上报已启动（NeoForge {}）", version);
            ticker = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "minecraft-server-report-activity");
                thread.setDaemon(true);
                return thread;
            });
            ticker.scheduleAtFixedRate(() -> {
                MinecraftServer current = server;
                if (current == null) return;
                try {
                    current.execute(() -> pollActivity(current));
                } catch (RuntimeException ignored) {
                    // 服务器正在关闭时不再提交活动检测。
                }
            }, 1, 1, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOGGER.error("启动上报失败", e);
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (ticker != null) {
            ticker.shutdownNow();
            ticker = null;
        }
        if (reporter != null) reporter.stop();
        reporter = null;
        ledger = null;
        server = null;
        ONLINE.clear();
        LOOKS.clear();
    }

    @SubscribeEvent
    public void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || ledger == null) return;
        String key = key(player);
        ONLINE.put(key, new OnlinePlayer(key, player.getGameProfile().getName()));
        ledger.join(key, player.getGameProfile().getName(), System.currentTimeMillis());
        if (reporter != null) reporter.requestReport();
    }

    @SubscribeEvent
    public void onQuit(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || ledger == null) return;
        String key = key(player);
        ledger.leave(key, player.getGameProfile().getName(), System.currentTimeMillis());
        ONLINE.remove(key);
        LOOKS.remove(player.getUUID());
        if (reporter != null) reporter.requestReport();
    }

    private void pollActivity(MinecraftServer current) {
        if (ledger == null || server == null) return;
        long now = System.currentTimeMillis();
        for (ServerPlayer player : current.getPlayerList().getPlayers()) {
            Look previous = LOOKS.get(player.getUUID());
            Look look = new Look(player.position(), player.getYRot(), player.getXRot());
            if (previous == null || previous.changed(look)) {
                ledger.markActivity(key(player), player.getGameProfile().getName(), now);
            }
            LOOKS.put(player.getUUID(), look);
        }
    }

    private static String currentMinecraftVersion() {
        Object version = SharedConstants.getCurrentVersion();
        for (String method : new String[]{"name", "getName"}) {
            try {
                Object value = version.getClass().getMethod(method).invoke(version);
                if (value instanceof String text && !text.isBlank()) return text;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return "unknown";
    }

    private static String key(ServerPlayer player) {
        return player.getUUID().toString().replace("-", "").toLowerCase();
    }

    private record Look(Vec3 position, float yaw, float pitch) {
        boolean changed(Look other) {
            return position.distanceToSqr(other.position) > 0.0001
                    || Math.abs(yaw - other.yaw) > 0.5
                    || Math.abs(pitch - other.pitch) > 0.5;
        }
    }
}
