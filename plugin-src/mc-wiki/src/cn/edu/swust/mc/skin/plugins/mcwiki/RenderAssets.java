package cn.edu.swust.mc.skin.plugins.mcwiki;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 共享渲染资产：从 Owen1212055/mc-assets 拉取 256px 背包渲染图，所有版本共用。
 * 渲染图写入插件目录 assets/renders 下，由宿主 /plugin-assets/<id>/... 静态托管。
 */
final class RenderAssets {
    static final String JOB_VERSION = "__renders__";
    private static final String SOURCE_URL = "https://codeload.github.com/Owen1212055/mc-assets/zip/refs/heads/main";
    private static final Pattern GAME_VERSION = Pattern.compile("Last updated:\\s*Minecraft\\s*<!--MC TOKEN-->(.*?)<!--MC TOKEN-->");

    /** 旧版本物品 id → 现代渲染名，未覆盖的旧 id 自然回退到缺图。 */
    private static final Map<String, String> LEGACY_ALIASES = Map.ofEntries(
            Map.entry("planks", "OAK_PLANKS"), Map.entry("log", "OAK_LOG"), Map.entry("log2", "ACACIA_LOG"),
            Map.entry("leaves", "OAK_LEAVES"), Map.entry("leaves2", "ACACIA_LEAVES"), Map.entry("sapling", "OAK_SAPLING"),
            Map.entry("grass", "GRASS_BLOCK"), Map.entry("tallgrass", "SHORT_GRASS"), Map.entry("double_plant", "SUNFLOWER"),
            Map.entry("red_flower", "POPPY"), Map.entry("yellow_flower", "DANDELION"),
            Map.entry("wool", "WHITE_WOOL"), Map.entry("carpet", "WHITE_CARPET"), Map.entry("banner", "WHITE_BANNER"), Map.entry("bed", "RED_BED"),
            Map.entry("concrete", "WHITE_CONCRETE"), Map.entry("concrete_powder", "WHITE_CONCRETE_POWDER"),
            Map.entry("stained_glass", "WHITE_STAINED_GLASS"), Map.entry("stained_glass_pane", "WHITE_STAINED_GLASS_PANE"),
            Map.entry("stained_hardened_clay", "WHITE_TERRACOTTA"), Map.entry("hardened_clay", "TERRACOTTA"),
            Map.entry("silver_shulker_box", "LIGHT_GRAY_SHULKER_BOX"), Map.entry("silver_glazed_terracotta", "LIGHT_GRAY_GLAZED_TERRACOTTA"),
            Map.entry("stonebrick", "STONE_BRICKS"), Map.entry("stone_slab", "STONE_SLAB"), Map.entry("wooden_slab", "OAK_SLAB"),
            Map.entry("double_stone_slab", "SMOOTH_STONE_SLAB"), Map.entry("double_wooden_slab", "OAK_SLAB"),
            Map.entry("fence", "OAK_FENCE"), Map.entry("fence_gate", "OAK_FENCE_GATE"), Map.entry("trapdoor", "OAK_TRAPDOOR"),
            Map.entry("wooden_door", "OAK_DOOR"), Map.entry("sign", "OAK_SIGN"), Map.entry("boat", "OAK_BOAT"),
            Map.entry("wooden_pressure_plate", "OAK_PRESSURE_PLATE"), Map.entry("wooden_button", "OAK_BUTTON"),
            Map.entry("workbench", "CRAFTING_TABLE"), Map.entry("enchantment_table", "ENCHANTING_TABLE"),
            Map.entry("snow_layer", "SNOW"), Map.entry("snow", "SNOW_BLOCK"), Map.entry("web", "COBWEB"), Map.entry("waterlily", "LILY_PAD"),
            Map.entry("noteblock", "NOTE_BLOCK"), Map.entry("quartz_ore", "NETHER_QUARTZ_ORE"), Map.entry("nether_brick", "NETHER_BRICKS"),
            Map.entry("netherbrick", "NETHER_BRICK"), Map.entry("end_bricks", "END_STONE_BRICKS"), Map.entry("slime", "SLIME_BLOCK"),
            Map.entry("magma", "MAGMA_BLOCK"), Map.entry("monster_egg", "INFESTED_STONE"), Map.entry("skull", "SKELETON_SKULL"),
            Map.entry("golden_rail", "POWERED_RAIL"), Map.entry("reeds", "SUGAR_CANE"), Map.entry("dye", "INK_SAC"),
            Map.entry("fish", "COD"), Map.entry("cooked_fish", "COOKED_COD"), Map.entry("clownfish", "TROPICAL_FISH"),
            Map.entry("speckled_melon", "GLISTERING_MELON_SLICE"), Map.entry("fireworks", "FIREWORK_ROCKET"), Map.entry("firework_charge", "FIREWORK_STAR"),
            Map.entry("record_13", "MUSIC_DISC_13"), Map.entry("record_cat", "MUSIC_DISC_CAT"), Map.entry("record_blocks", "MUSIC_DISC_BLOCKS"),
            Map.entry("record_chirp", "MUSIC_DISC_CHIRP"), Map.entry("record_far", "MUSIC_DISC_FAR"), Map.entry("record_mall", "MUSIC_DISC_MALL"),
            Map.entry("record_mellohi", "MUSIC_DISC_MELLOHI"), Map.entry("record_stal", "MUSIC_DISC_STAL"), Map.entry("record_strad", "MUSIC_DISC_STRAD"),
            Map.entry("record_ward", "MUSIC_DISC_WARD"), Map.entry("record_11", "MUSIC_DISC_11"), Map.entry("record_wait", "MUSIC_DISC_WAIT"),
            Map.entry("redstone_wire", "REDSTONE"), Map.entry("tripwire", "STRING"),
            Map.entry("chain", "IRON_CHAIN"));

    private final WikiStore store;
    private final MojangClient mojang;
    private volatile Index index;

    private record Index(Set<String> items, Set<String> flat, Set<String> iso) {
    }

    RenderAssets(WikiStore store, MojangClient mojang) {
        this.store = store;
        this.mojang = mojang;
    }

    boolean available() {
        return store.rendersDir() != null;
    }

    Map<String, Object> meta() {
        Map<String, Object> raw = store.rendersMeta();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updated", !raw.isEmpty() && raw.get("updatedAt") != null);
        result.put("commit", raw.getOrDefault("commit", ""));
        result.put("gameVersion", raw.getOrDefault("gameVersion", ""));
        result.put("items", number(raw.get("items")));
        result.put("entitiesFlat", number(raw.get("flat")));
        result.put("entitiesIsometric", number(raw.get("iso")));
        result.put("updatedAt", number(raw.get("updatedAt")));
        return result;
    }

    String renderName(String namespacedId) {
        String path = namespacedId == null ? "" : namespacedId.substring(namespacedId.indexOf(':') + 1);
        String alias = LEGACY_ALIASES.get(path);
        return alias != null ? alias : path.toUpperCase(Locale.ROOT);
    }

    /** 返回插件目录内的相对路径（assets/renders/...），前端拼 /plugin-assets/<id>/ 使用；缺图返回 null。 */
    String iconPath(String namespacedId, String kind) {
        if (!available()) return null;
        String name = renderName(namespacedId);
        Index current = index();
        if ("entity".equals(kind)) {
            if (current.iso().contains(name)) return "assets/renders/entity/isometric/" + name + ".png";
            if (current.flat().contains(name)) return "assets/renders/entity/flat/" + name + ".png";
            return null;
        }
        if (current.items().contains(name)) return "assets/renders/item/" + name + ".png";
        // 1.12.2 的物品 id 词序与现代注册名不同（ore_diamond → DIAMOND_ORE、block_diamond → DIAMOND_BLOCK），
        // 直接查找失败时按词根排列组合再试一次。
        String legacy = legacyCandidate(name, current.items());
        if (legacy != null) return "assets/renders/item/" + legacy + ".png";
        if (name.startsWith("WALL_") && current.items().contains(name.substring(5))) {
            return "assets/renders/item/" + name.substring(5) + ".png";
        }
        if (name.startsWith("POTTED_") && current.items().contains(name.substring(7))) {
            return "assets/renders/item/" + name.substring(7) + ".png";
        }
        return null;
    }

    private String legacyCandidate(String name, Set<String> names) {
        String[] parts = name.toLowerCase(Locale.ROOT).split("_");
        if (parts.length < 2 || parts.length > 3) return null;
        java.util.List<String[]> permutations = new java.util.ArrayList<>();
        permute(parts, 0, permutations);
        for (String[] parts2 : permutations) {
            String candidate = String.join("_", parts2).toUpperCase(Locale.ROOT);
            if (!candidate.equals(name) && names.contains(candidate)) return candidate;
        }
        return null;
    }

    private static void permute(String[] values, int index, List<String[]> out) {
        if (index == values.length) {
            out.add(values.clone());
            return;
        }
        for (int i = index; i < values.length; i++) {
            String temp = values[index];
            values[index] = values[i];
            values[i] = temp;
            permute(values, index + 1, out);
            temp = values[index];
            values[index] = values[i];
            values[i] = temp;
        }
    }

    private Index index() {
        Index current = index;
        if (current == null) {
            synchronized (this) {
                current = index;
                if (current == null) {
                    Map<String, Object> raw = store.rendersMeta();
                    current = new Index(Set.copyOf(strings(raw.get("itemNames"))),
                            Set.copyOf(strings(raw.get("flatNames"))), Set.copyOf(strings(raw.get("isoNames"))));
                    index = current;
                }
            }
        }
        return current;
    }

    void update(JobService.Control control) {
        if (!available()) throw new IllegalStateException("无法定位插件目录，不能写入渲染资产");
        control.progress("DOWNLOAD", 0, 4, "正在下载 mc-assets 渲染资产包");
        byte[] zip = mojang.download(SOURCE_URL);
        control.log("INFO", "渲染资产包 " + (zip.length / 1024 / 1024) + " MB");
        control.checkpoint();
        control.progress("WRITE", 1, 4, "正在写入渲染图");

        String commit = "";
        String gameVersion = "";
        int items = 0;
        int flat = 0;
        int iso = 0;
        int written = 0;
        Set<String> itemNames = new LinkedHashSet<>();
        Set<String> flatNames = new LinkedHashSet<>();
        Set<String> isoNames = new LinkedHashSet<>();
        try (ZipInputStream in = new ZipInputStream(new java.io.ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                int slash = name.indexOf('/');
                if (slash < 0) continue;
                String path = name.substring(slash + 1);
                if (commit.isEmpty()) commit = name.substring(0, slash).replace("Owen1212055-mc-assets-", "").replace("mc-assets-", "");
                if ("README.md".equals(path)) {
                    Matcher matcher = GAME_VERSION.matcher(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                    if (matcher.find()) gameVersion = matcher.group(1).trim();
                    continue;
                }
                String relative = null;
                if (path.startsWith("item-assets/") && path.endsWith(".png")) {
                    relative = "renders/item/" + path.substring("item-assets/".length());
                } else if (path.startsWith("entity-assets/flat/") && path.endsWith(".png")) {
                    relative = "renders/entity/flat/" + path.substring("entity-assets/flat/".length());
                } else if (path.startsWith("entity-assets/isometric/") && path.endsWith(".png")) {
                    relative = "renders/entity/isometric/" + path.substring("entity-assets/isometric/".length());
                }
                if (relative == null) continue;
                byte[] bytes = in.readAllBytes();
                store.writeAsset(relative, bytes);
                String base = path.substring(path.lastIndexOf('/') + 1, path.length() - 4);
                if (relative.startsWith("renders/item/")) {
                    itemNames.add(base);
                    items++;
                } else if (relative.contains("/flat/")) {
                    flatNames.add(base);
                    flat++;
                } else {
                    isoNames.add(base);
                    iso++;
                }
                if (++written % 200 == 0) {
                    control.checkpoint();
                    control.progress("WRITE", 1, 4, "已写入 " + written + " 个渲染图");
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("mc-assets 资产包解包失败", ex);
        }

        control.checkpoint();
        control.progress("CLEANUP", 2, 4, "正在清理已下架的渲染图");
        Map<String, Object> previous = store.rendersMeta();
        int removed = 0;
        for (String name : strings(previous.get("itemNames"))) {
            if (!itemNames.contains(name)) {
                store.deleteAsset("renders/item/" + name + ".png");
                removed++;
            }
        }
        for (String name : strings(previous.get("flatNames"))) {
            if (!flatNames.contains(name)) {
                store.deleteAsset("renders/entity/flat/" + name + ".png");
                removed++;
            }
        }
        for (String name : strings(previous.get("isoNames"))) {
            if (!isoNames.contains(name)) {
                store.deleteAsset("renders/entity/isometric/" + name + ".png");
                removed++;
            }
        }
        if (removed > 0) control.log("INFO", "清理已下架渲染图 " + removed + " 个");

        control.progress("META", 3, 4, "正在更新渲染资产信息");
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("commit", commit);
        meta.put("gameVersion", gameVersion);
        meta.put("items", items);
        meta.put("flat", flat);
        meta.put("iso", iso);
        meta.put("updatedAt", System.currentTimeMillis());
        meta.put("itemNames", List.copyOf(itemNames));
        meta.put("flatNames", List.copyOf(flatNames));
        meta.put("isoNames", List.copyOf(isoNames));
        store.writeRendersMeta(meta);
        index = new Index(Set.copyOf(itemNames), Set.copyOf(flatNames), Set.copyOf(isoNames));
        control.log("INFO", "渲染资产更新完成：物品 " + items + "、生物平面 " + flat + "、生物等轴 " + iso
                + (gameVersion.isBlank() ? "" : "（Minecraft " + gameVersion + "）"));
    }

    private List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object item : list) result.add(String.valueOf(item));
        return result;
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
