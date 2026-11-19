package kr.wave.core;

import kr.wave.core.util.Log;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.Collection;

/**
 * 다른 플러그인 연동 (컴파일 의존성 없이 리플렉션 사용)
 * - Vault Chat: 칭호(접두사) 읽기  (Skript 의 player's prefix)
 * - WorldGuard: 구역 이름 확인    (Skript 의 region enter)
 * - 콘솔 명령어: LuckPerms, TAB, CrazyCrates, BetterRTP, Essentials 명령어 실행
 */
public class Hooks {

    private final WaveCore plugin;
    private Object vaultChat;
    private Method vaultPrefix;
    private boolean vaultTried;

    public Hooks(WaveCore plugin) {
        this.plugin = plugin;
    }

    public void console(String command) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.startsWith("/") ? command.substring(1) : command);
    }

    /** LuckPerms 등 Vault Chat 연동 플러그인의 접두사. 없으면 "" */
    public String prefix(Player p) {
        if (!vaultTried) {
            vaultTried = true;
            try {
                Class<?> chatClass = Class.forName("net.milkbowl.vault.chat.Chat", true, getClass().getClassLoader());
                RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(chatClass);
                if (rsp != null) {
                    vaultChat = rsp.getProvider();
                    vaultPrefix = chatClass.getMethod("getPlayerPrefix", Player.class);
                }
            } catch (ReflectiveOperationException | LinkageError e) {
                Log.once("hook:vault", "Vault Chat 연동 중 오류: " + e);
            }
            if (vaultChat == null) plugin.getLogger().warning("Vault Chat 을 찾지 못했습니다. 채팅/탭에 칭호가 표시되지 않습니다.");
        }
        if (vaultChat == null) return "";
        try {
            Object r = vaultPrefix.invoke(vaultChat, p);
            return r == null ? "" : r.toString();
        } catch (ReflectiveOperationException e) {
            Log.once("hook:vault-prefix", "칭호(접두사)를 읽지 못했습니다: " + e);
            return "";
        }
    }

    /** 위치가 WorldGuard 구역(id)에 들어 있는지. WorldGuard 가 없으면 false */
    public boolean inRegion(Location loc, String regionId) {
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) return false;
        try {
            ClassLoader cl = getClass().getClassLoader();
            Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard", true, cl);
            Object wg = wgClass.getMethod("getInstance").invoke(null);
            Object platform = call(wg, "getPlatform");
            Object container = call(platform, "getRegionContainer");
            Object query = call(container, "createQuery");
            Class<?> adapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter", true, cl);
            Object weLoc = adapter.getMethod("adapt", Location.class).invoke(null, loc);
            Object set = call(query, "getApplicableRegions", weLoc);
            Object regions = call(set, "getRegions");
            if (regions instanceof Collection<?> c) {
                for (Object r : c) {
                    if (regionId.equalsIgnoreCase(String.valueOf(call(r, "getId")))) return true;
                }
            }
        } catch (Exception | LinkageError e) {
            Log.once("hook:wg-region", "WorldGuard 구역 확인에 실패했습니다. 보호구역 판정이 동작하지 않습니다: " + e);
        }
        return false;
    }

    /** 이 위치에서 WorldGuard 구역이 PvP 를 막는지 (pvp 플래그가 deny 로 정해진 곳만). WorldGuard 가 없거나 확인 실패 시 false */
    public boolean pvpDenied(Location loc) {
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) return false;
        try {
            ClassLoader cl = getClass().getClassLoader();
            Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard", true, cl);
            Object wg = wgClass.getMethod("getInstance").invoke(null);
            Object query = call(call(call(wg, "getPlatform"), "getRegionContainer"), "createQuery");
            Class<?> adapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter", true, cl);
            Object weLoc = adapter.getMethod("adapt", Location.class).invoke(null, loc);
            Class<?> flags = Class.forName("com.sk89q.worldguard.protection.flags.Flags", true, cl);
            Object pvp = flags.getField("PVP").get(null);
            Class<?> stateFlag = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag", true, cl);
            Object arr = java.lang.reflect.Array.newInstance(stateFlag, 1);
            java.lang.reflect.Array.set(arr, 0, pvp);
            Class<?> assoc = Class.forName("com.sk89q.worldguard.protection.association.RegionAssociable", true, cl);
            Class<?> weLocClass = Class.forName("com.sk89q.worldedit.util.Location", true, cl);
            // queryState: 구역이 pvp 를 정하지 않았으면 null, 정했으면 ALLOW / DENY.
            // (testState 는 아무도 정하지 않은 곳도 false 를 돌려줘서, 일반 야생까지 PvP 금지로 잘못 판단했음)
            Method m = query.getClass().getMethod("queryState", weLocClass, assoc, arr.getClass());
            Object r = m.invoke(query, weLoc, null, arr);
            return r != null && "DENY".equals(r.toString());
        } catch (Exception | LinkageError e) {
            Log.once("hook:wg-pvp", "WorldGuard PvP 확인에 실패했습니다. PvP 금지 구역 스킬 제한이 동작하지 않습니다: " + e);
            return false;
        }
    }

    private static Object call(Object target, String name, Object... args) throws ReflectiveOperationException {
        Class<?> c = target.getClass();
        while (c != null) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == args.length && paramsMatch(m, args)) {
                    m.setAccessible(true);
                    return m.invoke(target, args);
                }
            }
            for (Class<?> i : c.getInterfaces()) {
                for (Method m : i.getMethods()) {
                    if (m.getName().equals(name) && m.getParameterCount() == args.length && paramsMatch(m, args)) {
                        m.setAccessible(true);
                        return m.invoke(target, args);
                    }
                }
            }
            c = c.getSuperclass();
        }
        throw new NoSuchMethodException(name);
    }

    private static boolean paramsMatch(Method m, Object[] args) {
        Class<?>[] types = m.getParameterTypes();
        for (int i = 0; i < types.length; i++) {
            if (args[i] != null && !types[i].isInstance(args[i])) return false;
        }
        return true;
    }
}
