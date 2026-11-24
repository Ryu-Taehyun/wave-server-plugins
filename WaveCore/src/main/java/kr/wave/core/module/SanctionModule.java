package kr.wave.core.module;

import io.papermc.paper.event.player.AsyncChatEvent;
import kr.wave.core.WaveCore;
import kr.wave.core.data.GlobalData;
import kr.wave.core.data.PlayerData;
import kr.wave.core.util.Cmd;
import kr.wave.core.util.Discord;
import kr.wave.core.util.Dur;
import kr.wave.core.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 제재와 채팅 관리
 * - /경고 <플레이어> <사유>          누적되면 config sanctions.warn-actions 에 따라 자동 처벌
 * - /경고목록 [플레이어] · /경고삭제 <플레이어> [개수|전체]
 * - /임시밴 <플레이어> <기간> <사유>  기간: 30m 2h 7d 1w (영구는 /serverban)
 * - /뮤트 <플레이어> <기간> [사유] · /뮤트해제 <플레이어>
 * - 채팅 필터: 금지어 가리기, 다른 서버 주소 · IP 광고 막기, 도배 막기 (귓속말 포함)
 * 모든 제재는 플레이어 기록 · logs/sanctions.log · 디스코드(설정 시)에 남습니다.
 */
public class SanctionModule extends Module {

    private static final String TAG = "&c&l[&f&l제재&c&l] ";

    public SanctionModule(WaveCore plugin) {
        super(plugin);
    }

    @Override
    public void enable() {
        cmd("경고", ADMIN, this::warnCmd, (s, a) -> a.length <= 1 ? null : List.of(), "warn");
        cmd("경고목록", null, this::warnListCmd, (s, a) -> a.length <= 1 && s.hasPermission(ADMIN) ? null : List.of(), "warnings");
        cmd("경고삭제", ADMIN, this::warnClearCmd, (s, a) -> a.length <= 1 ? null : a.length == 2 ? List.of("1", "전체") : List.of(), "unwarn");
        cmd("임시밴", ADMIN, this::tempBanCmd, (s, a) -> a.length <= 1 ? null : a.length == 2 ? List.of("1h", "1d", "3d", "7d", "30d") : List.of(), "tempban");
        cmd("뮤트", ADMIN, this::muteCmd, (s, a) -> a.length <= 1 ? null : a.length == 2 ? List.of("10m", "30m", "1h", "1d") : List.of(), "mute");
        cmd("뮤트해제", ADMIN, this::unmuteCmd, (s, a) -> a.length <= 1 ? null : List.of(), "unmute");
    }

    /* ======================= 기록 ======================= */

    /** 플레이어 기록 + 로그 파일 + 디스코드 */
    public void record(OfflinePlayer t, String kind, String detail, String by) {
        PlayerData d = plugin.store().get(t);
        d.sanctions.add(Dur.now() + " | " + kind + " | " + detail + " | " + by);
        while (d.sanctions.size() > 50) d.sanctions.remove(0);
        plugin.store().saveAuto(d);
        File dir = new File(plugin.getDataFolder(), "logs");
        dir.mkdirs();
        try {
            Files.writeString(new File(dir, "sanctions.log").toPath(), Dur.now() + " | " + kind + " | " + t.getName() + "(" + t.getUniqueId() + ") | "
                    + Msg.plainOf(detail) + " | " + by + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            plugin.getLogger().warning("제재 기록을 남기지 못했습니다: " + e.getMessage());
        }
        int color = kind.contains("해제") || kind.contains("삭제") ? Discord.GREEN : kind.contains("경고") ? Discord.ORANGE : Discord.RED;
        Discord.send("sanction", "🔨 " + kind, "**" + t.getName() + "**", color, "내용", detail, "처리자", by, "누적 경고", String.valueOf(d.warnings));
    }

    /* ======================= 경고 ======================= */

    private void warnCmd(CommandSender s, String l, String[] a) {
        if (a.length < 2) {
            Msg.send(s, "&c/" + l + " <플레이어> <사유>");
            return;
        }
        OfflinePlayer t = Cmd.offline(a[0]);
        if (t == null) {
            Msg.send(s, "&c플레이어를 찾을 수 없습니다.");
            return;
        }
        String reason = Cmd.rest(a, 1);
        PlayerData d = plugin.store().get(t);
        d.warnings++;
        record(t, "경고", reason + " (누적 " + d.warnings + "회)", s.getName());
        Msg.send(s, TAG + "&f" + t.getName() + "&7에게 경고를 주었습니다. &8(누적 " + d.warnings + "회)");
        if (t.getPlayer() != null) {
            Player p = t.getPlayer();
            Msg.send(p, " ");
            Msg.send(p, TAG + "&c관리자로부터 경고를 받았습니다. &7(누적 &f" + d.warnings + "회&7)");
            Msg.send(p, "  &7사유 : &f" + Msg.esc(reason));
            Msg.send(p, "  &8경고가 쌓이면 자동으로 임시 차단될 수 있습니다. (/경고목록)");
            Msg.send(p, " ");
            Msg.title(p, "&c&l경고 " + d.warnings + "회", "&f" + Msg.esc(reason), 5, 60, 10);
            Msg.sound(p, "entity.elder_guardian.curse", 0.6f, 1.2f);
        }
        autoAction(t, d.warnings, s.getName());
    }

    /** 누적 경고 수에 맞는 자동 처벌 (config sanctions.warn-actions) */
    private void autoAction(OfflinePlayer t, int count, String by) {
        List<Map<?, ?>> rules = plugin.getConfig().getMapList("sanctions.warn-actions");
        if (rules.isEmpty()) rules = List.of(Map.of("count", 3, "action", "mute", "duration", "1h"),
                Map.of("count", 5, "action", "tempban", "duration", "1d"),
                Map.of("count", 7, "action", "tempban", "duration", "7d"),
                Map.of("count", 10, "action", "ban", "duration", "영구"));
        for (Map<?, ?> r : rules) {
            Object c = r.get("count");
            if (!(c instanceof Number n) || n.intValue() != count) continue;
            String action = String.valueOf(r.get("action"));
            long dur = Dur.parse(String.valueOf(r.get("duration")));
            String why = "경고 " + count + "회 누적 (자동)";
            switch (action) {
                case "mute" -> mute(t, Math.max(60_000, dur), why, by);
                case "tempban" -> ban(t, Math.max(60_000, dur), why, by);
                case "ban" -> ban(t, 0, why, by);
                default -> plugin.getLogger().warning("sanctions.warn-actions: 알 수 없는 action '" + action + "'");
            }
            return;
        }
    }

    private void warnListCmd(CommandSender s, String l, String[] a) {
        OfflinePlayer t = a.length >= 1 && s.hasPermission(ADMIN) ? Cmd.offline(a[0]) : s instanceof Player p ? p : null;
        if (t == null) {
            Msg.send(s, "&c플레이어를 찾을 수 없습니다.");
            return;
        }
        PlayerData d = plugin.store().get(t);
        Msg.send(s, TAG + "&f" + t.getName() + "&7의 누적 경고 : &c" + d.warnings + "회");
        List<String> rows = new ArrayList<>();
        for (String x : d.sanctions) if (x.contains("| 경고 |") || x.contains("| 경고 삭제 |")) rows.add(x);
        int from = Math.max(0, rows.size() - 10);
        for (String x : rows.subList(from, rows.size())) {
            String[] p = x.split(" \\| ", 4);
            Msg.send(s, " &8- &7" + (p.length > 0 ? p[0] : "") + " &f" + (p.length > 2 ? Msg.esc(p[2]) : x));
        }
        if (rows.isEmpty()) Msg.send(s, " &8경고 기록이 없습니다.");
    }

    private void warnClearCmd(CommandSender s, String l, String[] a) {
        OfflinePlayer t = a.length >= 1 ? Cmd.offline(a[0]) : null;
        if (t == null) {
            Msg.send(s, "&c/" + l + " <플레이어> [개수|전체]");
            return;
        }
        PlayerData d = plugin.store().get(t);
        int n = a.length >= 2 && !a[1].equals("전체") && !a[1].equalsIgnoreCase("all") ? Math.max(1, parseInt(a[1])) : d.warnings;
        int before = d.warnings;
        d.warnings = Math.max(0, d.warnings - n);
        record(t, "경고 삭제", (before - d.warnings) + "회 삭제 (남은 경고 " + d.warnings + "회)", s.getName());
        Msg.send(s, TAG + "&f" + t.getName() + "&7의 경고 &f" + (before - d.warnings) + "회&7를 지웠습니다. &8(남은 " + d.warnings + "회)");
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /* ======================= 차단 ======================= */

    private void tempBanCmd(CommandSender s, String l, String[] a) {
        if (a.length < 3) {
            Msg.send(s, "&c/" + l + " <플레이어> <기간> <사유> &7(기간: 30m 2h 7d 1w · 영구는 /serverban)");
            return;
        }
        OfflinePlayer t = Cmd.offline(a[0]);
        long dur = Dur.parse(a[1]);
        if (t == null || dur <= 0) {
            Msg.send(s, t == null ? "&c플레이어를 찾을 수 없습니다." : "&c기간 형식이 올바르지 않습니다. 예) 30m, 2h, 7d, 1w");
            return;
        }
        ban(t, dur, Cmd.rest(a, 2), s.getName());
        Msg.send(s, TAG + "&f" + t.getName() + "&7를 &f" + Dur.text(dur) + "&7 동안 차단했습니다.");
    }

    /** 차단 (duration 0 = 영구). 접속 중이면 내보내고 전체 공지 */
    public void ban(OfflinePlayer t, long duration, String reason, String by) {
        GlobalData.Ban ban = new GlobalData.Ban();
        ban.reason = reason;
        ban.code = CoreModule.banCode();
        ban.by = by;
        ban.at = System.currentTimeMillis();
        ban.until = duration > 0 ? ban.at + duration : 0;
        global().bans.put(t.getUniqueId(), ban);
        plugin.store().saveGlobal();
        CoreModule core = plugin.module(CoreModule.class);
        if (t.getPlayer() != null) t.getPlayer().kick(Msg.c(core.banMessage(ban)));
        String what = duration > 0 ? Dur.text(duration) + " 차단" : "영구 차단";
        Msg.broadcast("&c-----------------------------------------------------%nl%%nl%§c&l[&f&l Security Notice &c&l] &c&l이용약관 위반 유저 "
                + (duration > 0 ? "임시 차단" : "차단") + " 알림%nl%&7%nl%&7&l플레이어 이름 : &e" + t.getName()
                + (duration > 0 ? "%nl%&7&l기간 : &f" + Dur.text(duration) : "") + "%nl%&7&l사유 : &c" + Msg.esc(reason)
                + "%nl%%nl%&c-----------------------------------------------------");
        record(t, duration > 0 ? "임시 차단" : "영구 차단", what + " · " + reason + " · 코드 #" + ban.code, by);
    }

    /* ======================= 채팅 금지 ======================= */

    private void muteCmd(CommandSender s, String l, String[] a) {
        if (a.length < 2) {
            Msg.send(s, "&c/" + l + " <플레이어> <기간> [사유] &7(기간: 10m 1h 1d)");
            return;
        }
        OfflinePlayer t = Cmd.offline(a[0]);
        long dur = Dur.parse(a[1]);
        if (t == null || dur <= 0) {
            Msg.send(s, t == null ? "&c플레이어를 찾을 수 없습니다." : "&c기간 형식이 올바르지 않습니다. 예) 10m, 1h, 1d");
            return;
        }
        mute(t, dur, a.length >= 3 ? Cmd.rest(a, 2) : "사유 미작성", s.getName());
        Msg.send(s, TAG + "&f" + t.getName() + "&7의 채팅을 &f" + Dur.text(dur) + "&7 동안 막았습니다.");
    }

    public void mute(OfflinePlayer t, long duration, String reason, String by) {
        PlayerData d = plugin.store().get(t);
        d.muteUntil = System.currentTimeMillis() + duration;
        d.muteReason = reason;
        record(t, "채팅 금지", Dur.text(duration) + " · " + reason, by);
        if (t.getPlayer() != null) {
            Msg.send(t.getPlayer(), TAG + "&c" + Dur.text(duration) + " 동안 채팅이 금지되었습니다. &7(사유: " + Msg.esc(reason) + ")");
            Msg.sound(t.getPlayer(), "block.note_block.bass", 0.8f, 0.6f);
        }
    }

    private void unmuteCmd(CommandSender s, String l, String[] a) {
        OfflinePlayer t = a.length >= 1 ? Cmd.offline(a[0]) : null;
        if (t == null) {
            Msg.send(s, "&c/" + l + " <플레이어>");
            return;
        }
        PlayerData d = plugin.store().get(t);
        d.muteUntil = 0;
        record(t, "채팅 금지 해제", "-", s.getName());
        Msg.send(s, TAG + "&f" + t.getName() + "&7의 채팅 금지를 풀었습니다.");
        if (t.getPlayer() != null) Msg.send(t.getPlayer(), TAG + "&a채팅 금지가 풀렸습니다.");
    }

    public boolean muted(Player p) {
        return data(p).muteUntil > System.currentTimeMillis();
    }

    /* ======================= 채팅 필터 ======================= */

    /** 최근 채팅 (도배 판단용) */
    private record Recent(String text, long at, int repeats) {
    }

    private final Map<UUID, Recent> recent = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastChat = new ConcurrentHashMap<>();

    private static final Pattern IP = Pattern.compile("\\b\\d{1,3}(?:\\s*[.,·]\\s*\\d{1,3}){3}\\b");
    private static final Pattern DOMAIN = Pattern.compile("(?i)\\b[a-z0-9-]{2,}\\s*(?:\\.|점|닷)\\s*(?:kr|com|net|org|gg|io|me|xyz|co|cc|tk|ml|club|site|online|world)\\b");

    /** 결과: null 이면 보내지 않음 (안내는 이미 보냄), 아니면 (금지어를 가린) 보낼 글자 */
    public String check(Player p, String text, boolean whisper) {
        if (muted(p)) {
            PlayerData d = data(p);
            Msg.send(p, TAG + "&c채팅이 금지된 상태입니다. &7(" + Dur.left(d.muteUntil) + " 남음 · 사유: " + Msg.esc(d.muteReason) + ")");
            return null;
        }
        var cfg = plugin.getConfig();
        if (!cfg.getBoolean("chat-filter.enabled", true) || p.hasPermission(ADMIN)) return text;
        long now = System.currentTimeMillis();
        UUID id = p.getUniqueId();
        // 도배: 너무 빠름 / 같은 말 반복
        long gap = cfg.getLong("chat-filter.min-interval-ms", 700);
        Long last = lastChat.put(id, now);
        if (!whisper && last != null && now - last < gap) {
            Msg.send(p, "&c&l[!] &c채팅을 너무 빠르게 보내고 있습니다. 잠시 후 다시 보내 주세요.");
            return null;
        }
        String norm = text.replaceAll("\\s+", "").toLowerCase();
        Recent r = recent.get(id);
        int repeats = r != null && r.text.equals(norm) && now - r.at < 15_000 ? r.repeats + 1 : 1;
        recent.put(id, new Recent(norm, now, repeats));
        if (!whisper && repeats > cfg.getInt("chat-filter.same-message-limit", 2)) {
            Msg.send(p, "&c&l[!] &c같은 말을 반복해서 보낼 수 없습니다.");
            return null;
        }
        // 광고: IP · 다른 서버 주소 (우리 서버 링크는 허용)
        if (cfg.getBoolean("chat-filter.block-ads", true) && (IP.matcher(text).find() || adDomain(text))) {
            Msg.send(p, "&c&l[!] &c서버 주소 · 링크는 채팅으로 보낼 수 없습니다.");
            plugin.getLogger().info("[채팅 필터] 광고로 보이는 채팅을 막았습니다: " + p.getName() + " : " + text);
            return null;
        }
        // 금지어: 가운데 띄어쓰기 · 기호를 넣어도 가림
        String out = text;
        for (String w : cfg.getStringList("chat-filter.bad-words")) {
            if (w.isBlank()) continue;
            StringBuilder re = new StringBuilder();
            for (int i = 0; i < w.length(); i++) {
                if (i > 0) re.append("[\\s\\p{Punct}_]*");
                re.append(Pattern.quote(String.valueOf(w.charAt(i))));
            }
            Matcher m = Pattern.compile(re.toString(), Pattern.CASE_INSENSITIVE).matcher(out);
            StringBuilder sb = new StringBuilder();
            while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement("*".repeat(m.group().length())));
            m.appendTail(sb);
            out = sb.toString();
        }
        return out;
    }

    private boolean adDomain(String text) {
        Matcher m = DOMAIN.matcher(text);
        while (m.find()) {
            String hit = m.group().replaceAll("\\s+", "").replace("점", ".").replace("닷", ".").toLowerCase();
            boolean ours = false;
            for (String key : new String[]{"links.discord", "links.minelist", "links.mineboard"}) {
                String u = plugin.getConfig().getString(key, "");
                if (u != null && !u.isBlank() && u.toLowerCase().contains(hit)) ours = true;
            }
            for (String ok : plugin.getConfig().getStringList("chat-filter.allowed-domains")) if (hit.contains(ok.toLowerCase())) ours = true;
            if (!ours) return true;
        }
        return false;
    }

    /** 전체 · 팀 채팅: 다른 채팅 처리보다 먼저 */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        String text = Msg.plain(e.message());
        String out = check(e.getPlayer(), text, false);
        if (out == null) {
            e.setCancelled(true);
            return;
        }
        if (!out.equals(text)) e.message(Component.text(out));
    }
}
