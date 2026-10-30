package com.examofpve.listener;

import com.examofpve.ExamOfPVEPlugin;
import com.examofpve.manager.ExamManager;
import com.examofpve.session.ExamSession;
import org.bukkit.Location;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 考核暂停：
 * <ol>
 *   <li>连按指定按键触发暂停 / 取消暂停（默认三击，按 1 次仅提示）。
 *       R/C/X/Z/V 等键位在原版协议中不会产生任何事件，服务器端无法检测，
 *       因此通过"连按次数"（settings.pause-clicks-required，默认 3）降低误触；
 *       按键通过 settings.pause-key 配置：SNEAK（潜行/Shift，默认）、SPRINT（疾跑）、SWAP（换手/F）。</li>
 *   <li>暂停期间冻结玩家（移动 / 视角 / 物品 / 回血 / 饥饿）与生物（AI 关闭）。</li>
 * </ol>
 */
public class PauseListener implements Listener {

    private final ExamOfPVEPlugin plugin;
    private final Map<UUID, PressTrack> presses = new ConcurrentHashMap<>();

    /**
     * 一次连按序列：以第一次按下时间为起点，记录已按次数。
     */
    private record PressTrack(long firstPress, int count) {
    }

    public PauseListener(ExamOfPVEPlugin plugin) {
        this.plugin = plugin;
    }

    private ExamManager manager() {
        return plugin.getExamManager();
    }

    private boolean enabled() {
        return plugin.getConfigManager().isPauseEnabled();
    }

    private boolean keyMatches(String configured) {
        String key = plugin.getConfigManager().getPauseKey();
        return key != null && key.equalsIgnoreCase(configured);
    }

    // ---------------- 按键检测 ----------------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        if (!enabled() || !keyMatches("SNEAK")) {
            return;
        }
        if (event.isSneaking()) {
            handleKeyPress(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onToggleSprint(PlayerToggleSprintEvent event) {
        if (!enabled() || !keyMatches("SPRINT")) {
            return;
        }
        if (event.isSprinting()) {
            handleKeyPress(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (isPaused(player)) {
            event.setCancelled(true);
            return;
        }
        if (!enabled() || !keyMatches("SWAP")) {
            return;
        }
        handleKeyPress(player);
    }

    private void handleKeyPress(Player player) {
        ExamSession session = manager().getSession(player.getUniqueId());
        if (session == null) {
            presses.remove(player.getUniqueId()); // 清理不再考核玩家的连按记录
            return;
        }
        long now = System.currentTimeMillis();
        int required = plugin.getConfigManager().getPauseClicksRequired();
        long window = plugin.getConfigManager().getPauseDoubleClickMs();

        PressTrack current = presses.compute(player.getUniqueId(), (k, old) -> {
            if (old == null || now - old.firstPress() > window) {
                return new PressTrack(now, 1); // 新的一次连按序列
            }
            return new PressTrack(old.firstPress(), old.count() + 1);
        });

        if (current.count() >= required) {
            presses.remove(player.getUniqueId());
            session.togglePause();
        } else {
            session.promptPause(current.count(), required);
        }
    }

    // ---------------- 暂停中冻结 ----------------

    private boolean isPaused(HumanEntity who) {
        if (!enabled()) {
            return false;
        }
        ExamSession session = manager().getSession(who.getUniqueId());
        return session != null && session.isPaused();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!isPaused(player)) {
            return;
        }
        if (hasMoved(event.getFrom(), event.getTo())) {
            event.setTo(event.getFrom()); // 冻结移动与视角
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (isPaused(event.getPlayer())) {
            event.setCancelled(true); // 冻结一切物品使用（含吃、珍珠、攻击等）
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (isPaused(event.getWhoClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        if (isPaused(event.getPlayer())) {
            event.setCancelled(true); // 禁止快捷栏切换
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDropItem(PlayerDropItemEvent event) {
        if (isPaused(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickupItem(PlayerAttemptPickupItemEvent event) {
        if (isPaused(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof Player player && isPaused(player)) {
            event.setCancelled(true); // 暂停中不回血
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && isPaused(player)) {
            event.setCancelled(true); // 暂停中饥饿值不变
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && isPaused(player)) {
            event.setCancelled(true);
        }
    }

    private boolean hasMoved(Location from, Location to) {
        return from.getX() != to.getX()
                || from.getY() != to.getY()
                || from.getZ() != to.getZ()
                || from.getYaw() != to.getYaw()
                || from.getPitch() != to.getPitch();
    }
}
