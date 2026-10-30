package com.examofpve.listener;

import com.examofpve.ExamOfPVEPlugin;
import com.examofpve.manager.ExamManager;
import com.examofpve.session.ExamSession;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * 考核世界保护与方块变更记录：
 * <ol>
 *   <li>依据配置允许 / 禁止玩家破坏、放置、使用桶 / 骨粉 / 打火石等；</li>
 *   <li>记录所有实际发生的方块变更（玩家操作、爆炸、燃烧、液体流动、实体改方块、
 *       作物生长、雪/冰融化、树叶 decay、活塞移动等），考核结束后恢复地图原样；</li>
 *   <li>通关后的 5 秒停留窗口内禁止一切方块改动，避免这段“非考核状态”破坏地图。</li>
 * </ol>
 */
public class WorldProtectionListener implements Listener {

    private final ExamOfPVEPlugin plugin;

    public WorldProtectionListener(ExamOfPVEPlugin plugin) {
        this.plugin = plugin;
    }

    private ExamManager manager() {
        return plugin.getExamManager();
    }

    private ExamSession sessionFor(World world) {
        return world == null ? null : manager().getSessionByWorld(world.getName());
    }

    private ExamSession sessionFor(Player player, World world) {
        if (player == null || world == null) {
            return null;
        }
        ExamSession session = manager().getSession(player.getUniqueId());
        if (session == null || !session.getWorldName().equals(world.getName())) {
            return null;
        }
        return session;
    }

    /**
     * 通关后的 5 秒停留窗口：会话已结束，但地图仍需保持静态。
     */
    private boolean isFinishing(World world) {
        return world != null && manager().isWorldFinishing(world.getName());
    }

    // ================= 玩家直接改动 =================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        World world = block.getWorld();
        if (isFinishing(world)) {
            event.setCancelled(true);
            return;
        }
        ExamSession session = sessionFor(event.getPlayer(), world);
        if (session == null) {
            return;
        }
        if (!session.getExam().isAllowBreak()) {
            event.setCancelled(true);
            return;
        }
        session.recordBlockChange(block);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        World world = block.getWorld();
        if (isFinishing(world)) {
            event.setCancelled(true);
            return;
        }
        ExamSession session = sessionFor(event.getPlayer(), world);
        if (session == null) {
            return;
        }
        if (!session.getExam().isAllowPlace()) {
            event.setCancelled(true);
            return;
        }
        session.recordBlockChange(event.getBlockReplacedState());
    }

    /**
     * 床、门、双格植物等一次放置多个方块的场景。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMultiPlace(BlockMultiPlaceEvent event) {
        World world = event.getBlock().getWorld();
        if (isFinishing(world)) {
            event.setCancelled(true);
            return;
        }
        ExamSession session = sessionFor(event.getPlayer(), world);
        if (session == null) {
            return;
        }
        if (!session.getExam().isAllowPlace()) {
            event.setCancelled(true);
            return;
        }
        for (BlockState state : event.getReplacedBlockStates()) {
            if (state != null) {
                session.recordBlockChange(state);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Block block = event.getBlock();
        World world = block == null ? event.getPlayer().getWorld() : block.getWorld();
        if (isFinishing(world)) {
            event.setCancelled(true);
            return;
        }
        ExamSession session = sessionFor(event.getPlayer(), world);
        if (session == null || block == null) {
            return;
        }
        if (!session.getExam().isAllowPlace()) {
            event.setCancelled(true);
            return;
        }
        session.recordBlockChange(block);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Block block = event.getBlock();
        World world = block == null ? event.getPlayer().getWorld() : block.getWorld();
        if (isFinishing(world)) {
            event.setCancelled(true);
            return;
        }
        ExamSession session = sessionFor(event.getPlayer(), world);
        if (session == null || block == null) {
            return;
        }
        if (!session.getExam().isAllowBreak()) {
            event.setCancelled(true);
            return;
        }
        session.recordBlockChange(block);
    }

    /**
     * 锄头耕地、铲子做土径、斧头去皮/刮铜、蜜脾涂蜡、刷子刷可疑沙砾、
     * 染料/墨囊改告示牌等操作不会触发 BlockPlaceEvent，这里统一拦截并记录。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTransformTool(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        Block block = event.getClickedBlock();
        if (item == null || block == null
                || !isTransformTool(item.getType())
                || !canTransform(block.getType(), item.getType())) {
            return;
        }
        World world = block.getWorld();
        if (isFinishing(world)) {
            event.setCancelled(true);
            return;
        }
        ExamSession session = sessionFor(event.getPlayer(), world);
        if (session == null) {
            return;
        }
        if (!session.getExam().isAllowPlace()) {
            event.setCancelled(true);
            return;
        }
        session.recordBlockChange(block);
    }

    /**
     * 甜浆果等右键收获会改变方块状态。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent event) {
        Block block = event.getHarvestedBlock();
        World world = block.getWorld();
        if (isFinishing(world)) {
            event.setCancelled(true);
            return;
        }
        ExamSession session = sessionFor(event.getPlayer(), world);
        if (session == null) {
            return;
        }
        if (!session.getExam().isAllowBreak()) {
            event.setCancelled(true);
            return;
        }
        session.recordBlockChange(block);
    }

    /**
     * 告示牌文字修改不会触发方块放置事件，单独记录原始文字。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        Block block = event.getBlock();
        World world = block.getWorld();
        if (isFinishing(world)) {
            event.setCancelled(true);
            return;
        }
        ExamSession session = sessionFor(event.getPlayer(), world);
        if (session == null) {
            return;
        }
        if (!session.getExam().isAllowPlace()) {
            event.setCancelled(true);
            return;
        }
        session.recordBlockChange(block);
    }

    /**
     * 通关后的 5 秒收尾窗口内禁止打开容器，避免把物品带出即将卸载的地图。
     * 考核进行中不记录容器内容：玩家取走的物品无法无损回滚，记录并恢复会造成复制。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        if (event.getInventory().getType() == InventoryType.ENDER_CHEST) {
            return;
        }
        Location location = event.getInventory().getLocation();
        if (location == null || location.getWorld() == null) {
            return;
        }
        if (isFinishing(location.getWorld())) {
            event.setCancelled(true);
        }
    }

    // ================= 世界变化（自然 / 爆炸 / 实体） =================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        World world = event.getLocation().getWorld();
        if (isFinishing(world)) {
            event.setCancelled(true);
            return;
        }
        ExamSession session = sessionFor(world);
        if (session == null) {
            return;
        }
        for (Block block : event.blockList()) {
            session.recordBlockChange(block);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        World world = event.getBlock().getWorld();
        if (isFinishing(world)) {
            event.setCancelled(true);
            return;
        }
        ExamSession session = sessionFor(world);
        if (session == null) {
            return;
        }
        for (Block block : event.blockList()) {
            session.recordBlockChange(block);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        World world = event.getBlock().getWorld();
        if (isFinishing(world)) {
            event.setCancelled(true);
            return;
        }
        ExamSession session = sessionFor(world);
        if (session != null) {
            session.recordBlockChange(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        World world = event.getBlock().getWorld();
        if (isFinishing(world)) {
            event.setCancelled(true);
            return;
        }
        ExamSession session = sessionFor(world);
        if (session != null) {
            session.recordBlockChange(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        World world = event.getBlock().getWorld();
        if (isFinishing(world)) {
            event.setCancelled(true);
            return;
        }
        ExamSession session = sessionFor(world);
        if (session != null) {
            session.recordBlockChange(event.getToBlock());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        World world = event.getBlock().getWorld();
        if (isFinishing(world)) {
            event.setCancelled(true);
            return;
        }
        ExamSession session = sessionFor(world);
        if (session == null) {
            return;
        }
        // 玩家主动点火视为“放置”；自然火（岩浆、闪电）只记录并最终恢复
        if (event.getPlayer() != null && !session.getExam().isAllowPlace()) {
            event.setCancelled(true);
            return;
        }
        session.recordBlockChange(event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockFertilize(BlockFertilizeEvent event) {
        World world = event.getBlock().getWorld();
        if (isFinishing(world)) {
            event.setCancelled(true);
            return;
        }
        ExamSession session = sessionFor(world);
        if (session == null) {
            return;
        }
        if (event.getPlayer() != null && !session.getExam().isAllowPlace()) {
            event.setCancelled(true);
            return;
        }
        for (BlockState state : event.getBlocks()) {
            if (state != null) {
                session.recordBlockChange(state.getBlock());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        World world = event.getLocation().getWorld();
        if (isFinishing(world)) {
            event.setCancelled(true);
            return;
        }
        ExamSession session = sessionFor(world);
        if (session == null) {
            return;
        }
        if (event.getPlayer() != null && !session.getExam().isAllowPlace()) {
            event.setCancelled(true);
            return;
        }
        for (BlockState state : event.getBlocks()) {
            if (state != null) {
                session.recordBlockChange(state.getBlock());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        World world = event.getBlock().getWorld();
        if (isFinishing(world)) {
            event.setCancelled(true);
            return;
        }
        ExamSession session = sessionFor(world);
        if (session != null) {
            session.recordBlockChange(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        World world = event.getBlock().getWorld();
        if (isFinishing(world)) {
            event.setCancelled(true);
            return;
        }
        ExamSession session = sessionFor(world);
        if (session != null) {
            session.recordBlockChange(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        World world = event.getBlock().getWorld();
        if (isFinishing(world)) {
            event.setCancelled(true);
            return;
        }
        ExamSession session = sessionFor(world);
        if (session != null) {
            session.recordBlockChange(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        World world = event.getBlock().getWorld();
        if (isFinishing(world)) {
            event.setCancelled(true);
            return;
        }
        ExamSession session = sessionFor(world);
        if (session != null) {
            session.recordBlockChange(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        World world = event.getBlock().getWorld();
        if (isFinishing(world)) {
            event.setCancelled(true);
            return;
        }
        ExamSession session = sessionFor(world);
        if (session != null) {
            session.recordBlockChange(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        handlePiston(event, event.getBlocks());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        handlePiston(event, event.getBlocks());
    }

    private void handlePiston(BlockPistonEvent event, List<Block> movedBlocks) {
        World world = event.getBlock().getWorld();
        if (isFinishing(world)) {
            event.setCancelled(true);
            return;
        }
        ExamSession session = sessionFor(world);
        if (session == null) {
            return;
        }
        // 活塞会同时“破坏”和“放置”方块：两种权限都允许才放行
        if (!session.getExam().isAllowBreak() || !session.getExam().isAllowPlace()) {
            event.setCancelled(true);
            return;
        }
        BlockFace direction = event.getDirection();
        for (Block block : movedBlocks) {
            session.recordBlockChange(block);
            session.recordBlockChange(block.getRelative(direction));
        }
    }

    // ================= 工具方法 =================

    private static boolean isTransformTool(Material material) {
        String name = material.name();
        return name.endsWith("_HOE")
                || name.endsWith("_SHOVEL")
                || name.endsWith("_AXE")
                || material == Material.HONEYCOMB
                || material == Material.BRUSH
                || name.endsWith("_DYE")
                || material == Material.INK_SAC
                || material == Material.GLOW_INK_SAC;
    }

    private static boolean canTransform(Material block, Material tool) {
        String blockName = block.name();
        String toolName = tool.name();
        if (toolName.endsWith("_HOE")) {
            return isTillable(block);
        }
        if (toolName.endsWith("_SHOVEL")) {
            return isPathable(block);
        }
        if (toolName.endsWith("_AXE")) {
            return blockName.endsWith("_LOG")
                    || blockName.endsWith("_WOOD")
                    || blockName.endsWith("_STEM")
                    || blockName.endsWith("_HYPHAE")
                    || blockName.contains("COPPER");
        }
        if (tool == Material.HONEYCOMB) {
            return blockName.contains("COPPER")
                    || blockName.endsWith("_SIGN")
                    || blockName.endsWith("_HANGING_SIGN");
        }
        if (tool == Material.BRUSH) {
            return block == Material.SUSPICIOUS_SAND || block == Material.SUSPICIOUS_GRAVEL;
        }
        if (toolName.endsWith("_DYE")
                || tool == Material.INK_SAC
                || tool == Material.GLOW_INK_SAC) {
            return blockName.endsWith("_SIGN") || blockName.endsWith("_HANGING_SIGN");
        }
        return false;
    }

    private static boolean isTillable(Material material) {
        return material == Material.DIRT
                || material == Material.GRASS_BLOCK
                || material == Material.DIRT_PATH
                || material == Material.COARSE_DIRT
                || material == Material.ROOTED_DIRT;
    }

    private static boolean isPathable(Material material) {
        return material == Material.GRASS_BLOCK
                || material == Material.DIRT
                || material == Material.COARSE_DIRT
                || material == Material.ROOTED_DIRT
                || material == Material.PODZOL
                || material == Material.MYCELIUM
                || material == Material.FARMLAND
                || material == Material.DIRT_PATH;
    }
}
