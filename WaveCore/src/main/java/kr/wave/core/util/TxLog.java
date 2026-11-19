package kr.wave.core.util;

import kr.wave.core.WaveCore;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 운영 기록 (plugins/WaveCore/logs/).
 * - paid-cash-날짜.log : 유료 캐시 지급 · 사용 · 환불 · 초기화
 * - crate-날짜.log     : 상자 오픈 결과
 * 한 줄 형식: 시각 | 종류 | 플레이어(UUID) | 변동 | 잔액 | 내용
 * 파일 쓰기는 별도 스레드에서 하므로 서버가 멈추지 않습니다.
 */
public final class TxLog {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "WaveCore-TxLog");
        t.setDaemon(true);
        return t;
    });

    private TxLog() {
    }

    /** 유료 캐시 변동. delta 는 +지급 / -사용 */
    public static void paid(String kind, String name, UUID uuid, long delta, long balance, String detail) {
        write("paid-cash", String.join(" | ", LocalDateTime.now().format(TIME), kind, name + "(" + uuid + ")",
                (delta >= 0 ? "+" : "") + delta, "잔액 " + balance, clean(detail)));
    }

    /** 상자 오픈 결과 */
    public static void crate(String name, UUID uuid, String crate, int times, String result) {
        write("crate", String.join(" | ", LocalDateTime.now().format(TIME), "상자 오픈", name + "(" + uuid + ")",
                crate + " x" + times, clean(result)));
    }

    private static String clean(String s) {
        return s == null ? "-" : Msg.plainOf(s).replace('\n', ' ').replace('|', '/').trim();
    }

    private static void write(String prefix, String line) {
        WaveCore w = WaveCore.get();
        if (w == null) return;
        File dir = new File(w.getDataFolder(), "logs");
        File f = new File(dir, prefix + "-" + LocalDate.now() + ".log");
        Runnable job = () -> {
            try {
                dir.mkdirs();
                Files.writeString(f.toPath(), line + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                w.getLogger().warning("기록을 남기지 못했습니다: " + f.getName() + " (" + e.getMessage() + ")");
            }
        };
        try {
            io.submit(job);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            job.run();
        }
    }

    /** 서버 종료 시: 남은 기록을 모두 쓰고 끝냄 */
    public static void shutdown() {
        io.shutdown();
        try {
            io.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        io = Executors.newSingleThreadExecutor();
    }
}
