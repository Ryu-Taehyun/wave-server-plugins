package com.dailyshop.currency;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;

/**
 * Vault 경제 결제. Vault API 를 컴파일 의존성 없이 리플렉션으로 호출한다.
 */
public class VaultCurrency implements Currency {

    private final Object economy;
    private final Method getBalance;
    private final Method withdrawPlayer;
    private final Method transactionSuccess;

    private VaultCurrency(Object economy, Method getBalance, Method withdrawPlayer, Method transactionSuccess) {
        this.economy = economy;
        this.getBalance = getBalance;
        this.withdrawPlayer = withdrawPlayer;
        this.transactionSuccess = transactionSuccess;
    }

    /** Vault 와 경제 플러그인이 있으면 생성, 없으면 null */
    public static VaultCurrency tryCreate() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) return null;
        try {
            Class<?> ecoClass = Class.forName("net.milkbowl.vault.economy.Economy", true, VaultCurrency.class.getClassLoader());
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(ecoClass);
            if (rsp == null) return null;
            Object eco = rsp.getProvider();
            Method bal = ecoClass.getMethod("getBalance", OfflinePlayer.class);
            Method wd = ecoClass.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            Method ok = wd.getReturnType().getMethod("transactionSuccess");
            return new VaultCurrency(eco, bal, wd, ok);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    @Override
    public String id() {
        return "VAULT";
    }

    @Override
    public long balance(Player p) {
        try {
            return (long) Math.floor((double) getBalance.invoke(economy, p));
        } catch (ReflectiveOperationException e) {
            return 0;
        }
    }

    @Override
    public boolean withdraw(Player p, long amount) {
        if (balance(p) < amount) return false;
        try {
            Object resp = withdrawPlayer.invoke(economy, p, (double) amount);
            return (boolean) transactionSuccess.invoke(resp);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
