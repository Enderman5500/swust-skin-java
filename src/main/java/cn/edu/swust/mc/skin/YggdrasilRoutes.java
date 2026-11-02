package cn.edu.swust.mc.skin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class YggdrasilRoutes {
    private YggdrasilRoutes() {
    }

    public static void register(App app) {
        Web.Router router = app.router;

        router.get("/api/yggdrasil", ctx -> {
            if (!app.yggdrasil.enabled()) {
                throw new Yggdrasil.YggdrasilError(404, "NotFoundException", "Not found.");
            }
            raw(ctx, app.yggdrasil::metadata);
        });

        router.post("/api/yggdrasil/authserver/authenticate", ctx -> {
            ensureEnabled(app);
            String ip = Web.clientIp(ctx.exchange, app.config.trustProxy);
            try {
                app.rateLimiter.hit("ygg-auth:" + ip, 30, 10 * 60 * 1000);
            } catch (Web.HttpError e) {
                throw new Yggdrasil.YggdrasilError(429, "TooManyRequestsException",
                        "Too many authentication attempts. Try again later.");
            }
            Map<String, Object> body = body(ctx);
            raw(ctx, () -> app.yggdrasil.authenticate(body));
        });

        router.post("/api/yggdrasil/authserver/refresh", ctx -> {
            ensureEnabled(app);
            Map<String, Object> body = body(ctx);
            raw(ctx, () -> app.yggdrasil.refresh(body));
        });

        router.post("/api/yggdrasil/authserver/validate", ctx -> {
            ensureEnabled(app);
            Map<String, Object> body = body(ctx);
            raw(ctx, () -> {
                app.yggdrasil.validate(Authz.str(body.get("accessToken")));
                return null;
            });
        });

        router.post("/api/yggdrasil/authserver/invalidate", ctx -> {
            ensureEnabled(app);
            Map<String, Object> body = body(ctx);
            raw(ctx, () -> {
                app.yggdrasil.invalidate(body.get("accessToken") == null ? null : Authz.str(body.get("accessToken")));
                return null;
            });
        });

        router.post("/api/yggdrasil/authserver/signout", ctx -> {
            ensureEnabled(app);
            Map<String, Object> body = body(ctx);
            raw(ctx, () -> {
                app.yggdrasil.signout(Authz.str(body.get("username")), Authz.str(body.get("password")));
                return null;
            });
        });

        router.post("/api/yggdrasil/sessionserver/session/minecraft/join", ctx -> {
            ensureEnabled(app);
            Map<String, Object> body = body(ctx);
            raw(ctx, () -> {
                app.yggdrasil.join(body);
                return null;
            });
        });

        router.get("/api/yggdrasil/sessionserver/session/minecraft/hasJoined", ctx -> {
            ensureEnabled(app);
            raw(ctx, () -> app.yggdrasil.hasJoined(ctx.query("username"), ctx.query("serverId")));
        });

        router.get("/api/yggdrasil/sessionserver/session/minecraft/profile/:uuid", ctx -> {
            ensureEnabled(app);
            raw(ctx, () -> app.yggdrasil.profileFor(ctx.param("uuid")));
        });

        router.post("/api/yggdrasil/api/profiles/minecraft", ctx -> {
            ensureEnabled(app);
            Object value = bodyAny(ctx);
            List<String> names;
            if (value instanceof List<?> list) {
                names = list.stream().map(Authz::str).toList();
            } else if (value instanceof Map<?, ?> map) {
                names = Users.stringList(map.get("names"));
            } else {
                names = List.of();
            }
            raw(ctx, () -> app.yggdrasil.profilesByName(names));
        });

        router.get("/api/yggdrasil/api/profiles/minecraft/:name", ctx -> {
            ensureEnabled(app);
            raw(ctx, () -> app.yggdrasil.profileByName(ctx.param("name")));
        });
    }

    private static void ensureEnabled(App app) {
        if (!app.yggdrasil.enabled()) {
            throw new Yggdrasil.YggdrasilError(403, "ForbiddenOperationException",
                    "Yggdrasil API is disabled on this server.");
        }
    }

    private static void raw(Web.Ctx ctx, Supplier<Object> action) throws Exception {
        try {
            Object result = action.get();
            if (result == null) {
                ctx.noContent();
                return;
            }
            ctx.sendJson(200, result);
        } catch (Yggdrasil.YggdrasilError e) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("error", e.code);
            payload.put("errorMessage", e.getMessage());
            ctx.sendJson(e.status, payload);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> body(Web.Ctx ctx) throws Exception {
        Object value = bodyAny(ctx);
        if (!(value instanceof Map<?, ?>)) {
            throw new Yggdrasil.YggdrasilError(400, "IllegalArgumentException", "Malformed JSON body.");
        }
        return (Map<String, Object>) value;
    }

    private static Object bodyAny(Web.Ctx ctx) throws Exception {
        try {
            return ctx.jsonAny(1024 * 1024);
        } catch (Web.HttpError e) {
            throw new Yggdrasil.YggdrasilError(400, "IllegalArgumentException", "Malformed JSON body.");
        }
    }
}
