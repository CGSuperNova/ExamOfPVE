package com.examofpve.command;

import com.examofpve.ExamOfPVEPlugin;
import com.examofpve.config.ChallengeConfig;
import com.examofpve.config.ConfigManager;
import com.examofpve.config.ExamConfig;
import com.examofpve.manager.DataManager;
import com.examofpve.manager.ExamManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * /exam 命令：list / start &lt;id&gt; / abort / reload /
 * challenges / challenge &lt;id&gt; [on|off] / top &lt;id&gt; / stats &lt;id&gt; [player] /
 * playing [id] / log &lt;id&gt; [player]。
 */
public class ExamCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "list", "start", "forcestart", "abort", "reload", "challenges", "challenge",
            "settings", "top", "stats", "playing", "log");

    private final ExamOfPVEPlugin plugin;

    public ExamCommand(ExamOfPVEPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        ConfigManager cfg = plugin.getConfigManager();
        if (args.length == 0) {
            cfg.send(sender, "usage");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> list(sender);
            case "start" -> start(sender, args);
            case "forcestart" -> forcestart(sender, args);
            case "abort", "cancel", "stop", "quit" -> abort(sender);
            case "reload" -> reload(sender);
            case "challenges" -> challenges(sender);
            case "challenge" -> challenge(sender, args);
            case "settings", "setting" -> settings(sender, args);
            case "top" -> top(sender, args);
            case "stats" -> stats(sender, args);
            case "playing" -> playing(sender, args);
            case "log" -> log(sender, args);
            default -> cfg.send(sender, "usage");
        }
        return true;
    }

    // ---------------- list ----------------

    private void list(CommandSender sender) {
        ConfigManager cfg = plugin.getConfigManager();
        if (cfg.getExams().isEmpty()) {
            cfg.send(sender, "exam-list-none");
            return;
        }
        cfg.send(sender, "exam-list-header");
        for (ExamConfig exam : cfg.getExams()) {
            boolean open = exam.getOpenWindow() == null || exam.getOpenWindow().isOpenNow();
            if (!open && cfg.isHideClosedInList()) {
                continue; // 配置了隐藏未开放的考核
            }
            String window = exam.getOpenWindow() == null ? "全天" : exam.getOpenWindow().toString();
            String status = open ? cfg.format("exam-list-available") : cfg.format("exam-list-closed");
            cfg.send(sender, "exam-list-entry", Map.of(
                    "exam", exam.getId(),
                    "display", exam.getDisplayName(),
                    "window", window,
                    "status", status
            ));
        }
    }

    // ---------------- start / abort / reload ----------------

    private void start(CommandSender sender, String[] args) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!(sender instanceof Player player)) {
            cfg.send(sender, "only-player");
            return;
        }
        if (!player.hasPermission("examofpve.use")) {
            cfg.send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            cfg.send(sender, "usage");
            return;
        }
        plugin.getExamManager().startExam(player, args[1]);
    }

    /**
     * /exam forcestart &lt;id&gt; [玩家]：强制指定玩家开始考核，
     * 无视时间段限制与进入费用（需 examofpve.admin）。
     */
    private void forcestart(CommandSender sender, String[] args) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!sender.hasPermission("examofpve.admin")) {
            cfg.send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            cfg.send(sender, "usage");
            return;
        }
        String examId = args[1];
        if (cfg.getExam(examId) == null) {
            cfg.send(sender, "exam-not-found", Map.of("exam", examId));
            return;
        }
        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                cfg.send(sender, "player-not-found", Map.of("player", args[2]));
                return;
            }
        } else {
            if (!(sender instanceof Player player)) {
                cfg.send(sender, "only-player");
                return;
            }
            target = player;
        }
        boolean started = plugin.getExamManager().startExam(target, examId, true);
        if (started) {
            cfg.send(sender, "exam-force-start", Map.of(
                    "exam", cfg.getExam(examId).getDisplayName(),
                    "player", target.getName()
            ));
        } else if (target != sender) {
            // 目标玩家已收到具体失败原因；这里只给管理员一个汇总提示
            cfg.send(sender, "exam-force-start-failed", Map.of(
                    "exam", cfg.getExam(examId).getDisplayName(),
                    "player", target.getName()
            ));
        }
    }

    private void abort(CommandSender sender) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!(sender instanceof Player player)) {
            cfg.send(sender, "only-player");
            return;
        }
        plugin.getExamManager().abortExam(player);
    }

    private void reload(CommandSender sender) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!sender.hasPermission("examofpve.admin")) {
            cfg.send(sender, "no-permission");
            return;
        }
        cfg.reload();
        plugin.getDataManager().reload();
        cfg.send(sender, "reload-success");
    }

    // ---------------- challenges / challenge ----------------

    private void challenges(CommandSender sender) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!(sender instanceof Player player)) {
            cfg.send(sender, "only-player");
            return;
        }
        Set<String> selected = plugin.getDataManager().getSelectedChallenges(player.getUniqueId());
        cfg.send(player, "challenge-list-header");
        for (Map.Entry<String, Double> e : cfg.getChallengeConfig().getMultipliers().entrySet()) {
            boolean on = selected.contains(e.getKey());
            String status = on ? cfg.format("challenge-status-on") : cfg.format("challenge-status-off");
            cfg.send(player, "challenge-list-entry", Map.of(
                    "id", e.getKey(),
                    "name", cfg.getMessage("challenge-name-" + e.getKey()),
                    "multiplier", ExamManager.formatMultiplier(e.getValue()),
                    "status", status
            ));
        }
    }

    private void challenge(CommandSender sender, String[] args) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!(sender instanceof Player player)) {
            cfg.send(sender, "only-player");
            return;
        }
        if (args.length < 2) {
            cfg.send(sender, "challenge-usage");
            return;
        }
        String id = ChallengeConfig.canonicalId(args[1]);
        if (!cfg.getChallengeConfig().exists(id)) {
            cfg.send(player, "challenge-not-found", Map.of("id", id));
            return;
        }
        boolean currentlyOn = plugin.getDataManager().getSelectedChallenges(player.getUniqueId()).contains(id);
        boolean enable;
        if (args.length >= 3) {
            enable = parseOnOff(args[2], currentlyOn);
        } else {
            enable = !currentlyOn; // 无参数时切换
        }
        plugin.getDataManager().setChallenge(player.getUniqueId(), id, enable);
        String name = cfg.getMessage("challenge-name-" + id);
        if (enable) {
            cfg.send(player, "challenge-enabled", Map.of(
                    "name", name,
                    "multiplier", ExamManager.formatMultiplier(cfg.getChallengeConfig().getMultiplier(id))
            ));
        } else {
            cfg.send(player, "challenge-disabled", Map.of("name", name));
        }
    }

    /**
     * /exam settings [mob-glow] [on|off]：个人考核偏好设置。
     */
    private void settings(CommandSender sender, String[] args) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!(sender instanceof Player player)) {
            cfg.send(sender, "only-player");
            return;
        }
        if (!player.hasPermission("examofpve.use")) {
            cfg.send(sender, "no-permission");
            return;
        }
        DataManager data = plugin.getDataManager();
        UUID playerId = player.getUniqueId();

        if (args.length < 2) {
            cfg.send(player, "settings-header");
            boolean glow = data.isMobGlowEnabled(playerId);
            cfg.send(player, "settings-entry", Map.of(
                    "key", "mob-glow",
                    "name", cfg.getMessage("setting-name-mob-glow"),
                    "status", glow ? cfg.format("setting-status-on") : cfg.format("setting-status-off")
            ));
            return;
        }

        String key = args[1].toLowerCase(Locale.ROOT);
        if (!key.equals("mob-glow")) {
            cfg.send(player, "settings-not-found", Map.of("key", key));
            return;
        }
        boolean current = data.isMobGlowEnabled(playerId);
        boolean enable = args.length >= 3 ? parseOnOff(args[2], current) : !current;
        data.setMobGlowEnabled(playerId, enable);

        // 考核进行中切换时立即刷新当前存活怪物的发光状态
        var session = plugin.getExamManager().getSession(playerId);
        if (session != null) {
            session.updateMobGlow();
        }
        cfg.send(player, enable ? "settings-enabled" : "settings-disabled",
                Map.of("name", cfg.getMessage("setting-name-mob-glow")));
    }

    private boolean parseOnOff(String arg, boolean current) {
        String a = arg.toLowerCase(Locale.ROOT);
        if (a.equals("on") || a.equals("true") || a.equals("1") || a.equals("enable") || a.equals("开")) {
            return true;
        }
        if (a.equals("off") || a.equals("false") || a.equals("0") || a.equals("disable") || a.equals("关")) {
            return false;
        }
        return current;
    }

    // ---------------- top / stats / playing ----------------

    private void top(CommandSender sender, String[] args) {
        ConfigManager cfg = plugin.getConfigManager();
        if (args.length < 2) {
            cfg.send(sender, "usage");
            return;
        }
        String examId = args[1];
        ExamConfig exam = cfg.getExam(examId);
        if (exam == null) {
            cfg.send(sender, "exam-not-found", Map.of("exam", examId));
            return;
        }
        List<DataManager.LeaderboardEntry> leaderboard = plugin.getDataManager().getLeaderboard(exam.getId());
        if (leaderboard.isEmpty()) {
            cfg.send(sender, "top-none", Map.of("exam", exam.getDisplayName()));
            return;
        }
        cfg.send(sender, "top-header", Map.of("exam", exam.getDisplayName()));
        int rank = 1;
        for (DataManager.LeaderboardEntry entry : leaderboard) {
            cfg.send(sender, "top-entry", Map.of(
                    "rank", String.valueOf(rank),
                    "player", entry.name(),
                    "time", formatTime(entry.timeSeconds())
            ));
            rank++;
        }
    }

    private void stats(CommandSender sender, String[] args) {
        ConfigManager cfg = plugin.getConfigManager();
        if (args.length < 2) {
            cfg.send(sender, "usage");
            return;
        }
        String examId = args[1];
        ExamConfig exam = cfg.getExam(examId);
        if (exam == null) {
            cfg.send(sender, "exam-not-found", Map.of("exam", examId));
            return;
        }
        UUID targetId;
        String targetName;
        if (args.length >= 3) {
            OfflinePlayer target = resolvePlayer(args[2]);
            if (target == null || !target.hasPlayedBefore()) {
                cfg.send(sender, "player-not-found", Map.of("player", args[2]));
                return;
            }
            targetId = target.getUniqueId();
            targetName = target.getName() == null ? args[2] : target.getName();
        } else {
            if (!(sender instanceof Player player)) {
                cfg.send(sender, "only-player");
                return;
            }
            targetId = player.getUniqueId();
            targetName = player.getName();
        }
        DataManager.ExamStats stats = plugin.getDataManager().getStats(targetId, exam.getId());
        String best = stats.bestTime > 0 ? formatTime(stats.bestTime) : "无";
        cfg.send(sender, "stats-entry", Map.of(
                "player", targetName,
                "exam", exam.getDisplayName(),
                "plays", String.valueOf(stats.plays),
                "wins", String.valueOf(stats.wins),
                "best", best
        ));
    }

    private void playing(CommandSender sender, String[] args) {
        ConfigManager cfg = plugin.getConfigManager();
        if (args.length >= 2) {
            ExamConfig exam = cfg.getExam(args[1]);
            if (exam == null) {
                cfg.send(sender, "exam-not-found", Map.of("exam", args[1]));
                return;
            }
            cfg.send(sender, "playing-entry", Map.of(
                    "exam", exam.getDisplayName(),
                    "count", String.valueOf(plugin.getExamManager().countPlaying(exam.getId()))
            ));
            return;
        }
        cfg.send(sender, "playing-header");
        for (ExamConfig exam : cfg.getExams()) {
            cfg.send(sender, "playing-entry", Map.of(
                    "exam", exam.getDisplayName(),
                    "count", String.valueOf(plugin.getExamManager().countPlaying(exam.getId()))
            ));
        }
    }

    /**
     * /exam log &lt;id&gt; [player]：查看某玩家在某考核的最近 20 次考核记录。
     */
    private void log(CommandSender sender, String[] args) {
        ConfigManager cfg = plugin.getConfigManager();
        if (args.length < 2) {
            cfg.send(sender, "usage");
            return;
        }
        ExamConfig exam = cfg.getExam(args[1]);
        if (exam == null) {
            cfg.send(sender, "exam-not-found", Map.of("exam", args[1]));
            return;
        }
        UUID targetId;
        String targetName;
        if (args.length >= 3) {
            OfflinePlayer target = resolvePlayer(args[2]);
            if (target == null || !target.hasPlayedBefore()) {
                cfg.send(sender, "player-not-found", Map.of("player", args[2]));
                return;
            }
            targetId = target.getUniqueId();
            targetName = target.getName() == null ? args[2] : target.getName();
        } else {
            if (!(sender instanceof Player player)) {
                cfg.send(sender, "only-player");
                return;
            }
            targetId = player.getUniqueId();
            targetName = player.getName();
        }
        List<DataManager.ExamLog> logs = plugin.getDataManager().getLogs(targetId, exam.getId());
        if (logs.isEmpty()) {
            cfg.send(sender, "log-none", Map.of("exam", exam.getDisplayName()));
            return;
        }
        cfg.send(sender, "log-header", Map.of(
                "exam", exam.getDisplayName(),
                "count", String.valueOf(logs.size())
        ));
        int index = 1;
        for (DataManager.ExamLog log : logs) {
            String result = cfg.getMessage("exam-log-result-" + log.result());
            String timeUsed = log.duration() > 0 ? formatTime(log.duration()) : "-";
            StringBuilder extra = new StringBuilder();
            if (log.challenges() > 0) {
                extra.append(" · 挑战x").append(log.challenges());
            }
            if (log.multiplier() > 1.0) {
                extra.append(" · 倍率x").append(ExamManager.formatMultiplier(log.multiplier()));
            }
            if (log.pauses() > 0) {
                extra.append(" · 暂停x").append(log.pauses());
            }
            cfg.send(sender, "log-entry", Map.of(
                    "index", String.valueOf(index),
                    "time", log.time(),
                    "result", result,
                    "time-used", timeUsed,
                    "extra", extra.toString()
            ));
            index++;
        }
    }

    private OfflinePlayer resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        return Bukkit.getOfflinePlayerIfCached(name);
    }

    private String formatTime(double seconds) {
        int total = (int) Math.round(seconds);
        return String.format("%d:%02d", total / 60, total % 60);
    }

    // ---------------- tab 补全 ----------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            if (sub.equals("start") || sub.equals("forcestart") || sub.equals("top")
                    || sub.equals("stats") || sub.equals("playing") || sub.equals("log")) {
                List<String> ids = new ArrayList<>();
                for (ExamConfig exam : plugin.getConfigManager().getExams()) {
                    ids.add(exam.getId());
                }
                return filter(ids, args[1]);
            }
            if (sub.equals("challenge")) {
                return filter(new ArrayList<>(plugin.getConfigManager().getChallengeConfig().getMultipliers().keySet()),
                        args[1]);
            }
            if (sub.equals("settings") || sub.equals("setting")) {
                return filter(List.of("mob-glow"), args[1]);
            }
        }
        if (args.length == 3 && sub.equals("challenge")) {
            return filter(List.of("on", "off"), args[2]);
        }
        if (args.length == 3 && (sub.equals("settings") || sub.equals("setting"))) {
            return filter(List.of("on", "off"), args[2]);
        }
        if (args.length == 3 && sub.equals("forcestart")) {
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                names.add(online.getName());
            }
            return filter(names, args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
                result.add(option);
            }
        }
        return result;
    }
}
