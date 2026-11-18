package com.premiumpass.util;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class Text {

    private static String prefix = "";

    private Text() {
    }

    public static void setPrefix(String p) {
        prefix = color(p == null ? "" : p);
    }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    public static List<String> color(List<String> list) {
        List<String> out = new ArrayList<>(list.size());
        for (String s : list) out.add(color(s));
        return out;
    }

    public static void send(CommandSender to, String msg) {
        to.sendMessage(prefix + color(msg));
    }

    public static void actionBar(Player p, String msg) {
        p.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(color(msg)));
    }

    /** 진행 바: ■■■■□□□□ */
    public static String bar(long current, long max, int length) {
        double ratio = max <= 0 ? 1 : Math.min(1.0, Math.max(0, (double) current / max));
        int filled = (int) Math.round(ratio * length);
        StringBuilder sb = new StringBuilder("&a");
        for (int i = 0; i < length; i++) {
            if (i == filled) sb.append("&8");
            sb.append('■');
        }
        return sb.toString();
    }

    public static String num(long n) {
        return String.format("%,d", n);
    }

    public static String duration(Duration d) {
        long h = d.toHours();
        long m = d.toMinutesPart();
        if (h > 0) return h + "시간 " + m + "분";
        return Math.max(1, m) + "분";
    }
}
