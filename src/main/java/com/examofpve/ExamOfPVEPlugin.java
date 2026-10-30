package com.examofpve;

import com.examofpve.command.ExamCommand;
import com.examofpve.config.ConfigManager;
import com.examofpve.economy.EconomyService;
import com.examofpve.listener.ChallengeListener;
import com.examofpve.listener.PauseListener;
import com.examofpve.listener.PlayerListener;
import com.examofpve.listener.WorldProtectionListener;
import com.examofpve.manager.DataManager;
import com.examofpve.manager.ExamManager;
import com.examofpve.manager.WorldManager;
import com.examofpve.mob.MobSpawner;
import com.examofpve.placeholderapi.PlaceholderAPIExpansion;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ExamOfPVE 主类。
 * 面向 JDK 21 + Paper 1.21.11。
 */
public final class ExamOfPVEPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private WorldManager worldManager;
    private MobSpawner mobSpawner;
    private EconomyService economyService;
    private DataManager dataManager;
    private ExamManager examManager;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        configManager.reload();

        worldManager = new WorldManager(this);
        mobSpawner = new MobSpawner(this);
        economyService = new EconomyService();
        dataManager = new DataManager(this);
        examManager = new ExamManager(this);

        PluginCommand command = getCommand("exam");
        if (command != null) {
            ExamCommand executor = new ExamCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new WorldProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new ChallengeListener(this), this);
        getServer().getPluginManager().registerEvents(new PauseListener(this), this);

        if (!worldManager.isAvailable()) {
            getLogger().warning("未检测到 Multiverse-Core，世界加载/卸载功能不可用。");
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderAPIExpansion(this).register();
            getLogger().info("已注册 PlaceholderAPI 变量（%examofpve_*%）。");
        }

        getLogger().info("ExamOfPVE 已启用。");
    }

    @Override
    public void onDisable() {
        if (examManager != null) {
            examManager.shutdown();
        }
        if (dataManager != null) {
            dataManager.save();
        }
        getLogger().info("ExamOfPVE 已禁用。");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public WorldManager getWorldManager() {
        return worldManager;
    }

    public MobSpawner getMobSpawner() {
        return mobSpawner;
    }

    public EconomyService getEconomyService() {
        return economyService;
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public ExamManager getExamManager() {
        return examManager;
    }
}
