package com.dailyshop.currency;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/** WaveCore 캐시 결제 (리플렉션, WaveCore 가 없으면 사용 불가) */
public class WaveCashCurrency implements Currency {

    private final Method get, take;

    private WaveCashCurrency(Method get, Method take) {
        this.get = get;
        this.take = take;
    }

    /** WaveCore 가 없으면 null */
    public static WaveCashCurrency tryCreate() {
        Plugin w = Bukkit.getPluginManager().getPlugin("WaveCore");
        if (w == null || !w.isEnabled()) return null;
        try {
            Class<?> c = Class.forName("kr.wave.core.api.WaveAPI", true, w.getClass().getClassLoader());
            return new WaveCashCurrency(c.getMethod("getCash", Player.class, boolean.class), c.getMethod("takeCash", Player.class, long.class, boolean.class));
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public String id() {
        return "WAVECASH";
    }

    @Override
    public long balance(Player p) {
        try {
            return (long) get.invoke(null, p, false);
        } catch (Throwable t) {
            return 0;
        }
    }

    @Override
    public boolean withdraw(Player p, long amount) {
        try {
            return (boolean) take.invoke(null, p, amount, false);
        } catch (Throwable t) {
            return false;
        }
    }
}
