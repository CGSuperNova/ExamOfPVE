package com.examofpve.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

import java.util.Locale;
import java.util.logging.Logger;

/**
 * 一种可生成的生物定义：原版生物或 MythicMobs 生物。
 * 既用于考核点位直接配置（type / mythicmob），也用于怪物池条目。
 */
public class MobDefinition {

    private final EntityType entityType; // 原版生物类型；MythicMobs 条目为 null
    private final String mythicMob;      // MythicMobs 生物名；原版条目为 null
    private final String name;           // 可选：自定义显示名称
    private final double weight;         // 在怪物池中的抽取权重

    public MobDefinition(EntityType entityType, String mythicMob, String name, double weight) {
        this.entityType = entityType;
        this.mythicMob = mythicMob;
        this.name = name;
        this.weight = weight;
    }

    /**
     * 解析单条生物定义；非法配置返回 null（调用方应跳过并记录日志）。
     *
     * @param context 日志上下文，例如 "mobs" 或 "mob-pools.undead"
     */
    public static MobDefinition parse(ConfigurationSection section, Logger logger, String context) {
        String typeStr = section.getString("type");
        String mythic = section.getString("mythicmob");

        EntityType type = null;
        if (typeStr != null && !typeStr.isBlank()) {
            try {
                type = EntityType.valueOf(typeStr.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                logger.warning(context + " 中的 EntityType 无效：" + typeStr);
                return null;
            }
        }

        if (type == null && (mythic == null || mythic.isBlank())) {
            logger.warning(context + " 必须提供 type（原版）或 mythicmob（MythicMobs）之一。");
            return null;
        }

        double weight = section.getDouble("weight", 1.0);
        if (!(weight > 0)) {
            logger.warning(context + " 的 weight 必须大于 0，已忽略该条目。");
            return null;
        }

        return new MobDefinition(
                type,
                mythic == null || mythic.isBlank() ? null : mythic.trim(),
                section.getString("name"),
                weight
        );
    }

    public boolean isMythic() {
        return mythicMob != null;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public String getMythicMob() {
        return mythicMob;
    }

    public String getName() {
        return name;
    }

    public double getWeight() {
        return weight;
    }

    @Override
    public String toString() {
        return isMythic() ? "MythicMobs:" + mythicMob : String.valueOf(entityType);
    }
}
