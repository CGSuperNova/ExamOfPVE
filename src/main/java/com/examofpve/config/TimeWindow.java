package com.examofpve.config;

import org.bukkit.configuration.ConfigurationSection;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 现实时间开放窗口（可选），支持非连续的多个时段。
 * 未配置（或配置无效）表示全天开放。每个时段可跨午夜（如 23:00 ~ 01:00）。
 */
public class TimeWindow {

    private final List<TimeSlot> slots;

    public TimeWindow(List<TimeSlot> slots) {
        this.slots = new ArrayList<>(slots);
    }

    /**
     * 从配置解析时间窗口。支持两种格式：
     * <pre>
     * 列表（推荐，多个非连续时段）：
     *   open-window:
     *     - start: "08:00"
     *       end: "09:00"
     *     - start: "12:00"
     *       end: "13:00"
     * 旧版单时段：
     *   open-window:
     *     start: "08:00"
     *     end: "09:00"
     * </pre>
     * 无有效时段时返回 null（全天开放）。
     */
    public static TimeWindow parse(ConfigurationSection parent, String path) {
        if (parent == null || !parent.isSet(path)) {
            return null;
        }
        List<TimeSlot> slots = new ArrayList<>();
        // 列表格式
        for (Map<?, ?> m : parent.getMapList(path)) {
            TimeSlot slot = parseSlot(String.valueOf(m.get("start")), String.valueOf(m.get("end")));
            if (slot != null) {
                slots.add(slot);
            }
        }
        // 旧版单时段格式（open-window 为 map）
        if (slots.isEmpty()) {
            ConfigurationSection sec = parent.getConfigurationSection(path);
            if (sec != null) {
                TimeSlot slot = parseSlot(sec.getString("start"), sec.getString("end"));
                if (slot != null) {
                    slots.add(slot);
                }
            }
        }
        return slots.isEmpty() ? null : new TimeWindow(slots);
    }

    private static TimeSlot parseSlot(String startStr, String endStr) {
        if (startStr == null || endStr == null
                || startStr.equals("null") || endStr.equals("null")) {
            return null;
        }
        try {
            return new TimeSlot(LocalTime.parse(startStr.trim()), LocalTime.parse(endStr.trim()));
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isOpenNow() {
        return isOpen(LocalTime.now());
    }

    public boolean isOpen(LocalTime now) {
        for (TimeSlot slot : slots) {
            if (slot.isOpen(now)) {
                return true;
            }
        }
        return false;
    }

    public List<TimeSlot> getSlots() {
        return slots;
    }

    public record TimeSlot(LocalTime start, LocalTime end) {

        public boolean isOpen(LocalTime now) {
            // 起止相同视为该时段全天开放
            if (start.equals(end)) {
                return true;
            }
            if (start.isBefore(end)) {
                return !now.isBefore(start) && now.isBefore(end);
            }
            // 跨午夜
            return !now.isBefore(start) || now.isBefore(end);
        }

        @Override
        public String toString() {
            return start + "~" + end;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (TimeSlot slot : slots) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(slot);
        }
        return sb.toString();
    }
}
