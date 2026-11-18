package kr.wave.core.api;

import kr.wave.core.WaveCore;
import kr.wave.core.data.PlayerData;
import org.bukkit.entity.Player;

/**
 * 다른 플러그인(예: DailyShop)이 WaveCore 캐시를 쓰기 위한 공개 API.
 * 사용 전 {@link #isAvailable()} 로 확인하세요.
 */
public final class WaveAPI {

    private WaveAPI() {
    }

    public static boolean isAvailable() {
        WaveCore w = WaveCore.get();
        return w != null && w.isEnabled();
    }

    /** 캐시 (paid = true 면 유료 캐시) */
    public static long getCash(Player p, boolean paid) {
        PlayerData d = WaveCore.get().store().get(p);
        return paid ? d.exMoney : d.money;
    }

    /** 잔액이 충분하면 차감하고 true */
    public static boolean takeCash(Player p, long amount, boolean paid) {
        if (amount < 0) return false;
        PlayerData d = WaveCore.get().store().get(p);
        if (paid) {
            if (d.exMoney < amount) return false;
            d.exMoney -= amount;
        } else {
            if (d.money < amount) return false;
            d.money -= amount;
        }
        WaveCore.get().store().save(d);
        return true;
    }
}
