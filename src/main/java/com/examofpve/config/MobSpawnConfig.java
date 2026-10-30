package com.examofpve.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.EntityType;

import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 单个刷怪点位配置。
 *
 * <p>支持多种写法：</p>
 * <pre>
 * # 1. 直接指定原版生物
 * - type: ZOMBIE
 *   amount: 3
 *   ...
 *
 * # 2. 直接指定 MythicMobs 生物
 * - mythicmob: "SkeletonKing"
 *   amount: 1
 *   ...
 *
 * # 3. 引用命名怪物池（每生成一只独立随机抽取）
 * - pool: "undead"
 *   amount: 5
 *   ...
 *
 * # 4. 内联怪物池
 * - pool:
 *     - type: ZOMBIE
 *     - type: SKELETON
 *   amount: 5
 *   ...
 *
 * # 5. 区域随机生成（在矩形区域内每次随机挑选一个安全位置）
 * - type: ZOMBIE
 *   amount: 10
 *   region: "0,0,100,100"
 *   # 或：
 *   # region:
 *   #   min-x: 0
 *   #   min-z: 0
 *   #   max-x: 100
 *   #   max-z: 100
 *   #   min-y: 60
 *   #   max-y: 100
 * </pre>
 */
public class MobSpawnConfig {

    private final MobDefinition direct; // 直接配置时的生物定义；使用怪物池时为 null
    private final MobPool pool;         // 怪物池；直接配置时为 null
    private final String poolId;        // 怪物池名称（内联池为 "inline"）；直接配置时为 null
    private final int amount;
    private final int delaySeconds;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final String name;          // 点位级自定义名称（怪物池条目未设置名称时使用）
    private final MobRegion region;     // 区域随机生成；非区域点位时为 null

    public MobSpawnConfig(MobDefinition direct, MobPool pool, String poolId,
                          int amount, int delaySeconds,
                          double x, double y, double z, float yaw, float pitch,
                          String name, MobRegion region) {
        this.direct = direct;
        this.pool = pool;
        this.poolId = poolId;
        this.amount = amount;
        this.delaySeconds = delaySeconds;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.name = name;
        this.region = region;
    }

    /**
     * 解析单个刷怪点位；非法配置返回 null（调用方应跳过并记录日志）。
     *
     * @param pools 已解析的命名怪物池（键为小写池名）
     */
    public static MobSpawnConfig parse(ConfigurationSection section, Logger logger,
                                       Map<String, MobPool> pools) {
        MobRegion region = null;
        Object rawRegion = section.get("region");
        if (rawRegion instanceof ConfigurationSection regionSection) {
            region = MobRegion.parse(regionSection, logger);
        } else if (rawRegion instanceof Map<?, ?> regionMap) {
            region = MobRegion.parse(toSection(regionMap), logger);
        } else if (rawRegion instanceof String regionText) {
            region = MobRegion.parse(regionText, logger);
        } else if (rawRegion != null) {
            logger.warning("mobs 配置中的 region 必须是区域映射或 \"x1,z1,x2,z2\" 字符串。");
            return null;
        }
        if (rawRegion != null && region == null) {
            return null;
        }

        if (region == null
                && (!section.isSet("x") || !section.isSet("y") || !section.isSet("z"))) {
            logger.warning("mobs 配置项缺少坐标 x/y/z 或 region。");
            return null;
        }
        if (region != null
                && (section.isSet("x") || section.isSet("y") || section.isSet("z"))) {
            logger.warning("mobs 配置同时设置了 region 与 x/y/z，已忽略 x/y/z。");
        }

        MobDefinition direct = null;
        MobPool pool = null;
        String poolId = null;

        if (section.isSet("pool")) {
            if (section.isString("pool")) {
                String rawName = section.getString("pool", "").trim();
                if (rawName.isEmpty()) {
                    logger.warning("mobs 配置中的 pool 不能为空。");
                    return null;
                }
                poolId = rawName;
                pool = pools.get(rawName.toLowerCase(Locale.ROOT));
                if (pool == null) {
                    logger.warning("mobs 配置引用了不存在的怪物池：" + rawName);
                    return null;
                }
            } else if (section.isList("pool")) {
                poolId = "inline";
                pool = MobPool.parse("inline", section.getList("pool"), logger);
                if (pool == null) {
                    return null;
                }
            } else {
                logger.warning("mobs 配置中的 pool 必须是怪物池名称或怪物列表。");
                return null;
            }

            if (section.isSet("type") || section.isSet("mythicmob")) {
                logger.warning("mobs 配置同时设置了 pool 与 type/mythicmob，已忽略 type/mythicmob。");
            }
        } else {
            direct = MobDefinition.parse(section, logger, "mobs");
            if (direct == null) {
                return null;
            }
        }

        int amount = section.getInt("amount", 1);
        if (amount < 1) {
            amount = 1;
        }
        int delay = section.getInt("delay", 0);
        if (delay < 0) {
            delay = 0;
        }

        return new MobSpawnConfig(
                direct,
                pool,
                poolId,
                amount,
                delay,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw", 0.0),
                (float) section.getDouble("pitch", 0.0),
                section.getString("name"),
                region
        );
    }

    /**
     * 为本次生成随机抽取一种生物；直接配置时始终返回同一种。
     */
    public MobDefinition pickMob() {
        return pool != null ? pool.pick() : direct;
    }

    private static ConfigurationSection toSection(Map<?, ?> map) {
        MemoryConfiguration section = new MemoryConfiguration();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            section.set(String.valueOf(entry.getKey()), entry.getValue());
        }
        return section;
    }

    public boolean usesPool() {
        return pool != null;
    }

    public String getPoolId() {
        return poolId;
    }

    /**
     * 该点位是否使用区域随机生成。
     */
    public boolean isRegionSpawn() {
        return region != null;
    }

    public MobRegion getRegion() {
        return region;
    }

    public EntityType getEntityType() {
        return direct == null ? null : direct.getEntityType();
    }

    public String getMythicMob() {
        return direct == null ? null : direct.getMythicMob();
    }

    public boolean isMythic() {
        return direct != null && direct.isMythic();
    }

    public int getAmount() {
        return amount;
    }

    public int getDelaySeconds() {
        return delaySeconds;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public String getName() {
        return name;
    }
}
