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
    public static final String NO_WEAPON = "no-weapon";                 // 禁用武器（剑/斧/重锤/矛） x2
    public static final String LEGACY_NO_SWORD_AXE = "no-sword-axe";    // 旧 ID，仅用于配置/数据迁移
    public static final String NO_ARMOR = "no-armor";                   // 不允许穿戴护甲 x5

    private final Map<String, Double> multipliers = new LinkedHashMap<>();

    public ChallengeConfig() {
        resetDefaults();
    }

    /**
     * 将旧挑战 ID 统一为新 ID；未知 ID 仅做小写处理。
     */
    public static String canonicalId(String id) {
        if (id == null) {
            return null;
        }
        String normalized = id.toLowerCase(Locale.ROOT);
        return normalized.equals(LEGACY_NO_SWORD_AXE) ? NO_WEAPON : normalized;
    }

    private void resetDefaults() {
        multipliers.clear();
        multipliers.put(HALF_HEALTH, 1.4);
        multipliers.put(DOUBLE_MOBS, 1.7);
        multipliers.put(NO_NATURAL_REGEN, 1.7);
        multipliers.put(NO_REGEN, 3.2);
        multipliers.put(NO_VANILLA_TOOLS, 1.5);
        multipliers.put(HALF_TIME, 2.0);
        multipliers.put(NO_WEAPON, 2.0);
        multipliers.put(NO_ARMOR, 5.0);
    }

    /**
     * 从配置节覆盖默认倍率（只读正值项）。
     * 每次加载先恢复默认值，确保 reload 后已删除的挑战不会残留旧倍率。
     * 旧的 no-sword-axe 会自动迁移为 no-weapon；若两者同时存在，以 no-weapon 为准。
     */
    public void load(ConfigurationSection section) {
        resetDefaults();
        if (section == null) {
            return;
        }
        boolean hasNewWeaponId = section.isSet(NO_WEAPON);
        for (String key : section.getKeys(false)) {
            if (hasNewWeaponId && key.equalsIgnoreCase(LEGACY_NO_SWORD_AXE)) {
                continue;
            }
            double value = section.getDouble(key, 0.0);
            if (value > 0) {
                multipliers.put(canonicalId(key), value);
            }
        }
    }

    public boolean exists(String id) {
        String canonical = canonicalId(id);
        return canonical != null && multipliers.containsKey(canonical);
    }

    public double getMultiplier(String id) {
        String canonical = canonicalId(id);
        return canonical == null ? 1.0 : multipliers.getOrDefault(canonical, 1.0);
    }

    public Map<String, Double> getMultipliers() {
        return multipliers;
    }
}
