package kr.wave.core.module;

import kr.wave.core.WaveCore;
import kr.wave.core.data.GlobalData;
import kr.wave.core.util.Cmd;
import kr.wave.core.util.Discord;
import kr.wave.core.util.Dur;
import kr.wave.core.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 부계정(차단 우회) 감지.
 * 접속 주소는 그대로 저장하지 않고 서버마다 다른 값(salt)을 섞은 해시로만 저장합니다 (alts.yml).
 * - 차단된 계정과 같은 곳에서 다른 계정이 접속하면: 관리자 · 디스코드 알림 (alt-guard.action: alert)
 *   또는 접속 차단 (alt-guard.action: block)
 * - /부계정 <플레이어> : 같은 곳에서 접속한 적 있는 계정 목록 (관리자)
 * - 같은 곳 계정끼리의 처치는 순위(처치 수)에 넣지 않음
 */
public class AltModule extends Module {

    private static final int KEEP_PER_PLAYER = 5;

    private final Map<UUID, LinkedHashSet<String>> ipsOf = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> byIp = new ConcurrentHashMap<>();
    private String salt = "";
    private volatile boolean dirty;
    private int tick;

    public AltModule(WaveCore plugin) {
        super(plugin);
    }

    private File file() {
        return new File(plugin.getDataFolder(), "alts.yml");
    }

    @Override
    public void enable() {
        cmd("부계정", ADMIN, this::altsCmd, (s, a) -> a.length <= 1 ? null : List.of(), "alts");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file());
        salt = y.getString("salt", "");
        if (salt.isEmpty()) {
            byte[] b = new byte[16];
            new SecureRandom().nextBytes(b);
            salt = HexFormat.of().formatHex(b);
            dirty = true;
        }
        var sec = y.getConfigurationSection("players");
        if (sec != null) for (String k : sec.getKeys(false)) {
            try {
                UUID id = UUID.fromString(k);
                for (String h : sec.getStringList(k)) remember(id, h);
            } catch (IllegalArgumentException ignored) {   // 잘못된 줄은 건너뜀
            }
        }
    }

    @Override
    public void disable() {
        save();
    }

    private String hash(InetAddress addr) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            md.update(addr.getAddress());
            return HexFormat.of().formatHex(md.digest()).substring(0, 24);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);   // 모든 자바에 있는 알고리즘
        }
    }

    private void remember(UUID id, String h) {
        LinkedHashSet<String> set = ipsOf.computeIfAbsent(id, k -> new LinkedHashSet<>());
        synchronized (set) {
            set.remove(h);
            set.add(h);
            while (set.size() > KEEP_PER_PLAYER) {
                String old = set.iterator().next();
                set.remove(old);
                Set<UUID> users = byIp.get(old);
                if (users != null) users.remove(id);
            }
        }
        byIp.computeIfAbsent(h, k -> ConcurrentHashMap.newKeySet()).add(id);
    }

    /** id 와 같은 곳에서 접속한 적 있는 다른 계정 */
    public Set<UUID> altsOf(UUID id) {
        Set<UUID> out = new LinkedHashSet<>();
        LinkedHashSet<String> set = ipsOf.get(id);
        if (set == null) return out;
        List<String> copy;
        synchronized (set) {
            copy = new ArrayList<>(set);
        }
        for (String h : copy) for (UUID o : byIp.getOrDefault(h, Set.of())) if (!o.equals(id)) out.add(o);
        return out;
    }

    public boolean sameNetwork(UUID a, UUID b) {
        return altsOf(a).contains(b);
    }

    private GlobalData.Ban activeBan(UUID id) {
        GlobalData.Ban b = global().bans.get(id);
        return b != null && (b.permanent() || b.until > System.currentTimeMillis()) ? b : null;
    }

    private static String name(UUID id) {
        OfflinePlayer o = Bukkit.getOfflinePlayer(id);
        return o.getName() == null ? id.toString().substring(0, 8) : o.getName();
    }

    /** 접속 직전 (다른 스레드): 같은 곳의 차단된 계정 확인 */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        if (!plugin.getConfig().getBoolean("alt-guard.enabled", true)) return;
        String h = hash(e.getAddress());
        UUID me = e.getUniqueId();
        UUID bannedAlt = null;
        GlobalData.Ban ban = null;
        for (UUID o : byIp.getOrDefault(h, Set.of())) {
            if (o.equals(me)) continue;
            GlobalData.Ban b = activeBan(o);
            if (b != null) {
                bannedAlt = o;
                ban = b;
                break;
            }
        }
        if (bannedAlt == null) return;
        if (plugin.getConfig().getStringList("alt-guard.allow").stream().anyMatch(n -> n.equalsIgnoreCase(e.getName()))) return;
        boolean block = "block".equalsIgnoreCase(plugin.getConfig().getString("alt-guard.action", "alert"));
        String orig = name(bannedAlt);
        String why = ban.reason == null ? "-" : ban.reason;
        String left = ban.permanent() ? "영구" : Dur.left(ban.until) + " 남음";
        if (block) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, Msg.c("§c§l[🚫 Wave Online 🚫]%nl%%nl%§c차단된 계정과 같은 곳에서의 접속은 막혀 있습니다.%nl%%nl%§7차단 코드 : §f#"
                    + ban.code + "%nl%§7문의 : §f" + plugin.getConfig().getString("ban-contact", "관리자에게 문의하세요")));
        }
        String name = e.getName();
        Bukkit.getScheduler().runTask(plugin, () -> {
            String line = "&c&l[부계정] &f" + name + " &7님이 차단된 계정 &c" + orig + " &7과 같은 곳에서 접속" + (block ? "을 시도해 &c막았습니다" : "했습니다")
                    + ". &8(차단 " + left + " · " + Msg.esc(why) + ")";
            for (Player p : Bukkit.getOnlinePlayers()) if (p.hasPermission(ADMIN)) Msg.send(p, line);
            plugin.getLogger().warning(Msg.plainOf(line));
            Discord.send("alt", "🕵 부계정 의심 접속", "**" + name + "** ← 차단 계정 **" + orig + "**", Discord.ORANGE,
                    "조치", block ? "접속 차단" : "알림만", "원래 차단", left + " · " + why);
        });
    }

    /** 접속 (본 스레드): 접속한 곳 기록 */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        var addr = e.getPlayer().getAddress();
        if (addr == null || addr.getAddress() == null) return;
        remember(e.getPlayer().getUniqueId(), hash(addr.getAddress()));
        dirty = true;
    }

    @Override
    public void secondTick() {
        if (++tick % 30 == 0 && dirty) save();
    }

    public void save() {
        if (!dirty) return;
        dirty = false;
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("부계정 감지용 접속 기록. 주소는 해시로만 저장됩니다. 직접 고치지 마세요."));
        y.set("salt", salt);
        ipsOf.forEach((id, set) -> {
            synchronized (set) {
                y.set("players." + id, new ArrayList<>(set));
            }
        });
        try {
            y.save(file());
        } catch (IOException ex) {
            plugin.getLogger().warning("alts.yml 을 저장하지 못했습니다: " + ex.getMessage());
            dirty = true;
        }
    }

    private void altsCmd(CommandSender s, String l, String[] a) {
        OfflinePlayer t = a.length >= 1 ? Cmd.offline(a[0]) : null;
        if (t == null) {
            Msg.send(s, "&c/" + l + " <플레이어>");
            return;
        }
        Set<UUID> alts = altsOf(t.getUniqueId());
        Msg.send(s, "&6[부계정] &f" + t.getName() + " &7과 같은 곳에서 접속한 계정 : &f" + alts.size() + "개");
        for (UUID o : alts) {
            GlobalData.Ban b = activeBan(o);
            Msg.send(s, " &8- &f" + name(o) + (b != null ? " &c(차단 중 · " + (b.permanent() ? "영구" : Dur.left(b.until) + " 남음") + ")" : "")
                    + (Bukkit.getPlayer(o) != null ? " &a● 접속 중" : ""));
        }
        if (alts.isEmpty()) Msg.send(s, " &8없음");
        Msg.send(s, "&8가족 · 같은 PC방처럼 같은 곳을 쓰는 사람일 수도 있으니 참고만 하세요.");
    }

    /** /조회 용 한 줄 */
    public String summary(UUID id) {
        Set<UUID> alts = altsOf(id);
        if (alts.isEmpty()) return "&a없음";
        List<String> names = new ArrayList<>();
        for (UUID o : alts) names.add((activeBan(o) != null ? "&c" : "&f") + name(o));
        return String.join("&7, ", names);
    }
}
