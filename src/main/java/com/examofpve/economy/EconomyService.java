package com.examofpve.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Vault 经济软依赖封装。Vault 缺失时 isAvailable() 返回 false，金币相关功能自动跳过。
 */
public class EconomyService {

    private final Economy economy;

    public EconomyService() {
        Economy econ = null;
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                econ = rsp.getProvider();
            }
        }
        this.economy = econ;
    }

    public boolean isAvailable() {
        return economy != null;
    }

    public boolean has(OfflinePlayer player, double amount) {
        return isAvailable() && economy.has(player, amount);
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        if (!isAvailable() || amount <= 0) {
            return false;
        }
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        if (!isAvailable() || amount <= 0) {
            return false;
        }
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }
}
