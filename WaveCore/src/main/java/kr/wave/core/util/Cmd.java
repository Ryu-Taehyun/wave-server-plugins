package kr.wave.core.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * 명령어를 코드에서 바로 등록한다. Skript 처럼 같은 이름의 기존 명령어(/help, /spawn 등)를 덮어쓴다.
 */
public final class Cmd {

    public interface Exec {
        void run(CommandSender s, String label, String[] a);
    }

    public static final String NO_PERM = "&c당신은 이 명령어를 사용할 권한이 없습니다.";

    private Cmd() {
    }

    /** 자동완성: 지금까지 입력한 인자를 받아 후보 목록 */
    public interface Tab {
        java.util.List<String> complete(CommandSender s, String[] a);
    }

    public static void register(String name, String permission, String permMessage, Exec exec, String... aliases) {
        register(name, permission, permMessage, exec, null, aliases);
    }

    public static void register(String name, String permission, String permMessage, Exec exec, Tab tab, String... aliases) {
        BukkitCommand cmd = new BukkitCommand(name, "", "/" + name, Arrays.asList(aliases)) {
            @Override
            public @NotNull java.util.List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
                if (tab == null) return super.tabComplete(sender, alias, args);
                if (permission != null && !sender.hasPermission(permission)) return java.util.List.of();
                String cur = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
                java.util.List<String> out = new java.util.ArrayList<>();
                for (String c : tab.complete(sender, args)) if (c.toLowerCase().startsWith(cur)) out.add(c);
                return out;
            }

            @Override
            public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
                if (permission != null && !sender.hasPermission(permission)) {
                    Msg.send(sender, permMessage != null ? permMessage : NO_PERM);
                    return true;
                }
                try {
                    exec.run(sender, label, args);
                } catch (RuntimeException ex) {   // 명령어 하나의 오류가 서버 전체로 번지지 않게
                    Bukkit.getLogger().log(Level.SEVERE, "[WaveCore] /" + name + " 처리 중 오류", ex);
                    Msg.send(sender, "&c명령어 처리 중 오류가 발생했습니다.");
                }
                return true;
            }
        };
        if (permission != null) cmd.setPermission(permission);   // 권한이 없으면 / 자동완성 목록에서 숨김
        CommandMap map = Bukkit.getCommandMap();
        map.register("wavecore", cmd);
        Map<String, Command> known = map.getKnownCommands();
        known.put(name.toLowerCase(), cmd);
        for (String a : aliases) known.put(a.toLowerCase(), cmd);
    }

    public static void register(String name, String permission, Exec exec, String... aliases) {
        register(name, permission, null, exec, aliases);
    }

    /* ---------------- 인자 도우미 ---------------- */

    public static String arg(String[] a, int i) {
        return i < a.length ? a[i] : null;
    }

    /** i 번째부터 끝까지 이어 붙인 문자열 */
    public static String rest(String[] a, int i) {
        return i < a.length ? String.join(" ", Arrays.copyOfRange(a, i, a.length)) : null;
    }

    public static Player online(String name) {
        if (name == null) return null;
        Player p = Bukkit.getPlayerExact(name);
        return p != null ? p : Bukkit.getPlayer(name);
    }

    /** 접속한 적 있는 플레이어 (온라인 우선) */
    public static OfflinePlayer offline(String name) {
        if (name == null) return null;
        Player p = online(name);
        if (p != null) return p;
        OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(name);
        return op != null && op.hasPlayedBefore() ? op : null;
    }

    public static Long num(String s) {
        if (s == null) return null;
        try {
            return Long.parseLong(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Double dbl(String s) {
        if (s == null) return null;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static boolean isPlayer(CommandSender s) {
        if (s instanceof Player) return true;
        Msg.send(s, "&c게임 안에서만 사용할 수 있습니다.");
        return false;
    }

    public static List<String> none() {
        return List.of();
    }
}
