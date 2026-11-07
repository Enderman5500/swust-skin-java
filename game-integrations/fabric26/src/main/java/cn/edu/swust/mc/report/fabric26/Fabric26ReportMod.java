package cn.edu.swust.mc.report.fabric26;

import cn.edu.swust.mc.report.OnlinePlayer;
import cn.edu.swust.mc.report.PlaytimeLedger;
import cn.edu.swust.mc.report.ReportConfig;
import cn.edu.swust.mc.report.Reporter;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Fabric26ReportMod implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("minecraft-server-report");
    private static final Map<String, OnlinePlayer> ONLINE = new ConcurrentHashMap<>();
    private static final Map<UUID, Look> LOOKS = new ConcurrentHashMap<>();

    private PlaytimeLedger ledger;
    private Reporter reporter;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(this::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> stop());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            String key = key(player);
            ONLINE.put(key, new OnlinePlayer(key, player.getName().getString()));
            if (ledger != null) {
                long now = System.currentTimeMillis();
                ledger.join(key, player.getName().getString(), now);
                if (reporter != null) reporter.requestReport();
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.player;
            String key = key(player);
            if (ledger != null) {
                ledger.leave(key, player.getName().getString(), System.currentTimeMillis());
                if (reporter != null) reporter.requestReport();
            }
            ONLINE.remove(key);
            LOOKS.remove(player.getUUID());
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (ledger == null) return;
            long now = System.currentTimeMillis();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                Look previous = LOOKS.get(player.getUUID());
                Look current = new Look(player.position(), player.getYRot(), player.getXRot());
                if (previous == null || previous.changed(current)) {
                    ledger.markActivity(key(player), player.getName().getString(), now);
                }
                LOOKS.put(player.getUUID(), current);
            }
        });
    }

    private void start(MinecraftServer server) {
        try {
            Path dir = FabricLoader.getInstance().getConfigDir().resolve("minecraft-server-report");
            ReportConfig config = ReportConfig.load(dir.resolve("config.properties"));
            ledger = new PlaytimeLedger(dir.resolve("playtime.properties"), config.afkTimeoutMillis());
            String version = FabricLoader.getInstance().getModContainer("minecraft")
                    .map(container -> container.getMetadata().getVersion().getFriendlyString())
                    .orElse("unknown");
            reporter = new Reporter(config, ledger, () -> ONLINE.values().stream().toList(),
                    () -> server.getPlayerList().getMaxPlayers(), version, "fabric");
            reporter.start();
            if (!config.valid()) {
                LOGGER.warn("请先填写 config/minecraft-server-report/config.properties 中的 server-id 和 api-key");
            }
            LOGGER.info("Minecraft 服务器上报已启动（Fabric 26.x / {}）", version);
        } catch (Exception e) {
            LOGGER.error("启动上报失败", e);
        }
    }

    private void stop() {
        if (reporter != null) reporter.stop();
        reporter = null;
        ledger = null;
        ONLINE.clear();
        LOOKS.clear();
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
