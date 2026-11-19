package com.premiumpass.quest;

import com.premiumpass.PremiumPass;
import com.premiumpass.data.PlayerData;
import kr.wave.common.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** 일일 / 시즌 퀘스트 로드 및 진행 처리 */
public class QuestManager {

    private final PremiumPass plugin;
    private final Map<String, QuestDef> daily = new LinkedHashMap<>();
    private final Map<String, QuestDef> season = new LinkedHashMap<>();
    private final Map<UUID, Location> lastLocation = new HashMap<>();

    private int dailyCount;
    private boolean actionbar;

    public QuestManager(PremiumPass plugin) {
        this.plugin = plugin;
    }

    public void load() {
        daily.clear();
        season.clear();
        File f = new File(plugin.getDataFolder(), "quests.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        read(y.getConfigurationSection("daily"), daily);
        read(y.getConfigurationSection("season"), season);
        dailyCount = Math.max(1, plugin.getConfig().getInt("daily.count", 5));
        actionbar = plugin.getConfig().getBoolean("quests.actionbar-progress", true);
        plugin.getLogger().info("퀘스트 로드: 일일 " + daily.size() + "개, 시즌 " + season.size() + "개");
    }

    private void read(ConfigurationSection sec, Map<String, QuestDef> into) {
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(id);
            if (s == null) continue;
            QuestType type;
            try {
                type = QuestType.valueOf(s.getString("type", "").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("quests.yml: '" + id + "' 의 type 이 올바르지 않습니다: " + s.getString("type"));
                continue;
            }
            Material icon = Material.matchMaterial(s.getString("icon", "PAPER"));
            if (icon == null || !icon.isItem()) icon = Material.PAPER;
            into.put(id, new QuestDef(id,
                    s.getString("name", id),
                    s.getString("description", ""),
                    type,
                    s.getStringList("targets"),
                    s.getLong("amount", 1),
                    s.getLong("xp", 0),
                    s.getLong("points", 0),
                    icon));
        }
    }

    /* ---------------- 조회 ---------------- */

    public QuestDef daily(String id) {
        return daily.get(id);
    }

    public List<QuestDef> seasonQuests() {
        return new ArrayList<>(season.values());
    }

    public List<QuestDef> dailyQuestsOf(PlayerData d) {
        List<QuestDef> out = new ArrayList<>();
        for (String id : d.dailyIds) {
            QuestDef q = daily.get(id);
            if (q != null) out.add(q);
        }
        return out;
    }

    /** 이 블록 종류가 BREAK_BLOCK 퀘스트 대상인지 (설치 블록 추적 여부 판단용) */
    public boolean isBreakTarget(Material m) {
        String key = m.name();
        for (QuestDef q : daily.values()) if (q.type == QuestType.BREAK_BLOCK && q.matches(key)) return true;
        for (QuestDef q : season.values()) if (q.type == QuestType.BREAK_BLOCK && q.matches(key)) return true;
        return false;
    }

    /* ---------------- 일일 배정 ---------------- */

    public void assignDaily(PlayerData d, String key) {
        d.dailyKey = key;
        d.dailyIds.clear();
        d.dailyProgress.clear();
        d.dailyDone.clear();
        List<String> pool = new ArrayList<>(daily.keySet());
        Collections.shuffle(pool);
        for (int i = 0; i < Math.min(dailyCount, pool.size()); i++) d.dailyIds.add(pool.get(i));
    }

    /* ---------------- 진행 ---------------- */

    public void progress(Player p, QuestType type, long amount, String... keys) {
        if (amount <= 0) return;
        PlayerData d = plugin.data().get(p);
        plugin.pass().ensure(p, d);

        for (String id : new ArrayList<>(d.dailyIds)) {
            QuestDef q = daily.get(id);
            if (q == null || q.type != type || d.dailyDone.contains(id) || !q.matches(keys)) continue;
            long now = Math.min(q.amount, d.dailyProgress.getOrDefault(id, 0L) + amount);
            d.dailyProgress.put(id, now);
            if (now >= q.amount) {
                d.dailyDone.add(id);
                complete(p, d, q, "일일");
                progress(p, QuestType.COMPLETE_DAILY, 1, "ANY");
            } else {
                showProgress(p, q, now);
            }
        }

        for (QuestDef q : season.values()) {
            if (q.type != type || d.seasonDone.contains(q.id) || !q.matches(keys)) continue;
            long now = Math.min(q.amount, d.seasonProgress.getOrDefault(q.id, 0L) + amount);
            d.seasonProgress.put(q.id, now);
            if (now >= q.amount) {
                d.seasonDone.add(q.id);
                complete(p, d, q, "시즌");
            } else {
                showProgress(p, q, now);
            }
        }
    }

    private void showProgress(Player p, QuestDef q, long now) {
        if (!actionbar) return;
        Text.actionBar(p, q.name + " &f" + Text.num(now) + "&7/&f" + Text.num(q.amount) + " " + Text.bar(now, q.amount, 10));
    }

    private void complete(Player p, PlayerData d, QuestDef q, String kind) {
        p.sendTitle(Text.color("&a퀘스트 완료!"), Text.color("&7[" + kind + "] " + q.name), 5, 40, 10);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.2f);
        boolean premium = plugin.pass().isPremium(p, d);
        long xp = premium ? q.xp * (100 + plugin.pass().questXpBonus()) / 100 : q.xp;
        long pts = premium ? q.points * (100 + plugin.pass().questPointBonus()) / 100 : q.points;
        Text.send(p, "&7[" + kind + " 퀘스트] " + q.name + " &a완료! &f보상: &b" + Text.num(xp) + " XP"
                + (pts > 0 ? " &f+ &6" + Text.num(pts) + " 포인트" : "")
                + (premium && (xp != q.xp || pts != q.points) ? " &6(프리미엄 보너스)" : ""));
        plugin.pass().addXp(p, d, xp);
        plugin.pass().addPoints(p, d, pts, kind + " 퀘스트 보상");
        plugin.data().save(d);
    }

    /* ---------------- 접속 시간 ---------------- */

    /** 1분마다 호출: 움직인 플레이어만 접속 시간 인정 + 일일 초기화 확인 */
    public void minuteTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = plugin.data().get(p);
            plugin.pass().ensure(p, d);

            Location now = p.getLocation();
            Location before = lastLocation.put(p.getUniqueId(), now);
            boolean moved = before == null
                    || before.getWorld() != now.getWorld()
                    || before.distanceSquared(now) > 1
                    || Math.abs(before.getYaw() - now.getYaw()) > 1;
            if (before != null && moved) {
                progress(p, QuestType.PLAYTIME, 1, "ANY");
            }
        }
        lastLocation.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
    }
}
