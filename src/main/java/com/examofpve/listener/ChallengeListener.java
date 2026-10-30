package com.examofpve.listener;

import com.examofpve.ExamOfPVEPlugin;
import com.examofpve.config.ChallengeConfig;
import com.examofpve.manager.ExamManager;
import com.examofpve.session.ExamSession;
import org.bukkit.Material;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;


/**
 * 挑战限制（考核中生效，仅在携带对应挑战的会话内拦截）：
 * 回血限制、禁用原版辅助道具、禁用武器（剑/斧/重锤/矛）、禁止穿戴护甲。
 */
public class ChallengeListener implements Listener {

    private final ExamOfPVEPlugin plugin;

    public ChallengeListener(ExamOfPVEPlugin plugin) {
        this.plugin = plugin;
    }

    private ExamManager manager() {
        return plugin.getExamManager();
    }

    // ---------------- 回血限制 ----------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ExamSession session = manager().getSession(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (session.hasChallenge(ChallengeConfig.NO_REGEN)) {
            // 禁止任意形式回血
            event.setCancelled(true);
            return;
        }
        if (session.hasChallenge(ChallengeConfig.NO_NATURAL_REGEN)) {
            // 仅禁止自然回血（饥饿/饱和度回血）
            EntityRegainHealthEvent.RegainReason reason = event.getRegainReason();
            if (reason == EntityRegainHealthEvent.RegainReason.REGEN
                    || reason == EntityRegainHealthEvent.RegainReason.SATIATED) {
                event.setCancelled(true);
            }
        }
    }

    // ---------------- 禁用原版辅助道具 ----------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ExamSession session = manager().getSession(player.getUniqueId());
        if (session != null && session.hasChallenge(ChallengeConfig.NO_VANILLA_TOOLS)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ExamSession session = manager().getSession(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }
        if (session.hasChallenge(ChallengeConfig.NO_VANILLA_TOOLS) && isBannedTool(item.getType())) {
            event.setCancelled(true);
            plugin.getConfigManager().send(player, "challenge-tool-blocked");
            return;
        }
        if (session.hasChallenge(ChallengeConfig.NO_ARMOR) && isArmor(item.getType())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof EnderPearl || event.getEntity() instanceof ThrownPotion)) {
            return;
        }
        if (!(event.getEntity().getShooter() instanceof Player player)) {
            return;
        }
        ExamSession session = manager().getSession(player.getUniqueId());
        if (session != null && session.hasChallenge(ChallengeConfig.NO_VANILLA_TOOLS)) {
            event.setCancelled(true);
        }
    }

    // ---------------- 禁用武器（剑/斧/重锤/矛） ----------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        ExamSession session = manager().getSession(player.getUniqueId());
        if (session == null || !session.hasChallenge(ChallengeConfig.NO_WEAPON)) {
            return;
        }
        if (isBannedWeapon(player.getInventory().getItemInMainHand().getType())) {
            event.setCancelled(true);
        }
    }

    // ---------------- 禁止穿戴护甲 ----------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ExamSession session = manager().getSession(player.getUniqueId());
        if (session == null || !session.hasChallenge(ChallengeConfig.NO_ARMOR)) {
            return;
        }
        if (event.getSlotType() == InventoryType.SlotType.ARMOR) {
            event.setCancelled(true);
            return;
        }
        ItemStack current = event.getCurrentItem();
        if (event.isShiftClick() && current != null && isArmor(current.getType())) {
            event.setCancelled(true);
        }
    }

    // ---------------- 工具方法 ----------------

    private boolean isBannedTool(Material material) {
        return material == Material.ENDER_PEARL
                || material == Material.POTION
                || material == Material.SPLASH_POTION
                || material == Material.LINGERING_POTION
                || material == Material.FIREWORK_ROCKET;
    }

    static boolean isBannedWeapon(Material material) {
        if (material == Material.MACE) {
            return true;
        }
        String name = material.name();
        return name.endsWith("_SWORD")
                || name.endsWith("_AXE")
                || name.endsWith("_SPEAR");
    }

    private boolean isArmor(Material material) {
        String name = material.name();
        return name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS");
    }
}
