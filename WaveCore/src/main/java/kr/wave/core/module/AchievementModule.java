package kr.wave.core.module;

import kr.wave.core.WaveCore;
import kr.wave.core.data.PlayerData;
import kr.wave.core.util.Cmd;
import kr.wave.core.util.Gui;
import kr.wave.core.util.It;
import kr.wave.core.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToLongFunction;

/**
 * 업적 (도전 과제)
 * - 업적마다 여러 단계(I, II, III ...). 단계를 넘으면 알림, /업적 에서 클릭해 보상(캐시) 받기
 * - 마지막 단계 보상은 업적 전용 칭호 (프로필 → 칭호에서 적용), 달성하면 전체 공지
 * - 기록은 시즌 초기화와 상관없이 계속 쌓임 (PlayerData.stats)
 * config achievements: enabled, reward-multiplier, broadcast
 */
public class AchievementModule extends Module {

    public static final String TAG = "<b><gradient:#FFE259:#FFA751>[ 업적 ]</gradient></b> ";
    public static final String TITLE_PREFIX = "ach|";

    /** 한 단계: 목표 수치, 보상 캐시, (마지막 단계면) 칭호 */
    public record Tier(long goal, long cash, String title) {
    }

    /** 업적 하나 (단계 여러 개) */
    public record Ach(String id, String cat, Material icon, String name, String desc, String unit,
                      ToLongFunction<PlayerData> value, Tier... tiers) {
        public String titleKey() {
            return TITLE_PREFIX + id;
        }

        public String title() {
            for (Tier t : tiers) if (t.title() != null) return t.title();
            return null;
        }
    }

    private static Tier t(long goal, long cash) {
        return new Tier(goal, cash, null);
    }

    private static Tier t(long goal, long cash, String title) {
        return new Tier(goal, cash, title);
    }

    private static ToLongFunction<PlayerData> stat(String key) {
        return d -> d.stat(key);
    }

    private static String ti(String colors, String text) {
        return "<b><gradient:" + colors + ">" + text + "</gradient></b>";
    }

    /** 업적 목록 (GUI 순서 그대로) */
    public static final List<Ach> ALL = List.of(
            // 전투
            new Ach("pvp", "전투", Material.IRON_SWORD, "전장의 사냥꾼", "PvP 에서 다른 플레이어를 처치하세요.%nl%&8(같은 상대는 10분에 한 번만 인정)", "처치",
                    stat("pvp"), t(10, 2_000), t(50, 5_000), t(200, 15_000), t(500, 40_000), t(1000, 100_000, ti("#FF6B6B:#B71C1C", "[⚔] 전장의 학살자"))),
            new Ach("streak", "전투", Material.BLAZE_POWDER, "멈추지 않는 칼날", "죽지 않고 연속으로 처치하세요.%nl%&8(최고 기록 기준)", "연속 처치",
                    stat("streak"), t(3, 3_000), t(5, 8_000), t(10, 20_000), t(20, 50_000, ti("#FFB347:#FF3D00", "[🔥] 불멸의 광전사"))),
            new Ach("duel", "전투", Material.SHIELD, "결투의 달인", "1:1 결투에서 승리하세요.", "승",
                    stat("duel"), t(1, 1_000), t(10, 5_000), t(50, 15_000), t(100, 40_000, ti("#E0E0E0:#FF5252", "[🗡] 결투왕"))),
            new Ach("boss", "전투", Material.END_CRYSTAL, "천공을 꺾은 자", "천공의 심판자 처치에 참여하세요.", "회",
                    stat("boss"), t(1, 5_000), t(5, 15_000), t(15, 40_000), t(30, 80_000, ti("#FFF200:#FFFCD4", "[⚡] 천공의 정복자"))),
            new Ach("mob", "전투", Material.ZOMBIE_HEAD, "몬스터 사냥꾼", "적대적인 몬스터를 처치하세요.", "마리",
                    stat("mob"), t(100, 1_000), t(1000, 5_000), t(5000, 20_000), t(20000, 50_000, ti("#9CCC65:#33691E", "[☠] 괴물 사냥꾼"))),
            // 성장 · 활동
            new Ach("level", "성장", Material.EXPERIENCE_BOTTLE, "성장의 길", "플레이어 레벨을 올리세요.", "레벨",
                    d -> d.level, t(10, 5_000), t(20, 10_000), t(30, 20_000), t(40, 35_000), t(50, 60_000, ti("#CEFF74:#00C853", "[✦] 정점에 선 자"))),
            new Ach("attend", "성장", Material.CLOCK, "개근상", "출석한 날을 모으세요. (누적)", "일",
                    d -> d.attendTotal, t(7, 3_000), t(30, 10_000), t(60, 25_000), t(100, 50_000, ti("#FFD84D:#FF9F1C", "[📅] 개근왕"))),
            new Ach("playtime", "성장", Material.COMPASS, "서버의 주민", "서버에서 시간을 보내세요.%nl%&8(5분 넘게 가만히 있으면 세지 않음)", "시간",
                    d -> d.stat("playtime") / 60, t(10, 3_000), t(50, 10_000), t(100, 25_000), t(300, 60_000, ti("#80DEEA:#3949AB", "[⏳] 붙박이 주민"))),
            new Ach("money", "성장", Material.GOLD_INGOT, "큰손", "캐시를 모으세요. (누적 획득)", "캐시",
                    d -> d.moneyTotal, t(50_000, 2_000), t(200_000, 8_000), t(1_000_000, 30_000), t(5_000_000, 100_000, ti("#FFF3B0:#FFB300", "[💰] 황금 손"))),
            new Ach("ore", "성장", Material.DIAMOND_ORE, "심층의 광부", "다이아몬드 · 고대 잔해를 캐세요.%nl%&8(직접 설치한 광석은 제외)", "개",
                    stat("ore"), t(50, 2_000), t(300, 10_000), t(1000, 30_000, ti("#B2EBF2:#00838F", "[⛏] 심층의 광부"))),
            // 수집 · 이벤트
            new Ach("collect", "수집", Material.NETHER_STAR, "수집가", "특별 아이템을 수집하세요. (/컬렉션)", "종",
                    d -> d.collected.size(), t(3, 5_000), t(6, 15_000), t(10, 40_000, ti("#E1BEE7:#8E24AA", "[💎] 전설의 수집가"))),
            new Ach("quiz", "수집", Material.WRITABLE_BOOK, "척척박사", "채팅 퀴즈를 가장 먼저 맞히세요.", "회",
                    stat("quiz"), t(1, 1_000), t(10, 5_000), t(50, 15_000), t(100, 30_000, ti("#FFF59D:#FBC02D", "[💡] 척척박사"))),
            new Ach("supply", "수집", Material.CHEST, "보급병", "보급 상자를 가장 먼저 여세요.", "회",
                    stat("supply"), t(1, 2_000), t(5, 8_000), t(20, 25_000, ti("#C8E6C9:#2E7D32", "[📦] 보급의 달인")))
    );

    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII"};

    /** GUI 칸 (카테고리별 한 줄) */
    private static final int[] SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24, 30, 31, 32};

    /** 지금 연속 처치 수 (죽으면 0) */
    private final Map<UUID, Integer> streak = new HashMap<>();
    /** 접속 시간: 마지막 위치 · 가만히 있은 분 */
    private final Map<UUID, Location> lastPos = new HashMap<>();
    private final Map<UUID, Integer> idle = new HashMap<>();
    private int tick;

    public AchievementModule(WaveCore plugin) {
        super(plugin);
    }

    @Override
    public void enable() {
        cmd("업적", null, (s, l, a) -> {
            if (!Cmd.isPlayer(s)) return;
            Player p = (Player) s;
            if (!enabled()) {
                Msg.send(p, "&c업적이 꺼져 있습니다.");
                return;
            }
            check(p, true);
            open(p, null);
        }, "도전과제", "achievements", "achievement");
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("achievements.enabled", true);
    }

    public static String roman(int i) {
        return i < ROMAN.length ? ROMAN[i] : String.valueOf(i + 1);
    }

    public static Ach byId(String id) {
        for (Ach a : ALL) if (a.id().equals(id)) return a;
        return null;
    }

    /** 업적 칭호 키 → 표시 문자열 (업적 칭호가 아니면 null) */
    public static String titleText(String key) {
        if (key == null || !key.startsWith(TITLE_PREFIX)) return null;
        Ach a = byId(key.substring(TITLE_PREFIX.length()));
        return a == null ? null : a.title();
    }

    private long cash(Tier t) {
        double m = plugin.getConfig().getDouble("achievements.reward-multiplier", 1.0);
        return Math.max(0, Math.round(t.cash() * m));
    }

    /* ======================= 기록 ======================= */

    /** 다른 모듈에서: 기록 더하기 (모듈이 없거나 꺼져 있으면 무시) */
    public static void add(WaveCore plugin, Player p, String key, long n) {
        AchievementModule m = plugin.module(AchievementModule.class);
        if (m != null && p != null) m.add(p, key, n);
    }

    public void add(Player p, String key, long n) {
        if (!enabled() || n <= 0) return;
        PlayerData d = data(p);
        d.stats.merge(key, n, Long::sum);
        check(p, false);
        plugin.store().saveAuto(d);
    }

    /** 최고 기록 갱신 (연속 처치 등) */
    public void max(Player p, String key, long v) {
        if (!enabled()) return;
        PlayerData d = data(p);
        if (v <= d.stat(key)) return;
        d.stats.put(key, v);
        check(p, false);
        plugin.store().saveAuto(d);
    }

    /** RankingModule 에서 인정된 PvP 처치 */
    public static void pvpKill(WaveCore plugin, Player killer) {
        AchievementModule m = plugin.module(AchievementModule.class);
        if (m == null) return;
        int s = m.streak.merge(killer.getUniqueId(), 1, Integer::sum);
        m.add(killer, "pvp", 1);
        m.max(killer, "streak", s);
    }

    public int streakOf(Player p) {
        return streak.getOrDefault(p.getUniqueId(), 0);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        streak.remove(e.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        streak.remove(id);
        lastPos.remove(id);
        idle.remove(id);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        later(100, () -> {
            if (!p.isOnline() || !enabled()) return;
            check(p, true);
            int n = claimable(data(p));
            if (n > 0) Msg.send(p, TAG + "&f받지 않은 업적 보상이 &e" + n + "개 &f있습니다! &e/업적");
        });
    }

    @Override
    public void secondTick() {
        if (!enabled()) return;
        tick++;
        if (tick % 60 == 0) {
            // 1분마다 접속 시간 (5분 넘게 같은 자리 · 같은 시선이면 자리 비움)
            for (Player p : Bukkit.getOnlinePlayers()) {
                Location now = p.getLocation(), before = lastPos.put(p.getUniqueId(), now.clone());
                boolean still = before != null && before.getWorld() == now.getWorld() && before.distanceSquared(now) < 1
                        && Math.abs(before.getYaw() - now.getYaw()) < 1 && Math.abs(before.getPitch() - now.getPitch()) < 1;
                int i = still ? idle.merge(p.getUniqueId(), 1, Integer::sum) : 0;
                if (!still) idle.put(p.getUniqueId(), 0);
                if (i < 5) data(p).stats.merge("playtime", 1L, Long::sum);
            }
        }
        if (tick % 30 == 0) for (Player p : Bukkit.getOnlinePlayers()) check(p, false);   // 레벨 · 출석 · 캐시처럼 다른 곳에서 바뀌는 기록
    }

    /* ======================= 달성 확인 ======================= */

    /** 새로 넘은 단계를 기록하고 알림. quiet 면 알림 대신 한 줄 요약 */
    public void check(Player p, boolean quiet) {
        PlayerData d = data(p);
        List<String> fresh = new ArrayList<>();
        for (Ach a : ALL) {
            long v = a.value().applyAsLong(d);
            for (int i = 0; i < a.tiers().length; i++) {
                String k = a.id() + ":" + i;
                if (v < a.tiers()[i].goal() || d.achReached.contains(k)) continue;
                d.achReached.add(k);
                fresh.add(k);
                Tier t = a.tiers()[i];
                if (t.title() != null && plugin.getConfig().getBoolean("achievements.broadcast", true))
                    Msg.broadcast(TAG + "&e" + p.getName() + "&f님이 업적 &6" + a.name() + "&f의 마지막 단계를 달성했습니다! &7(칭호 " + t.title() + "&7)");
            }
        }
        if (fresh.isEmpty()) return;
        plugin.store().saveAuto(d);
        if (quiet || fresh.size() > 2) {
            Msg.send(p, TAG + "&f업적 &e" + fresh.size() + "개&f를 달성했습니다! &e/업적 &f에서 보상을 받으세요.");
            Msg.sound(p, "ui.toast.challenge_complete", 0.6f, 1.2f);
            return;
        }
        for (String k : fresh) {
            String[] sp = k.split(":");
            Ach a = byId(sp[0]);
            int i = Integer.parseInt(sp[1]);
            Msg.title(p, "<gradient:#FFE259:#FFA751><b>업적 달성!</b></gradient>", "&f" + a.name() + " " + roman(i), 5, 50, 15);
            Msg.send(p, TAG + "&6" + a.name() + " " + roman(i) + " &f달성! &7(" + Msg.num(a.tiers()[i].goal()) + " " + a.unit() + ") &e/업적 &f에서 보상을 받으세요.");
            Msg.sound(p, "ui.toast.challenge_complete", 0.7f, 1.2f);
        }
    }

    /** 받을 수 있는 보상 수 */
    public int claimable(PlayerData d) {
        int n = 0;
        for (String k : d.achReached) if (!d.achClaimed.contains(k) && valid(k)) n++;
        return n;
    }

    private static boolean valid(String k) {
        String[] sp = k.split(":");
        Ach a = sp.length == 2 ? byId(sp[0]) : null;
        if (a == null) return false;
        try {
            int i = Integer.parseInt(sp[1]);
            return i >= 0 && i < a.tiers().length;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** 이 업적에서 받을 수 있는 보상을 모두 받기. 받은 단계 수 */
    public int claim(Player p, Ach a) {
        PlayerData d = data(p);
        int n = 0;
        long total = 0;
        for (int i = 0; i < a.tiers().length; i++) {
            String k = a.id() + ":" + i;
            if (!d.achReached.contains(k) || d.achClaimed.contains(k)) continue;
            d.achClaimed.add(k);
            Tier t = a.tiers()[i];
            long c = cash(t);
            d.money += c;
            d.moneyTotal += c;
            total += c;
            n++;
            if (t.title() != null && d.titles.add(a.titleKey())) {
                Msg.send(p, TAG + "&f칭호 " + t.title() + " &f획득! &7(프로필 → 칭호에서 적용)");
                Msg.title(p, t.title(), "&f업적 칭호 획득!", 5, 60, 15);
                Msg.sound(p, "ui.toast.challenge_complete", 1f, 1f);
            }
        }
        if (n == 0) return 0;
        Msg.send(p, TAG + "&6" + a.name() + " &f보상 " + n + "개 수령! &a+" + Msg.num(total) + " 캐시");
        Msg.sound(p, "entity.player.levelup", 0.6f, 1.6f);
        plugin.store().save(d);
        return n;
    }

    /* ======================= GUI ======================= */

    private static String bar(long v, long goal) {
        int filled = goal <= 0 ? 10 : (int) Math.min(10, v * 10 / goal);
        return "&a" + "■".repeat(filled) + "&8" + "■".repeat(10 - filled);
    }

    private ItemStack icon(PlayerData d, Ach a) {
        long v = a.value().applyAsLong(d);
        int done = 0, ready = 0;
        List<String> lore = new ArrayList<>();
        lore.add("&8" + a.cat());
        lore.add("&7" + a.desc());
        lore.add("");
        Tier next = null;
        for (int i = 0; i < a.tiers().length; i++) {
            Tier t = a.tiers()[i];
            String k = a.id() + ":" + i;
            boolean reached = v >= t.goal() || d.achReached.contains(k);
            boolean claimed = d.achClaimed.contains(k);
            if (claimed) done++;
            else if (reached) ready++;
            else if (next == null) next = t;
            String mark = claimed ? "&a✔ " : reached ? "&6★ " : next == t ? "&e▶ " : "&8✘ ";
            String col = claimed ? "&a" : reached ? "&6" : next == t ? "&f" : "&7";
            lore.add(mark + col + roman(i) + " &7· " + col + Msg.num(t.goal()) + " " + a.unit() + " &8→ &e" + Msg.num(cash(t)) + " 캐시"
                    + (t.title() != null ? " &8+ &f칭호 " + t.title() : ""));
        }
        lore.add("");
        if (next != null) {
            lore.add("&7진행 : &f" + Msg.num(v) + " &7/ " + Msg.num(next.goal()) + " " + a.unit());
            lore.add(bar(v, next.goal()) + " &f" + Math.min(100, next.goal() <= 0 ? 100 : v * 100 / next.goal()) + "%");
        } else {
            lore.add("&a&l모든 단계 달성! &7(" + Msg.num(v) + " " + a.unit() + ")");
        }
        lore.add("");
        lore.add(ready > 0 ? "&e&l클릭하여 보상 받기! &7(" + ready + "개)" : "&8받을 보상이 없습니다.");
        boolean all = done == a.tiers().length;
        String stage = all ? "&a&l완료" : "&7" + done + "/" + a.tiers().length;
        It b = It.of(a.icon()).hide().name((ready > 0 ? "&6&l" : all ? "&a&l" : "&f&l") + a.name() + " &8[" + stage + "&8]")
                .lore(lore.toArray(new String[0]));
        if (ready > 0) b.ench(Enchantment.UNBREAKING, 1);
        return b.build();
    }

    /** back: 뒤로 가기 (null 이면 서버 메뉴) */
    public void open(Player p, Runnable back) {
        tutorial(p, "ach");
        PlayerData d = data(p);
        Gui g = new Gui(5, "&8&l업적");
        int total = 0, done = 0;
        for (Ach a : ALL) {
            total += a.tiers().length;
            for (int i = 0; i < a.tiers().length; i++) if (d.achClaimed.contains(a.id() + ":" + i)) done++;
        }
        int ready = claimable(d);
        g.set(4, It.of(Material.KNOWLEDGE_BOOK).name("&6&l업적")
                .lore("&7기록을 쌓아 단계를 넘으면 보상을 받습니다.", "&7마지막 단계는 &f업적 칭호&7를 줍니다.", "&8기록은 시즌이 바뀌어도 유지됩니다.", "",
                        "&7달성 : &f" + done + " &7/ " + total + " 단계", "&7받을 보상 : " + (ready > 0 ? "&e" + ready + "개" : "&8없음"),
                        "&7지금 연속 처치 : &f" + streakOf(p)).build());
        g.set(10, It.of(Material.RED_STAINED_GLASS_PANE).name("&c&l전투").build());
        g.set(19, It.of(Material.LIME_STAINED_GLASS_PANE).name("&a&l성장 · 활동").build());
        g.set(28, It.of(Material.PURPLE_STAINED_GLASS_PANE).name("&d&l수집 · 이벤트").build());
        for (int i = 0; i < ALL.size() && i < SLOTS.length; i++) {
            Ach a = ALL.get(i);
            g.set(SLOTS[i], icon(d, a), e -> {
                if (claim(p, a) == 0) Gui.denySound(p);
                later(1, () -> open(p, back));
            });
        }
        if (ready > 0) {
            g.set(g.last() + 6, It.of(Material.CHEST_MINECART).name("&e&l모두 받기").lore("&7받을 수 있는 보상 &e" + ready + "개&7를 한 번에 받습니다.").build(), e -> {
                for (Ach a : ALL) claim(p, a);
                later(1, () -> open(p, back));
            });
        }
        MenuModule menu = plugin.module(MenuModule.class);
        g.nav(p, back != null ? back : menu == null ? null : () -> menu.menu(p)).fill().open(p);
    }
}
