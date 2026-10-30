package com.examofpve.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * 完成指定数量挑战的额外奖励（每个完成数量档位可完全自定义）：
 * 金币 / 经验 / 点券（走全局 settings.voucher-command 模板）/ 自定义命令。
 */
public class ChallengeRewardConfig {

    private final double money;
    private final int exp;
    private final int points;
    private final List<String> commands;

    public ChallengeRewardConfig(double money, int exp, int points, List<String> commands) {
        this.money = money;
        this.exp = exp;
        this.points = points;
        this.commands = commands == null ? new ArrayList<>() : new ArrayList<>(commands);
    }

    public static ChallengeRewardConfig parse(ConfigurationSection section) {
        if (section == null) {
            return new ChallengeRewardConfig(0, 0, 0, new ArrayList<>());
        }
        return new ChallengeRewardConfig(
                section.getDouble("money", 0.0),
                section.getInt("exp", 0),
                section.getInt("points", 0),
                section.getStringList("commands")
        );
    }

    public boolean isEmpty() {
        return money <= 0 && exp <= 0 && points <= 0 && commands.isEmpty();
    }

    public double getMoney() {
        return money;
    }

    public int getExp() {
        return exp;
    }

    public int getPoints() {
        return points;
    }

    public List<String> getCommands() {
        return commands;
    }
}
