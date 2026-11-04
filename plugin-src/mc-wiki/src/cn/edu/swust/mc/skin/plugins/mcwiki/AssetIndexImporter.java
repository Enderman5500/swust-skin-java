package cn.edu.swust.mc.skin.plugins.mcwiki;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/** 从版本 assetIndex 中只取中文语言文件（客户端 JAR 通常不含 zh_cn）。 */
final class AssetIndexImporter {
    private static final String OBJECT_BASE = "https://resources.download.minecraft.net/";

    private final ObjectMapper mapper;
    private final MojangClient mojang;

    AssetIndexImporter(ObjectMapper mapper, MojangClient mojang) {
        this.mapper = mapper;
        this.mojang = mojang;
    }

    byte[] importZhLang(String indexUrl, JobService.Control control) {
        try {
            JsonNode objects = mapper.readTree(mojang.download(indexUrl)).path("objects");
            List<String> hashes = new ArrayList<>();
            objects.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                if (key.startsWith("minecraft/lang/zh_cn.")) {
                    String hash = entry.getValue().path("hash").asText();
                    if (hash.matches("[a-f0-9]{40}")) hashes.add(hash);
                }
            });
            if (hashes.isEmpty()) {
                control.log("INFO", "assetIndex 中没有中文语言文件");
                return null;
            }
            String hash = hashes.getFirst();
            control.log("INFO", "正在下载中文语言文件 " + hash);
            return mojang.download(OBJECT_BASE + hash.substring(0, 2) + "/" + hash);
        } catch (Exception ex) {
            control.log("WARN", "中文语言文件下载失败: " + ex.getClass().getSimpleName());
            return null;
        }
    }
}
