package kr.wave.core.util;

import kr.wave.core.WaveCore;
import org.bukkit.Bukkit;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/** 서버 콘솔 경고. 매 틱 반복될 수 있는 실패는 once() 로 한 번만 남김 */
public final class Log {

    private static final Set<String> seen = ConcurrentHashMap.newKeySet();

    private Log() {
    }

    private static Logger logger() {
        WaveCore w = WaveCore.get();
        return w != null ? w.getLogger() : Bukkit.getLogger();
    }

    public static void warn(String msg) {
        logger().warning(msg);
    }

    /** 같은 key 로는 서버가 켜져 있는 동안 한 번만 경고 */
    public static void once(String key, String msg) {
        if (seen.add(key)) logger().warning(msg + " (같은 경고는 다시 표시하지 않습니다)");
    }
}
