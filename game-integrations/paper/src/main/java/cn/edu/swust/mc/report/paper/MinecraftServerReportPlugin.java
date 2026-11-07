package cn.edu.swust.mc.report.paper;

import cn.edu.swust.mc.report.OnlinePlayer;
import cn.edu.swust.mc.report.PlaytimeLedger;
import cn.edu.swust.mc.report.ReportConfig;
import cn.edu.swust.mc.report.Reporter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MinecraftServerReportPlugin extends JavaPlugin implements Listener {
    private final Map<String, OnlinePlayer> online = new ConcurrentHashMap<>();
    private PlaytimeLedger ledger;
    private Reporter reporter;

    @Override
    public void onEnable() {
        File dataFolder = getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            getLogger().warning("无法创建插件数据目录");
            return;
        }
        try {
            ReportConfig config = ReportConfig.load(new File(dataFolder, "config.properties").toPath());
            ledger = new PlaytimeLedger(new File(dataFolder, "playtime.properties").toPath(),
                    config.afkTimeoutMillis());
            if (!config.valid()) {
                getLogger().warning("请先在 plugins/MinecraftServerReport/config.properties 填写 base-url、server-id 和 api-key");
            }
            String version = Bukkit.getBukkitVersion().split("-")[0];
            reporter = new Reporter(config, ledger, () -> new ArrayList<>(online.values()),
                    Bukkit::getMaxPlayers, version, "paper");
            getServer().getPluginManager().registerEvents(this, this);
            long now = System.currentTimeMillis();
            for (Player player : Bukkit.getOnlinePlayers()) {
                track(player, now);
                ledger.join(key(player), player.getName(), now);
            }
            reporter.start();
            getLogger().info("Minecraft 服务器上报已启动（Paper API " + version + "）");
        } catch (Exception e) {
            getLogger().severe("启动上报失败：" + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (reporter != null) reporter.stop();
        reporter = null;
        ledger = null;
        online.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (ledger == null || reporter == null) return;
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        track(player, now);
        ledger.join(key(player), player.getName(), now);
        reporter.requestReport();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (ledger == null || reporter == null) return;
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        ledger.leave(key(player), player.getName(), now);
        online.remove(key(player));
        reporter.requestReport();
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (ledger == null) return;
        if (event.getFrom().distanceSquared(event.getTo()) > 0.0001
                || Math.abs(event.getFrom().getYaw() - event.getTo().getYaw()) > 0.5
                || Math.abs(event.getFrom().getPitch() - event.getTo().getPitch()) > 0.5) {
            Player player = event.getPlayer();
            ledger.markActivity(key(player), player.getName(), System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (ledger == null) return;
        Player player = event.getPlayer();
        ledger.markActivity(key(player), player.getName(), System.currentTimeMillis());
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (ledger == null) return;
        Player player = event.getPlayer();
        ledger.markActivity(key(player), player.getName(), System.currentTimeMillis());
    }

    private void track(Player player, long now) {
        String key = key(player);
        online.put(key, new OnlinePlayer(key, player.getName()));
        if (ledger != null) ledger.markActivity(key, player.getName(), now);
    }

    private static String key(Player player) {
        return player.getUniqueId().toString().replace("-", "").toLowerCase();
    }
}
