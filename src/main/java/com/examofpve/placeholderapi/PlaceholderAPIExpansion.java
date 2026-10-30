package com.examofpve.placeholderapi;

import com.examofpve.ExamOfPVEPlugin;
import com.examofpve.config.ExamConfig;
import com.examofpve.manager.DataManager;
import com.examofpve.manager.ExamManager;
import com.examofpve.session.ExamSession;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PlaceholderAPI 扩展：注册 %examofpve_*% 变量。
 *
 * <pre>
 * 玩家相关（%examofpve_xxx% 需由在线玩家触发，或用 PAPI 的 %xxx_player% 指定目标）：
 *   in_exam                  是否正在考核（true/false）
 *   exam_id                  当前考核 id
 *   multiplier               当前考核最终挑战倍率（未考核时为 1）
 *   selectedchallenges       已选挑战数量
 *   selectedchallenges_list  已选挑战 id 列表（逗号分隔）
 *   challenge_&lt;id&gt;           玩家是否已启用挑战 &lt;id&gt;（true/false）
 *   stats_&lt;id&gt;_plays        某考核参加次数
 *   stats_&lt;id&gt;_wins         某考核成功次数
 *   stats_&lt;id&gt;_best         某考核最佳用时（秒）
 *   stats_&lt;id&gt;_best_fmt      某考核最佳用时（格式化 mm:ss，无则为"无"）
 *
 * 全局：
 *   available_exams          当前可参加（开放中）的考核 id 列表（逗号分隔，无则空）
 *   total_playing            所有考核进行中总人数
 *   playing_&lt;id&gt;             某考核当前人数
 *   open_&lt;id&gt;               某考核是否开放（true/false）
 *   top_&lt;id&gt;_&lt;rank&gt;_name     某考核排行榜第 rank 名玩家名（rank 1~10）
 *   top_&lt;id&gt;_&lt;rank&gt;_time     某考核排行榜第 rank 名成绩（秒）
 * </pre>
 */
public class PlaceholderAPIExpansion extends PlaceholderExpansion {

    private static final Pattern TOP_PATTERN =
            Pattern.compile("top_(.+)_([1-9]|10)_(name|time)");

    private final ExamOfPVEPlugin plugin;

    public PlaceholderAPIExpansion(ExamOfPVEPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "examofpve";
    }

    @Override
    public String getAuthor() {
        return "ExamOfPVE";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (params == null) {
            return "";
        }
        String p = params.toLowerCase(Locale.ROOT);
        DataManager dm = plugin.getDataManager();
        ExamManager em = plugin.getExamManager();

        // ---- 玩家相关变量 ----
        if (player != null) {
            ExamSession session = em.getSession(player.getUniqueId());
            switch (p) {
                case "in_exam" -> {
                    return session != null ? "true" : "false";
                }
                case "exam_id" -> {
                    return session != null ? session.getExam().getId() : "";
                }
                case "multiplier" -> {
                    return session != null
                            ? ExamManager.formatMultiplier(session.getMultiplier()) : "1";
                }
                case "selectedchallenges" -> {
                    return String.valueOf(dm.getSelectedChallenges(player.getUniqueId()).size());
                }
                case "selectedchallenges_list" -> {
                    return String.join(", ", dm.getSelectedChallenges(player.getUniqueId()));
                }
                default -> {
                    // 继续向下处理 challenge_* / stats_*
                }
            }

            if (p.startsWith("challenge_")) {
                // 挑战 id 使用连字符（如 half-health），占位符中下划线/连字符均兼容
                String id = p.substring("challenge_".length()).replace('_', '-');
                return dm.getSelectedChallenges(player.getUniqueId()).contains(id)
                        ? "true" : "false";
            }

            if (p.startsWith("stats_")) {
                String rest = p.substring("stats_".length());
                if (rest.endsWith("_plays")) {
                    String id = rest.substring(0, rest.length() - "_plays".length());
                    return String.valueOf(dm.getStats(player.getUniqueId(), id).plays);
                }
                if (rest.endsWith("_wins")) {
                    String id = rest.substring(0, rest.length() - "_wins".length());
                    return String.valueOf(dm.getStats(player.getUniqueId(), id).wins);
                }
                if (rest.endsWith("_best_fmt")) {
                    String id = rest.substring(0, rest.length() - "_best_fmt".length());
                    double t = dm.getStats(player.getUniqueId(), id).bestTime;
                    return t > 0 ? formatTime(t) : "无";
                }
                if (rest.endsWith("_best")) {
                    String id = rest.substring(0, rest.length() - "_best".length());
                    return String.valueOf(dm.getStats(player.getUniqueId(), id).bestTime);
                }
            }
        }

        // ---- 全局变量 ----
        if (p.equals("available_exams")) {
            List<String> ids = new ArrayList<>();
            for (ExamConfig exam : plugin.getConfigManager().getExams()) {
                if (exam.getOpenWindow() == null || exam.getOpenWindow().isOpenNow()) {
                    ids.add(exam.getId());
                }
            }
            return String.join(", ", ids);
        }
        if (p.equals("total_playing")) {
            return String.valueOf(em.getTotalPlaying());
        }
        if (p.startsWith("playing_")) {
            return String.valueOf(em.countPlaying(p.substring("playing_".length())));
        }
        if (p.startsWith("open_")) {
            ExamConfig exam = plugin.getConfigManager().getExam(p.substring("open_".length()));
            if (exam == null) {
                return "false";
            }
            return exam.getOpenWindow() == null || exam.getOpenWindow().isOpenNow()
                    ? "true" : "false";
        }
        Matcher m = TOP_PATTERN.matcher(p);
        if (m.matches()) {
            String examId = m.group(1);
            int rank = Integer.parseInt(m.group(2));
            String field = m.group(3);
            List<DataManager.LeaderboardEntry> lb = dm.getLeaderboard(examId);
            if (rank < 1 || rank > lb.size()) {
                return "";
            }
            DataManager.LeaderboardEntry entry = lb.get(rank - 1);
            return "name".equals(field) ? entry.name() : String.valueOf(entry.timeSeconds());
        }
        return "";
    }

    private String formatTime(double seconds) {
        int total = (int) Math.round(seconds);
        return String.format("%d:%02d", total / 60, total % 60);
    }
}
