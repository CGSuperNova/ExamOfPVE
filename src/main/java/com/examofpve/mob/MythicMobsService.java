package com.examofpve.mob;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/**
 * MythicMobs 5.x 软依赖封装。
 * 注意：面向 io.lumine:Mythic-Dist（MythicMobs 5）的 API。
 * 若使用其它 MythicMobs 版本，请相应调整此类的 API 调用。
 */
public class MythicMobsService {

    private final MythicBukkit mythicBukkit;

    public MythicMobsService() {
        MythicBukkit inst = null;
        Plugin plugin = Bukkit.getPluginManager().getPlugin("MythicMobs");
        if (plugin != null && plugin.isEnabled()) {
            try {
                inst = MythicBukkit.inst();
            } catch (Throwable ignored) {
                inst = null;
            }
        }
        this.mythicBukkit = inst;
    }

    public boolean isAvailable() {
        return mythicBukkit != null;
    }

    /**
     * 生成 MythicMobs 生物；失败或不可用时返回 null。
     */
    public Entity spawn(String mobName, Location location) {
        if (!isAvailable()) {
            return null;
        }
        try {
            Object result = mythicBukkit.getAPIHelper().spawnMythicMob(mobName, location);
            if (result == null) {
                return null;
            }
            if (result instanceof ActiveMob) {
                return ((ActiveMob) result).getEntity().getBukkitEntity();
            }
            if (result instanceof Entity) {
                return (Entity) result;
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }
}
