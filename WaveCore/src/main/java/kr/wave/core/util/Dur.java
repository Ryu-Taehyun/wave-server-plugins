package kr.wave.core.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 기간 글자 (30m, 2h, 7d, 1w, 영구) 와 남은 시간 표시 */
public final class Dur {

    private static final Pattern PART = Pattern.compile("(\\d+)\\s*(s|m|h|d|w|초|분|시간|일|주)", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter SHOW = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private Dur() {
    }

    /** "1d12h" -> 밀리초. "영구" · "perm" -> 0. 해석할 수 없으면 -1 */
    public static long parse(String s) {
        if (s == null) return -1;
        String t = s.trim().toLowerCase();
        if (t.equals("영구") || t.equals("perm") || t.equals("permanent")) return 0;
        Matcher m = PART.matcher(t);
        long total = 0;
        int end = 0;
        while (m.find()) {
            if (m.start() != end) return -1;
            long n = Long.parseLong(m.group(1));
            total += n * switch (m.group(2)) {
                case "s", "초" -> 1_000L;
                case "m", "분" -> 60_000L;
                case "h", "시간" -> 3_600_000L;
                case "d", "일" -> 86_400_000L;
                default -> 7 * 86_400_000L;
            };
            end = m.end();
        }
        return end == t.length() && total > 0 ? total : -1;
    }

    /** 밀리초 -> "1일 3시간" 같은 글자 */
    public static String text(long ms) {
        if (ms <= 0) return "영구";
        long min = Math.max(1, ms / 60_000);
        long d = min / 1440, h = (min % 1440) / 60, mm = min % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("일 ");
        if (h > 0) sb.append(h).append("시간 ");
        if (mm > 0 && d == 0) sb.append(mm).append("분");
        return sb.toString().trim();
    }

    /** 시각 -> "2026-11-24 18:30" */
    public static String when(long at) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(at), ZoneId.systemDefault()).format(SHOW);
    }

    /** 그 시각까지 남은 시간 글자 */
    public static String left(long until) {
        return text(Math.max(60_000, until - System.currentTimeMillis()));
    }

    public static String now() {
        return LocalDateTime.now().format(SHOW);
    }
}
