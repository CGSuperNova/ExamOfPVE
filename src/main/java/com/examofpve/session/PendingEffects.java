package com.examofpve.session;

import org.bukkit.GameMode;
import org.bukkit.inventory.ItemStack;

/**
 * 玩家中途退出考核时尚未还原的效果快照。
 * 由于玩家退出后可能无法立即写回属性 / 装备，先持久化到 data.yml，
 * 待其下次进入服务器（或重生）时再补做还原。
 *
 * @param armor       挑战“不允许穿戴护甲”暂存的护甲；null 表示无需还原
 * @param gameMode    考核强制修改前的游戏模式；null 表示无需还原
 * @param maxHealth   挑战“生命值减半”前的最大生命值；null 表示无需还原
 * @param allowFlight 冻结期间强制开启飞行前的 allowFlight 值；null 表示无需还原
 */
public record PendingEffects(ItemStack[] armor, GameMode gameMode, Double maxHealth, Boolean allowFlight) {

    public PendingEffects {
        if (armor != null) {
            ItemStack[] copy = new ItemStack[armor.length];
            for (int i = 0; i < armor.length; i++) {
                copy[i] = armor[i] == null ? null : armor[i].clone();
            }
            armor = copy;
        }
    }

    public boolean isEmpty() {
        if (gameMode != null || maxHealth != null || allowFlight != null) {
            return false;
        }
        if (armor == null) {
            return true;
        }
        for (ItemStack item : armor) {
            if (item != null) {
                return false;
            }
        }
        return true;
    }
}
