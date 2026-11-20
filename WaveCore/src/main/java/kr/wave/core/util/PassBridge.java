package kr.wave.core.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

/** PremiumPass 플러그인 연동 (리플렉션, 없으면 아무것도 하지 않음) */
public final class PassBridge {

    private PassBridge() {
    }

    private static Class<?> api() {
        if (!Bukkit.getPluginManager().isPluginEnabled("PremiumPass")) return null;
        try {
            Class<?> c = Class.forName("com.premiumpass.api.PremiumPassAPI", true, Bukkit.getPluginManager().getPlugin("PremiumPass").getClass().getClassLoader());
            return (boolean) c.getMethod("isAvailable").invoke(null) ? c : null;
        } catch (RuntimeException | LinkageError | ReflectiveOperationException t) {
            return null;
        }
    }

    public static boolean available() {
        return api() != null;
    }

    private static Object call(String name, Class<?>[] types, Object... args) {
        Class<?> c = api();
        if (c == null) return null;
        try {
            Method m = c.getMethod(name, types);
            return m.invoke(null, args);
        } catch (RuntimeException | LinkageError | ReflectiveOperationException t) {
            return null;
        }
    }

    public static int level(Player p) {
        Object o = call("getLevel", new Class[]{Player.class}, p);
        return o instanceof Integer i ? i : 0;
    }

    public static int maxLevel() {
        Object o = call("getMaxLevel", new Class[0]);
        return o instanceof Integer i ? i : 100;
    }

    /** 실제로 올라간 레벨 수 (-1 = 연동 불가) */
    public static int addLevels(Player p, int n) {
        Object o = call("addLevels", new Class[]{Player.class, int.class}, p, n);
        return o instanceof Integer i ? i : -1;
    }

    public static ItemStack voucher(int amount) {
        Object o = call("createVoucher", new Class[]{int.class}, amount);
        return o instanceof ItemStack it ? it : null;
    }

    public static boolean isPremium(Player p) {
        Object o = call("isPremium", new Class[]{Player.class}, p);
        return o instanceof Boolean b && b;
    }
}
