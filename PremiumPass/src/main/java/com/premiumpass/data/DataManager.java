package com.premiumpass.data;

import com.premiumpass.PremiumPass;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * 플레이어 데이터 로드/저장.
 * 파일 쓰기는 단일 스레드에서 순서대로 처리하므로 오래된 데이터가 최신 데이터를 덮어쓰지 않는다.
 */
public class DataManager implements Listener {

    private final PremiumPass plugin;
    private final File folder;
    /** 접속 전 미리 읽기(비동기)와 메인 스레드가 같이 쓰므로 스레드 안전한 맵 */
    private final Map<UUID, PlayerData> cache = new java.util.concurrent.ConcurrentHashMap<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "PremiumPass-IO");
        t.setDaemon(true);
        return t;
    });

    public DataManager(PremiumPass plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "playerdata");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("playerdata 폴더를 만들 수 없습니다.");
        }
    }

    public PlayerData get(Player p) {
        PlayerData d = getOrLoad(p.getUniqueId());
        d.name = p.getName();
        return d;
    }

    public PlayerData getOrLoad(UUID id) {
        return cache.computeIfAbsent(id, this::loadFromDisk);
    }

    public Iterable<PlayerData> cached() {
        return cache.values();
    }

    private PlayerData loadFromDisk(UUID id) {
        PlayerData d = new PlayerData(id);
        File f = file(id);
        if (f.exists()) {
            d.load(YamlConfiguration.loadConfiguration(f));
        }
        return d;
    }

    private File file(UUID id) {
        return new File(folder, id + ".yml");
    }

    public void save(PlayerData d) {
        final String content = d.toYaml();
        final File f = file(d.uuid);
        submit(() -> write(f, content));
    }

    /** 저장 스레드에 맡김. 서버 종료로 이미 멈췄으면 바로 씀 (파일 쓰기는 잠금으로 한 번에 하나) */
    private void submit(Runnable job) {
        try {
            io.submit(job);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            job.run();
        }
    }

    /** 저장 후, 접속 중이 아니면 캐시에서 제거 */
    public void saveAndRelease(UUID id) {
        PlayerData d = cache.get(id);
        if (d == null) return;
        final String content = d.toYaml();
        final File f = file(id);
        submit(() -> {
            write(f, content);
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (Bukkit.getPlayer(id) == null) cache.remove(id);
                });
            }
        });
    }

    public void saveAll() {
        for (PlayerData d : cache.values()) save(d);
    }

    /** 파일 쓰기는 한 번에 하나만 (저장 스레드와 종료 시 메인 스레드가 겹치지 않게) */
    private final Object writeLock = new Object();

    private void write(File f, String content) {
        synchronized (writeLock) {
            writeNow(f, content);
        }
    }

    private void writeNow(File f, String content) {
        try {
            File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
            Files.writeString(tmp.toPath(), content, StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "플레이어 데이터 저장 실패: " + f.getName(), e);
        }
    }

    /**
     * 종료 시 저장: 남은 저장 작업이 끝나기를 기다리고, 시간 안에 안 끝나면 대기 중인 작업은 취소한 뒤
     * 마지막 상태를 한 번 더 씁니다. 파일 쓰기는 writeLock 으로 한 번에 하나만 하므로
     * 저장 스레드와 메인 스레드가 같은 파일을 동시에 쓰는 일은 없습니다.
     */
    public void shutdown() {
        io.shutdown();
        try {
            if (!io.awaitTermination(10, TimeUnit.SECONDS)) {
                int dropped = io.shutdownNow().size();   // 아래에서 최신 상태로 다시 쓰므로 취소해도 손실 없음
                plugin.getLogger().warning("저장 작업이 10초 안에 끝나지 않아 대기 중인 " + dropped + "개를 취소하고 마지막 상태를 다시 저장합니다.");
                if (!io.awaitTermination(5, TimeUnit.SECONDS)) plugin.getLogger().warning("저장 스레드가 아직 끝나지 않았습니다. 진행 중인 쓰기가 끝난 뒤 이어서 저장합니다.");
            }
        } catch (InterruptedException e) {
            io.shutdownNow();
            Thread.currentThread().interrupt();
        }
        for (PlayerData d : cache.values()) write(file(d.uuid), d.toYaml());
    }

    /** 접속 전에 (메인 스레드가 아닌 곳에서) 파일을 미리 읽어 둠: 접속할 때 디스크를 읽느라 멈추지 않게 */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(org.bukkit.event.player.AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() != org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        try {
            getOrLoad(e.getUniqueId());
        } catch (RuntimeException ex) {   // 실패하면 접속할 때 다시 읽음
            plugin.getLogger().log(Level.WARNING, "플레이어 데이터 미리 읽기 실패: " + e.getUniqueId(), ex);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        PlayerData d = get(p);
        plugin.pass().ensure(p, d);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        saveAndRelease(e.getPlayer().getUniqueId());
    }
}
