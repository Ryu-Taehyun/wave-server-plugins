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

    public boolean hasFile(UUID id) {
        return file(id).exists();
    }

    /** 저장된 모든 플레이어 데이터에 작업 적용 후 저장 (전체 초기화용) */
    public int forAll(java.util.function.Consumer<PlayerData> action) {
        java.util.Set<UUID> ids = new java.util.HashSet<>(cache.keySet());
        File[] files = folder.listFiles((dir, n) -> n.endsWith(".yml"));
        if (files != null) for (File f : files) {
            try {
                ids.add(UUID.fromString(f.getName().substring(0, f.getName().length() - 4)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        for (UUID id : ids) {
            PlayerData d = get(id);
            action.accept(d);
            saveAuto(d);
        }
        return ids.size();
    }

    public void save(PlayerData d) {
        String content = d.toYaml();
        File f = file(d.uuid);
        io.submit(() -> write(f, content));
    }

    /** 오프라인 플레이어 데이터를 수정한 뒤 호출: 저장하고 캐시에서 내림 */
    public void saveRelease(PlayerData d) {
        String content = d.toYaml();
        File f = file(d.uuid);
        UUID id = d.uuid;
        io.submit(() -> {
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
        io.submit(() -> write(globalFile, content));
    }

    public void saveAll() {
        for (PlayerData d : cache.values()) save(d);
        saveGlobal();
    }

    private void write(File f, String content) {
        try {
            File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
            Files.writeString(tmp.toPath(), content, StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "저장 실패: " + f.getName(), e);
        }
    }

    public void shutdown() {
        io.shutdown();
        try {
            io.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        for (PlayerData d : cache.values()) write(file(d.uuid), d.toYaml());
        write(globalFile, global.toYaml());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        get(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        PlayerData d = cache.get(e.getPlayer().getUniqueId());
        if (d != null) saveRelease(d);
    }
}
