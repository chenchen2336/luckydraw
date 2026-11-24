package cn.blockforge.generated.fabric263wishesfabri.data;

import cn.blockforge.generated.fabric263wishesfabri.config.LotteryConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家数据仓库。文件放在 <世界目录>/lottery/players.json，
 * 跟着存档走，服务器重启、玩家下线都不会丢保底计数。
 */
public final class PlayerStore {

    private static final Logger LOG = LoggerFactory.getLogger("luckydraw");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final Map<UUID, PlayerData> DATA = new HashMap<>();
    private static Path file;

    private PlayerStore() {
    }

    public static void init(MinecraftServer server) {
        DATA.clear();
        try {
            file = server.getWorldPath(LevelResource.ROOT).resolve("lottery").resolve("players.json");
        } catch (Exception e) {
            LOG.error("[抽奖机] 找不到世界目录，玩家数据不会保存：{}", e.toString());
            file = null;
        }
        load();
    }

    private static void load() {
        if (file == null || !Files.exists(file)) return;
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject players = root.has("players") ? root.getAsJsonObject("players") : null;
            if (players == null) return;
            for (Map.Entry<String, JsonElement> entry : players.entrySet()) {
                try {
                    UUID id = UUID.fromString(entry.getKey());
                    PlayerData data = GSON.fromJson(entry.getValue(), PlayerData.class);
                    if (data != null) DATA.put(id, data);
                } catch (Exception ignored) {
                    // 单条坏数据不影响其它玩家
                }
            }
        } catch (Exception e) {
            LOG.error("[抽奖机] 读取玩家抽奖数据失败：{}", e.toString());
        }
    }

    public static PlayerData get(UUID id) {
        PlayerData data = DATA.get(id);
        if (data == null) {
            BigDecimal start = LotteryConfig.get().economy.startingBalance;
            data = new PlayerData(start);
            DATA.put(id, data);
        }
        return data;
    }

    public static void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            JsonObject root = new JsonObject();
            JsonObject players = new JsonObject();
            for (Map.Entry<UUID, PlayerData> entry : DATA.entrySet()) {
                players.add(entry.getKey().toString(), GSON.toJsonTree(entry.getValue()));
            }
            root.add("players", players);
            Path tmp = file.resolveSibling("players.json.tmp");
            Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.error("[抽奖机] 保存玩家抽奖数据失败：{}", e.toString());
        }
    }
}
