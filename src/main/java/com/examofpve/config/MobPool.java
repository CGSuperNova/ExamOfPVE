package com.examofpve.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * 怪物池：一组带权重的生物定义。每次生成时按权重随机抽取一种。
 *
 * <p>配置示例（config.yml）：</p>
 * <pre>
 * mob-pools:
 *   undead:
 *     - type: ZOMBIE
 *       weight: 3
 *     - type: SKELETON
 *       weight: 2
 *     - mythicmob: "SkeletonKing"
 *       weight: 1
 *     # 也支持简写：
 *     - "SPIDER"
 *     - "mythic:ZombieLord"
 * </pre>
 */
public class MobPool {

    private final String id;
    private final List<MobDefinition> entries;
    private final double totalWeight;

    public MobPool(String id, List<MobDefinition> entries) {
        this.id = id;
        this.entries = List.copyOf(entries);
        double sum = 0;
        for (MobDefinition entry : this.entries) {
            sum += entry.getWeight();
        }
        this.totalWeight = sum;
    }

    /**
     * 从配置列表解析怪物池；没有有效条目时返回 null。
     *
     * @param id  池名称（仅用于日志）
     * @param raw 池条目列表（键值映射或字符串简写）
     */
    public static MobPool parse(String id, List<?> raw, Logger logger) {
        List<MobDefinition> entries = new ArrayList<>();
        String context = "mob-pools." + id;
        for (Object item : raw) {
            MobDefinition definition = parseEntry(item, logger, context);
            if (definition != null) {
                entries.add(definition);
            }
        }
        if (entries.isEmpty()) {
            logger.warning(context + " 没有有效条目，已忽略该怪物池。");
            return null;
        }
        return new MobPool(id, entries);
    }

    private static MobDefinition parseEntry(Object item, Logger logger, String context) {
        if (item instanceof String text) {
            String value = text.trim();
            if (value.isEmpty()) {
                return null;
            }
            String lower = value.toLowerCase(Locale.ROOT);
            if (lower.startsWith("mythicmobs:")) {
                return mythicEntry(value.substring("mythicmobs:".length()).trim(), logger, context);
            }
            if (lower.startsWith("mythic:")) {
                return mythicEntry(value.substring("mythic:".length()).trim(), logger, context);
            }
            try {
                return new MobDefinition(
                        EntityType.valueOf(value.toUpperCase(Locale.ROOT)), null, null, 1.0);
            } catch (IllegalArgumentException e) {
                logger.warning(context + " 中的 EntityType 无效：" + value);
                return null;
            }
        }

        ConfigurationSection section = null;
        if (item instanceof Map<?, ?> map) {
            section = toSection(map);
        } else if (item instanceof ConfigurationSection cs) {
            section = cs;
        }
        if (section == null) {
            logger.warning(context + " 中存在无法识别的条目：" + item);
            return null;
        }
        return MobDefinition.parse(section, logger, context);
    }

    private static MobDefinition mythicEntry(String name, Logger logger, String context) {
        if (name.isEmpty()) {
            logger.warning(context + " 中的 MythicMobs 名称不能为空。");
            return null;
        }
        return new MobDefinition(null, name, null, 1.0);
    }

    /**
     * 按权重随机抽取一种生物；池为空时返回 null。
     */
    public MobDefinition pick() {
        if (entries.isEmpty()) {
            return null;
        }
        double roll = ThreadLocalRandom.current().nextDouble() * totalWeight;
        for (MobDefinition entry : entries) {
            roll -= entry.getWeight();
            if (roll < 0) {
                return entry;
            }
        }
        return entries.get(entries.size() - 1);
    }

    private static ConfigurationSection toSection(Map<?, ?> map) {
        MemoryConfiguration section = new MemoryConfiguration();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            section.set(String.valueOf(entry.getKey()), entry.getValue());
        }
        return section;
    }

    public String getId() {
        return id;
    }

    public List<MobDefinition> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
