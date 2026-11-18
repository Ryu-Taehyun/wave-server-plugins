package com.premiumpass.data;

import com.premiumpass.reward.Track;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 플레이어 한 명의 패스 데이터 */
public class PlayerData {

    public final UUID uuid;
    public String name = "";

    /** 데이터가 속한 시즌 번호 (0 = 아직 초기화 안 됨) */
    public int season = 0;
    public int level = 1;
    /** 현재 레벨에서 누적된 XP */
    public long xp = 0;
    /** 최대 레벨 이후 포인트로 전환되기 전의 잔여 XP */
    public long overflowXp = 0;
    /** 프리미엄 패스 포인트 */
    public long points = 0;
    /** 이번 시즌 누적 획득 포인트 (통계용) */
    public long seasonPointsEarned = 0;

    /** 프리미엄 패스가 활성화된 시즌 번호 (현재 시즌과 같으면 프리미엄 보유) */
    public int premiumSeason = 0;

    public final Set<Integer> claimedFree = new HashSet<>();
    public final Set<Integer> claimedPremium = new HashSet<>();

    public String dailyKey = "";
    public final List<String> dailyIds = new ArrayList<>();
    public final Map<String, Long> dailyProgress = new HashMap<>();
    public final Set<String> dailyDone = new HashSet<>();

    public final Map<String, Long> seasonProgress = new HashMap<>();
    public final Set<String> seasonDone = new HashSet<>();

    /** 상점 아이템별 이번 시즌 구매 횟수 */
    public final Map<String, Integer> purchases = new HashMap<>();

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public void load(YamlConfiguration y) {
        name = y.getString("name", "");
        season = y.getInt("season", 0);
        level = Math.max(1, y.getInt("level", 1));
        xp = y.getLong("xp", 0);
        overflowXp = y.getLong("overflow-xp", 0);
        points = y.getLong("points", 0);
        seasonPointsEarned = y.getLong("season-points-earned", 0);
        premiumSeason = y.getInt("premium-season", 0);
        claimedFree.addAll(y.getIntegerList("claimed-free"));
        claimedPremium.addAll(y.getIntegerList("claimed-premium"));
        claimedPremium.addAll(y.getIntegerList("claimed")); // 이전 버전 데이터

        dailyKey = y.getString("daily.key", "");
        dailyIds.addAll(y.getStringList("daily.quests"));
        readLongMap(y.getConfigurationSection("daily.progress"), dailyProgress);
        dailyDone.addAll(y.getStringList("daily.done"));

        readLongMap(y.getConfigurationSection("season-quests.progress"), seasonProgress);
        seasonDone.addAll(y.getStringList("season-quests.done"));

        ConfigurationSection ps = y.getConfigurationSection("purchases");
        if (ps != null) for (String k : ps.getKeys(false)) purchases.put(k, ps.getInt(k));
    }

    public String toYaml() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("name", name);
        y.set("season", season);
        y.set("level", level);
        y.set("xp", xp);
        y.set("overflow-xp", overflowXp);
        y.set("points", points);
        y.set("season-points-earned", seasonPointsEarned);
        y.set("premium-season", premiumSeason);
        y.set("claimed-free", new ArrayList<>(claimedFree));
        y.set("claimed-premium", new ArrayList<>(claimedPremium));
        y.set("daily.key", dailyKey);
        y.set("daily.quests", new ArrayList<>(dailyIds));
        y.set("daily.progress", new HashMap<>(dailyProgress));
        y.set("daily.done", new ArrayList<>(dailyDone));
        y.set("season-quests.progress", new HashMap<>(seasonProgress));
        y.set("season-quests.done", new ArrayList<>(seasonDone));
        y.set("purchases", new HashMap<>(purchases));
        return y.saveToString();
    }

    public Set<Integer> claimed(Track track) {
        return track == Track.FREE ? claimedFree : claimedPremium;
    }

    private static void readLongMap(ConfigurationSection sec, Map<String, Long> into) {
        if (sec == null) return;
        for (String k : sec.getKeys(false)) into.put(k, sec.getLong(k));
    }
}
