package kr.wave.core.module;

import kr.wave.core.WaveCore;
import kr.wave.core.util.Backup;
import kr.wave.core.util.Cmd;
import kr.wave.core.util.Discord;
import kr.wave.core.util.Dur;
import kr.wave.core.util.Log;
import kr.wave.core.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * 운영 도구
 * - 매일 자동 백업 (config backup.*) · /백업 으로 바로 백업
 * - /공지 <내용>                        바로 전체 공지
 * - /공지 예약 <HH:mm | 10m> <내용>     한 번 예약 (HH:mm 이 지났으면 다음 날)
 * - /공지 매일 <HH:mm> <내용>           매일 같은 시각
 * - /공지 목록 · /공지 취소 <번호>
 * (돌아가며 나오는 자동 공지는 config announcements 에서 설정)
 */
public class OpsModule extends Module {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final String NOTICE = "<b><gradient:#FFB347:#FFE29A>[ 📢 공지 ]</gradient></b> ";

    private volatile boolean backupRunning;
    private String lastAutoBackupDay = "";

    public OpsModule(WaveCore plugin) {
        super(plugin);
    }

    @Override
    public void enable() {
        cmd("백업", ADMIN, (s, l, a) -> runBackup(s, "manual-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))), "backup");
        cmd("공지", ADMIN, this::noticeCmd, (s, a) -> a.length <= 1 ? List.of("예약", "매일", "목록", "취소")
                : a.length == 2 && (a[0].equals("예약") || a[0].equals("매일")) ? List.of("21:00", "10m", "1h") : List.of(), "notice", "bc");
        lastAutoBackupDay = LocalDate.now().format(DAY).equals(plugin.getConfig().getString("backup.last-day", "")) ? LocalDate.now().format(DAY) : "";
    }

    /* ======================= 백업 ======================= */

    /** 저장을 마친 뒤 다른 스레드에서 zip. 끝나면 오래된 daily 백업 정리 */
    public void runBackup(CommandSender s, String name) {
        if (backupRunning) {
            if (s != null) Msg.send(s, "&c이미 백업이 진행 중입니다.");
            return;
        }
        backupRunning = true;
        plugin.store().saveAll();
        if (s != null) Msg.send(s, "&a[백업] &7백업을 시작합니다...");
        int keep = Math.max(1, plugin.getConfig().getInt("backup.keep-days", 14));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String result;
            boolean ok;
            try {
                plugin.store().flush(30);
                File out = Backup.zip(plugin, name);
                int removed = prune(keep, out);
                ok = true;
                result = out.getName() + " (" + (out.length() / 1024) + "KB" + (removed > 0 ? ", 오래된 백업 " + removed + "개 삭제" : "") + ")";
                plugin.getLogger().info("[백업] " + result);
            } catch (IOException | RuntimeException e) {
                ok = false;
                result = e.getMessage();
                plugin.getLogger().log(Level.SEVERE, "[백업] 실패", e);
                Discord.send("backup", "⚠️ 백업 실패", "자동 백업을 만들지 못했습니다. 서버 콘솔을 확인하세요.", Discord.RED, "파일", name, "오류", String.valueOf(e.getMessage()));
            } finally {
                backupRunning = false;
            }
            String msg = ok ? "&a[백업] &7완료 : &f" + result : "&c[백업] 실패 : " + result;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (s != null) Msg.send(s, msg);
            });
        });
    }

    /** daily-*.zip 중 keep 일보다 오래된 것 삭제 */
    private int prune(int keep, File current) {
        File[] files = new File(plugin.getDataFolder(), "backups").listFiles((d, n) -> n.startsWith("daily-") && n.endsWith(".zip"));
        if (files == null) return 0;
        String limit = LocalDate.now().minusDays(keep).format(DAY);
        int n = 0;
        for (File f : files) {
            String day = f.getName().substring(6, Math.min(14, f.getName().length() - 4));
            if (!f.equals(current) && day.compareTo(limit) <= 0 && f.delete()) n++;
        }
        return n;
    }

    public boolean backupRunning() {
        return backupRunning;
    }

    /* ======================= 공지 ======================= */

    private void noticeCmd(CommandSender s, String l, String[] a) {
        if (a.length == 0) {
            Msg.send(s, "&e/공지 <내용> &7- 바로 공지");
            Msg.send(s, "&e/공지 예약 <HH:mm|10m> <내용> &7- 한 번 예약");
            Msg.send(s, "&e/공지 매일 <HH:mm> <내용> &7- 매일 같은 시각");
            Msg.send(s, "&e/공지 목록 &7· &e/공지 취소 <번호>");
            return;
        }
        switch (a[0]) {
            case "예약", "매일" -> {
                if (a.length < 3) {
                    Msg.send(s, "&c/공지 " + a[0] + " <" + (a[0].equals("매일") ? "HH:mm" : "HH:mm|10m") + "> <내용>");
                    return;
                }
                boolean daily = a[0].equals("매일");
                long at = when(a[1], daily);
                if (at < 0) {
                    Msg.send(s, "&c시각 형식이 올바르지 않습니다. 예) 21:00" + (daily ? "" : ", 10m, 1h"));
                    return;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("at", at);
                m.put("daily", daily ? a[1] : "");
                m.put("text", Cmd.rest(a, 2));
                m.put("by", s.getName());
                List<Map<?, ?>> list = scheduled();
                list.add(m);
                save(list);
                Msg.send(s, "&a[공지] &7" + (daily ? "매일 " + a[1] + "에" : Dur.when(at) + "에") + " 공지합니다. &8(번호 " + list.size() + ")");
            }
            case "목록" -> {
                List<Map<?, ?>> list = scheduled();
                Msg.send(s, "&6[공지] &7예약된 공지 " + list.size() + "개");
                for (int i = 0; i < list.size(); i++) {
                    Map<?, ?> m = list.get(i);
                    String daily = String.valueOf(m.get("daily"));
                    Msg.send(s, " &e" + (i + 1) + ". &7" + (daily.isEmpty() || "null".equals(daily) ? Dur.when(num(m.get("at"))) : "매일 " + daily) + " &f" + m.get("text"));
                }
            }
            case "취소" -> {
                List<Map<?, ?>> list = scheduled();
                int i = parse(Cmd.arg(a, 1)) - 1;
                if (i < 0 || i >= list.size()) {
                    Msg.send(s, "&c번호를 확인해 주세요. (/공지 목록)");
                    return;
                }
                Map<?, ?> m = list.remove(i);
                save(list);
                Msg.send(s, "&a[공지] &7예약을 취소했습니다 : &f" + m.get("text"));
            }
            default -> broadcast(Cmd.rest(a, 0));
        }
    }

    public void broadcast(String text) {
        Msg.broadcast(" ");
        Msg.broadcast(NOTICE + "&f" + text);
        Msg.broadcast(" ");
        for (Player p : Bukkit.getOnlinePlayers()) Msg.sound(p, "block.note_block.bell", 0.6f, 1.2f);
    }

    /** "21:00" -> 다음 21:00, "10m" -> 10분 뒤 (daily 면 HH:mm 만) */
    static long when(String s, boolean daily) {
        try {
            LocalTime t = LocalTime.parse(s.length() == 4 ? "0" + s : s, HM);
            LocalDateTime at = LocalDate.now().atTime(t);
            if (!at.isAfter(LocalDateTime.now())) at = at.plusDays(1);
            return at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            if (daily) return -1;
            long d = Dur.parse(s);
            return d > 0 ? System.currentTimeMillis() + d : -1;
        }
    }

    private List<Map<?, ?>> scheduled() {
        return new ArrayList<>(plugin.getConfig().getMapList("announcements.scheduled"));
    }

    private void save(List<Map<?, ?>> list) {
        plugin.getConfig().set("announcements.scheduled", list);
        plugin.saveConfig();
    }

    private static long num(Object o) {
        return o instanceof Number n ? n.longValue() : 0;
    }

    private static int parse(String s) {
        try {
            return s == null ? 0 : Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int tick;

    @Override
    public void secondTick() {
        // 예약 공지
        long now = System.currentTimeMillis();
        List<Map<?, ?>> list = scheduled();
        boolean changed = false;
        for (int i = 0; i < list.size(); i++) {
            Map<?, ?> m = list.get(i);
            if (num(m.get("at")) > now) continue;
            broadcast(String.valueOf(m.get("text")));
            String daily = String.valueOf(m.get("daily"));
            if (daily.isEmpty() || "null".equals(daily)) {
                list.remove(i--);
            } else {
                Map<String, Object> next = new LinkedHashMap<>();
                m.forEach((k, v) -> next.put(String.valueOf(k), v));
                next.put("at", when(daily, true));
                list.set(i, next);
            }
            changed = true;
        }
        if (changed) save(list);
        // 매일 자동 백업 (30초마다 확인)
        if (++tick % 30 != 0 || !plugin.getConfig().getBoolean("backup.enabled", true)) return;
        String today = LocalDate.now().format(DAY);
        if (today.equals(lastAutoBackupDay)) return;
        LocalTime at;
        try {
            at = LocalTime.parse(plugin.getConfig().getString("backup.time", "05:00"), HM);
        } catch (DateTimeParseException e) {
            Log.once("backup.time", "backup.time 형식이 올바르지 않습니다 (예: 05:00). 05:00 으로 백업합니다.");
            at = LocalTime.of(5, 0);
        }
        if (LocalTime.now().isBefore(at)) return;
        lastAutoBackupDay = today;
        plugin.getConfig().set("backup.last-day", today);
        plugin.saveConfig();
        runBackup(null, "daily-" + today);
    }
}
