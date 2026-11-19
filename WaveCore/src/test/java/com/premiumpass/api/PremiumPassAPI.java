package com.premiumpass.api;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 테스트용 PremiumPass API 대역 */
public final class PremiumPassAPI {
    public static final Map<UUID, Integer> LEVEL = new HashMap<>();

    public static boolean isAvailable() {
        return true;
    }

    public static int getLevel(Player p) {
        return LEVEL.getOrDefault(p.getUniqueId(), 1);
    }

    public static int getMaxLevel() {
        return 100;
    }

    public static int addLevels(Player p, int n) {
        int before = getLevel(p);
        int after = Math.min(100, before + n);
        LEVEL.put(p.getUniqueId(), after);
        return after - before;
    }

    public static ItemStack createVoucher(int amount) {
        ItemStack it = new ItemStack(Material.PAPER, amount);
        var m = it.getItemMeta();
        m.setDisplayName("프리미엄 패스권");
        it.setItemMeta(m);
        return it;
    }

    public static boolean isPremium(Player p) {
        return false;
    }
}
