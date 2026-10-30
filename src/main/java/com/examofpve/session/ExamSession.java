package com.examofpve.session;

import com.examofpve.ExamOfPVEPlugin;
import com.examofpve.config.ChallengeConfig;
import com.examofpve.config.ExamConfig;
import com.examofpve.config.MobDefinition;
import com.examofpve.config.MobSpawnConfig;
import com.examofpve.util.BlockKey;
import com.examofpve.util.BlockSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 一次进行中的考核会话（每名玩家至多一个）。
 * 负责记录状态：生成物追踪、方块快照、BossBar、定时任务、挑战效果等。
 */
public class ExamSession {

    private final ExamOfPVEPlugin plugin;
    private final Player player;
    private final UUID playerId;
    private final ExamConfig exam;
    private final String worldName;
    private final Location returnLocation;

    // ---- 挑战相关 ----
    private final Set<String> challenges = new HashSet<>();
    private final double multiplier;        // 所有挑战倍率相乘后的最终倍率
    private final int challengeCount;       // 携带的挑战数量
    private final int timeLimitSeconds;     // 应用 half-time 后的实际限时
    private final int totalToKill;          // 应用 double-mobs 后的总目标数
    private long startTimeMillis;           // 考核正式开始时间（预备阶段结束后）

    private final AtomicInteger pendingKills;
    private final Set<UUID> aliveMobs = ConcurrentHashMap.newKeySet();
    private final Map<UUID, MobSpawnConfig> entityConfigs = new ConcurrentHashMap<>();
    private final Map<UUID, MobDefinition> entityDefinitions = new ConcurrentHashMap<>();
    private final Map<UUID, Location> entitySpawnLocations = new ConcurrentHashMap<>();
    /** 区域刷怪：每个区域点位已经使用过的位置（用于 2 格间距约束）。 */
    private final Map<MobSpawnConfig, List<Location>> regionSpawnPoints = new ConcurrentHashMap<>();
    private final Map<BlockKey, BlockSnapshot> snapshots = new ConcurrentHashMap<>();
    private final BossBar bossBar;
    private final List<BukkitTask> tasks = new CopyOnWriteArrayList<>();
    private final AtomicBoolean finished = new AtomicBoolean(false);
    private final AtomicBoolean deathHandled = new AtomicBoolean(false);

    // ---- 挑战效果状态 ----
    private Location spawnLocation;         // 考核出生点（虚空传送用）
    private double originalMaxHealth = -1.0;
    private ItemStack[] storedArmor;
    private boolean challengeEffectsApplied = false;

    private GameMode previousGameMode;
    private boolean gameModeChanged = false;

    // ---- 考核暂停状态 ----
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicInteger pauseCountUsed = new AtomicInteger(0);
    private BukkitTask pauseTask;
    private int pauseRemaining = 0;
    private long lastPromptTime = 0;
    private long pauseStartedAt = 0;      // 当前暂停开始时间（0 = 未暂停）
    private long pausedTotalMillis = 0;   // 本场累计暂停时长，用于扣除在通关用时外

    // ---- 考核预备阶段（入场后正式计时前的准备时间） ----
    private final AtomicBoolean prepPhase = new AtomicBoolean(false);

    // ---- 冻结期间防飞行踢出（暂停/预备时若玩家在空中，临时开启飞行悬浮） ----
    private boolean flightForced = false;
    private boolean allowFlightBackup = false;

    // ---- 低血量提醒状态（已提醒过的阈值，回血后清除） ----
    private final Set<Integer> lowHealthWarned = ConcurrentHashMap.newKeySet();

    // ---- 参加费用（是否实际扣除过，用于加载失败时仅退还已扣费用） ----
    private boolean feePaid = false;

    public ExamSession(ExamOfPVEPlugin plugin, Player player, ExamConfig exam, String worldName,
                       Set<String> challenges, double multiplier, int timeLimitSeconds, int totalToKill) {
        this.plugin = plugin;
        this.player = player;
        this.playerId = player.getUniqueId();
        this.exam = exam;
        this.worldName = worldName;
        this.returnLocation = player.getLocation().clone();
        this.challenges.addAll(challenges);
        this.multiplier = multiplier;
        this.challengeCount = challenges.size();
        this.timeLimitSeconds = timeLimitSeconds;
        this.totalToKill = totalToKill;
        this.pendingKills = new AtomicInteger(totalToKill);
        this.bossBar = Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SOLID);
        this.bossBar.setVisible(true);
    }

    // ---------------- 生命周期 ----------------

    public void addTask(BukkitTask task) {
        tasks.add(task);
    }

    public void cancelTasks() {
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
    }

    public boolean isFinished() {
        return finished.get();
    }

    public boolean markFinished() {
        return finished.compareAndSet(false, true);
    }

    /**
     * 标记考核正式开始（预备阶段结束）。通关用时不包含地图加载与预备阶段。
     */
    public void markStarted() {
        this.startTimeMillis = System.currentTimeMillis();
    }

    public boolean isDeathHandled() {
        return deathHandled.get();
    }

    public void markDeathHandled() {
        deathHandled.set(true);
    }

    // ---------------- 挑战效果 ----------------

    public boolean hasChallenge(String id) {
        return challenges.contains(id);
    }

    public Set<String> getChallenges() {
        return challenges;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public int getChallengeCount() {
        return challengeCount;
    }

    public int getTimeLimitSeconds() {
        return timeLimitSeconds;
    }

    public long getStartTimeMillis() {
        return startTimeMillis;
    }

    /**
     * 本场已用时间（秒），不包含地图加载、预备阶段与暂停时长。
     * 未正式开始时返回 0。
     */
    public double getElapsedSeconds() {
        if (startTimeMillis <= 0) {
            return 0.0;
        }
        long now = System.currentTimeMillis();
        long pausedMillis = pausedTotalMillis;
        if (paused.get() && pauseStartedAt > 0) {
            pausedMillis += Math.max(0, now - pauseStartedAt);
        }
        return Math.max(0, now - startTimeMillis - pausedMillis) / 1000.0;
    }

    public Location getSpawnLocation() {
        return spawnLocation;
    }

    public void setSpawnLocation(Location spawnLocation) {
        this.spawnLocation = spawnLocation == null ? null : spawnLocation.clone();
    }

    /**
     * 进入考核后应用挑战效果（在传送至考核世界后调用）：
     * 生命值减半、脱下护甲。
     */
    public void applyChallenges() {
        if (challengeEffectsApplied || !player.isOnline()) {
            return;
        }
        if (hasChallenge(ChallengeConfig.HALF_HEALTH)) {
            originalMaxHealth = player.getMaxHealth();
            player.setMaxHealth(Math.max(1.0, originalMaxHealth / 2));
            player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
        }
        if (hasChallenge(ChallengeConfig.NO_ARMOR)) {
            storedArmor = player.getInventory().getArmorContents();
            player.getInventory().setArmorContents(new ItemStack[4]);
        }
        challengeEffectsApplied = true;
    }

    /**
     * 考核结束时恢复挑战带来的属性变化（生命上限、护甲）。
     */
    public void restoreChallenges() {
        if (!challengeEffectsApplied) {
            return;
        }
        if (originalMaxHealth > 0 && player.isOnline()) {
            try {
                player.setMaxHealth(originalMaxHealth);
                if (player.getHealth() > originalMaxHealth) {
                    player.setHealth(originalMaxHealth);
                }
            } catch (RuntimeException e) {
                plugin.getLogger().warning("恢复生命上限失败：" + e.getMessage());
            }
        }
        // 玩家退出时也尝试写回：退出事件发生在玩家数据存档之前，通常能生效；
        // 若离线状态下无法写回，会在退出时生成 PendingEffects，下次进入服务器时补做。
        if (storedArmor != null) {
            try {
                player.getInventory().setArmorContents(storedArmor);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("恢复护甲失败：" + e.getMessage());
            }
        }
        challengeEffectsApplied = false;
    }

    // ---------------- 游戏模式 ----------------

    public void applyGameMode() {
        GameMode gm = exam.getGameMode();
        if (gm != null && player.isOnline() && player.getGameMode() != gm) {
            previousGameMode = player.getGameMode();
            gameModeChanged = true;
            player.setGameMode(gm);
        }
    }

    public void restoreGameMode() {
        if (gameModeChanged) {
            try {
                player.setGameMode(previousGameMode);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("恢复游戏模式失败：" + e.getMessage());
            }
            gameModeChanged = false;
        }
    }

    /**
     * 生成“退出时尚未还原的效果”快照，供 DataManager 持久化并在下次进入时补做。
     */
    public PendingEffects snapshotPendingEffects() {
        ItemStack[] armor = null;
        if (storedArmor != null) {
            armor = new ItemStack[storedArmor.length];
            for (int i = 0; i < storedArmor.length; i++) {
                armor[i] = storedArmor[i] == null ? null : storedArmor[i].clone();
            }
        }
        return new PendingEffects(
                armor,
                gameModeChanged ? previousGameMode : null,
                originalMaxHealth > 0 ? originalMaxHealth : null,
                flightForced ? allowFlightBackup : null
        );
    }

    /**
     * 清理暂停 / 预备 / 强制飞行等临时状态（考核结束时调用）。
     * 必须在 snapshotPendingEffects 之后调用，否则强制飞行状态已被清除，无法写入补偿记录。
     */
    public void clearTransientState() {
        if (paused.getAndSet(false)) {
            accumulatePauseTime();
            if (pauseTask != null) {
                pauseTask.cancel();
                pauseTask = null;
            }
        }
        prepPhase.set(false);
        unfreezePlayer();
    }

    // ---------------- 方块记录 / 恢复 ----------------

    public void recordBlockChange(Block block) {
        recordBlockChange(block.getState(false));
    }

    public void recordBlockChange(BlockState state) {
        BlockKey key = new BlockKey(state.getWorld().getName(), state.getX(), state.getY(), state.getZ());
        // 只保留方块的最初状态
        snapshots.computeIfAbsent(key, k -> BlockSnapshot.capture(state));
    }

    public void restoreBlocks() {
        for (BlockSnapshot snapshot : snapshots.values()) {
            try {
                if (snapshot.getLocation().getWorld() != null) {
                    snapshot.restore();
                }
            } catch (Exception ignored) {
                // 忽略单个方块恢复异常
            }
        }
        snapshots.clear();
    }

    // ---------------- 生物追踪 ----------------

    public void registerEntity(UUID uuid) {
        aliveMobs.add(uuid);
    }

    public void putEntityConfig(UUID uuid, MobSpawnConfig cfg) {
        entityConfigs.put(uuid, cfg);
    }

    public MobSpawnConfig removeEntityConfig(UUID uuid) {
        return entityConfigs.remove(uuid);
    }

    /**
     * 记录某只生成物实际抽取到的生物定义（怪物池需要，用于消失后原样重生）。
     */
    public void putEntityDefinition(UUID uuid, MobDefinition definition) {
        entityDefinitions.put(uuid, definition);
    }

    public MobDefinition removeEntityDefinition(UUID uuid) {
        return entityDefinitions.remove(uuid);
    }

    public void onEntityKilled(UUID uuid) {
        aliveMobs.remove(uuid);
        entityConfigs.remove(uuid);
        entityDefinitions.remove(uuid);
        entitySpawnLocations.remove(uuid);
        pendingKills.decrementAndGet();
        updateMobGlow();
    }

    public void onEntityDespawned(UUID uuid) {
        aliveMobs.remove(uuid);
        entityDefinitions.remove(uuid);
        entitySpawnLocations.remove(uuid);
    }

    /**
     * 记录某只生物实际生成的位置（区域刷怪用于消失后原位置重生）。
     */
    public void putEntitySpawnLocation(UUID uuid, Location location) {
        if (location != null) {
            entitySpawnLocations.put(uuid, location.clone());
        }
    }

    public Location removeEntitySpawnLocation(UUID uuid) {
        return entitySpawnLocations.remove(uuid);
    }

    /**
     * 某区域点位已经使用过的生成位置（供间距约束读取）。
     */
    public List<Location> getRegionSpawnPoints(MobSpawnConfig cfg) {
        return regionSpawnPoints.computeIfAbsent(cfg, k -> new ArrayList<>());
    }

    public void addRegionSpawnPoint(MobSpawnConfig cfg, Location location) {
        if (location != null) {
            getRegionSpawnPoints(cfg).add(location.clone());
        }
    }

    public void removeRegionSpawnPoint(MobSpawnConfig cfg, Location location) {
        if (location == null) {
            return;
        }
        List<Location> points = regionSpawnPoints.get(cfg);
        if (points != null) {
            points.remove(location);
        }
    }

    /**
     * 生物生成失败时豁免该目标，避免玩家无法通关。
     */
    public void waiveKill() {
        pendingKills.decrementAndGet();
        updateMobGlow();
    }

    public int getPendingKills() {
        return pendingKills.get();
    }

    public Set<UUID> getAliveMobs() {
        return aliveMobs;
    }

    public void clearEntityConfigs() {
        entityConfigs.clear();
    }

    public void clearEntityDefinitions() {
        entityDefinitions.clear();
    }

    /**
     * 清理区域刷怪点位与实体生成位置记录（考核结束时调用）。
     */
    public void clearSpawnTracking() {
        entitySpawnLocations.clear();
        regionSpawnPoints.clear();
    }

    // ---------------- 剩余怪物高亮 ----------------

    /**
     * 玩家是否开启“剩余怪物高亮”个人设置（默认开启）。
     */
    public boolean isMobGlowEnabled() {
        return plugin.getDataManager().isMobGlowEnabled(playerId);
    }

    /**
     * 剩余目标数不超过总数的 20% 时启用高亮；未生成的目标也计入剩余数。
     */
    public boolean shouldGlowMobs() {
        return isMobGlowEnabled()
                && totalToKill > 0
                && pendingKills.get() <= totalToKill * 0.2;
    }

    /**
     * 根据当前剩余目标数刷新所有存活生物的发光状态（也用于玩家切换个人设置时）。
     */
    public void updateMobGlow() {
        boolean glow = shouldGlowMobs();
        for (UUID uuid : aliveMobs) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity == null) {
                continue;
            }
            try {
                if (entity.isGlowing() != glow) {
                    entity.setGlowing(glow);
                }
            } catch (RuntimeException ignored) {
                // 单个实体不支持发光时不影响其他怪物与考核流程
            }
        }
    }

    /**
     * 新生成 / 重生一只生物时同步高亮状态（此时可能已经进入 20% 阈值）。
     */
    public void applyMobGlow(Entity entity) {
        if (entity == null || !shouldGlowMobs()) {
            return;
        }
        try {
            entity.setGlowing(true);
        } catch (RuntimeException ignored) {
            // 忽略单个实体异常
        }
    }

    // ---------------- BossBar ----------------

    public void updateBossBar(int secondsLeft) {
        int killed = totalToKill - pendingKills.get();
        String title = plugin.getConfigManager().format("bossbar-title", Map.of(
                "exam", exam.getDisplayName(),
                "kills", String.valueOf(killed),
                "total", String.valueOf(totalToKill),
                "time", String.valueOf(Math.max(0, secondsLeft))
        ));
        bossBar.setTitle(title);
        double progress = totalToKill <= 0
                ? 1.0
                : Math.max(0.0, Math.min(1.0, (double) killed / totalToKill));
        bossBar.setProgress(progress);
    }

    public BossBar getBossBar() {
        return bossBar;
    }

    // ---------------- getters ----------------

    public Player getPlayer() {
        return player;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public ExamConfig getExam() {
        return exam;
    }

    public String getWorldName() {
        return worldName;
    }

    public Location getReturnLocation() {
        return returnLocation;
    }

    public int getTotalToKill() {
        return totalToKill;
    }

    // ---------------- 考核暂停 ----------------

    public boolean isPaused() {
        return paused.get();
    }

    /**
     * 本场已使用的暂停次数（含提前恢复）。
     */
    public int getPauseCountUsed() {
        return pauseCountUsed.get();
    }

    public boolean canPause() {
        return !finished.get() && !paused.get() && !deathHandled.get()
                && !prepPhase.get() // 预备阶段不可暂停
                && spawnLocation != null // 世界加载完成前不可暂停
                && pauseCountUsed.get() < plugin.getConfigManager().getMaxPauses();
    }

    /**
     * 单击按键时的提示（带冷却，避免刷屏）。
     *
     * @param clicks   本次连按序列中已按下的次数
     * @param required 触发暂停所需的总次数
     */
    public void promptPause(int clicks, int required) {
        if (!plugin.getConfigManager().isPauseEnabled() || !canPause()) {
            return;
        }
        if (clicks == 1) {
            long now = System.currentTimeMillis();
            if (now - lastPromptTime < 3000) {
                return;
            }
            lastPromptTime = now;
        }
        plugin.getConfigManager().send(player, "exam-pause-prompt", Map.of(
                "key", pauseKeyName(),
                "clicks", String.valueOf(Math.max(0, required - clicks)),
                "left", String.valueOf(plugin.getConfigManager().getMaxPauses() - pauseCountUsed.get()),
                "seconds", String.valueOf(plugin.getConfigManager().getPauseDurationSeconds())
        ));
    }

    /**
     * 连按按键达到所需次数：暂停 / 取消暂停。
     */
    public void togglePause() {
        if (!plugin.getConfigManager().isPauseEnabled() || finished.get()
                || player == null || !player.isOnline()) {
            return;
        }
        if (paused.get()) {
            endPause();
            return;
        }
        if (prepPhase.get()) {
            return; // 预备阶段不可暂停
        }
        if (pauseCountUsed.get() >= plugin.getConfigManager().getMaxPauses()) {
            plugin.getConfigManager().send(player, "exam-pause-limit");
            return;
        }
        startPause();
    }

    private void startPause() {
        paused.set(true);
        pauseStartedAt = System.currentTimeMillis();
        pauseCountUsed.incrementAndGet();
        pauseRemaining = plugin.getConfigManager().getPauseDurationSeconds();

        // 冻结玩家（若在空中则临时开启飞行，避免被服务器判定为飞行踢出）
        freezePlayer();

        // 冻结生物：关闭 AI（不移动、不攻击）。
        // 注意：MythicMobs 基于 OnTimer 等非 AI 触发的技能无法通过 setAI(false) 完全阻止。
        freezeMobs(true);

        updatePauseBossBar();
        int required = plugin.getConfigManager().getPauseClicksRequired();
        pauseTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!paused.get()) {
                return;
            }
            pauseRemaining--;
            updatePauseBossBar();
            // 暂停即将结束提醒（音效 + 提示）
            if (pauseRemaining > 0 && pauseRemaining == plugin.getConfigManager().getPauseWarningAt()) {
                plugin.getConfigManager().send(player, "pause-warning",
                        Map.of("seconds", String.valueOf(pauseRemaining)));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
            }
            if (pauseRemaining <= 0) {
                endPause();
            }
        }, 20L, 20L);
        tasks.add(pauseTask);

        plugin.getConfigManager().send(player, "exam-paused", Map.of(
                "key", pauseKeyName(),
                "clicks", String.valueOf(required),
                "seconds", String.valueOf(plugin.getConfigManager().getPauseDurationSeconds()),
                "left", String.valueOf(plugin.getConfigManager().getMaxPauses() - pauseCountUsed.get())
        ));
    }

    private void endPause() {
        if (!paused.getAndSet(false)) {
            return;
        }
        accumulatePauseTime();
        if (pauseTask != null) {
            pauseTask.cancel();
            pauseTask = null;
        }
        unfreezePlayer();
        freezeMobs(false);
        plugin.getConfigManager().send(player, "exam-resumed");
    }

    /**
     * 把当前这次暂停的时长累计到 pausedTotalMillis。
     */
    private void accumulatePauseTime() {
        if (pauseStartedAt > 0) {
            pausedTotalMillis += Math.max(0, System.currentTimeMillis() - pauseStartedAt);
            pauseStartedAt = 0;
        }
    }

    // ---------------- 冻结辅助（暂停 / 预备共用） ----------------

    /**
     * 开始冻结玩家：若玩家在空中，临时开启飞行使其悬浮，
     * 否则服务器会把静止悬空的玩家判定为飞行并踢出。
     */
    public void freezePlayer() {
        if (player == null || !player.isOnline() || flightForced) {
            return;
        }
        player.setVelocity(new Vector(0, 0, 0));
        if (!player.isOnGround()) {
            allowFlightBackup = player.getAllowFlight();
            player.setAllowFlight(true);
            player.setFlying(true);
            flightForced = true;
        }
    }

    /**
     * 结束冻结：若冻结期间开启过飞行，则关闭飞行并还原原设置。
     */
    public void unfreezePlayer() {
        if (player == null || !player.isOnline() || !flightForced) {
            return;
        }
        player.setFlying(false);
        player.setAllowFlight(allowFlightBackup);
        player.setFallDistance(0f);
        flightForced = false;
    }

    // ---------------- 考核预备阶段 ----------------

    public boolean isInPrep() {
        return prepPhase.get();
    }

    /**
     * 进入预备阶段；返回 true 表示本次是首次进入（应由调用方启动倒计时）。
     */
    public boolean enterPrep() {
        boolean entered = prepPhase.compareAndSet(false, true);
        if (entered) {
            freezePlayer();
        }
        return entered;
    }

    /**
     * 结束预备阶段（正式开始时调用）。
     */
    public void endPrep() {
        if (prepPhase.compareAndSet(true, false)) {
            unfreezePlayer();
        }
    }

    // ---------------- 参加费用 ----------------

    public void markFeePaid() {
        this.feePaid = true;
    }

    public boolean isFeePaid() {
        return feePaid;
    }

    private void freezeMobs(boolean freeze) {
        for (UUID uuid : aliveMobs) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity instanceof LivingEntity living) {
                living.setAI(!freeze);
            }
        }
    }

    private void updatePauseBossBar() {
        bossBar.setTitle(plugin.getConfigManager().format("bossbar-paused",
                Map.of("seconds", String.valueOf(Math.max(0, pauseRemaining)))));
    }

    // ---------------- 低血量提醒 ----------------

    /**
     * 尝试标记某阈值已提醒；返回 true 表示本次是首次触发（应发送提醒）。
     */
    public boolean tryWarnLowHealth(int percent) {
        return lowHealthWarned.add(percent);
    }

    /**
     * 玩家回血到阈值以上时清除提醒标记，允许再次触发。
     */
    public void clearLowHealthWarning(int percent) {
        lowHealthWarned.remove(percent);
    }

    private String pauseKeyName() {
        switch (plugin.getConfigManager().getPauseKey()) {
            case "SPRINT":
                return "疾跑键";
            case "SWAP":
                return "换手键(F)";
            default:
                return "潜行键(Shift)";
        }
    }
}
