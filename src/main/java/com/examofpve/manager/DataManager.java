package com.examofpve.manager;

import com.examofpve.ExamOfPVEPlugin;
import com.examofpve.config.ChallengeConfig;
import com.examofpve.session.PendingEffects;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 持久化数据（data.yml）：
 * <ul>
 *   <li>排行榜（每场考核最快通过的前 10 名）</li>
 *   <li>玩家统计（参加次数 / 成功次数 / 最佳时间）</li>
 *   <li>玩家的挑战选择</li>
 * </ul>
 */
public class DataManager {

    private final ExamOfPVEPlugin plugin;
    private File file;
    private YamlConfiguration data;

    public DataManager(ExamOfPVEPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        file = new File(plugin.getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(file);
        if (!file.exists()) {
            save();
        }
    }

    public void save() {
        try {
            data.save(file);
        } catch (Exception e) {
            plugin.getLogger().warning("保存 data.yml 失败：" + e.getMessage());
        }
    }

    // ---------------- 挑战选择 ----------------

    public Set<String> getSelectedChallenges(UUID playerId) {
        Set<String> result = new HashSet<>();
        for (String id : data.getStringList("challenges." + playerId)) {
            if (id != null && !id.isBlank()) {
                result.add(ChallengeConfig.canonicalId(id));
            }
        }
        return result;
    }

    public void setChallenge(UUID playerId, String id, boolean enabled) {
        String canonical = ChallengeConfig.canonicalId(id);
        if (canonical == null) {
            return;
        }
        String path = "challenges." + playerId;
        List<String> list = new ArrayList<>(data.getStringList(path));
        // 同时清理旧 ID，避免 no-sword-axe 与 no-weapon 并存
        list.removeIf(existing -> canonical.equals(ChallengeConfig.canonicalId(existing)));
        if (enabled) {
            list.add(canonical);
        }
        data.set(path, list);
        save();
    }

    // ---------------- 个人设置 ----------------

    /**
     * 读取布尔型个人设置；不存在时返回默认值。
     */
    public boolean getBooleanSetting(UUID playerId, String key, boolean defaultValue) {
        return data.getBoolean("player-settings." + playerId + "." + key, defaultValue);
    }

    public void setBooleanSetting(UUID playerId, String key, boolean value) {
        data.set("player-settings." + playerId + "." + key, value);
        save();
    }

    /**
     * 剩余怪物高亮：默认开启。
     */
    public boolean isMobGlowEnabled(UUID playerId) {
        return getBooleanSetting(playerId, "mob-glow", true);
    }

    public void setMobGlowEnabled(UUID playerId, boolean enabled) {
        setBooleanSetting(playerId, "mob-glow", enabled);
    }

    // ---------------- 玩家统计 ----------------

    public static class ExamStats {
        public int plays;
        public int wins;
        public double bestTime; // 秒，0 表示暂无
    }

    public ExamStats getStats(UUID playerId, String examId) {
        ExamStats stats = new ExamStats();
        String path = "stats." + playerId + "." + examId;
        stats.plays = data.getInt(path + ".plays", 0);
        stats.wins = data.getInt(path + ".wins", 0);
        stats.bestTime = data.getDouble(path + ".best-time", 0.0);
        return stats;
    }

    /**
     * 记录一次考核结果（无论成功失败均计入参加次数）。
     *
     * @return 是否首次通过该考核
     */
    public boolean recordAttempt(UUID playerId, String playerName, String examId,
                                 boolean success, double timeSeconds) {
        String path = "stats." + playerId + "." + examId;
        int plays = data.getInt(path + ".plays", 0) + 1;
        data.set(path + ".plays", plays);

        boolean firstWin = false;
        if (success) {
            int wins = data.getInt(path + ".wins", 0) + 1;
            data.set(path + ".wins", wins);
            firstWin = wins == 1;

            double best = data.getDouble(path + ".best-time", 0.0);
            if (best <= 0 || timeSeconds < best) {
                best = timeSeconds;
                data.set(path + ".best-time", best);
            }
            updateLeaderboard(playerId, playerName, examId, best);
        }
        save();
        return firstWin;
    }

    // ---------------- 排行榜 ----------------

    private void updateLeaderboard(UUID playerId, String playerName, String examId, double time) {
        String path = "leaderboards." + examId;
        List<Map<?, ?>> list = new ArrayList<>(data.getMapList(path));
        String uuid = playerId.toString();

        list.removeIf(m -> uuid.equals(String.valueOf(m.get("uuid"))));

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("uuid", uuid);
        entry.put("name", playerName);
        entry.put("time", time);
        list.add(entry);

        list.sort((a, b) -> Double.compare(toDouble(a.get("time")), toDouble(b.get("time"))));
        if (list.size() > 10) {
            list = new ArrayList<>(list.subList(0, 10));
        }
        data.set(path, list);
    }

    public record LeaderboardEntry(String name, String uuid, double timeSeconds) {
    }

    /**
     * 排行榜（已按完成时间升序，最快在前）。
     */
    public List<LeaderboardEntry> getLeaderboard(String examId) {
        List<LeaderboardEntry> result = new ArrayList<>();
        for (Map<?, ?> m : data.getMapList("leaderboards." + examId)) {
            result.add(new LeaderboardEntry(
                    String.valueOf(m.get("name")),
                    String.valueOf(m.get("uuid")),
                    toDouble(m.get("time"))
            ));
        }
        return result;
    }

    private static double toDouble(Object o) {
        try {
            return o == null ? 0.0 : Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    // ---------------- 考核日志（每玩家每考核最近 20 条） ----------------

    private static final java.time.format.DateTimeFormatter LOG_FORMAT =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public record ExamLog(String time, String result, double duration, int challenges,
                          double multiplier, int pauses) {
    }

    /**
     * 记录一次考核情况（最新的在最前，最多保留 20 条）。
     */
    public void recordLog(UUID playerId, String examId, String result,
                          double durationSeconds, int challenges, double multiplier, int pauses) {
        String path = "logs." + playerId + "." + examId;
        List<Map<?, ?>> list = new ArrayList<>(data.getMapList(path));

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("time", java.time.LocalDateTime.now().format(LOG_FORMAT));
        entry.put("result", result);
        entry.put("duration", durationSeconds);
        entry.put("challenges", challenges);
        entry.put("multiplier", multiplier);
        entry.put("pauses", pauses);
        list.add(0, entry);
        while (list.size() > 20) {
            list.remove(list.size() - 1);
        }
        data.set(path, list);
        save();
    }

    /**
     * 某玩家在某考核的最近考核记录（最新的在前）。
     */
    public List<ExamLog> getLogs(UUID playerId, String examId) {
        List<ExamLog> result = new ArrayList<>();
        for (Map<?, ?> m : data.getMapList("logs." + playerId + "." + examId)) {
            result.add(new ExamLog(
                    String.valueOf(m.get("time")),
                    String.valueOf(m.get("result")),
                    toDouble(m.get("duration")),
                    (int) toDouble(m.get("challenges")),
                    toDouble(m.get("multiplier")),
                    (int) toDouble(m.get("pauses"))
            ));
        }
        return result;
    }

    // ---------------- 退出时尚未还原的考核效果 ----------------

    /**
     * 将玩家退出考核时未能立即还原的效果（护甲 / 游戏模式 / 生命上限 / 飞行权限）
     * 持久化到 data.yml，待其下次进入服务器时补做。
     */
    public void savePendingEffects(UUID playerId, PendingEffects effects) {
        if (effects == null || effects.isEmpty()) {
            return;
        }
        String base = "pending-effects." + playerId;
        data.set(base, null);

        if (effects.armor() != null) {
            List<String> encoded = new ArrayList<>();
            boolean armorValid = true;
            for (ItemStack item : effects.armor()) {
                if (item == null) {
                    encoded.add("");
                    continue;
                }
                try {
                    encoded.add(Base64.getEncoder().encodeToString(item.serializeAsBytes()));
                } catch (RuntimeException e) {
                    armorValid = false;
                    plugin.getLogger().warning("保存退出前护甲失败：" + e.getMessage());
                    break;
                }
            }
            if (armorValid) {
                data.set(base + ".armor", encoded);
            } else {
                plugin.getLogger().warning("护甲数据无法完整序列化，已跳过护甲补偿（避免覆盖已还原的装备）。");
            }
        }
        if (effects.gameMode() != null) {
            data.set(base + ".game-mode", effects.gameMode().name());
        }
        if (effects.maxHealth() != null) {
            data.set(base + ".max-health", effects.maxHealth());
        }
        if (effects.allowFlight() != null) {
            data.set(base + ".allow-flight", effects.allowFlight());
        }
        data.set(base + ".saved-at", System.currentTimeMillis());
        save();
    }

    /**
     * 读取并清除玩家待恢复的效果；没有记录时返回 null。
     */
    public PendingEffects consumePendingEffects(UUID playerId) {
        String base = "pending-effects." + playerId;
        if (!data.isConfigurationSection(base)) {
            return null;
        }

        ItemStack[] armor = null;
        if (data.isSet(base + ".armor")) {
            List<String> encoded = data.getStringList(base + ".armor");
            // 护甲恒为 4 个槽位；损坏/旧数据也不会导致 setArmorContents 抛错
            ItemStack[] decoded = new ItemStack[4];
            boolean armorValid = true;
            for (int i = 0; i < Math.min(encoded.size(), decoded.length); i++) {
                String value = encoded.get(i);
                if (value == null || value.isBlank()) {
                    continue;
                }
                try {
                    decoded[i] = ItemStack.deserializeBytes(Base64.getDecoder().decode(value));
                } catch (RuntimeException e) {
                    armorValid = false;
                    plugin.getLogger().warning("恢复退出前护甲失败：" + e.getMessage());
                    break;
                }
            }
            if (armorValid) {
                armor = decoded;
            } else {
                plugin.getLogger().warning("护甲数据不完整，已跳过护甲补偿。");
            }
        }

        GameMode gameMode = null;
        String gameModeName = data.getString(base + ".game-mode");
        if (gameModeName != null) {
            try {
                gameMode = GameMode.valueOf(gameModeName);
            } catch (IllegalArgumentException ignored) {
                // 非法游戏模式直接跳过
            }
        }
        Double maxHealth = data.isSet(base + ".max-health")
                ? data.getDouble(base + ".max-health") : null;
        Boolean allowFlight = data.isSet(base + ".allow-flight")
                ? data.getBoolean(base + ".allow-flight") : null;

        data.set(base, null);
        save();
        return new PendingEffects(armor, gameMode, maxHealth, allowFlight);
    }
}
