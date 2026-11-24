package kr.wave.core.util;

import kr.wave.core.WaveCore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 디스코드 관리자 채널 알림 (웹훅).
 * 주소는 비밀값이라 private.yml 의 discord-webhook.url 에 둡니다. 비어 있으면 아무것도 보내지 않습니다.
 * 종류(kind): report, sanction, refund, paid-cash, season, backup — config discord-webhook.events 로 고름
 */
public final class Discord {

    public static final int RED = 0xE5484D, ORANGE = 0xF5A524, GREEN = 0x30A46C, BLUE = 0x3E63DD, GRAY = 0x8B8D98;

    private static HttpClient client;

    private Discord() {
    }

    public static boolean enabled() {
        WaveCore w = WaveCore.get();
        if (w == null) return false;
        String url = w.getConfig().getString("discord-webhook.url", "");
        return url != null && url.startsWith("https://");
    }

    /** 알림 보내기. fields = 이름, 값, 이름, 값 ... (임베드 칸) */
    public static void send(String kind, String title, String description, int color, String... fields) {
        WaveCore w = WaveCore.get();
        if (w == null || !enabled()) return;
        List<String> events = w.getConfig().getStringList("discord-webhook.events");
        if (!events.isEmpty() && !events.contains(kind)) return;
        String url = w.getConfig().getString("discord-webhook.url");
        StringBuilder f = new StringBuilder();
        for (int i = 0; i + 1 < fields.length; i += 2) {
            if (f.length() > 0) f.append(',');
            f.append("{\"name\":").append(q(fields[i])).append(",\"value\":").append(q(fields[i + 1] == null || fields[i + 1].isEmpty() ? "-" : fields[i + 1])).append(",\"inline\":true}");
        }
        String body = "{\"username\":" + q(w.getConfig().getString("discord-webhook.name", "Wave 서버 알림"))
                + ",\"allowed_mentions\":{\"parse\":[]}"   // @everyone 등 멘션이 울리지 않게
                + ",\"embeds\":[{\"title\":" + q(title) + ",\"description\":" + q(description)
                + ",\"color\":" + color + ",\"fields\":[" + f + "],\"timestamp\":" + q(Instant.now().toString()) + "}]}";
        try {
            if (client == null) client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
            client.sendAsync(req, java.net.http.HttpResponse.BodyHandlers.discarding()).whenComplete((res, err) -> {
                if (err != null) Log.once("discord:err", "디스코드 알림을 보내지 못했습니다: " + err.getMessage());
                else if (res.statusCode() >= 300) Log.once("discord:" + res.statusCode(), "디스코드 알림 응답 오류: HTTP " + res.statusCode() + " (웹훅 주소를 확인하세요)");
            });
        } catch (IllegalArgumentException e) {
            Log.once("discord:url", "discord-webhook.url 주소가 올바르지 않습니다.");
        }
    }

    /** JSON 글자 (색 코드 제거 + 이스케이프) */
    static String q(String s) {
        String t = s == null ? "" : Msg.plainOf(s);
        StringBuilder b = new StringBuilder("\"");
        for (char c : t.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> {
                }
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.append('"').toString();
    }
}
