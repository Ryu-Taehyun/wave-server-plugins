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
    private final Map<UUID, PlayerData> cache = new HashMap<>();
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
        io.submit(() -> write(f, content));
    }

    /** 저장 후, 접속 중이 아니면 캐시에서 제거 */
    public void saveAndRelease(UUID id) {
        PlayerData d = cache.get(id);
        if (d == null) return;
        final String content = d.toYaml();
        final File f = file(id);
        io.submit(() -> {
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

    private void write(File f, String content) {
        try {
            File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
            Files.writeString(tmp.toPath(), content, StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "플레이어 데이터 저장 실패: " + f.getName(), e);
        }
    }

    public void shutdown() {
        io.shutdown();
        try {
            if (!io.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("저장 작업이 시간 내에 끝나지 않았습니다.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        for (PlayerData d : cache.values()) write(file(d.uuid), d.toYaml());
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
