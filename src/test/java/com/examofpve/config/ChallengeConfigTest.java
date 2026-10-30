package com.examofpve.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChallengeConfigTest {

    @Test
    void reloadDropsRemovedOverrides() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("challenges.half-health", 9.9);
        config.set("challenges.custom-challenge", 2.0);

        ChallengeConfig challenges = new ChallengeConfig();
        challenges.load(config.getConfigurationSection("challenges"));
        assertEquals(9.9, challenges.getMultiplier("half-health"), 1e-9);
        assertTrue(challenges.exists("custom-challenge"));

        // reload 后配置里删掉了覆盖项：应恢复默认倍率，而不是保留旧值
        challenges.load(null);
        assertEquals(1.4, challenges.getMultiplier("half-health"), 1e-9);
        assertFalse(challenges.exists("custom-challenge"));
    }
}
