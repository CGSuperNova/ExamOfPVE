package com.examofpve.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobPoolTest {

    private static final Logger LOG = Logger.getLogger("MobPoolTest");

    @Test
    void parsesEntriesAndWeights() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                mob-pools:
                  undead:
                    - type: ZOMBIE
                      weight: 3
                    - type: SKELETON
                      weight: 2
                      name: "&7Skeleton"
                    - mythicmob: SkeletonKing
                      weight: 1
                """);

        ConfigurationSection section = config.getConfigurationSection("mob-pools");
        assertNotNull(section);
        List<?> raw = section.getList("undead");
        assertNotNull(raw);
        MobPool pool = MobPool.parse("undead", raw, LOG);
        assertNotNull(pool);
        assertEquals(3, pool.getEntries().size());
        assertEquals(3.0, pool.getEntries().get(0).getWeight(), 1e-9);
        assertTrue(pool.getEntries().get(2).isMythic());
        assertEquals("SkeletonKing", pool.getEntries().get(2).getMythicMob());
    }

    @Test
    void picksByWeight() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                mob-pools:
                  weighted:
                    - type: ZOMBIE
                      weight: 3
                    - type: SKELETON
                      weight: 2
                    - type: CREEPER
                      weight: 1
                """);
        ConfigurationSection section = config.getConfigurationSection("mob-pools");
        MobPool pool = MobPool.parse("weighted", section.getList("weighted"), LOG);
        assertNotNull(pool);

        Map<String, Integer> counts = new LinkedHashMap<>();
        int samples = 60_000;
        for (int i = 0; i < samples; i++) {
            counts.merge(pool.pick().toString(), 1, Integer::sum);
        }
        assertEquals(0.5, counts.getOrDefault("ZOMBIE", 0) / (double) samples, 0.02);
        assertEquals(2.0 / 6.0, counts.getOrDefault("SKELETON", 0) / (double) samples, 0.02);
        assertEquals(1.0 / 6.0, counts.getOrDefault("CREEPER", 0) / (double) samples, 0.02);
    }

    @Test
    void supportsStringShorthand() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                mob-pools:
                  shorthand:
                    - "SPIDER"
                    - "mythic:ZombieLord"
                """);
        ConfigurationSection section = config.getConfigurationSection("mob-pools");
        MobPool pool = MobPool.parse("shorthand", section.getList("shorthand"), LOG);
        assertNotNull(pool);
        assertEquals(2, pool.getEntries().size());
        assertEquals("SPIDER", pool.getEntries().get(0).toString());
        assertTrue(pool.getEntries().get(1).isMythic());
        assertEquals("ZombieLord", pool.getEntries().get(1).getMythicMob());
    }
}
