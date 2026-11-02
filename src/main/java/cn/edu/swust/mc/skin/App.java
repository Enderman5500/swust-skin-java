package cn.edu.swust.mc.skin;

public final class App {
    public final Config config;
    public final Database db;
    public final Web.Router router = new Web.Router();
    public final Web.RateLimiter rateLimiter = new Web.RateLimiter();
    public final long startedAt = System.currentTimeMillis();
    public Yggdrasil yggdrasil;
    public Plugins plugins;

    public App(Config config, Database db) {
        this.config = config;
        this.db = db;
    }
}
