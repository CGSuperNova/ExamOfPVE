package com.examofpve.config;

import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;

import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobRegionTest {

    private static final Logger LOG = Logger.getLogger("MobRegionTest");

    static {
        LOG.setLevel(Level.OFF);
    }

    @Test
    void parsesMapAndNormalizesBounds() {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("min-x", 100);
        config.set("max-x", 0);
        config.set("min-z", 50);
        config.set("max-z", -10);
        config.set("min-y", 80);
        config.set("max-y", 60);

        MobRegion region = MobRegion.parse(config, LOG);
        assertNotNull(region);
        assertEquals(0, region.getMinX());
        assertEquals(100, region.getMaxX());
        assertEquals(-10, region.getMinZ());
        assertEquals(50, region.getMaxZ());
        assertTrue(region.hasYRange());
        assertEquals(60, region.getMinY().intValue());
        assertEquals(80, region.getMaxY().intValue());
    }

    @Test
    void parsesStringWithAndWithoutYRange() {
        MobRegion flat = MobRegion.parse("0,0,100，100", LOG);
        assertNotNull(flat);
        assertEquals(100, flat.getMaxX());
        assertFalse(flat.hasYRange());

        MobRegion ranged = MobRegion.parse("0,0,100,100,60,80", LOG);
        assertNotNull(ranged);
        assertTrue(ranged.hasYRange());
        assertEquals(60, ranged.getMinY().intValue());
        assertEquals(80, ranged.getMaxY().intValue());
    }

    @Test
    void supportsCornerAliasesAndRejectsInvalidInput() {
        MemoryConfiguration config = new MemoryConfiguration();
        config.set("x1", 1);
        config.set("z1", 2);
        config.set("x2", 11);
        config.set("z2", 12);
        MobRegion region = MobRegion.parse(config, LOG);
        assertNotNull(region);
        assertEquals(1, region.getMinX());
        assertEquals(12, region.getMaxZ());

        assertNull(MobRegion.parse("not-a-region", LOG));
        assertNull(MobRegion.parse("0,0,100", LOG));
    }
}
