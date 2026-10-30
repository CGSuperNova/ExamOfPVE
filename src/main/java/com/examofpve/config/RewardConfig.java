package com.examofpve.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * 考核成功后的奖励配置：金币（Vault）、经验值、自定义命令。
 */
public class RewardConfig {

    private final double money;
    private final int exp;
    private final List<String> commands;

    public RewardConfig(double money, int exp, List<String> commands) {
        this.money = money;
        this.exp = exp;
        this.commands = commands == null ? new ArrayList<>() : new ArrayList<>(commands);
    }

    public static RewardConfig parse(ConfigurationSection section) {
        if (section == null) {
            return new RewardConfig(0.0, 0, new ArrayList<>());
        }
        return new RewardConfig(
                section.getDouble("money", 0.0),
                section.getInt("exp", 0),
                section.getStringList("commands")
        );
    }

    public double getMoney() {
        return money;
    }

    public int getExp() {
        return exp;
    }

    public List<String> getCommands() {
        return commands;
    }
}
