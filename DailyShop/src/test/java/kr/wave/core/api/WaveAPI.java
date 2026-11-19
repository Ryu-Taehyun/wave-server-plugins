package kr.wave.core.api;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 테스트용 WaveCore 캐시 대역 */
public final class WaveAPI {
    public static final Map<UUID, Long> CASH = new HashMap<>();

    public static long getCash(Player p, boolean paid) {
        return CASH.getOrDefault(p.getUniqueId(), 0L);
    }

    public static boolean takeCash(Player p, long amount, boolean paid) {
        long have = getCash(p, paid);
        if (have < amount) return false;
        CASH.put(p.getUniqueId(), have - amount);
        return true;
    }
}
