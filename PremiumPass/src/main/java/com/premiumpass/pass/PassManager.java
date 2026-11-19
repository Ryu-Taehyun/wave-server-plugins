package com.premiumpass.pass;

import com.premiumpass.PremiumPass;
import com.premiumpass.data.PlayerData;
import kr.wave.common.Text;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** 레벨 / XP / 포인트 / 시즌 / 일일 초기화 관리 */
public class PassManager {

    private final PremiumPass plugin;

    private int maxLevel;
    private long baseXp;
    private long increasePerLevel;
    private long xpPerPoint;
    private int seasonId;
    private String seasonName;
    private boolean keepPoints;
    private ZoneId zone;
    private int resetHour;
    private boolean broadcastMax;
    private boolean keepPremium;
    private int questXpBonus;
    private int questPointBonus;
    private boolean broadcastPremium;

    public PassManager(PremiumPass plugin) {
        this.plugin = plugin;
    }

    public void load() {
        FileConfiguration c = plugin.getConfig();
        maxLevel = Math.max(1, c.getInt("level.max", 100));
        baseXp = Math.max(1, c.getLong("level.base-xp", 1000));
        increasePerLevel = Math.max(0, c.getLong("level.increase-per-level", 0));
        xpPerPoint = Math.max(1, c.getLong("overflow.xp-per-point", 100));
        seasonId = c.getInt("season.id", 1);
        seasonName = c.getString("season.name", "시즌 " + seasonId);
        keepPoints = c.getBoolean("season.keep-points", true);
        try {
            zone = ZoneId.of(c.getString("daily.timezone", "Asia/Seoul"));
        } catch (java.time.DateTimeException e) {   // 없는 시간대 이름 · 잘못된 형식
            plugin.warnings().invalid("config.yml", "daily.timezone", c.getString("daily.timezone"), "Asia/Seoul 을 사용합니다.");
            zone = ZoneId.of("Asia/Seoul");
        }
        resetHour = Math.floorMod(c.getInt("daily.reset-hour", 0), 24);
        broadcastMax = c.getBoolean("broadcast-max-level", true);
        keepPremium = c.getBoolean("premium.keep-on-new-season", false);
        questXpBonus = Math.max(0, c.getInt("premium.quest-xp-bonus", 0));
        questPointBonus = Math.max(0, c.getInt("premium.quest-point-bonus", 50));
        broadcastPremium = c.getBoolean("premium.broadcast-activation", true);
    }

    /* ---------------- 조회 ---------------- */

    public int maxLevel() {
        return maxLevel;
    }

    public long xpPerPoint() {
        return xpPerPoint;
    }

    public int seasonId() {
        return seasonId;
    }

    public String seasonName() {
        return seasonName;
    }

    /** level -> level+1 에 필요한 XP */
    public long required(int level) {
        return Math.max(1, baseXp + increasePerLevel * (level - 1));
    }

    public String todayKey() {
        return LocalDateTime.now(zone).minusHours(resetHour).toLocalDate().toString();
    }

    public Duration untilDailyReset() {
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime next = now.toLocalDate().atTime(resetHour, 0).atZone(zone);
        if (!next.isAfter(now)) next = next.plusDays(1);
        return Duration.between(now, next);
    }

    /* ---------------- 시즌 / 일일 초기화 ---------------- */

    /** 시즌 변경 및 일일 퀘스트 초기화를 확인한다. p 는 null 가능(오프라인). */
    public void ensure(Player p, PlayerData d) {
        if (d.season != seasonId) {
            boolean firstTime = d.season == 0;
            resetSeason(d);
            if (p != null && !firstTime) {
                Text.send(p, "&e새로운 시즌 &6" + seasonName + "&e이(가) 시작되었습니다! 패스 레벨이 초기화되었습니다.");
            }
        }
        String today = todayKey();
        if (!today.equals(d.dailyKey)) {
            plugin.quests().assignDaily(d, today);
            if (p != null) {
                Text.send(p, "&a일일 퀘스트가 갱신되었습니다! &7(/pass 일일)");
            }
        }
    }

    public void resetSeason(PlayerData d) {
        boolean carryPremium = keepPremium && d.season != 0 && d.premiumSeason == d.season;
        d.season = seasonId;
        if (carryPremium) d.premiumSeason = seasonId;
        d.level = 1;
        d.xp = 0;
        d.overflowXp = 0;
        d.seasonPointsEarned = 0;
        d.claimedFree.clear();
        d.claimedPremium.clear();
        d.seasonProgress.clear();
        d.seasonDone.clear();
        d.purchases.clear();
        if (!keepPoints) d.points = 0;
    }

    /** 다음 시즌으로 넘긴다. 접속 중인 플레이어는 즉시 초기화된다. */
    public void nextSeason(String newName) {
        seasonId++;
        seasonName = newName != null ? newName : "시즌 " + seasonId;
        plugin.getConfig().set("season.id", seasonId);
        plugin.getConfig().set("season.name", seasonName);
        plugin.saveConfig();
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = plugin.data().get(p);
            ensure(p, d);
        }
    }

    /* ---------------- 프리미엄 ---------------- */

    /** 프리미엄 패스 보유 여부. p 가 null 이면(오프라인) 권한은 확인하지 않는다. */
    public boolean isPremium(Player p, PlayerData d) {
        if (d.premiumSeason == seasonId) return true;
        return p != null && p.hasPermission("premiumpass.premium");
    }

    /** @return 새로 활성화됐으면 true, 이미 보유 중이면 false */
    public boolean activatePremium(Player p, PlayerData d) {
        if (d.premiumSeason == seasonId) return false;
        d.premiumSeason = seasonId;
        if (p != null) {
            p.sendTitle(Text.color("&6&l프리미엄 패스"), Text.color("&e" + seasonName + " 프리미엄 패스가 활성화되었습니다!"), 10, 60, 20);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            Text.send(p, "&6프리미엄 패스가 활성화되었습니다! &e이미 도달한 레벨의 프리미엄 보상도 받을 수 있습니다. &7(/pass 보상)");
        }
        if (broadcastPremium) {
            Bukkit.broadcastMessage(Text.color("&6[프리미엄 패스] &e" + d.name + "&f님이 &6프리미엄 패스&f를 활성화했습니다!"));
        }
        plugin.data().save(d);
        return true;
    }

    public void deactivatePremium(PlayerData d) {
        d.premiumSeason = 0;
    }

    public int questXpBonus() {
        return questXpBonus;
    }

    public int questPointBonus() {
        return questPointBonus;
    }

    /* ---------------- XP / 포인트 ---------------- */

    public void addXp(Player p, PlayerData d, long amount) {
        if (amount <= 0) return;
        int before = d.level;
        long overflow = amount;

        if (d.level < maxLevel) {
            d.xp += amount;
            overflow = 0;
            while (d.level < maxLevel && d.xp >= required(d.level)) {
                d.xp -= required(d.level);
                d.level++;
            }
            if (d.level >= maxLevel) {
                overflow = d.xp;
                d.xp = 0;
            }
        }

        if (d.level > before) onLevelUp(p, d, before);

        if (overflow > 0 && d.level >= maxLevel) {
            d.overflowXp += overflow;
            long pts = d.overflowXp / xpPerPoint;
            d.overflowXp %= xpPerPoint;
            if (pts > 0) addPoints(p, d, pts, "최대 레벨 초과 XP 전환");
        }
    }

    private void onLevelUp(Player p, PlayerData d, int before) {
        if (p != null) {
            p.sendTitle(Text.color("&6&l LEVEL UP!"), Text.color("&e프리미엄 패스 &f" + before + " &7→ &a" + d.level), 5, 40, 10);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            Text.send(p, "&a프리미엄 패스 레벨이 &e" + d.level + "&a이(가) 되었습니다! &7(/pass 에서 보상 수령)");
        }
        if (d.level >= maxLevel && before < maxLevel) {
            if (p != null) {
                Text.send(p, "&6&l최대 레벨 달성! &e이제부터 얻는 패스 XP는 &6프리미엄 패스 포인트&e로 적립됩니다.");
            }
            if (broadcastMax) {
                Bukkit.broadcastMessage(Text.color("&6[프리미엄 패스] &e" + d.name + "&f님이 &6" + seasonName + " &f프리미엄 패스 &c" + maxLevel + "레벨&f을 달성했습니다!"));
            }
        }
    }

    public void addPoints(Player p, PlayerData d, long amount, String reason) {
        if (amount <= 0) return;
        d.points += amount;
        d.seasonPointsEarned += amount;
        if (p != null) {
            Text.send(p, "&6+" + Text.num(amount) + " 프리미엄 패스 포인트 &7(" + reason + ") &f보유: &6" + Text.num(d.points));
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.5f);
        }
    }

    public void setLevel(PlayerData d, int level) {
        d.level = Math.max(1, Math.min(maxLevel, level));
        d.xp = 0;
    }
}
