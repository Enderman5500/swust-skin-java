package cn.edu.swust.mc.report;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class Reporter {
    private final ReportConfig config;
    private final PlaytimeLedger ledger;
    private final Supplier<List<OnlinePlayer>> onlineSupplier;
    private final IntSupplier maxPlayersSupplier;
    private final String version;
    private final String loader;
    private final ReportClient client;
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final AtomicLong sequence = new AtomicLong();
    private ScheduledExecutorService executor;

    public Reporter(ReportConfig config, PlaytimeLedger ledger,
                    Supplier<List<OnlinePlayer>> onlineSupplier,
                    IntSupplier maxPlayersSupplier,
                    String version, String loader) {
        this.config = config;
        this.ledger = ledger;
        this.onlineSupplier = onlineSupplier;
        this.maxPlayersSupplier = maxPlayersSupplier;
        this.version = version == null ? "" : version;
        this.loader = loader == null ? "" : loader;
        this.client = new ReportClient(config);
    }

    public void start() {
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "minecraft-server-report");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleAtFixedRate(this::tick, 5, config.reportIntervalSeconds, TimeUnit.SECONDS);
    }

    public void requestReport() {
        ScheduledExecutorService current = executor;
        if (current != null && !current.isShutdown()) current.execute(this::tick);
    }

    public void stop() {
        ScheduledExecutorService current = executor;
        executor = null;
        if (current != null) current.shutdownNow();
        try {
            ledger.save();
        } catch (IOException e) {
            if (config.debug) System.out.println("[minecraft-server-report] 保存时长失败：" + e.getMessage());
        }
    }

    private void tick() {
        if (!busy.compareAndSet(false, true)) return;
        try {
            long now = System.currentTimeMillis();
            List<OnlinePlayer> online = onlineSupplier.get();
            ledger.sample(online == null ? List.of() : new ArrayList<>(online), now);
            List<PlayerTime> snapshot = ledger.snapshot();
            client.send(snapshot, maxPlayersSupplier.getAsInt(), version, loader, sequence.incrementAndGet());
        } catch (Exception e) {
            if (config.debug) System.out.println("[minecraft-server-report] 采样失败：" + e.getMessage());
        } finally {
            busy.set(false);
        }
    }
}
