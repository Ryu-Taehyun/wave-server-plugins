package kr.wave.core.module;

import kr.wave.core.WaveCore;
import kr.wave.core.util.Cmd;
import kr.wave.core.util.Msg;
import kr.wave.core.util.PassBridge;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 핫타임: 정해진 시간 동안 보상 배율 (기본 2배)
 * - config hot-time.schedule 의 시간에 자동 시작 ("20:00 60" = 매일 20시부터 60분, "토,일 14:00 120" = 주말만)
 * - 시작 전 알림(notice-minutes), 시작 · 종료 공지, 진행 중에는 보스바로 남은 시간 표시
 * - 배율 적용: 레벨 경험치, 상점 판매가, 채팅 퀴즈 보상, 프리미엄 패스 퀘스트 XP (boost.* 로 개별 끄기)
 * - /핫타임 : 상태 · 다음 일정, 관리자 /핫타임 시작 <분> [배율] · /핫타임 종료
 */
public class HotTimeModule extends Module {

    public static final String TAG = "<b><gradient:#FF9A3C:#FF3D3D>[ 핫타임 ]</gradient></b> ";

    /** 일정 한 줄 */
    public record Slot(Set<DayOfWeek> days, LocalTime start, int minutes) {
    }

    /** 지금 진행 중인 핫타임이 끝나는 시각 (0 = 없음) */
    private long endsAt;
    private long startedAt;
    private double mult = 1;
    /** 자동 일정으로 시작한 경우 그 일정의 시작 시각 (같은 일정이 두 번 시작되지 않게) */
    private LocalDateTime lastAutoStart;
    /** 이미 보낸 사전 알림 (일정 시작 시각 + 분) */
    private final Set<String> noticed = new java.util.HashSet<>();
    private BossBar bar;

    /** 테스트에서 시간을 바꿀 수 있게 */
    public Supplier<LocalDateTime> clock = () -> LocalDateTime.now(zone());

    public HotTimeModule(WaveCore plugin) {
        super(plugin);
    }

    @Override
    public void enable() {
        cmd("핫타임", null, this::command, "hottime");
    }

    @Override
    public void disable() {
        hideBar();
        PassBridge.setXpBoost(1);
    }

    private ZoneId zone() {
        try {
            return ZoneId.of(plugin.getConfig().getString("hot-time.timezone", "Asia/Seoul"));
        } catch (RuntimeException e) {
            return ZoneId.systemDefault();
        }
    }

    /* ======================= 배율 ======================= */

    public boolean active() {
        return endsAt > System.currentTimeMillis();
    }

    /** 이 항목(level-exp, sell, quiz, pass-xp)에 적용할 배율. 핫타임이 아니거나 꺼진 항목이면 1 */
    public double mult(String what) {
        if (!active() || !plugin.getConfig().getBoolean("hot-time.boost." + what, true)) return 1;
        return mult;
    }

    /** 다른 모듈에서: amount 에 배율 적용 (모듈이 없으면 그대로) */
    public static long boost(WaveCore plugin, String what, long amount) {
        HotTimeModule m = plugin.module(HotTimeModule.class);
        return m == null ? amount : Math.round(amount * m.mult(what));
    }

    /** 메시지 뒤에 붙일 표시 (핫타임이 아니면 빈 문자열) */
    public static String mark(WaveCore plugin, String what) {
        HotTimeModule m = plugin.module(HotTimeModule.class);
        if (m == null || m.mult(what) == 1) return "";
        return " &6&l🔥x" + fmt(m.mult(what));
    }

    static String fmt(double m) {
        return m == Math.floor(m) ? String.valueOf((long) m) : String.valueOf(m);
    }

    /** 무엇이 몇 배인지 한 줄 */
    private String boostList() {
        List<String> out = new ArrayList<>();
        var c = plugin.getConfig();
        if (c.getBoolean("hot-time.boost.level-exp", true)) out.add("레벨 경험치");
        if (c.getBoolean("hot-time.boost.pass-xp", true)) out.add("패스 퀘스트 XP");
        if (c.getBoolean("hot-time.boost.sell", true)) out.add("상점 판매가");
        if (c.getBoolean("hot-time.boost.quiz", true)) out.add("퀴즈 보상");
        return String.join(" · ", out);
    }

    /* ======================= 일정 ======================= */

    public List<Slot> schedule() {
        List<Slot> out = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("hot-time.schedule")) {
            Slot s = parse(line);
            if (s == null) plugin.getLogger().warning("[핫타임] 일정을 읽을 수 없습니다: \"" + line + "\" (예: \"20:00 60\", \"토,일 14:00 120\")");
            else out.add(s);
        }
        return out;
    }

    /** "20:00 60" / "토,일 14:00 120" / "평일 21:00 30" / "주말 ..." */
    static Slot parse(String line) {
        if (line == null) return null;
        String[] p = line.trim().split("\\s+");
        if (p.length < 2) return null;
        Set<DayOfWeek> days = EnumSet.allOf(DayOfWeek.class);
        int i = 0;
        if (p.length >= 3) {
            days = days(p[0]);
            if (days == null) return null;
            i = 1;
        }
        try {
            LocalTime t = LocalTime.parse(p[i].length() == 4 ? "0" + p[i] : p[i]);
            int min = Integer.parseInt(p[i + 1]);
            if (min <= 0 || min > 24 * 60) return null;
            return new Slot(days, t, min);
        } catch (DateTimeParseException | NumberFormatException e) {
            return null;
        }
    }

    private static Set<DayOfWeek> days(String s) {
        Set<DayOfWeek> out = EnumSet.noneOf(DayOfWeek.class);
        for (String d : s.split(",")) {
            switch (d.trim()) {
                case "매일" -> out.addAll(EnumSet.allOf(DayOfWeek.class));
                case "평일" -> out.addAll(EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY));
                case "주말" -> out.addAll(EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY));
                case "월" -> out.add(DayOfWeek.MONDAY);
                case "화" -> out.add(DayOfWeek.TUESDAY);
                case "수" -> out.add(DayOfWeek.WEDNESDAY);
                case "목" -> out.add(DayOfWeek.THURSDAY);
                case "금" -> out.add(DayOfWeek.FRIDAY);
                case "토" -> out.add(DayOfWeek.SATURDAY);
                case "일" -> out.add(DayOfWeek.SUNDAY);
                default -> {
                    return null;
                }
            }
        }
        return out.isEmpty() ? null : out;
    }

    /** now 기준 다음 시작 시각 (지금 진행 중인 일정 포함, 없으면 null) */
    public LocalDateTime next(LocalDateTime now) {
        LocalDateTime best = null;
        for (Slot s : schedule()) {
            for (int d = -1; d <= 7; d++) {
                LocalDateTime st = now.toLocalDate().plusDays(d).atTime(s.start());
                if (!s.days().contains(st.getDayOfWeek())) continue;
                if (!st.plusMinutes(s.minutes()).isAfter(now)) continue;
                if (best == null || st.isBefore(best)) best = st;
                break;
            }
        }
        return best;
    }

    /** now 에 진행 중이어야 하는 일정 (없으면 null) */
    private Slot running(LocalDateTime now, LocalDateTime[] startOut) {
        for (Slot s : schedule()) {
            for (int d = -1; d <= 0; d++) {
                LocalDateTime st = now.toLocalDate().plusDays(d).atTime(s.start());
                if (!s.days().contains(st.getDayOfWeek())) continue;
                if (!now.isBefore(st) && now.isBefore(st.plusMinutes(s.minutes()))) {
                    startOut[0] = st;
                    return s;
                }
            }
        }
        return null;
    }

    /* ======================= 매초 ======================= */

    @Override
    public void secondTick() {
        var c = plugin.getConfig();
        if (active()) {
            updateBar();
            return;
        }
        if (endsAt != 0) end(false);
        if (!c.getBoolean("hot-time.enabled", true)) return;
        LocalDateTime now = clock.get();
        LocalDateTime[] st = new LocalDateTime[1];
        Slot s = running(now, st);
        if (s != null && !st[0].equals(lastAutoStart)) {
            lastAutoStart = st[0];
            long left = java.time.Duration.between(now, st[0].plusMinutes(s.minutes())).toMillis();
            start(left, c.getDouble("hot-time.multiplier", 2.0));
            return;
        }
        // 사전 알림
        LocalDateTime n = next(now);
        if (n == null || !n.isAfter(now)) return;
        long sec = java.time.Duration.between(now, n).getSeconds();
        for (int m : c.getIntegerList("hot-time.notice-minutes").isEmpty() ? List.of(10, 5, 1) : c.getIntegerList("hot-time.notice-minutes")) {
            String key = n + "|" + m;
            if (sec <= m * 60L && sec > m * 60L - 60 && noticed.add(key)) {
                Msg.broadcast(TAG + "&f" + m + "분 뒤 핫타임이 시작됩니다! &7(" + boostList() + " &6x" + fmt(c.getDouble("hot-time.multiplier", 2.0)) + "&7)");
                for (Player p : Bukkit.getOnlinePlayers()) Msg.sound(p, "block.note_block.pling", 0.6f, 1.4f);
            }
        }
        if (noticed.size() > 200) noticed.clear();
    }

    /* ======================= 시작 · 종료 ======================= */

    public void start(long millis, double multiplier) {
        mult = Math.max(1, Math.min(10, multiplier));
        startedAt = System.currentTimeMillis();
        endsAt = startedAt + millis;
        PassBridge.setXpBoost(mult("pass-xp"));
        long min = Math.round(millis / 60000.0);
        Msg.broadcast(" ");
        Msg.broadcast(TAG + "&f지금부터 &e" + min + "분&f 동안 &6&l보상 x" + fmt(mult) + "&f!");
        Msg.broadcast("&7  적용 : &f" + boostList());
        Msg.broadcast(" ");
        for (Player p : Bukkit.getOnlinePlayers()) {
            Msg.title(p, "<b><gradient:#FF9A3C:#FF3D3D>🔥 HOT TIME 🔥</gradient></b>", "&f" + min + "분 동안 보상 &6&lx" + fmt(mult), 10, 60, 20);
            Msg.sound(p, "entity.blaze.shoot", 0.7f, 0.8f);
            Msg.sound(p, "ui.toast.challenge_complete", 0.6f, 1.3f);
        }
        showBar();
    }

    /** manual: 관리자가 끝낸 경우 */
    public void end(boolean manual) {
        if (endsAt == 0) return;
        endsAt = 0;
        mult = 1;
        PassBridge.setXpBoost(1);
        hideBar();
        LocalDateTime n = next(clock.get());
        Msg.broadcast(TAG + "&7핫타임이 " + (manual ? "종료" : "끝났") + "습니다." + (n == null ? "" : " &8다음 핫타임 : &f" + when(n)));
        for (Player p : Bukkit.getOnlinePlayers()) Msg.sound(p, "block.beacon.deactivate", 0.5f, 1.2f);
    }

    private void showBar() {
        if (bar == null) bar = Bukkit.createBossBar("", BarColor.YELLOW, BarStyle.SEGMENTED_10);
        for (Player p : Bukkit.getOnlinePlayers()) bar.addPlayer(p);
        bar.setVisible(true);
        updateBar();
    }

    private void updateBar() {
        if (bar == null) return;
        long left = Math.max(0, endsAt - System.currentTimeMillis()), total = Math.max(1, endsAt - startedAt);
        bar.setProgress(Math.max(0, Math.min(1, left / (double) total)));
        bar.setTitle("§6§l🔥 핫타임 §f보상 §e§lx" + fmt(mult) + " §7· 남은 시간 §f" + clock(left / 1000));
    }

    private void hideBar() {
        if (bar == null) return;
        bar.removeAll();
        bar.setVisible(false);
    }

    private static String clock(long sec) {
        long h = sec / 3600, m = sec % 3600 / 60, s = sec % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }

    private String when(LocalDateTime t) {
        LocalDateTime now = clock.get();
        String day = t.toLocalDate().equals(now.toLocalDate()) ? "오늘" : t.toLocalDate().equals(now.toLocalDate().plusDays(1)) ? "내일"
                : t.getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.KOREAN) + "요일";
        return day + " " + String.format("%02d:%02d", t.getHour(), t.getMinute());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!active()) return;
        Player p = e.getPlayer();
        if (bar != null) bar.addPlayer(p);
        later(60, () -> {
            if (p.isOnline() && active())
                Msg.send(p, TAG + "&f지금 핫타임 진행 중! &6x" + fmt(mult) + " &7(" + boostList() + ") &8남은 시간 " + clock((endsAt - System.currentTimeMillis()) / 1000));
        });
    }

    /** 서버 메뉴용 한 줄 */
    public String statusLine() {
        if (active()) return "&6&l🔥 진행 중! &fx" + fmt(mult) + " &7· 남은 시간 &f" + clock((endsAt - System.currentTimeMillis()) / 1000);
        if (!plugin.getConfig().getBoolean("hot-time.enabled", true)) return "&8예정된 핫타임이 없습니다.";
        LocalDateTime n = next(clock.get());
        return n == null ? "&8예정된 핫타임이 없습니다." : "&7다음 핫타임 : &f" + when(n);
    }

    /* ======================= 명령어 ======================= */

    private void command(CommandSender s, String l, String[] a) {
        boolean admin = s.hasPermission(ADMIN);
        if (a.length >= 1 && admin && (a[0].equals("시작") || a[0].equalsIgnoreCase("start"))) {
            Long min = a.length >= 2 ? Cmd.num(a[1]) : Long.valueOf(60);
            if (min == null || min <= 0 || min > 24 * 60) {
                Msg.send(s, "&c사용법: /핫타임 시작 <분> [배율]");
                return;
            }
            double m = plugin.getConfig().getDouble("hot-time.multiplier", 2.0);
            if (a.length >= 3) {
                try {
                    m = Double.parseDouble(a[2]);
                } catch (NumberFormatException e) {
                    Msg.send(s, "&c배율은 숫자로 적어 주세요. (예: 1.5, 2, 3)");
                    return;
                }
            }
            if (active()) end(true);
            start(min * 60_000L, m);
            return;
        }
        if (a.length >= 1 && admin && (a[0].equals("종료") || a[0].equalsIgnoreCase("stop"))) {
            if (!active()) Msg.send(s, "&7진행 중인 핫타임이 없습니다.");
            else end(true);
            return;
        }
        Msg.send(s, " ");
        Msg.send(s, TAG + statusLine());
        Msg.send(s, "&7  적용 : &f" + boostList() + " &6x" + fmt(active() ? mult : plugin.getConfig().getDouble("hot-time.multiplier", 2.0)));
        List<Slot> sc = schedule();
        if (plugin.getConfig().getBoolean("hot-time.enabled", true) && !sc.isEmpty()) {
            Msg.send(s, "&7  일정 :");
            for (Slot x : sc) Msg.send(s, "&8   - &f" + dayText(x.days()) + " " + String.format("%02d:%02d", x.start().getHour(), x.start().getMinute()) + " &7(" + x.minutes() + "분)");
        }
        if (admin) Msg.send(s, "&8  관리자 : /핫타임 시작 <분> [배율] · /핫타임 종료");
        Msg.send(s, " ");
    }

    private static String dayText(Set<DayOfWeek> d) {
        if (d.size() == 7) return "매일";
        if (d.equals(EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))) return "평일";
        if (d.equals(EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))) return "주말";
        StringBuilder b = new StringBuilder();
        for (DayOfWeek w : d) b.append(b.length() > 0 ? "," : "").append(w.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.KOREAN));
        return b.toString();
    }
}
