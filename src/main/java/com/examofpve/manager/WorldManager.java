package com.examofpve.manager;

import com.examofpve.ExamOfPVEPlugin;
import com.examofpve.config.ExamConfig;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;
import org.mvplugins.multiverse.core.MultiverseCoreApi;
import org.mvplugins.multiverse.core.world.options.UnloadWorldOptions;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 考核世界（地图）管理：基于 Multiverse-Core 5.x 官方 API（MultiverseCoreApi）加载 / 卸载世界，
 * 并从每个考核配置的世界列表中挑选空闲副本，支持多玩家同时考核。
 *
 * <p>Multiverse 5 说明：
 * <ul>
 *   <li>入口为 {@link MultiverseCoreApi}，通过 {@code MultiverseCoreApi.get()} 获取实例；</li>
 *   <li>结果类型使用 vavr 的 Option（可选）与 Multiverse 自有的 Attempt&lt;T, F&gt;（成功/失败），
 *       不要再用旧版的 if (x != null) 判断；</li>
 *   <li>所有世界操作必须在服务器主线程执行。</li>
 * </ul>
 */
public class WorldManager {

    /**
     * Multiverse-Core 5.x 的 WorldManager 接口（与本类同名，故使用全限定名）。
     * Multiverse 未安装时为 null（软依赖，功能自动禁用）。
     */
    private final org.mvplugins.multiverse.core.world.WorldManager mvWorldManager;
    private final ExamOfPVEPlugin plugin;
    private final Set<String> occupied = ConcurrentHashMap.newKeySet();

    public WorldManager(ExamOfPVEPlugin plugin) {
        this.plugin = plugin;
        org.mvplugins.multiverse.core.world.WorldManager wm = null;
        try {
            if (MultiverseCoreApi.isLoaded()) {
                wm = MultiverseCoreApi.get().getWorldManager();
            }
        } catch (Throwable t) {
            // Multiverse 缺失或版本过旧：保持不可用
            wm = null;
        }
        this.mvWorldManager = wm;
    }

    public boolean isAvailable() {
        return mvWorldManager != null;
    }

    /**
     * 从考核的世界列表中挑选一个空闲世界并标记占用；没有空闲返回 null。
     */
    public synchronized String acquireWorld(ExamConfig exam) {
        for (String name : exam.getWorlds()) {
            if (!occupied.contains(name)) {
                occupied.add(name);
                return name;
            }
        }
        return null;
    }

    /**
     * 加载世界（必须在主线程调用）。已加载则立即回调 true；
     * 否则调用 Multiverse 加载（返回 Attempt 结果）并轮询等待 Bukkit 世界就绪。
     * 回调保证在服务器主线程执行。
     */
    public void loadWorld(String name, Consumer<Boolean> callback) {
        if (!isAvailable()) {
            callback.accept(Bukkit.getWorld(name) != null);
            return;
        }

        if (Bukkit.getWorld(name) != null) {
            callback.accept(true);
            return;
        }

        // loadWorld(String) 在 Multiverse 5.8.1 中仍可用（已标记 deprecated，后续可改用 LoadWorldOptions）
        var attempt = mvWorldManager.loadWorld(name);
        if (!attempt.isSuccess()) {
            plugin.getLogger().warning("Multiverse 加载世界失败：" + name
                    + "（原因：" + attempt.getFailureReason() + "）");
            callback.accept(false);
            return;
        }

        // 轮询等待 Bukkit 世界真正加载完成
        new BukkitRunnable() {
            int tries = 0;

            @Override
            public void run() {
                World w = Bukkit.getWorld(name);
                if (w != null) {
                    callback.accept(true);
                    cancel();
                    return;
                }
                if (++tries >= 200) { // 约 20 秒超时
                    plugin.getLogger().warning("加载世界超时：" + name);
                    callback.accept(false);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    /**
     * 释放世界（必须在主线程调用）：取消占用标记；若无其他会话占用则通过 Multiverse 卸载。
     */
    public void releaseWorld(String name) {
        occupied.remove(name);
        if (!isAvailable()) {
            return;
        }
        if (Bukkit.getWorld(name) == null || occupied.contains(name)) {
            return;
        }

        var loadedWorld = mvWorldManager.getLoadedWorld(name);
        if (!loadedWorld.isDefined()) {
            plugin.getLogger().warning("未找到 Multiverse 世界：" + name);
            return;
        }

        UnloadWorldOptions options = UnloadWorldOptions.world(loadedWorld.get())
                .unloadBukkitWorld(true)
                .saveBukkitWorld(true);
        var result = mvWorldManager.unloadWorld(options);
        if (!result.isSuccess()) {
            plugin.getLogger().warning("Multiverse 卸载世界失败：" + name
                    + "（原因：" + result.getFailureReason() + "）");
        }
    }

    /**
     * 服务器关闭时卸载所有被占用的世界（尽力而为，主线程）。
     */
    public void releaseAll() {
        if (!isAvailable()) {
            occupied.clear();
            return;
        }
        for (String name : occupied) {
            releaseWorld(name);
        }
        occupied.clear();
    }
}
