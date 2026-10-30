package com.examofpve.util;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

/**
 * 方块快照：记录一个方块的原始状态，用于考核结束后恢复地图原样。
 * 除 BlockData 外，还会对容器物品与告示牌文字做深拷贝。
 */
public final class BlockSnapshot {

    private final Location location;
    private final BlockData blockData;
    private final ItemStack[] containerContents; // 非容器时为 null
    private final String[] signLines;            // 非告示牌时为 null

    private BlockSnapshot(Location location, BlockData blockData, ItemStack[] containerContents, String[] signLines) {
        this.location = location;
        this.blockData = blockData;
        this.containerContents = containerContents;
        this.signLines = signLines;
    }

    public static BlockSnapshot capture(Block block) {
        return capture(block.getState(false));
    }

    public static BlockSnapshot capture(BlockState state) {
        Location loc = state.getLocation().clone();
        BlockData data = state.getBlockData().clone();

        ItemStack[] contents = null;
        String[] lines = null;

        if (state instanceof Container container) {
            ItemStack[] src = container.getInventory().getContents();
            contents = new ItemStack[src.length];
            for (int i = 0; i < src.length; i++) {
                contents[i] = src[i] == null ? null : src[i].clone();
            }
        } else if (state instanceof Sign sign) {
            lines = new String[4];
            for (int i = 0; i < 4; i++) {
                lines[i] = sign.getLine(i);
            }
        }

        return new BlockSnapshot(loc, data, contents, lines);
    }

    /**
     * 将方块恢复为记录时的状态（含容器物品 / 告示牌文字）。
     */
    public void restore() {
        Block block = location.getBlock();
        block.setBlockData(blockData, false);

        if (containerContents != null) {
            BlockState state = block.getState(false);
            if (state instanceof Container container) {
                ItemStack[] copy = new ItemStack[containerContents.length];
                for (int i = 0; i < containerContents.length; i++) {
                    copy[i] = containerContents[i] == null ? null : containerContents[i].clone();
                }
                container.getInventory().setContents(copy);
                container.update(true, false);
            }
        } else if (signLines != null) {
            BlockState state = block.getState(false);
            if (state instanceof Sign sign) {
                for (int i = 0; i < signLines.length && i < 4; i++) {
                    sign.setLine(i, signLines[i]);
                }
                sign.update(true, false);
            }
        }
    }

    public Location getLocation() {
        return location;
    }
}
