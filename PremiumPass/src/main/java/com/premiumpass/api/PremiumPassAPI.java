package com.premiumpass.api;

import com.premiumpass.PremiumPass;
import com.premiumpass.data.PlayerData;
import org.bukkit.entity.Player;

/**
 * 다른 플러그인(예: DailyShop)이 프리미엄 패스 정보를 쓰기 위한 공개 API.
 * 사용 전 {@link #isAvailable()} 로 확인하세요.
 */
public final class PremiumPassAPI {

    private static PremiumPass plugin;

    private PremiumPassAPI() {
    }

    public static void init(PremiumPass p) {
        plugin = p;
    }

    public static boolean isAvailable() {
        return plugin != null && plugin.isEnabled();
    }

    private static PlayerData data(Player p) {
        PlayerData d = plugin.data().get(p);
        plugin.pass().ensure(p, d);
        return d;
    }

    /** 프리미엄 패스 포인트 */
    public static long getPoints(Player p) {
        return data(p).points;
    }

    /** 포인트가 충분하면 차감하고 true */
    public static boolean takePoints(Player p, long amount) {
        if (amount < 0) return false;
        PlayerData d = data(p);
        if (d.points < amount) return false;
        d.points -= amount;
        plugin.data().save(d);
        return true;
    }

    public static void givePoints(Player p, long amount, String reason) {
        PlayerData d = data(p);
        plugin.pass().addPoints(p, d, amount, reason);
        plugin.data().save(d);
    }

    /** 이번 시즌 프리미엄 패스 보유 여부 */
    public static boolean isPremium(Player p) {
        return plugin.pass().isPremium(p, data(p));
    }

    /** 퀘스트 XP 배율 (WaveCore 핫타임). 1 = 없음 */
    public static void setXpBoost(double boost) {
        plugin.pass().setXpBoost(boost);
    }

    public static int getLevel(Player p) {
        return data(p).level;
    }

    public static int getMaxLevel() {
        return plugin.pass().maxLevel();
    }

    /** 레벨을 n 만큼 올림 (최대 레벨까지). 실제로 올라간 레벨 수 */
    public static int addLevels(Player p, int n) {
        PlayerData d = data(p);
        int before = d.level;
        plugin.pass().setLevel(d, before + Math.max(0, n));
        plugin.data().save(d);
        int up = d.level - before;
        if (up > 0) {
            kr.wave.common.Text.send(p, "&a프리미엄 패스 레벨이 &e" + before + " &a→ &e" + d.level + " &a(으)로 올랐습니다! &7(/pass 에서 보상 수령)");
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        }
        return up;
    }

    /** 프리미엄 패스권 아이템 */
    public static org.bukkit.inventory.ItemStack createVoucher(int amount) {
        return plugin.voucher().create(amount);
    }
}
