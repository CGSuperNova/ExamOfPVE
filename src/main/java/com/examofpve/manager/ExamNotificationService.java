package com.examofpve.manager;

import com.examofpve.ExamOfPVEPlugin;
import com.examofpve.config.ConfigManager;
import com.examofpve.config.ExamConfig;
import com.examofpve.config.TimeWindow;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 考核开放 / 结束开放的全服公告。
 *
 * <p>每秒检查一次各考核的开放状态。插件启动时只记录当前状态、不补发公告，
 * 之后只在状态发生切换时公告，避免重启时刷屏。</p>
 */
public final class ExamNotificationService {

    private final ExamOfPVEPlugin plugin;
    private final Map<String, Boolean> openStates = new HashMap<>();
    private BukkitTask task;

    public ExamNotificationService(ExamOfPVEPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        for (ExamConfig exam : plugin.getConfigManager().getExams()) {
            TimeWindow window = exam.getOpenWindow();
            if (window != null) {
                openStates.put(exam.getId(), window.isOpenNow());
            }
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        openStates.clear();
    }

    private void tick() {
        ConfigManager cfg = plugin.getConfigManager();
        Set<String> current = new HashSet<>();
        for (ExamConfig exam : cfg.getExams()) {
            TimeWindow window = exam.getOpenWindow();
            if (window == null) {
                continue; // 全天开放没有“到达/结束”节点
            }
            String id = exam.getId();
            current.add(id);
            boolean open = window.isOpenNow();
            Boolean previous = openStates.put(id, open);
            if (previous != null && previous != open) {
                try {
                    announce(exam, open);
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("发送考核开放公告失败（" + id + "）：" + e.getMessage());
                }
            }
        }
        openStates.keySet().retainAll(current);
    }

    private void announce(ExamConfig exam, boolean open) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isExamNotificationEnabled()) {
            return;
        }

        String key = open ? "exam-notification-open" : "exam-notification-close";
        String message = cfg.getPrefix() + cfg.format(key, Map.of(
                "exam", exam.getDisplayName(),
                "id", exam.getId(),
                "window", exam.getOpenWindow() == null ? "" : exam.getOpenWindow().toString()
        ));
        Sound sound = open ? cfg.getNotificationOpenSound() : cfg.getNotificationCloseSound();
        boolean playSound = cfg.isNotificationSoundEnabled() && sound != null;
        float volume = cfg.getNotificationSoundVolume();
        float pitch = cfg.getNotificationSoundPitch();

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
            if (playSound) {
                try {
                    player.playSound(player.getLocation(), sound, volume, pitch);
                } catch (RuntimeException ignored) {
                    // 单个玩家音效失败不影响公告
                }
            }
        }
        Bukkit.getConsoleSender().sendMessage(message);
    }
}
