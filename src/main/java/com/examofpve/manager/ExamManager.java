package com.examofpve.manager;

import com.examofpve.ExamOfPVEPlugin;
import com.examofpve.config.ChallengeConfig;
import com.examofpve.config.ChallengeRewardConfig;
import com.examofpve.config.ConfigManager;
import com.examofpve.config.ExamConfig;
import com.examofpve.config.MobDefinition;
import com.examofpve.config.MobSpawnConfig;
import com.examofpve.config.RewardConfig;
import com.examofpve.economy.EconomyService;
import com.examofpve.mob.MobSpawner;
import com.examofpve.session.ExamOutcome;
import com.examofpve.session.ExamSession;
import com.examofpve.session.PendingEffects;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitTask;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 考核核心：负责开始 / 结束考核、扣费、挑战应用、计时、生成生物、追踪击杀进度、
 * 恢复地图、按倍率发放奖励（含挑战点券 / 首通奖励）、统计与排行榜，
 * 以及处理死亡 / 退出 / 终止等失败场景。
 */
public class ExamManager {

    private final ExamOfPVEPlugin plugin;
    private final ConfigManager configManager;
    private final WorldManager worldManager;
    private final MobSpawner mobSpawner;
    private final EconomyService economyService;

    private final Map<UUID, ExamSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, ExamSession> sessionByWorld = new ConcurrentHashMap<>();
    private final Map<UUID, ExamSession> entityOwner = new ConcurrentHashMap<>();
    /** 兜底结束（玩家仍停在死亡界面）后，等待其重生时使用的返回点。 */
    private final Map<UUID, Location> pendingRespawnLocations = new ConcurrentHashMap<>();
    /** 成功通关后的 5 秒停留窗口：场地仍受保护，且不允许立刻开始下一场。 */
    private final Set<String> finishingWorlds = ConcurrentHashMap.newKeySet();
    private final Set<UUID> finishingPlayers = ConcurrentHashMap.newKeySet();

    public ExamManager(ExamOfPVEPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.worldManager = plugin.getWorldManager();
        this.mobSpawner = plugin.getMobSpawner();
        this.economyService = plugin.getEconomyService();
    }

    public boolean isInExam(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public ExamSession getSession(UUID playerId) {
        return sessions.get(playerId);
    }

    public ExamSession getSessionByWorld(String worldName) {
        return sessionByWorld.get(worldName);
    }

    /**
     * 某世界是否处于通关后的 5 秒停留窗口（此时仍需阻止方块改动）。
     */
    public boolean isWorldFinishing(String worldName) {
        return worldName != null && finishingWorlds.contains(worldName);
    }

    /**
     * 某玩家是否处于通关后的 5 秒停留窗口（此时不允许开始下一场考核）。
     */
    public boolean isPlayerFinishing(UUID playerId) {
        return playerId != null && finishingPlayers.contains(playerId);
    }

    /**
     * 某考核当前正在考核的人数。
     */
    public int countPlaying(String examId) {
        int count = 0;
        for (ExamSession s : sessions.values()) {
            if (s.getExam().getId().equalsIgnoreCase(examId)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 当前所有考核进行中的总人数。
     */
    public int getTotalPlaying() {
        return sessions.size();
    }

    // ================= 开始考核 =================

    public boolean startExam(Player player, String examId) {
        return startExam(player, examId, false);
    }

    /**
     * 开始考核。
     *
     * @param force 强制开始：无视时间段限制与进入费用（用于 /exam forcestart）
     * @return 是否成功进入加载流程；false 表示被即时校验拦下
     */
    public boolean startExam(Player player, String examId, boolean force) {
        if (isPlayerFinishing(player.getUniqueId())) {
            configManager.send(player, "exam-finishing");
            return false;
        }
        if (isInExam(player.getUniqueId())) {
            configManager.send(player, "exam-already-running");
            return false;
        }
        ExamConfig exam = configManager.getExam(examId);
        if (exam == null) {
            configManager.send(player, "exam-not-found", Map.of("exam", examId));
            return false;
        }
        if (exam.getOpenWindow() != null && !exam.getOpenWindow().isOpenNow()) {
            if (!force) {
                configManager.send(player, "exam-window-closed");
                return false;
            }
        }
        if (!worldManager.isAvailable()) {
            configManager.send(player, "exam-world-load-failed");
            plugin.getLogger().warning("Multiverse-Core 未安装，无法加载考核世界。");
            return false;
        }

        String worldName = worldManager.acquireWorld(exam);
        if (worldName == null) {
            configManager.send(player, "exam-no-free-world");
            return false;
        }

        // 参加费用（进入时扣除；仅加载失败时退还）。强制开始时无视费用。
        double fee = exam.getEntryFee();
        boolean chargeFee = fee > 0 && !force;
        if (chargeFee) {
            if (!economyService.isAvailable()) {
                worldManager.releaseWorld(worldName);
                configManager.send(player, "entry-fee-no-economy");
                return false;
            }
            if (!economyService.has(player, fee)) {
                worldManager.releaseWorld(worldName);
                configManager.send(player, "entry-fee-not-enough",
                        Map.of("fee", formatMultiplier(fee)));
                return false;
            }
            economyService.withdraw(player, fee);
        }

        // 挑战：玩家已选择的挑战（过滤无效 / 该考核不允许的）
        Set<String> challenges = new LinkedHashSet<>();
        for (String id : plugin.getDataManager().getSelectedChallenges(player.getUniqueId())) {
            if (configManager.getChallengeConfig().exists(id) && exam.isChallengeAllowed(id)) {
                challenges.add(id);
            }
        }

        double multiplier = 1.0;
        for (String id : challenges) {
            multiplier *= configManager.getChallengeConfig().getMultiplier(id);
        }

        int timeLimit = exam.getTimeLimitSeconds();
        if (challenges.contains(ChallengeConfig.HALF_TIME)) {
            timeLimit = Math.max(10, timeLimit / 2);
        }

        int mobFactor = challenges.contains(ChallengeConfig.DOUBLE_MOBS) ? 2 : 1;
        int totalToKill = 0;
        for (MobSpawnConfig m : exam.getMobs()) {
            totalToKill += m.getAmount() * mobFactor;
        }

        ExamSession session = new ExamSession(plugin, player, exam, worldName,
                challenges, multiplier, timeLimit, totalToKill);
        if (chargeFee) {
            session.markFeePaid();
        }
        sessions.put(player.getUniqueId(), session);
        sessionByWorld.put(worldName, session);

        configManager.send(player, "exam-world-loading");
        worldManager.loadWorld(worldName, success -> {
            if (session.isFinished()) {
                // 加载期间会话已结束（例如玩家退出），卸载刚加载好的世界
                worldManager.releaseWorld(worldName);
                return;
            }
            if (success) {
                onWorldLoaded(session);
            } else {
                endExam(session, ExamOutcome.ERROR, false);
            }
        });
        return true;
    }

    private void onWorldLoaded(ExamSession session) {
        World world = Bukkit.getWorld(session.getWorldName());
        if (world == null) {
            endExam(session, ExamOutcome.ERROR, false);
            return;
        }
        Player player = session.getPlayer();
        if (player == null || !player.isOnline()) {
            endExam(session, ExamOutcome.QUIT, false);
            return;
        }

        // 清理上一场可能遗留的掉落物 / 弹射物等临时实体
        clearArenaEntities(world);

        Location spawn = session.getExam().resolveSpawn(world);
        session.setSpawnLocation(spawn);
        player.teleport(spawn);

        // 应用挑战效果（生命值减半 / 脱下护甲）与游戏模式
        session.applyChallenges();
        session.applyGameMode();

        session.getBossBar().addPlayer(player);

        // 考核预备阶段：倒计时期间冻结玩家移动、不生成怪物，结束后才正式开始
        int prep = plugin.getConfigManager().getPrepTimeSeconds();
        if (prep > 0 && session.enterPrep()) {
            startPrepPhase(session, prep);
            return;
        }
        beginExam(session);
    }

    /**
     * 正式开始考核：更新 BossBar、发送开始提示、启动计时与刷怪。
     */
    private void beginExam(ExamSession session) {
        if (session.isFinished()) {
            return;
        }
        Player player = session.getPlayer();
        if (player == null || !player.isOnline()) {
            endExam(session, ExamOutcome.QUIT, false);
            return;
        }
        // 统计用时从正式开始时计算，不包含地图加载与预备阶段
        session.markStarted();
        session.updateBossBar(session.getTimeLimitSeconds());
        player.sendTitle("", plugin.getConfigManager().format("exam-prep-go"), 0, 20, 10);

        configManager.send(player, "exam-started", Map.of(
                "exam", session.getExam().getDisplayName(),
                "time", String.valueOf(session.getTimeLimitSeconds())
        ));
        if (session.getMultiplier() > 1.0) {
            configManager.send(player, "exam-challenges-active", Map.of(
                    "count", String.valueOf(session.getChallengeCount()),
                    "multiplier", formatMultiplier(session.getMultiplier())
            ));
        }

        scheduleTimeout(session);
        scheduleMobs(session);
    }

    /**
     * 考核预备阶段：期间冻结玩家移动、怪物不生成，每秒发送倒计时 subtitle 与音效，
     * 倒计时结束（prepSeconds 秒）后才正式开始考核。
     */
    private void startPrepPhase(ExamSession session, int prepSeconds) {
        Player player = session.getPlayer();
        plugin.getConfigManager().send(player, "exam-prep-start",
                Map.of("seconds", String.valueOf(prepSeconds)));
        final int[] remaining = {prepSeconds};
        AtomicReference<BukkitTask> taskRef = new AtomicReference<>();
        taskRef.set(plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (session.isFinished()) {
                return; // 会话结束时 cancelTasks 会一并取消本任务
            }
            if (remaining[0] <= 0) {
                BukkitTask task = taskRef.get();
                if (task != null) {
                    task.cancel();
                }
                session.endPrep();
                beginExam(session);
                return;
            }
            int left = remaining[0];
            player.sendTitle("", plugin.getConfigManager().format("exam-prep-countdown",
                    Map.of("seconds", String.valueOf(left))), 0, 25, 5);
            // 越临近开始音调越高
            float pitch = (float) Math.min(2.0, 1.0 + (prepSeconds - left) * 0.15);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, pitch);
            session.getBossBar().setTitle(plugin.getConfigManager().format("bossbar-prep",
                    Map.of("seconds", String.valueOf(left))));
            session.getBossBar().setProgress(1.0);
            remaining[0]--;
        }, 0L, 20L));
        session.addTask(taskRef.get());
    }

    private void scheduleTimeout(ExamSession session) {
        final int[] remaining = {session.getTimeLimitSeconds()};
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (session.isFinished()) {
                return;
            }
            if (session.isPaused()) {
                return; // 暂停中不计时
            }
            remaining[0]--;
            session.updateBossBar(remaining[0]);
            warnTimeLeft(session, remaining[0]);
            if (remaining[0] <= 0) {
                endExam(session, ExamOutcome.TIMEOUT, true);
            }
        }, 20L, 20L);
        session.addTask(task);
    }

    /**
     * 考核剩余时间提醒：剩余秒数到达配置的提醒点（如 60/30/10/5）时播放音效并提示。
     */
    private void warnTimeLeft(ExamSession session, int secondsLeft) {
        for (int threshold : plugin.getConfigManager().getTimeWarningAt()) {
            if (secondsLeft != threshold) {
                continue;
            }
            Player player = session.getPlayer();
            if (player != null && player.isOnline()) {
                plugin.getConfigManager().send(player, "time-warning",
                        Map.of("time", String.valueOf(threshold)));
                // 越临近结束音调越高
                float pitch = (float) Math.min(2.0, 1.0 + (60.0 - threshold) / 40.0);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, pitch);
            }
            break;
        }
    }

    private void scheduleMobs(ExamSession session) {
        World world = Bukkit.getWorld(session.getWorldName());
        if (world == null) {
            endExam(session, ExamOutcome.ERROR, false);
            return;
        }
        int mobFactor = session.hasChallenge(ChallengeConfig.DOUBLE_MOBS) ? 2 : 1;
        for (MobSpawnConfig cfg : session.getExam().getMobs()) {
            long delay = Math.max(0, cfg.getDelaySeconds()) * 20L;
            BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (session.isFinished()) {
                    return;
                }
                World w = Bukkit.getWorld(session.getWorldName());
                if (w == null) {
                    endExam(session, ExamOutcome.ERROR, false);
                    return;
                }
                for (int i = 0; i < cfg.getAmount() * mobFactor; i++) {
                    MobDefinition definition = cfg.pickMob();
                    Location spawnLocation = null;
                    if (cfg.isRegionSpawn()) {
                        spawnLocation = mobSpawner.pickRegionLocation(
                                cfg, w, session.getRegionSpawnPoints(cfg));
                        if (spawnLocation == null) {
                            session.waiveKill();
                            continue;
                        }
                        session.addRegionSpawnPoint(cfg, spawnLocation);
                    }

                    Entity entity = mobSpawner.spawn(definition, cfg, w, spawnLocation);
                    if (entity != null) {
                        // 暂停期间新生成的生物同样冻结
                        if (session.isPaused() && entity instanceof LivingEntity living) {
                            living.setAI(false);
                        }
                        session.registerEntity(entity.getUniqueId());
                        session.putEntityConfig(entity.getUniqueId(), cfg);
                        if (definition != null) {
                            // 记录实际抽中的生物，消失重生时保持同一类型
                            session.putEntityDefinition(entity.getUniqueId(), definition);
                        }
                        if (cfg.isRegionSpawn() && spawnLocation != null) {
                            session.putEntitySpawnLocation(entity.getUniqueId(), spawnLocation);
                        }
                        session.applyMobGlow(entity);
                        entityOwner.put(entity.getUniqueId(), session);
                    } else {
                        if (cfg.isRegionSpawn() && spawnLocation != null) {
                            session.removeRegionSpawnPoint(cfg, spawnLocation);
                        }
                        session.waiveKill();
                    }
                }
                checkWin(session);
            }, delay);
            session.addTask(task);
        }
    }

    private void checkWin(ExamSession session) {
        if (session.isFinished()) {
            return;
        }
        if (session.getPendingKills() <= 0) {
            endExam(session, ExamOutcome.SUCCESS, true);
        }
    }

    // ================= 终止 / 失败入口 =================

    public void abortExam(Player player) {
        ExamSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            configManager.send(player, "not-in-exam");
            return;
        }
        endExam(session, ExamOutcome.ABORT, true);
    }

    public void onQuit(Player player) {
        ExamSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        endExam(session, ExamOutcome.QUIT, false);
    }

    public void onDeath(Player player) {
        ExamSession session = sessions.get(player.getUniqueId());
        if (session == null || session.isDeathHandled()) {
            return;
        }
        session.markDeathHandled();
        session.cancelTasks();

        // 兜底：若玩家长时间停留在死亡界面未重生，强制完成清理
        BukkitTask fallback = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!session.isFinished() && sessions.containsKey(session.getPlayerId())) {
                endExam(session, ExamOutcome.DEATH, false);
            }
        }, 1200L); // 60 秒
        session.addTask(fallback);
    }

    /**
     * 重生时把玩家送到考核出发点，并结束会话。
     * 使用 PlayerRespawnEvent 重定向而不是修改玩家持久化重生点，避免污染玩家之后的死亡重生位置。
     */
    public void onRespawn(Player player, PlayerRespawnEvent event) {
        ExamSession session = sessions.get(player.getUniqueId());
        if (session != null && session.isDeathHandled()) {
            Location returnLocation = resolveReturnLocation(session);
            if (returnLocation != null) {
                event.setRespawnLocation(returnLocation);
            }
            // 重生点已由事件重定向，无需再走 5 秒停留 / 烟花流程
            endExam(session, ExamOutcome.DEATH, false);
            pendingRespawnLocations.remove(player.getUniqueId());
            return;
        }

        // 兜底结束会话后玩家才重生：仍然送回考核出发点
        Location pending = pendingRespawnLocations.remove(player.getUniqueId());
        if (pending != null) {
            event.setRespawnLocation(pending);
        }
    }

    /**
     * 补做玩家退出考核时未能立即还原的效果（护甲 / 游戏模式 / 生命上限 / 飞行权限）。
     * 玩家处于死亡状态时暂不处理，等重生后再补做。
     */
    public void applyPendingRestore(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return;
        }
        PendingEffects pending = plugin.getDataManager().consumePendingEffects(player.getUniqueId());
        if (pending == null) {
            return;
        }
        if (pending.maxHealth() != null && pending.maxHealth() > 0) {
            try {
                player.setMaxHealth(pending.maxHealth());
                if (player.getHealth() > pending.maxHealth()) {
                    player.setHealth(pending.maxHealth());
                }
            } catch (RuntimeException e) {
                plugin.getLogger().warning("恢复退出考核前的生命上限失败：" + e.getMessage());
            }
        }
        if (pending.gameMode() != null && player.getGameMode() != pending.gameMode()) {
            try {
                player.setGameMode(pending.gameMode());
            } catch (RuntimeException e) {
                plugin.getLogger().warning("恢复退出考核前的游戏模式失败：" + e.getMessage());
            }
        }
        if (pending.armor() != null) {
            try {
                player.getInventory().setArmorContents(pending.armor());
            } catch (RuntimeException e) {
                plugin.getLogger().warning("恢复退出考核前的护甲失败：" + e.getMessage());
            }
        }
        if (pending.allowFlight() != null) {
            try {
                player.setAllowFlight(pending.allowFlight());
                player.setFlying(false);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("恢复退出考核前的飞行状态失败：" + e.getMessage());
            }
        }
    }

    public void onEntityDeath(Entity entity) {
        ExamSession session = entityOwner.remove(entity.getUniqueId());
        if (session == null) {
            return;
        }
        session.onEntityKilled(entity.getUniqueId());
        checkWin(session);
    }

    public void onEntityDespawn(Entity entity) {
        ExamSession session = entityOwner.remove(entity.getUniqueId());
        if (session == null || session.isFinished() || session.isDeathHandled()) {
            return;
        }
        MobSpawnConfig cfg = session.removeEntityConfig(entity.getUniqueId());
        MobDefinition definition = session.removeEntityDefinition(entity.getUniqueId());
        Location spawnLocation = session.removeEntitySpawnLocation(entity.getUniqueId());
        session.onEntityDespawned(entity.getUniqueId());

        if (cfg == null) {
            // 没有对应点位配置，无法重生；豁免该目标避免卡进度
            session.waiveKill();
            checkWin(session);
            return;
        }

        World world = Bukkit.getWorld(session.getWorldName());
        if (world == null) {
            session.waiveKill();
            checkWin(session);
            return;
        }

        boolean newRegionPoint = false;
        if (cfg.isRegionSpawn()) {
            if (spawnLocation == null || !mobSpawner.isSpawnable(spawnLocation)) {
                if (spawnLocation != null) {
                    session.removeRegionSpawnPoint(cfg, spawnLocation);
                }
                spawnLocation = mobSpawner.pickRegionLocation(
                        cfg, world, session.getRegionSpawnPoints(cfg));
                if (spawnLocation != null) {
                    session.addRegionSpawnPoint(cfg, spawnLocation);
                    newRegionPoint = true;
                }
            }
        }

        MobDefinition respawnDefinition = definition != null ? definition : cfg.pickMob();
        Entity respawned = respawnDefinition == null ? null
                : mobSpawner.spawn(respawnDefinition, cfg, world, spawnLocation);
        if (respawned != null) {
            // 暂停期间重生的生物同样关闭 AI
            if (session.isPaused() && respawned instanceof LivingEntity living) {
                living.setAI(false);
            }
            session.registerEntity(respawned.getUniqueId());
            session.putEntityConfig(respawned.getUniqueId(), cfg);
            session.putEntityDefinition(respawned.getUniqueId(), respawnDefinition);
            if (cfg.isRegionSpawn() && spawnLocation != null) {
                session.putEntitySpawnLocation(respawned.getUniqueId(), spawnLocation);
            }
            session.applyMobGlow(respawned);
            entityOwner.put(respawned.getUniqueId(), session);
        } else {
            if (newRegionPoint) {
                session.removeRegionSpawnPoint(cfg, spawnLocation);
            }
            session.waiveKill();
            checkWin(session);
        }
    }

    // ================= 结束清理 =================

    public void endExam(ExamSession session, ExamOutcome outcome, boolean teleportOut) {
        if (!session.markFinished()) {
            return; // 已结束，防止重复处理
        }
        boolean success = outcome == ExamOutcome.SUCCESS;
        session.cancelTasks();

        // 玩家中途退出时，部分效果可能因离线无法立即写回：先留存快照，待下次进入时补做。
        // 死亡重生时也留一份，防止在重生事件期间写回装备/属性不生效。
        Player sessionPlayer = session.getPlayer();
        boolean savePending = outcome == ExamOutcome.QUIT || outcome == ExamOutcome.DEATH;
        PendingEffects pendingEffects = savePending ? session.snapshotPendingEffects() : null;
        // 清除暂停 / 预备 / 强制飞行等临时状态（必须在快照之后）
        session.clearTransientState();

        // 移除 BossBar
        session.getBossBar().removeAll();

        // 清理生成的生物，并先解除归属（避免移除时再次触发事件）
        List<UUID> spawned = new ArrayList<>(session.getAliveMobs());
        for (UUID uuid : spawned) {
            entityOwner.remove(uuid);
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) {
                entity.remove();
            }
        }
        session.getAliveMobs().clear();
        session.clearEntityConfigs();
        session.clearEntityDefinitions();
        session.clearSpawnTracking();

        // 清理场地中的掉落物、经验球、弹射物等临时实体，避免跨场残留
        World arenaWorld = Bukkit.getWorld(session.getWorldName());
        if (arenaWorld != null) {
            clearArenaEntities(arenaWorld);
        }

        // 恢复挑战效果与游戏模式
        session.restoreChallenges();
        session.restoreGameMode();
        if (pendingEffects != null && !pendingEffects.isEmpty()) {
            plugin.getDataManager().savePendingEffects(session.getPlayerId(), pendingEffects);
        }

        // 恢复方块（须在世界卸载前同步完成）
        session.restoreBlocks();

        // 传送玩家离开（若在线且需要）。
        // 只有成功通关才在场地停留 5 秒并放烟花；失败时立即传送回出发点。
        // 若玩家已不在考核世界（如死亡后已重生到出发点），则立即传送（实际为原地，无需等待）。
        Player player = sessionPlayer;
        String worldName = session.getWorldName();
        // 若玩家仍停在死亡界面，记录返回点，等其真正重生时再重定向
        if ((outcome == ExamOutcome.DEATH || outcome == ExamOutcome.QUIT)
                && player != null && player.isDead()) {
            Location deathReturn = resolveReturnLocation(session);
            if (deathReturn != null) {
                pendingRespawnLocations.put(session.getPlayerId(), deathReturn);
            }
        }
        // 只有成功通关才在场地停留 5 秒并放烟花；失败时立即传送离开。
        Location returnTarget = (teleportOut && player != null && player.isOnline())
                ? resolveReturnLocation(session) : null;
        boolean stayInArena = success && returnTarget != null && player != null && player.isOnline()
                && player.getWorld() != null && player.getWorld().getName().equals(worldName);

        if (stayInArena) {
            long stayTicks = 100L; // 5 秒
            finishingWorlds.add(worldName);
            finishingPlayers.add(player.getUniqueId());
            plugin.getConfigManager().send(player, "exam-end-teleport-delay",
                    Map.of("seconds", String.valueOf(stayTicks / 20L)));
            scheduleEndFireworks(player);
            Location target = returnTarget;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && !player.isDead()) {
                    player.teleport(target);
                }
                finishingPlayers.remove(player.getUniqueId());
            }, stayTicks);
            // 等玩家传送离开后再卸载世界（多留 1 秒余量）；期间场地继续保持保护
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                finishingWorlds.remove(worldName);
                worldManager.releaseWorld(worldName);
            }, stayTicks + 20L);
        } else {
            if (returnTarget != null) {
                player.teleport(returnTarget);
            }
            // 稍作延迟再卸载世界，给传送留出时间
            plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                    worldManager.releaseWorld(worldName), 10L);
        }

        // 移除会话
        sessions.remove(session.getPlayerId());
        sessionByWorld.remove(worldName);

        // 统计（不依赖玩家是否在线）
        double elapsed = success ? session.getElapsedSeconds() : 0.0;
        boolean firstWin = plugin.getDataManager().recordAttempt(
                session.getPlayerId(), player.getName(), session.getExam().getId(), success, elapsed);

        // 考核日志（每玩家每考核最近 20 条）
        plugin.getDataManager().recordLog(
                session.getPlayerId(), session.getExam().getId(), outcome.name(),
                elapsed, session.getChallengeCount(), session.getMultiplier(),
                session.getPauseCountUsed());

        // 加载失败时退还参加费用（仅退还实际扣除过的；强制开始未扣费不退还）
        if (outcome == ExamOutcome.ERROR && session.isFeePaid()) {
            economyService.deposit(player, session.getExam().getEntryFee());
        }

        // 消息与奖励
        if (player != null && player.isOnline()) {
            switch (outcome) {
                case SUCCESS -> {
                    configManager.send(player, "exam-success",
                            Map.of("exam", session.getExam().getDisplayName()));
                    if (session.getMultiplier() > 1.0) {
                        configManager.send(player, "exam-success-multiplier",
                                Map.of("multiplier", formatMultiplier(session.getMultiplier())));
                    }
                    grantRewards(session);
                    grantChallengeRewards(session);
                    if (firstWin) {
                        grantFirstWinRewards(session);
                    }
                }
                case TIMEOUT -> configManager.send(player, "exam-fail-timeout");
                case DEATH -> configManager.send(player, "exam-fail-death");
                case ABORT -> configManager.send(player, "exam-fail-abort");
                case ERROR -> configManager.send(player, "exam-fail-error");
                default -> {
                    // QUIT：玩家已离线，无需发送消息
                }
            }
            // 最终成绩附加暂停次数（仅本场使用过暂停时显示）
            if (session.getPauseCountUsed() > 0) {
                configManager.send(player, "exam-result-pauses",
                        Map.of("count", String.valueOf(session.getPauseCountUsed())));
            }
        }
    }

    // ================= 奖励 =================

    /**
     * 清理场地中的临时实体：掉落物、经验球、弹射物、烟花、点燃的 TNT、下落方块、药水云。
     * 保留玩家、生物、盔甲架、展示框等地图内容。
     */
    private void clearArenaEntities(World world) {
        for (Entity entity : world.getEntities()) {
            if (entity instanceof Item
                    || entity instanceof ExperienceOrb
                    || entity instanceof Projectile
                    || entity instanceof Firework
                    || entity instanceof TNTPrimed
                    || entity instanceof FallingBlock
                    || entity instanceof AreaEffectCloud) {
                entity.remove();
            }
        }
    }

    /**
     * 基础奖励 × 最终挑战倍率（金币与经验按倍率叠加，命令支持 %multiplier% 占位符）。
     */
    private void grantRewards(ExamSession session) {
        Player player = session.getPlayer();
        if (player == null || !player.isOnline()) {
            return;
        }
        RewardConfig rewards = session.getExam().getRewards();
        double multiplier = session.getMultiplier();
        double money = rewards.getMoney() * multiplier;
        int exp = (int) Math.round(rewards.getExp() * multiplier);

        if (money > 0) {
            if (economyService.isAvailable()) {
                economyService.deposit(player, money);
            } else {
                plugin.getLogger().info("Vault 未安装，跳过金币奖励。");
            }
        }
        if (exp > 0) {
            player.giveExp(exp);
        }
        for (String command : rewards.getCommands()) {
            if (command == null || command.isBlank()) {
                continue;
            }
            String cmd = command
                    .replace("%player%", player.getName())
                    .replace("%exam%", session.getExam().getId())
                    .replace("%multiplier%", formatMultiplier(multiplier));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }

    /**
     * 按完成的挑战数量发放额外奖励（每个数量档位可独立配置：
     * 金币 / 经验 / 点券（走全局 voucher-command 模板）/ 自定义命令）。
     */
    private void grantChallengeRewards(ExamSession session) {
        Player player = session.getPlayer();
        if (player == null || !player.isOnline()) {
            return;
        }
        int completed = session.getChallengeCount();
        if (completed <= 0) {
            return;
        }
        ChallengeRewardConfig reward = session.getExam().getChallengeReward(completed);
        if (reward == null || reward.isEmpty()) {
            return;
        }

        if (reward.getMoney() > 0 && economyService.isAvailable()) {
            economyService.deposit(player, reward.getMoney());
        }
        if (reward.getExp() > 0) {
            player.giveExp(reward.getExp());
        }
        for (String command : reward.getCommands()) {
            if (command == null || command.isBlank()) {
                continue;
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command
                    .replace("%player%", player.getName())
                    .replace("%exam%", session.getExam().getId()));
        }
        if (reward.getPoints() > 0) {
            String template = configManager.getVoucherCommand();
            if (template == null || template.isBlank()) {
                plugin.getLogger().info("未配置 settings.voucher-command，跳过点券奖励。");
            } else {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), template
                        .replace("%player%", player.getName())
                        .replace("%amount%", String.valueOf(reward.getPoints())));
            }
        }
        configManager.send(player, "challenge-complete-reward",
                Map.of("count", String.valueOf(completed)));
    }

    /**
     * 初次通过该考核的额外奖励。
     */
    private void grantFirstWinRewards(ExamSession session) {
        Player player = session.getPlayer();
        if (player == null || !player.isOnline()) {
            return;
        }
        RewardConfig rewards = session.getExam().getFirstWinRewards();
        if (rewards.getMoney() > 0 && economyService.isAvailable()) {
            economyService.deposit(player, rewards.getMoney());
        }
        if (rewards.getExp() > 0) {
            player.giveExp(rewards.getExp());
        }
        for (String command : rewards.getCommands()) {
            if (command == null || command.isBlank()) {
                continue;
            }
            String cmd = command
                    .replace("%player%", player.getName())
                    .replace("%exam%", session.getExam().getId());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
        configManager.send(player, "first-win-reward");
    }

    private Location resolveReturnLocation(ExamSession session) {
        Location ret = session.getReturnLocation();
        if (ret != null && ret.getWorld() != null
                && Bukkit.getWorld(ret.getWorld().getName()) != null) {
            return ret;
        }
        if (!Bukkit.getWorlds().isEmpty()) {
            return Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        return null;
    }

    // ================= 考核结束烟花 =================

    private static final Color[] FIREWORK_COLORS = {
            Color.RED, Color.ORANGE, Color.YELLOW, Color.GREEN, Color.LIME,
            Color.BLUE, Color.PURPLE, Color.FUCHSIA, Color.AQUA, Color.WHITE
    };
    private static final FireworkEffect.Type[] FIREWORK_TYPES = {
            FireworkEffect.Type.BALL, FireworkEffect.Type.BALL_LARGE,
            FireworkEffect.Type.BURST, FireworkEffect.Type.STAR
    };
    private final Random random = new Random();

    /**
     * 考核结束后的 5 秒停留期内，在玩家周围分多次释放烟花。
     */
    private void scheduleEndFireworks(Player player) {
        long[] delays = {10L, 25L, 40L, 55L, 70L}; // 0.5s ~ 3.5s
        for (long delay : delays) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                spawnFirework(player.getLocation());
            }, delay);
        }
    }

    private void spawnFirework(Location base) {
        World world = base.getWorld();
        if (world == null) {
            return;
        }
        Location loc = base.clone().add(
                (random.nextDouble() - 0.5) * 6.0,
                2.5 + random.nextDouble() * 2.0,
                (random.nextDouble() - 0.5) * 6.0);
        Firework firework = world.spawn(loc, Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        Color color = FIREWORK_COLORS[random.nextInt(FIREWORK_COLORS.length)];
        Color fade = FIREWORK_COLORS[random.nextInt(FIREWORK_COLORS.length)];
        FireworkEffect effect = FireworkEffect.builder()
                .with(FIREWORK_TYPES[random.nextInt(FIREWORK_TYPES.length)])
                .withColor(color)
                .withFade(fade)
                .flicker(random.nextBoolean())
                .trail(random.nextBoolean())
                .build();
        meta.addEffect(effect);
        meta.setPower(1 + random.nextInt(2));
        firework.setFireworkMeta(meta);
    }

    /**
     * 格式化倍率：保留最多 4 位小数并去掉多余尾零，如 1.4 / 1.44 / 2。
     * 直接使用 double 的字符串会显示 1.4 × 1.7 = 2.3799999999999994 这类浮点尾数。
     */
    public static String formatMultiplier(double value) {
        return BigDecimal.valueOf(value)
                .setScale(4, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    // ================= 关闭 =================

    public void shutdown() {
        for (ExamSession session : new ArrayList<>(sessions.values())) {
            endExam(session, ExamOutcome.ERROR, false);
        }
        sessions.clear();
        pendingRespawnLocations.clear();
        finishingWorlds.clear();
        finishingPlayers.clear();
        worldManager.releaseAll();
    }
}
