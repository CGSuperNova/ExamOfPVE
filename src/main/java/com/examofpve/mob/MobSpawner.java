package com.examofpve.mob;

import com.examofpve.ExamOfPVEPlugin;
import com.examofpve.config.MobDefinition;
import com.examofpve.config.MobRegion;
import com.examofpve.config.MobSpawnConfig;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 生物生成器：支持原版生物、MythicMobs 生物、怪物池随机抽取，以及区域随机生成。
 */
public class MobSpawner {

    /** 在满足“2 格间距”的前提下最多尝试的随机位置数量。 */
    private static final int SPACING_ATTEMPTS = 300;
    /** 间距限制导致找不到位置时的兜底尝试次数（区域被认为已填满）。 */
    private static final int FALLBACK_ATTEMPTS = 100;
    /** 区域随机点的最小间距（格）。 */
    private static final double MIN_SPACING = 2.0;

    private final ExamOfPVEPlugin plugin;
    private final MythicMobsService mythicMobsService;

    public MobSpawner(ExamOfPVEPlugin plugin) {
        this.plugin = plugin;
        this.mythicMobsService = new MythicMobsService();
    }

    public MythicMobsService getMythicMobsService() {
        return mythicMobsService;
    }

    /**
     * 按配置生成一只生物；若点位使用怪物池则随机抽取一种。
     * 区域点位会临时随机一个位置（不维护跨次间距，正式刷怪请用带 location 的重载）。
     * 生成失败返回 null（调用方应豁免该目标，避免无法通关）。
     */
    public Entity spawn(MobSpawnConfig cfg, World world) {
        MobDefinition definition = cfg.pickMob();
        Location location = cfg.isRegionSpawn()
                ? pickRegionLocation(cfg, world, List.of())
                : pointLocation(cfg, world);
        return spawn(definition, cfg, world, location);
    }

    /**
     * 按指定生物定义与固定点位生成。
     */
    public Entity spawn(MobDefinition definition, MobSpawnConfig cfg, World world) {
        Location location = cfg.isRegionSpawn()
                ? pickRegionLocation(cfg, world, List.of())
                : pointLocation(cfg, world);
        return spawn(definition, cfg, world, location);
    }

    /**
     * 按指定生物定义与位置生成。location 为 null 时：固定点位回退到配置坐标，区域点位生成失败。
     */
    public Entity spawn(MobDefinition definition, MobSpawnConfig cfg, World world, Location location) {
        if (definition == null) {
            plugin.getLogger().warning("生物定义为空，无法生成（点位："
                    + (cfg.getPoolId() == null ? "直接配置" : "怪物池 " + cfg.getPoolId()) + "）。");
            return null;
        }
        Location actualLocation = location;
        if (actualLocation == null) {
            if (cfg.isRegionSpawn()) {
                return null;
            }
            actualLocation = pointLocation(cfg, world);
        }
        if (actualLocation.getWorld() == null) {
            return null;
        }
        World targetWorld = actualLocation.getWorld();

        Entity entity;
        try {
            if (definition.isMythic()) {
                entity = mythicMobsService.spawn(definition.getMythicMob(), actualLocation);
                if (entity == null) {
                    plugin.getLogger().warning("MythicMobs 生物 '" + definition.getMythicMob()
                            + "' 生成失败（请检查名称，或确认 MythicMobs 已安装）。");
                }
            } else {
                entity = targetWorld.spawnEntity(actualLocation, definition.getEntityType());
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("生成生物失败：" + definition + "（" + e.getMessage() + "）");
            return null;
        }

        String displayName = definition.getName();
        if (displayName == null || displayName.isBlank()) {
            displayName = cfg.getName();
        }
        if (entity instanceof LivingEntity living && displayName != null && !displayName.isBlank()) {
            living.setCustomName(ChatColor.translateAlternateColorCodes('&', displayName));
            living.setCustomNameVisible(true);
        }
        return entity;
    }

    /**
     * 在区域中挑选一个安全位置。
     * 先尝试满足“与已生成位置至少 2 格距离”；多次失败后（可视为区域已被填满）放宽间距限制。
     *
     * @param existing 本次考核中该点位已经使用过的位置
     */
    public Location pickRegionLocation(MobSpawnConfig cfg, World world, List<Location> existing) {
        MobRegion region = cfg.getRegion();
        if (region == null || world == null) {
            return null;
        }
        List<Location> occupied = existing == null ? List.of() : existing;

        for (int i = 0; i < SPACING_ATTEMPTS; i++) {
            Location candidate = randomRegionLocation(region, world);
            if (candidate != null && hasEnoughSpacing(candidate, occupied)) {
                return candidate;
            }
        }
        // 区域可以视为已填满：允许放宽 2 格间距，但仍不得生成在方块内部
        for (int i = 0; i < FALLBACK_ATTEMPTS; i++) {
            Location candidate = randomRegionLocation(region, world);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 位置是否可以生成生物：脚部与头部不在固体/液体/火中，且脚下为固体方块。
     */
    public boolean isSpawnable(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        World world = location.getWorld();
        if (!world.getWorldBorder().isInside(location)) {
            return false;
        }
        Block feet = location.getBlock();
        Block head = feet.getRelative(BlockFace.UP);
        Block ground = feet.getRelative(BlockFace.DOWN);
        return isOpen(feet) && isOpen(head) && ground.getType().isSolid();
    }

    private static boolean isOpen(Block block) {
        Material type = block.getType();
        return !type.isSolid()
                && !block.isLiquid()
                && type != Material.FIRE
                && type != Material.SOUL_FIRE
                && type != Material.POWDER_SNOW;
    }

    private Location randomRegionLocation(MobRegion region, World world) {
        int x = ThreadLocalRandom.current().nextInt(region.getMinX(), region.getMaxX() + 1);
        int z = ThreadLocalRandom.current().nextInt(region.getMinZ(), region.getMaxZ() + 1);

        if (region.hasYRange()) {
            int y = ThreadLocalRandom.current().nextInt(region.getMinY(), region.getMaxY() + 1);
            Location candidate = new Location(world, x + 0.5, y, z + 0.5);
            return isSpawnable(candidate) ? candidate : null;
        }
        return surfaceLocation(world, x, z);
    }

    /**
     * 未配置 Y 范围时：从该 X/Z 的最高方块向下找第一块固体地面，在其上方生成。
     */
    private Location surfaceLocation(World world, int x, int z) {
        int minY = world.getMinHeight();
        int top = Math.min(world.getHighestBlockYAt(x, z), world.getMaxHeight() - 2);
        for (int y = top; y >= minY; y--) {
            Block ground = world.getBlockAt(x, y, z);
            if (!ground.getType().isSolid()) {
                continue;
            }
            Location candidate = new Location(world, x + 0.5, y + 1, z + 0.5);
            return isSpawnable(candidate) ? candidate : null;
        }
        return null;
    }

    private static boolean hasEnoughSpacing(Location candidate, List<Location> occupied) {
        for (Location point : occupied) {
            if (point == null || point.getWorld() == null) {
                continue;
            }
            if (point.getWorld().equals(candidate.getWorld())
                    && point.distanceSquared(candidate) < MIN_SPACING * MIN_SPACING) {
                return false;
            }
        }
        return true;
    }

    private static Location pointLocation(MobSpawnConfig cfg, World world) {
        return new Location(world, cfg.getX(), cfg.getY(), cfg.getZ(),
                cfg.getYaw(), cfg.getPitch());
    }
}
