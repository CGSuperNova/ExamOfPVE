package com.examofpve.listener;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import com.examofpve.ExamOfPVEPlugin;
import com.examofpve.manager.ExamManager;
import com.examofpve.session.ExamSession;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 玩家相关事件：退出 / 死亡 / 重生 / 命令拦截 / 生物死亡与消失。
 */
public class PlayerListener implements Listener {

    private final ExamOfPVEPlugin plugin;

    public PlayerListener(ExamOfPVEPlugin plugin) {
        this.plugin = plugin;
    }

    private ExamManager manager() {
        return plugin.getExamManager();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager().onQuit(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        manager().onDeath(event.getEntity());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // 等玩家完全进入服务器、其他插件的 join 逻辑执行完后再补做还原
        plugin.getServer().getScheduler().runTask(plugin, () -> manager().applyPendingRestore(player));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        manager().onRespawn(player, event);
        // 重生完成后再补做退出考核时未还原的效果（死亡状态下会跳过）
        plugin.getServer().getScheduler().runTask(plugin, () -> manager().applyPendingRestore(player));
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        manager().onEntityDeath(event.getEntity());
    }

    @EventHandler
    public void onEntityRemoveFromWorld(EntityRemoveFromWorldEvent event) {
        // 直接调用 manager 的统一处理方法
        // 注意: onEntityDespawn 方法内部需要能处理所有类型的实体移除
        manager().onEntityDespawn(event.getEntity());
    }

    /**
     * 虚空传送：考核中的玩家低于设定 Y 坐标时，传送回考核出生点。
     * 传送后重置摔落距离，避免落地时仍按虚空坠落高度结算摔落伤害。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.getLocation().getY() > plugin.getConfigManager().getVoidY()) {
            return;
        }
        ExamSession session = manager().getSession(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (!player.getWorld().getName().equals(session.getWorldName())) {
            return;
        }
        Location spawn = session.getSpawnLocation();
        if (spawn != null) {
            player.teleport(spawn);
            player.setFallDistance(0f);
        }
    }

    /**
     * 虚空伤害兜底：考核中玩家跌入虚空时取消虚空伤害并直接传送回出生点。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVoidDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ExamSession session = manager().getSession(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (!player.getWorld().getName().equals(session.getWorldName())) {
            return;
        }
        event.setCancelled(true);
        Location spawn = session.getSpawnLocation();
        if (spawn != null) {
            player.teleport(spawn);
            player.setFallDistance(0f);
        }
    }

    /**
     * 考核预备阶段：冻结玩家位移（允许转动视角），等待倒计时结束。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        ExamSession session = manager().getSession(player.getUniqueId());
        if (session == null || !session.isInPrep()) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            to.setX(from.getX());
            to.setY(from.getY());
            to.setZ(from.getZ());
            event.setTo(to);
        }
    }

    /**
     * 低血量提醒：考核中生命值（结算本次伤害后）低于配置的阈值（如 20% / 10%）时，
     * 播放音效并发送提示；回血到阈值以上后清除标记，允许再次触发。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ExamSession session = manager().getSession(player.getUniqueId());
        if (session == null) {
            return;
        }
        List<Integer> thresholds = plugin.getConfigManager().getLowHealthWarningAt();
        if (thresholds.isEmpty()) {
            return;
        }
        double max = player.getMaxHealth();
        double after = Math.max(0.0, player.getHealth() - event.getFinalDamage());
        for (int t : thresholds) {
            if (after <= max * t / 100.0) {
                if (session.tryWarnLowHealth(t)) {
                    plugin.getConfigManager().send(player, "low-health-warning",
                            Map.of("percent", String.valueOf(t)));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, t <= 10 ? 0.4f : 0.7f);
                }
            } else {
                session.clearLowHealthWarning(t);
            }
        }
    }

    /**
     * 回血到阈值以上时清除低血量提醒标记。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ExamSession session = manager().getSession(player.getUniqueId());
        if (session == null) {
            return;
        }
        List<Integer> thresholds = plugin.getConfigManager().getLowHealthWarningAt();
        if (thresholds.isEmpty()) {
            return;
        }
        double max = player.getMaxHealth();
        double after = Math.min(max, player.getHealth() + event.getAmount());
        for (int t : thresholds) {
            if (after > max * t / 100.0) {
                session.clearLowHealthWarning(t);
            }
        }
    }

    /**
     * 考核中禁止输入命令（终止命令与配置的白名单命令除外）。
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        // 通关后的 5 秒收尾窗口：会话已移除，但仍在场地内，禁止命令避免绕过方块保护
        if (manager().isPlayerFinishing(player.getUniqueId())) {
            event.setCancelled(true);
            plugin.getConfigManager().send(player, "command-blocked-finishing");
            return;
        }
        ExamSession session = manager().getSession(player.getUniqueId());
        if (session == null) {
            return;
        }

        String message = event.getMessage().trim().toLowerCase(Locale.ROOT);
        if (isAbortCommand(message) || isSettingsCommand(message)) {
            return;
        }
        if (isAllowedCommand(message)) {
            return;
        }

        event.setCancelled(true);
        plugin.getConfigManager().send(player, "command-blocked");
    }

    /**
     * 白名单命令按“命令标签”精确匹配，避免配置 "/msg" 时放行 "/msgfoo"。
     * 白名单项也可以带参数（如 "/msg hello"），此时按完整前缀匹配。
     */
    private boolean isAllowedCommand(String message) {
        String text = message.trim();
        String label = text.split("\\s+", 2)[0];
        for (String allowed : plugin.getConfigManager().getAllowedCommandsDuringExam()) {
            if (allowed == null || allowed.isBlank()) {
                continue;
            }
            String entry = allowed.trim().toLowerCase(Locale.ROOT);
            if (entry.equals(label)) {
                return true;
            }
            if (entry.indexOf(' ') >= 0
                    && (text.equals(entry) || text.startsWith(entry + " "))) {
                return true;
            }
        }
        return false;
    }

    private boolean isAbortCommand(String message) {
        String[] parts = message.split("\\s+");
        if (parts.length < 2) {
            return false;
        }
        String label = parts[0];
        if (!label.equals("/exam") && !label.equals("/examofpve") && !label.equals("/pveexam")) {
            return false;
        }
        String sub = parts[1];
        return sub.equals("abort") || sub.equals("cancel") || sub.equals("stop") || sub.equals("quit");
    }

    /**
     * 个人设置命令在考核中仍然可用（如切换剩余怪物高亮）。
     */
    private boolean isSettingsCommand(String message) {
        String[] parts = message.split("\\s+");
        if (parts.length < 2) {
            return false;
        }
        String label = parts[0];
        if (!label.equals("/exam") && !label.equals("/examofpve") && !label.equals("/pveexam")) {
            return false;
        }
        String sub = parts[1];
        return sub.equals("settings") || sub.equals("setting");
    }
}
