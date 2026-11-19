package kr.wave.core.data;

import kr.wave.core.WaveCore;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/** 플레이어 데이터(playerdata/uuid.yml)와 서버 데이터(global.yml) 저장소. 파일 쓰기는 한 스레드에서 순서대로. */
public class Store implements Listener {

    private final WaveCore plugin;
    private final File folder;
    private final File globalFile;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private final GlobalData global = new GlobalData();
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "WaveCore-IO");
        t.setDaemon(true);
        return t;
    });

    public Store(WaveCore plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "playerdata");
        this.globalFile = new File(plugin.getDataFolder(), "global.yml");
        folder.mkdirs();
        if (globalFile.exists()) global.load(YamlConfiguration.loadConfiguration(globalFile));
        Bukkit.getScheduler().runTaskTimer(plugin, this::sweepPreloaded, 600L, 600L);
    }

    public GlobalData global() {
        return global;
    }

    public PlayerData get(Player p) {
        PlayerData d = get(p.getUniqueId());
        d.name = p.getName();
        return d;
    }

    public PlayerData get(OfflinePlayer p) {
        PlayerData d = get(p.getUniqueId());
        if (p.getName() != null) d.name = p.getName();
        return d;
    }

    public PlayerData get(UUID id) {
        return cache.computeIfAbsent(id, this::load);
    }

    public Iterable<PlayerData> cached() {
        return cache.values();
    }

    private PlayerData load(UUID id) {
        PlayerData d = new PlayerData(id);
        File f = file(id);
        if (f.exists()) d.load(YamlConfiguration.loadConfiguration(f));
        return d;
    }

    private File file(UUID id) {
        return new File(folder, id + ".yml");
    }

    /** 저장된 플레이어 수 (첫 접속 안내용, 파일 목록만 셈) */
    public int playerCount() {
        String[] n = folder.list((dir, name) -> name.endsWith(".yml"));
        return n == null ? 0 : n.length;
    }

    /** 저장된 모든 플레이어 데이터에 작업 적용 후 저장 (전체 초기화용) */
    public int forAll(java.util.function.Consumer<PlayerData> action) {
        java.util.Set<UUID> ids = new java.util.HashSet<>(cache.keySet());
        File[] files = folder.listFiles((dir, n) -> n.endsWith(".yml"));
        if (files != null) for (File f : files) {
            try {
                ids.add(UUID.fromString(f.getName().substring(0, f.getName().length() - 4)));
            } catch (IllegalArgumentException e) {
                kr.wave.core.util.Log.once("store:name:" + f.getName(), "players 폴더에 플레이어 파일이 아닌 파일이 있어 건너뜁니다: " + f.getName());
            }
        }
        for (UUID id : ids) {
            PlayerData d = get(id);
            action.accept(d);
            saveAuto(d);
        }
        return ids.size();
    }

    /** 저장된 모든 플레이어 데이터를 읽기만 함 (저장 · 캐시 변경 없음, 미리보기용) */
    public int scanAll(java.util.function.Consumer<PlayerData> action) {
        java.util.Set<UUID> ids = new java.util.HashSet<>(cache.keySet());
        File[] files = folder.listFiles((dir, n) -> n.endsWith(".yml"));
        if (files != null) for (File f : files) {
            try {
                ids.add(UUID.fromString(f.getName().substring(0, f.getName().length() - 4)));
            } catch (IllegalArgumentException ignored) {   // 플레이어 파일이 아님 (forAll 에서 경고)
            }
        }
        for (UUID id : ids) {
            PlayerData d = cache.get(id);
            action.accept(d != null ? d : load(id));
        }
        return ids.size();
    }

    /** 지금까지 맡긴 저장이 모두 끝날 때까지 기다림 (백업 직전용). 시간 안에 끝나면 true */
    public boolean flush(long seconds) {
        try {
            io.submit(() -> {
            }).get(seconds, TimeUnit.SECONDS);
            return true;
        } catch (java.util.concurrent.RejectedExecutionException e) {
            return true;   // 이미 종료 중: 저장은 바로바로 쓰였음
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "저장 대기 중 오류", e);
            return false;
        }
    }

    /** playerdata 폴더 · global.yml 위치 (백업용) */
    public File folder() {
        return folder;
    }

    public File globalFile() {
        return globalFile;
    }

    public void save(PlayerData d) {
        String content = d.toYaml();
        File f = file(d.uuid);
        submit(() -> write(f, content));
    }

    /** 오프라인 플레이어 데이터를 수정한 뒤 호출: 저장하고 캐시에서 내림 */
    public void saveRelease(PlayerData d) {
        String content = d.toYaml();
        File f = file(d.uuid);
        UUID id = d.uuid;
        submit(() -> {
            write(f, content);
            if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> {
                if (Bukkit.getPlayer(id) == null) cache.remove(id);
            });
        });
    }

    /** 온라인이면 저장만, 오프라인이면 저장 후 캐시에서 내림 */
    public void saveAuto(PlayerData d) {
        if (Bukkit.getPlayer(d.uuid) != null) save(d);
        else saveRelease(d);
    }

    public void saveGlobal() {
        String content = global.toYaml();
        submit(() -> write(globalFile, content));
    }

    public void saveAll() {
        for (PlayerData d : cache.values()) save(d);
        saveGlobal();
    }

    /** 저장 스레드에 맡김. 서버 종료로 이미 멈췄으면 바로 씀 (파일 쓰기는 잠금으로 한 번에 하나) */
    private void submit(Runnable job) {
        try {
            io.submit(job);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            job.run();
        }
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
            plugin.getLogger().log(Level.SEVERE, "저장 실패: " + f.getName(), e);
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
        write(globalFile, global.toYaml());
    }

    /** 미리 읽기만 하고 아직 접속하지 않은 플레이어 -> 미리 읽은 서버 틱 */
    private final Map<UUID, Long> preloaded = new java.util.concurrent.ConcurrentHashMap<>();
    /** 미리 읽은 뒤 이 시간 안에 접속하지 않으면 캐시에서 내림 (접속이 밴 · 화이트리스트 · 인원 초과 등으로 실패한 경우) */
    private static final long PRELOAD_KEEP_TICKS = 20 * 60;

    /** 30초마다: 미리 읽었지만 접속하지 않은 데이터를 캐시에서 내림 (자동 저장 대상에서도 빠짐) */
    private void sweepPreloaded() {
        long now = Bukkit.getCurrentTick();
        for (var en : preloaded.entrySet()) {
            if (now - en.getValue() < PRELOAD_KEEP_TICKS) continue;
            UUID id = en.getKey();
            preloaded.remove(id);
            if (Bukkit.getPlayer(id) == null) cache.remove(id);
        }
    }

    /** 미리 읽기만 하고 아직 접속하지 않은 플레이어 수 (테스트 · 확인용) */
    public int preloadedCount() {
        return preloaded.size();
    }

    /** 접속 전에 (메인 스레드가 아닌 곳에서) 플레이어 파일을 미리 읽어 둠: 접속할 때 디스크를 읽느라 멈추지 않게 */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(org.bukkit.event.player.AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() != org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        try {
            if (!cache.containsKey(e.getUniqueId())) preloaded.put(e.getUniqueId(), (long) Bukkit.getCurrentTick());
            get(e.getUniqueId());
        } catch (RuntimeException ex) {   // 미리 읽기 실패: 접속할 때 다시 읽음
            plugin.getLogger().log(Level.WARNING, "플레이어 데이터 미리 읽기 실패: " + e.getUniqueId(), ex);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        preloaded.remove(e.getPlayer().getUniqueId());
        get(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        PlayerData d = cache.get(e.getPlayer().getUniqueId());
        if (d != null) saveRelease(d);
    }
}
