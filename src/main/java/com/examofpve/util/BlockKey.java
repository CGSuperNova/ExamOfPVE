package com.examofpve.util;

import org.bukkit.block.Block;

/**
 * 方块坐标键，用于记录考核期间被修改的方块。
 */
public record BlockKey(String world, int x, int y, int z) {

    public static BlockKey of(Block block) {
        return new BlockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }
}
