package com.examofpve.config;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * 单个考核（exam1 / exam2 ...）的完整配置。
 */
public class ExamConfig {

    private final String id;
    private final String displayName;
    private final List<String> worlds;
    private final SpawnPoint spawn;         // 可选，null 表示使用世界默认出生点
    private final int timeLimitSeconds;
    private final GameMode gameMode;        // 可选
    private final TimeWindow openWindow;    // 可选，null 表示全天开放
    private final boolean allowBreak;
    private final boolean allowPlace;
    private final List<MobSpawnConfig> mobs;
    private final RewardConfig rewards;
    private final double entryFee;          // 参加费用（金币），0 = 免费
    private final Set<String> allowedChallenges; // null 表示允许全部挑战
    private final Map<Integer, ChallengeRewardConfig> challengeRewards; // 完成挑战数量 -> 完整奖励
    private final RewardConfig firstWinRewards;          // 初次通过额外奖励

    public ExamConfig(String id, String displayName, List<String> worlds, SpawnPoint spawn,
                      int timeLimitSeconds, GameMode gameMode, TimeWindow openWindow,
                      boolean allowBreak, boolean allowPlace, List<MobSpawnConfig> mobs,
                      RewardConfig rewards, double entryFee, Set<String> allowedChallenges,
                      Map<Integer, ChallengeRewardConfig> challengeRewards, RewardConfig firstWinRewards) {
        this.id = id;
        this.displayName = displayName;
        this.worlds = new ArrayList<>(worlds);
        this.spawn = spawn;
        this.timeLimitSeconds = timeLimitSeconds;
        this.gameMode = gameMode;
        this.openWindow = openWindow;
        this.allowBreak = allowBreak;
        this.allowPlace = allowPlace;
        this.mobs = new ArrayList<>(mobs);
        this.rewards = rewards;
        this.entryFee = Math.max(0, entryFee);
        this.allowedChallenges = allowedChallenges == null ? null : new HashSet<>(allowedChallenges);
        this.challengeRewards = challengeRewards == null ? new HashMap<>() : new HashMap<>(challengeRewards);
        this.firstWinRewards = firstWinRewards;
    }

    /**
     * 解析考核配置（不使用怪物池）；配置非法（缺少 worlds / 没有有效 mobs）时返回 null。
     */
    public static ExamConfig parse(String id, ConfigurationSection s, Logger logger) {
        return parse(id, s, logger, Map.of());
    }

    /**
     * 解析考核配置；配置非法（缺少 worlds / 没有有效 mobs）时返回 null。
     *
     * @param pools 已解析的命名怪物池（键为小写池名），用于解析点位中的 pool 引用
     */
    public static ExamConfig parse(String id, ConfigurationSection s, Logger logger,
                                   Map<String, MobPool> pools) {
        List<String> worlds = s.getStringList("worlds");
        if (worlds.isEmpty()) {
            logger.warning("考核 " + id + " 未配置 worlds，已跳过。");
            return null;
        }

        int timeLimit = s.getInt("time-limit", 60);
        if (timeLimit <= 0) {
            timeLimit = 60;
        }

        List<MobSpawnConfig> mobs = new ArrayList<>();
        // mobs 在 YAML 中是列表，需用 getMapList 读取（getConfigurationSection 不支持列表）
        for (Map<?, ?> map : s.getMapList("mobs")) {
            ConfigurationSection ms = toSection(map);
            MobSpawnConfig mc = MobSpawnConfig.parse(ms, logger, pools);
            if (mc != null) {
                mobs.add(mc);
            }
        }
        if (mobs.isEmpty()) {
            logger.warning("考核 " + id + " 未配置有效的 mobs，已跳过。");
            return null;
        }

        return new ExamConfig(
                id,
                s.getString("display-name", id),
                worlds,
                parseSpawn(s.getConfigurationSection("spawn")),
                timeLimit,
                parseGameMode(s.getString("game-mode"), logger),
                TimeWindow.parse(s, "open-window"),
                s.getBoolean("world-settings.allow-break", false),
                s.getBoolean("world-settings.allow-place", false),
                mobs,
                RewardConfig.parse(s.getConfigurationSection("rewards")),
                s.getDouble("entry-fee", 0.0),
                parseAllowedChallenges(s),
                parseChallengeRewards(s.getConfigurationSection("challenge-rewards")),
                RewardConfig.parse(s.getConfigurationSection("first-win-rewards"))
        );
    }

    private static SpawnPoint parseSpawn(ConfigurationSection s) {
        if (s == null || !s.isSet("x") || !s.isSet("y") || !s.isSet("z")) {
            return null;
        }
        return new SpawnPoint(
                s.getDouble("x"),
                s.getDouble("y"),
                s.getDouble("z"),
                (float) s.getDouble("yaw", 0.0),
                (float) s.getDouble("pitch", 0.0)
        );
    }

    private static GameMode parseGameMode(String name, Logger logger) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return GameMode.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            logger.warning("无效的 game-mode：" + name);
            return null;
        }
    }

    private static Set<String> parseAllowedChallenges(ConfigurationSection s) {
        if (!s.isSet("allowed-challenges")) {
            return null; // 允许全部
        }
        Set<String> set = new HashSet<>();
        for (String item : s.getStringList("allowed-challenges")) {
            if (item != null && !item.isBlank()) {
                set.add(item.trim().toLowerCase(Locale.ROOT));
            }
        }
        return set;
    }

    /**
     * 解析挑战完成数量奖励。兼容两种格式：
     * 完整格式（推荐）：
     *   challenge-rewards:
     *     3:
     *       money: 1000.0
     *       exp: 0
     *       points: 0
     *       commands: []
     * 旧版纯数字（视为点券数量）：
     *   challenge-rewards:
     *     3: 1
     */
    private static Map<Integer, ChallengeRewardConfig> parseChallengeRewards(ConfigurationSection s) {
        Map<Integer, ChallengeRewardConfig> map = new HashMap<>();
        if (s == null) {
            return map;
        }
        for (String key : s.getKeys(false)) {
            try {
                int completed = Integer.parseInt(key);
                ConfigurationSection child = s.getConfigurationSection(key);
                ChallengeRewardConfig cfg;
                if (child != null) {
                    cfg = ChallengeRewardConfig.parse(child);
                } else {
                    // 旧版纯数字：视为点券数量
                    cfg = new ChallengeRewardConfig(0, 0, s.getInt(key, 0), List.of());
                }
                map.put(completed, cfg);
            } catch (NumberFormatException ignored) {
                // 忽略非法键
            }
        }
        return map;
    }

    /**
     * 将 getMapList 返回的 Map 包装成 ConfigurationSection，便于统一取值。
     */
    private static ConfigurationSection toSection(Map<?, ?> map) {
        MemoryConfiguration mc = new MemoryConfiguration();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            mc.set(String.valueOf(e.getKey()), e.getValue());
        }
        return mc;
    }

    /**
     * 计算玩家进入考核世界后的出生点。
     */
    public Location resolveSpawn(World world) {
        if (spawn != null) {
            return new Location(world, spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
        }
        return world.getSpawnLocation();
    }

    /**
     * 指定挑战是否允许在此考核中使用。
     */
    public boolean isChallengeAllowed(String challengeId) {
        return allowedChallenges == null || allowedChallenges.contains(challengeId);
    }

    /**
     * 根据完成的挑战数量取奖励（取所有 完成数 <= completed 配置中档位最高的那档）。
     */
    public ChallengeRewardConfig getChallengeReward(int completed) {
        ChallengeRewardConfig best = null;
        int bestKey = -1;
        for (Map.Entry<Integer, ChallengeRewardConfig> e : challengeRewards.entrySet()) {
            if (e.getKey() <= completed && e.getKey() > bestKey) {
                bestKey = e.getKey();
                best = e.getValue();
            }
        }
        return best;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getWorlds() {
        return worlds;
    }

    public int getTimeLimitSeconds() {
        return timeLimitSeconds;
    }

    public GameMode getGameMode() {
        return gameMode;
    }

    public TimeWindow getOpenWindow() {
        return openWindow;
    }

    public boolean isAllowBreak() {
        return allowBreak;
    }

    public boolean isAllowPlace() {
        return allowPlace;
    }

    public List<MobSpawnConfig> getMobs() {
        return mobs;
    }

    public RewardConfig getRewards() {
        return rewards;
    }

    public double getEntryFee() {
        return entryFee;
    }

    public RewardConfig getFirstWinRewards() {
        return firstWinRewards;
    }

    public record SpawnPoint(double x, double y, double z, float yaw, float pitch) {
    }
}
