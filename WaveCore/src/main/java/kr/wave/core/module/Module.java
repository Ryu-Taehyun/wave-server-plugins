package kr.wave.core.module;

import kr.wave.core.WaveCore;
import kr.wave.core.data.GlobalData;
import kr.wave.core.data.PlayerData;
import kr.wave.core.util.Cmd;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

/** 기능 묶음의 공통 부모 */
public abstract class Module implements Listener {

    protected final WaveCore plugin;

    protected Module(WaveCore plugin) {
        this.plugin = plugin;
    }

    /** 명령어 등록 등 */
    public void enable() {
    }

    /** 1초마다 호출 (Skript 의 every 1 second) */
    public void secondTick() {
    }

    public void disable() {
    }

    protected PlayerData data(Player p) {
        return plugin.store().get(p);
    }

    protected GlobalData global() {
        return plugin.store().global();
    }

    protected void cmd(String name, String permission, Cmd.Exec exec, String... aliases) {
        Cmd.register(name, permission, exec, aliases);
    }

    protected void cmd(String name, String permission, String permMessage, Cmd.Exec exec, String... aliases) {
        Cmd.register(name, permission, permMessage, exec, aliases);
    }

    protected void cmd(String name, String permission, Cmd.Exec exec, Cmd.Tab tab, String... aliases) {
        Cmd.register(name, permission, null, exec, tab, aliases);
    }

    protected static final String ADMIN = "wave.admin";

    protected String world(String key) {
        // 값이 없으면 플러그인 기본값(spawn-world, world ...)을 씀 (config 에 worlds: 가 두 번 들어가 앞의 값이 지워진 경우 등)
        String w = plugin.getConfig().getString("worlds." + key);
        return w == null || w.isBlank() ? key : w;
    }

    protected boolean inWorld(Player p, String key) {
        return p.getWorld().getName().equalsIgnoreCase(world(key));
    }

    /** 스폰 위치: '/wave setwarp spawn' 위치, 없으면 스폰 월드의 스폰 지점 */
    protected Location spawnPoint() {
        Location l = global().warp("spawn");
        return l != null ? l : spawnOf("spawn");
    }

    /** Multiverse 의 /mv tp 플레이어 월드 와 같은 동작: 해당 월드 스폰으로 이동 (스폰은 지정한 스폰 위치) */
    protected void tpWorld(Player p, String key) {
        if (key.equals("spawn")) {
            Location l = spawnPoint();
            if (l != null) {
                p.teleport(l);
                return;
            }
        }
        World w = Bukkit.getWorld(world(key));
        if (w == null) {
            plugin.getLogger().warning("월드 '" + world(key) + "' 를 찾을 수 없습니다. (config.yml 의 worlds." + key + ")");
            return;
        }
        p.teleport(w.getSpawnLocation().add(0.5, 0, 0.5));
    }

    protected Location spawnOf(String key) {
        World w = Bukkit.getWorld(world(key));
        return w == null ? null : w.getSpawnLocation().add(0.5, 0, 0.5);
    }

    /** 튜토리얼 단계 완료 알림 */
    protected void tutorial(Player p, String step) {
        TutorialModule t = plugin.module(TutorialModule.class);
        if (t != null) t.step(p, step);
    }

    protected void later(long ticks, Runnable r) {
        Bukkit.getScheduler().runTaskLater(plugin, r, ticks);
    }

    protected void console(String command) {
        plugin.hooks().console(command);
    }

    /** 닉네임 색상 코드 */
    protected String nickColor(Player p) {
        return nickColor(data(p).nickColor, "&f");
    }

    public static final String GRADIENT_GOLD = "<gradient:#FFF176:#FFB732>";   // 크라운 표식과 같은 색
    public static final String GLACIER = "<gradient:#39C7FF:#C2FFF5>";

    /** 닉네임 색상을 입힌 이름 (그라데이션 색상은 이름을 감싸야 해서 앞에 붙이는 코드만으로는 안 됨) */
    public static String nickName(int c, String name, String def) {
        return switch (c) {
            case 5 -> "<reset>" + GRADIENT_GOLD + name + "</gradient>";   // reset: 앞의 &k 등이 이어지지 않도록
            case 6 -> "<reset>" + GLACIER + name + "</gradient>";
            default -> nickColor(c, def) + name;
        };
    }

    public static String nickColor(int c, String def) {
        return switch (c) {
            case 1 -> "&c";
            case 2 -> "&6";
            case 3 -> "&5";
            case 4 -> "&d";
            case 5 -> "<#FFD54F>";
            case 6 -> "<#7FDDFB>";
            default -> def;
        };
    }
}
