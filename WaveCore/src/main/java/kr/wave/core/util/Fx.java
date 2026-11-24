package kr.wave.core.util;

import kr.wave.core.WaveCore;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntPredicate;

/**
 * 파티클 연출 도구. 모양(고리, 나선, 구체, 기둥, 충격파 등)을 그린다.
 * 서버 버전에 따라 파티클 데이터 형식이 다를 수 있어 모든 호출은 실패해도 조용히 넘어간다.
 */
public final class Fx {

    private Fx() {
    }

    /* ======================= 보는 사람별 숨기기 ======================= */

    /** 지금 그리는 연출이 어느 무기의 타격 연출인지 (null = 모두에게 보임) */
    private static final ThreadLocal<String> SCOPE = new ThreadLocal<>();
    /** (보는 사람, 무기 키) -> 숨김 여부. WaveCore 가 켜질 때 설정 */
    private static java.util.function.BiPredicate<org.bukkit.entity.Player, String> hides = (p, k) -> false;

    public static void hides(java.util.function.BiPredicate<org.bukkit.entity.Player, String> h) {
        hides = h == null ? (p, k) -> false : h;
    }

    /** key 무기의 타격 연출로 그림: 그 무기를 숨긴 플레이어에게는 보내지 않음 */
    public static void scoped(String key, Runnable r) {
        String prev = SCOPE.get();
        if (key == null) SCOPE.remove();
        else SCOPE.set(key);
        try {
            r.run();
        } finally {
            if (prev == null) SCOPE.remove();
            else SCOPE.set(prev);
        }
    }

    private static <T> void spawn(Location l, Particle type, int count, double sx, double sy, double sz, double speed, T data) {
        World w = l.getWorld();
        if (w == null) return;
        try {
            String key = SCOPE.get();
            if (key == null) {
                w.spawnParticle(type, l, count, sx, sy, sz, speed, data, true);
                return;
            }
            for (org.bukkit.entity.Player p : w.getPlayers()) {
                if (p.getLocation().distanceSquared(l) > 160 * 160 || hides.test(p, key)) continue;
                p.spawnParticle(type, l, count, sx, sy, sz, speed, data);
            }
        } catch (RuntimeException e) {   // 서버 버전에 따라 없는 파티클 · 잘못된 데이터: 연출만 생략
            Log.once("fx:" + e.getClass().getSimpleName() + ":" + e.getMessage(), "파티클을 표시하지 못했습니다: " + e);
        }
    }

    public static Color rgb(int hex) {
        return Color.fromRGB(hex);
    }

    /* ======================= 기본 ======================= */

    public static void p(Location l, Particle type, int count, double spread, double speed) {
        spawn(l, type, count, spread, spread, spread, speed, null);
    }

    public static void p(Location l, Particle type, int count, double sx, double sy, double sz, double speed) {
        spawn(l, type, count, sx, sy, sz, speed, null);
    }

    public static void dust(Location l, Color c, float size, int count, double spread) {
        spawn(l, Particle.DUST, count, spread, spread, spread, 0, new Particle.DustOptions(c, size));
    }

    /** from 색에서 to 색으로 변하는 먼지 */
    public static void fade(Location l, Color from, Color to, float size, int count, double spread) {
        spawn(l, Particle.DUST_COLOR_TRANSITION, count, spread, spread, spread, 0, new Particle.DustTransition(from, to, size));
    }

    public static void block(Location l, Particle type, BlockData data, int count, double spread) {
        spawn(l, type, count, spread, spread, spread, 0, data);
    }

    /** 번개 효과 (피해 없음) */
    public static void lightning(Location l) {
        World w = l.getWorld();
        if (w == null) return;
        try {
            w.strikeLightningEffect(l);
        } catch (RuntimeException e) {   // 서버 버전에 따라 없는 파티클 · 잘못된 데이터: 연출만 생략
            Log.once("fx:" + e.getClass().getSimpleName() + ":" + e.getMessage(), "파티클을 표시하지 못했습니다: " + e);
        }
    }

    /* ======================= 정적인 모양 ======================= */

    /** 수평 고리 */
    public static void ring(Location c, double r, int points, Color color, float size) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            dust(c.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r), color, size, 1, 0);
        }
    }

    /** 수평 고리 (일반 파티클) */
    public static void ring(Location c, double r, int points, Particle type) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            p(c.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r), type, 1, 0, 0);
        }
    }

    /** 구 표면 */
    public static void sphere(Location c, double r, int points, Color color, float size) {
        double golden = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < points; i++) {
            double y = 1 - (i / (double) (points - 1)) * 2;
            double rad = Math.sqrt(1 - y * y);
            double t = golden * i;
            dust(c.clone().add(Math.cos(t) * rad * r, y * r, Math.sin(t) * rad * r), color, size, 1, 0);
        }
    }

    /** 두 지점을 잇는 선 */
    public static void line(Location a, Location b, Color color, float size, double step) {
        Vector d = b.toVector().subtract(a.toVector());
        double len = d.length();
        if (len < 0.01) return;
        d.normalize().multiply(step);
        Location cur = a.clone();
        for (double t = 0; t < len; t += step) {
            dust(cur, color, size, 1, 0);
            cur.add(d);
        }
    }

    /** 하늘에서 내려꽂히는 빛기둥 */
    public static void pillar(Location base, double height, Color color, Particle extra) {
        for (double y = 0; y < height; y += 0.25) {
            Location l = base.clone().add(0, y, 0);
            dust(l, color, 1.6f, 2, 0.08);
            if (extra != null && y % 1 < 0.25) p(l, extra, 1, 0.1, 0.01);
        }
    }

    /** 사방으로 터지는 구형 폭발 */
    public static void burst(Location c, Particle type, int count, double speed) {
        p(c, type, count, 0.1, speed);
    }

    /** 바라보는 방향 뒤쪽 위에 뜨는 초승달 */
    public static void crescent(Location eye, Color outer, Color inner) {
        Vector dir = eye.getDirection().setY(0);
        if (dir.lengthSquared() < 0.01) dir = new Vector(0, 0, 1);
        dir.normalize();
        Vector side = new Vector(-dir.getZ(), 0, dir.getX());
        Location center = eye.clone().add(0, 0.9, 0).subtract(dir.clone().multiply(0.6));
        for (int i = 0; i <= 28; i++) {
            double a = Math.PI * (0.15 + 0.7 * i / 28.0);
            double r1 = 0.9;
            double r2 = 0.6;
            Location o = center.clone().add(side.clone().multiply(Math.cos(a) * r1)).add(0, Math.sin(a) * r1, 0);
            Location in = center.clone().add(side.clone().multiply(Math.cos(a) * r2 + 0.25)).add(0, Math.sin(a) * r2, 0);
            dust(o, outer, 1.1f, 1, 0);
            if (i % 2 == 0) dust(in, inner, 0.9f, 1, 0);
        }
    }

    /* ======================= 움직이는 모양 ======================= */

    private static BukkitTask timer(long period, IntPredicate step) {
        int[] t = {0};
        BukkitTask[] task = new BukkitTask[1];
        String key = SCOPE.get();   // 타격 연출 중에 시작한 움직이는 모양도 같은 무기로
        task[0] = Bukkit.getScheduler().runTaskTimer(WaveCore.get(), () -> scoped(key, () -> {
            if (!step.test(t[0]++)) task[0].cancel();
        }), 0L, period);
        return task[0];
    }

    /** 바깥으로 퍼지는 충격파 고리 */
    public static void shockwave(Location c, Color color, double maxR, int ticks) {
        Location base = c.clone();
        timer(1, t -> {
            double r = maxR * (t + 1) / ticks;
            ring(base, r, (int) Math.max(12, r * 14), color, 1.3f);
            return t + 1 < ticks;
        });
    }

    /** 대상을 따라 올라가는 이중 나선 */
    public static void helix(Entity follow, Color a, Color b, double radius, double height, int ticks) {
        timer(1, t -> {
            if (!follow.isValid()) return false;
            Location base = follow.getLocation();
            double prog = (double) t / ticks;
            double y = height * prog;
            double ang = t * 0.5;
            dust(base.clone().add(Math.cos(ang) * radius, y, Math.sin(ang) * radius), a, 1.2f, 1, 0);
            dust(base.clone().add(Math.cos(ang + Math.PI) * radius, y, Math.sin(ang + Math.PI) * radius), b, 1.2f, 1, 0);
            return t + 1 < ticks;
        });
    }

    /** 대상 주위를 도는 회오리 (지속 시간 동안) */
    public static void tornado(Entity follow, Color[] colors, Particle extra, int ticks) {
        timer(2, t -> {
            if (!follow.isValid()) return false;
            Location base = follow.getLocation();
            for (int strand = 0; strand < 3; strand++) {
                for (double y = 0; y < 2.6; y += 0.35) {
                    double r = 0.5 + y * 0.35;
                    double a = t * 0.45 + strand * (Math.PI * 2 / 3) + y * 1.4;
                    dust(base.clone().add(Math.cos(a) * r, y, Math.sin(a) * r), colors[(strand + (int) (y * 2)) % colors.length], 1.1f, 1, 0);
                }
            }
            if (extra != null && t % 2 == 0) p(base.clone().add(0, 1, 0), extra, 4, 1.2, 0.02);
            return t * 2 < ticks;
        });
    }

    /** 대상을 감싸며 안쪽으로 빨려 들어가는 구체 (내파) */
    public static void implode(Location c, Color color, double r, int ticks) {
        Location base = c.clone();
        timer(1, t -> {
            double cur = r * (1 - (double) t / ticks);
            sphere(base, Math.max(0.15, cur), 26, color, 1.0f);
            return t + 1 < ticks;
        });
    }

    /** 투사체 꼬리 */
    public static void trail(Entity projectile, Color color, Particle extra, int maxTicks) {
        timer(1, t -> {
            if (!projectile.isValid() || projectile.isOnGround() || t > maxTicks) return false;
            Location l = projectile.getLocation();
            dust(l, color, 1.0f, 2, 0.05);
            if (extra != null) p(l, extra, 2, 0.05, 0.01);
            return true;
        });
    }

}
