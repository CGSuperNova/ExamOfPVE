package com.examofpve.config;

import com.examofpve.ExamOfPVEPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 负责加载 / 重载 config.yml 与 messages.yml，并提供便捷的消息发送。
 */
public class ConfigManager {

    private final ExamOfPVEPlugin plugin;
    private FileConfiguration config;
    private FileConfiguration messages;
    private final Map<String, ExamConfig> exams = new LinkedHashMap<>();
    private final Map<String, MobPool> mobPools = new LinkedHashMap<>();
    private final ChallengeConfig challenges = new ChallengeConfig();
    private boolean debug = false;
    private List<String> allowedCommandsDuringExam = new ArrayList<>();
    private String voucherCommand = "";       // 点券发放命令模板
    private boolean hideClosedInList = false; // 列表中隐藏未开放考核
    private double voidY = -64.0;             // 虚空传送 Y 阈值
    private boolean pauseEnabled = true;      // 考核暂停开关
    private String pauseKey = "SNEAK";        // 暂停触发键：SNEAK / SPRINT / SWAP
    private int pauseClicksRequired = 3;      // 触发暂停所需连按次数（默认三击，避免误触）
    private int pauseDurationSeconds = 60;    // 每次暂停时长
    private int maxPauses = 2;                // 每场最多暂停次数
    private long pauseDoubleClickMs = 700;    // 连按判定窗口（毫秒）
    private List<Integer> timeWarningAt = List.of(60, 30, 10, 5); // 考核剩余时间提醒点（秒）
    private int pauseWarningAt = 5;           // 暂停剩余时间提醒点（秒）
    private List<Integer> lowHealthWarningAt = List.of(20, 10);   // 低血量提醒阈值（最大生命百分比）
    private int prepTimeSeconds = 5;          // 考核预备阶段时长（秒），0 = 关闭

    public ConfigManager(ExamOfPVEPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        saveDefault("config.yml");
        saveDefault("messages.yml");
        config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config.yml"));
        messages = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages.yml"));
        // 旧版 messages.yml 可能缺少新增的键：用内置资源作为默认值兜底
        InputStream defaultsStream = plugin.getResource("messages.yml");
        if (defaultsStream != null) {
            try (InputStreamReader reader = new InputStreamReader(defaultsStream, StandardCharsets.UTF_8)) {
                messages.setDefaults(YamlConfiguration.loadConfiguration(reader));
            } catch (Exception e) {
                plugin.getLogger().warning("加载内置 messages.yml 默认值失败：" + e.getMessage());
            }
        }
        parse();
    }

    public String format(String key) {
        return format(key, null);
    }

    private void saveDefault(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
    }

    private void parse() {
        exams.clear();
        debug = config.getBoolean("debug", false);
        allowedCommandsDuringExam = config.getStringList("settings.allowed-commands-during-exam");
        voucherCommand = config.getString("settings.voucher-command", "");
        hideClosedInList = config.getBoolean("settings.list-hide-closed", false);
        voidY = config.getDouble("settings.void-teleport-y", -64.0);
        pauseEnabled = config.getBoolean("settings.pause-enabled", true);
        pauseKey = config.getString("settings.pause-key", "SNEAK").toUpperCase(Locale.ROOT);
        pauseClicksRequired = Math.max(2, Math.min(8, config.getInt("settings.pause-clicks-required", 3)));
        pauseDurationSeconds = Math.max(1, config.getInt("settings.pause-duration-seconds", 60));
        maxPauses = Math.max(0, config.getInt("settings.max-pauses", 2));
        // 兼容旧配置键 pause-double-click-ms，新键 pause-click-window-ms 优先
        pauseDoubleClickMs = Math.max(200, config.getLong("settings.pause-click-window-ms",
                config.getLong("settings.pause-double-click-ms", 700)));
        timeWarningAt = parseSortedIntList(config, "settings.time-warning-at",
                List.of(60, 30, 10, 5), Integer.MAX_VALUE);
        pauseWarningAt = Math.max(1, config.getInt("settings.pause-warning-at", 5));
        lowHealthWarningAt = parseSortedIntList(config, "settings.low-health-warning-at",
                List.of(20, 10), 100);
        prepTimeSeconds = Math.max(0, config.getInt("settings.prep-time-seconds", 5));
        challenges.load(config.getConfigurationSection("challenges"));
        parseMobPools();

        ConfigurationSection examSection = config.getConfigurationSection("exams");
        if (examSection == null) {
            return;
        }
        for (String key : examSection.getKeys(false)) {
            ConfigurationSection s = examSection.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            ExamConfig ec = ExamConfig.parse(key, s, plugin.getLogger(), mobPools);
            if (ec != null) {
                exams.put(key.toLowerCase(Locale.ROOT), ec);
            }
        }
    }

    /**
     * 解析全局怪物池（config.yml 的 mob-pools 节）。
     * 池名按小写索引，点位中的 pool 引用不区分大小写。
     */
    private void parseMobPools() {
        mobPools.clear();
        ConfigurationSection section = config.getConfigurationSection("mob-pools");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            List<?> entries = section.getList(key);
            if (entries == null || entries.isEmpty()) {
                plugin.getLogger().warning("mob-pools." + key + " 不是有效的怪物列表，已忽略该怪物池。");
                continue;
            }
            MobPool pool = MobPool.parse(key, entries, plugin.getLogger());
            if (pool != null) {
                mobPools.put(key.toLowerCase(Locale.ROOT), pool);
            }
        }
    }

    public ExamConfig getExam(String id) {
        return id == null ? null : exams.get(id.toLowerCase(Locale.ROOT));
    }

    /**
     * 已解析的命名怪物池（键为小写池名）。
     */
    public Map<String, MobPool> getMobPools() {
        return mobPools;
    }

    /**
     * 解析正整数列表（如提醒阈值），按降序排列；非法或为空时使用默认值。
     *
     * @param maxValue 允许的最大值（如低血量百分比不超过 100）
     */
    private List<Integer> parseSortedIntList(FileConfiguration c, String path,
                                             List<Integer> def, int maxValue) {
        List<Integer> out = new ArrayList<>();
        List<?> raw = c.getList(path);
        if (raw != null) {
            for (Object o : raw) {
                try {
                    int v = (int) Math.round(Double.parseDouble(String.valueOf(o)));
                    if (v > 0) {
                        out.add(Math.min(maxValue, v));
                    }
                } catch (NumberFormatException ignored) {
                    // 忽略非法项
                }
            }
        }
        if (out.isEmpty()) {
            out = new ArrayList<>(def);
        }
        out.sort(Comparator.reverseOrder());
        return out;
    }

    public Collection<ExamConfig> getExams() {
        return exams.values();
    }

    public boolean isDebug() {
        return debug;
    }

    public List<String> getAllowedCommandsDuringExam() {
        return allowedCommandsDuringExam;
    }

    public ChallengeConfig getChallengeConfig() {
        return challenges;
    }

    public String getVoucherCommand() {
        return voucherCommand;
    }

    public boolean isHideClosedInList() {
        return hideClosedInList;
    }

    public double getVoidY() {
        return voidY;
    }

    public boolean isPauseEnabled() {
        return pauseEnabled;
    }

    public String getPauseKey() {
        return pauseKey;
    }

    public int getPauseClicksRequired() {
        return pauseClicksRequired;
    }

    public int getPauseDurationSeconds() {
        return pauseDurationSeconds;
    }

    public int getMaxPauses() {
        return maxPauses;
    }

    public long getPauseDoubleClickMs() {
        return pauseDoubleClickMs;
    }

    public List<Integer> getTimeWarningAt() {
        return timeWarningAt;
    }

    public int getPauseWarningAt() {
        return pauseWarningAt;
    }

    public List<Integer> getLowHealthWarningAt() {
        return lowHealthWarningAt;
    }

    public int getPrepTimeSeconds() {
        return prepTimeSeconds;
    }

    // ---------------- 消息工具 ----------------

    public String getMessage(String key) {
        String value = messages.getString(key);
        return value == null ? key : value;
    }

    public String getPrefix() {
        return ChatColor.translateAlternateColorCodes('&', getMessage("prefix"));
    }

    /**
     * 先替换占位符 {key}，再翻译 & 颜色代码。
     */
    public String format(String key, Map<String, String> placeholders) {
        String text = getMessage(key);
        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                text = text.replace("{" + e.getKey() + "}", String.valueOf(e.getValue()));
            }
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public void send(CommandSender target, String key) {
        send(target, key, null);
    }

    public void send(CommandSender target, String key, Map<String, String> placeholders) {
        target.sendMessage(getPrefix() + format(key, placeholders));
    }
}
