package com.examofpve.listener;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChallengeListenerTest {

    @Test
    void noWeaponBlocksSwordsAxesMaceAndSpears() {
        assertTrue(ChallengeListener.isBannedWeapon(Material.WOODEN_SWORD));
        assertTrue(ChallengeListener.isBannedWeapon(Material.NETHERITE_AXE));
        assertTrue(ChallengeListener.isBannedWeapon(Material.MACE));
        assertTrue(ChallengeListener.isBannedWeapon(Material.WOODEN_SPEAR));
        assertTrue(ChallengeListener.isBannedWeapon(Material.COPPER_SPEAR));
        assertTrue(ChallengeListener.isBannedWeapon(Material.STONE_SPEAR));
        assertTrue(ChallengeListener.isBannedWeapon(Material.IRON_SPEAR));
        assertTrue(ChallengeListener.isBannedWeapon(Material.GOLDEN_SPEAR));
        assertTrue(ChallengeListener.isBannedWeapon(Material.DIAMOND_SPEAR));
        assertTrue(ChallengeListener.isBannedWeapon(Material.NETHERITE_SPEAR));
    }

    @Test
    void noWeaponLeavesNonWeaponItemsUsable() {
        assertFalse(ChallengeListener.isBannedWeapon(Material.BOW));
        assertFalse(ChallengeListener.isBannedWeapon(Material.SHIELD));
        assertFalse(ChallengeListener.isBannedWeapon(Material.AIR));
    }
}
