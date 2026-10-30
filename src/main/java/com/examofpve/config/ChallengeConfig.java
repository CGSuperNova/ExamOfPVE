package com.examofpve.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 挑战类型与奖励倍率（全局配置，可在 config.yml 的 challenges 节覆盖默认倍率）。
 * 多个挑战的倍率相乘叠加，例如两个 x1.2 叠加为 x1.44。
 */
public class ChallengeConfig {

    public static final String HALF_HEALTH = "half-health";             // 生命值减半 x1.4
    public static final String DOUBLE_MOBS = "double-mobs";             // 怪物数量x2 x1.7
    public static final String NO_NATURAL_REGEN = "no-natural-regen";   // 禁止自然回血 x1.7
    public static final String NO_REGEN = "no-regen";                   // 禁止任意形式回血 x3.2
    public static final String NO_VANILLA_TOOLS = "no-vanilla-tools";   // 禁用原版辅助道具 x1.5
    public static final String HALF_TIME = "half-time";                 // 考核时间减半 x2
    public static final String NO_SWORD_AXE = "no-sword-axe";           // 禁用剑和斧 x2
    public static final String NO_ARMOR = "no-armor";                   // 不允许穿戴护甲 x5

    private final Map<String, Double> multipliers = new LinkedHashMap<>();

    public ChallengeConfig() {
        resetDefaults();
    }

    private void resetDefaults() {
        multipliers.clear();
        multipliers.put(HALF_HEALTH, 1.4);
        multipliers.put(DOUBLE_MOBS, 1.7);
        multipliers.put(NO_NATURAL_REGEN, 1.7);
        multipliers.put(NO_REGEN, 3.2);
        multipliers.put(NO_VANILLA_TOOLS, 1.5);
        multipliers.put(HALF_TIME, 2.0);
        multipliers.put(NO_SWORD_AXE, 2.0);
        multipliers.put(NO_ARMOR, 5.0);
    }

    /**
     * 从配置节覆盖默认倍率（只读正值项）。
     * 每次加载先恢复默认值，确保 reload 后已删除的挑战不会残留旧倍率。
     */
    public void load(ConfigurationSection section) {
        resetDefaults();
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            double value = section.getDouble(key, 0.0);
            if (value > 0) {
                multipliers.put(key.toLowerCase(Locale.ROOT), value);
            }
        }
    }

    public boolean exists(String id) {
        return id != null && multipliers.containsKey(id.toLowerCase(Locale.ROOT));
    }

    public double getMultiplier(String id) {
        return multipliers.getOrDefault(id.toLowerCase(Locale.ROOT), 1.0);
    }

    public Map<String, Double> getMultipliers() {
        return multipliers;
    }
}
