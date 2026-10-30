package com.examofpve.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeWindowTest {

    @Test
    void supportsMultipleSlotsAndMidnightCrossing() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("window", List.of(
                Map.of("start", "23:00", "end", "01:00"),
                Map.of("start", "08:00", "end", "09:00")
        ));

        TimeWindow window = TimeWindow.parse(config, "window");
        assertNotNull(window);
        assertTrue(window.isOpen(LocalTime.of(23, 30)));
        assertTrue(window.isOpen(LocalTime.of(0, 30)));
        assertTrue(window.isOpen(LocalTime.of(8, 30)));
        assertFalse(window.isOpen(LocalTime.of(12, 0)));
    }

    @Test
    void invalidWindowMeansAlwaysOpen() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("window", List.of(Map.of("start", "not-a-time", "end", "10:00")));
        assertNull(TimeWindow.parse(config, "window"));
    }
}
