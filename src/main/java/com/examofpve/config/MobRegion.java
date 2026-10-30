package com.examofpve.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.logging.Logger;

/**
 * 刷怪区域（矩形，包含边界）。
 *
 * <p>支持两种配置写法：</p>
 * <pre>
 * # 映射写法
 * region:
 *   min-x: 0
 *   min-z: 0
 *   max-x: 100
 *   max-z: 100
 *   min-y: 60      # 可选；省略时从该 X/Z 的最高方块向下寻找安全地面
 *   max-y: 100
 *
 * # 字符串写法：x1,z1,x2,z2 或 x1,z1,x2,z2,min-y,max-y
 * region: "0,0,100,100"
 * </pre>
 */
public final class MobRegion {

    private final int minX;
    private final int minZ;
    private final int maxX;
    private final int maxZ;
    private final Integer minY;
    private final Integer maxY;

    public MobRegion(int x1, int z1, int x2, int z2, Integer y1, Integer y2) {
        this.minX = Math.min(x1, x2);
        this.maxX = Math.max(x1, x2);
        this.minZ = Math.min(z1, z2);
        this.maxZ = Math.max(z1, z2);
        if (y1 == null && y2 == null) {
            this.minY = null;
            this.maxY = null;
        } else {
            int first = y1 != null ? y1 : y2;
            int second = y2 != null ? y2 : y1;
            this.minY = Math.min(first, second);
            this.maxY = Math.max(first, second);
        }
    }

    /**
     * 解析映射形式的区域配置；无效时返回 null。
     */
    public static MobRegion parse(ConfigurationSection section, Logger logger) {
        if (section == null) {
            logger.warning("mobs 配置中的 region 为空。");
            return null;
        }

        Integer minX = readInt(section, "min-x", "x1");
        Integer minZ = readInt(section, "min-z", "z1");
        Integer maxX = readInt(section, "max-x", "x2");
        Integer maxZ = readInt(section, "max-z", "z2");
        if (minX == null || minZ == null || maxX == null || maxZ == null) {
            logger.warning("mobs 的 region 缺少 min-x/min-z/max-x/max-z（或 x1/z1/x2/z2）。");
            return null;
        }

        Integer y = readInt(section, "y");
        Integer minY;
        Integer maxY;
        if (y != null) {
            minY = y;
            maxY = y;
        } else {
            minY = readInt(section, "min-y", "y1");
            maxY = readInt(section, "max-y", "y2");
            if (minY == null && maxY != null) {
                minY = maxY;
            } else if (maxY == null && minY != null) {
                maxY = minY;
            }
        }

        return new MobRegion(minX, minZ, maxX, maxZ, minY, maxY);
    }

    /**
     * 解析字符串形式的区域配置；无效时返回 null。
     */
    public static MobRegion parse(String text, Logger logger) {
        if (text == null || text.isBlank()) {
            logger.warning("mobs 配置中的 region 为空。");
            return null;
        }
        String normalized = text.replace('，', ',').trim();
        String[] parts = normalized.split("[,\\s]+");
        if (parts.length != 4 && parts.length != 6) {
            logger.warning("mobs 的 region 字符串必须是 x1,z1,x2,z2 或 x1,z1,x2,z2,min-y,max-y：" + text);
            return null;
        }
        try {
            int x1 = Integer.parseInt(parts[0]);
            int z1 = Integer.parseInt(parts[1]);
            int x2 = Integer.parseInt(parts[2]);
            int z2 = Integer.parseInt(parts[3]);
            Integer y1 = parts.length == 6 ? Integer.parseInt(parts[4]) : null;
            Integer y2 = parts.length == 6 ? Integer.parseInt(parts[5]) : null;
            return new MobRegion(x1, z1, x2, z2, y1, y2);
        } catch (NumberFormatException e) {
            logger.warning("mobs 的 region 字符串包含非整数坐标：" + text);
            return null;
        }
    }

    private static Integer readInt(ConfigurationSection section, String... keys) {
        for (String key : keys) {
            if (section.isSet(key)) {
                return section.getInt(key);
            }
        }
        return null;
    }

    public int getMinX() {
        return minX;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMaxZ() {
        return maxZ;
    }

    public Integer getMinY() {
        return minY;
    }

    public Integer getMaxY() {
        return maxY;
    }

    public boolean hasYRange() {
        return minY != null && maxY != null;
    }

    @Override
    public String toString() {
        String base = minX + "," + minZ + " -> " + maxX + "," + maxZ;
        return hasYRange() ? base + " (Y " + minY + ".." + maxY + ")" : base;
    }
}
