package com.examofpve.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobSpawnConfigTest {

    private static final Logger LOG = Logger.getLogger("MobSpawnConfigTest");

    static {
        // 该测试故意包含一个缺失的怪物池，会触发预期中的警告日志
        LOG.setLevel(Level.OFF);
    }

    @Test
    void resolvesNamedInlineAndMissingPools() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                mob-pools:
                  undead:
                    - type: ZOMBIE
                    - type: SKELETON
                exams:
                  test:
                    mobs:
                      - type: ZOMBIE
                        amount: 2
                        x: 1
                        y: 2
                        z: 3
                      - pool: UNDEAD
                        amount: 5
                        x: 1
                        y: 2
                        z: 3
                      - pool:
                          - type: SPIDER
                          - type: CAVE_SPIDER
                        amount: 3
                        x: 1
                        y: 2
                        z: 3
                      - pool: missing
                        amount: 1
                        x: 1
                        y: 2
                        z: 3
                """);

        MobPool undead = MobPool.parse("undead",
                config.getConfigurationSection("mob-pools").getList("undead"), LOG);
        assertNotNull(undead);
        Map<String, MobPool> pools = Map.of("undead", undead);

        List<Map<?, ?>> raw = config.getMapList("exams.test.mobs");
        assertEquals(4, raw.size());

        MobSpawnConfig direct = MobSpawnConfig.parse(toSection(raw.get(0)), LOG, pools);
        assertNotNull(direct);
        assertEquals(2, direct.getAmount());
        assertEquals("ZOMBIE", direct.pickMob().toString());

        MobSpawnConfig named = MobSpawnConfig.parse(toSection(raw.get(1)), LOG, pools);
        assertNotNull(named);
        assertTrue(named.usesPool());
        assertEquals("UNDEAD", named.getPoolId());
        assertEquals(5, named.getAmount());

        MobSpawnConfig inline = MobSpawnConfig.parse(toSection(raw.get(2)), LOG, pools);
        assertNotNull(inline);
        assertTrue(inline.usesPool());
        assertEquals("inline", inline.getPoolId());

        MobSpawnConfig missing = MobSpawnConfig.parse(toSection(raw.get(3)), LOG, pools);
        assertNull(missing);
    }

    @Test
    void parsesRegionSpawnPoint() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                mobs:
                  - type: ZOMBIE
                    amount: 4
                    region: "0,0,10,10"
                  - type: SKELETON
                    amount: 2
                    region:
                      min-x: 10
                      min-z: 10
                      max-x: 20
                      max-z: 20
                      min-y: 60
                      max-y: 80
                """);

        List<Map<?, ?>> raw = config.getMapList("mobs");
        MobSpawnConfig stringRegion = MobSpawnConfig.parse(toSection(raw.get(0)), LOG, Map.of());
        assertNotNull(stringRegion);
        assertTrue(stringRegion.isRegionSpawn());
        assertNotNull(stringRegion.getRegion());
        assertFalse(stringRegion.getRegion().hasYRange());
        assertEquals(4, stringRegion.getAmount());

        MobSpawnConfig mapRegion = MobSpawnConfig.parse(toSection(raw.get(1)), LOG, Map.of());
        assertNotNull(mapRegion);
        assertTrue(mapRegion.isRegionSpawn());
        assertTrue(mapRegion.getRegion().hasYRange());
        assertEquals(60, mapRegion.getRegion().getMinY().intValue());
        assertEquals(80, mapRegion.getRegion().getMaxY().intValue());
    }

    private static ConfigurationSection toSection(Map<?, ?> map) {
        MemoryConfiguration section = new MemoryConfiguration();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            section.set(String.valueOf(entry.getKey()), entry.getValue());
        }
        return section;
    }
}
