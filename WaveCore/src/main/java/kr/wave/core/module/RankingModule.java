package kr.wave.core.module;

import kr.wave.core.Items;
import kr.wave.core.WaveCore;
import kr.wave.core.data.GlobalData;
import kr.wave.core.data.PlayerData;
import kr.wave.core.util.Cmd;
import kr.wave.core.util.Discord;
import kr.wave.core.util.Gui;
import kr.wave.core.util.It;
import kr.wave.core.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 순위 (돈 · 처치), 1~200위.
 * - /순위 [돈|처치] : 순위 창
 * - TOP 10 : 접속 때 순위 입장 메시지, 시즌이 끝나면 순위가 새겨진 트로피 + 보상(config ranking.rewards)
 * - /순위 보상 [확인] : 지금 순위로 TOP 10 보상 지급 (관리자). 시즌 초기화 때는 자동 (ranking.reward-on-season-reset)
 * 계산은 ranking.yml 의 요약 값으로 하고, 접속 중인 사람은 ranking.update-seconds 마다 갱신합니다.
 */
public class RankingModule extends Module {

    public enum Cat {
        MONEY("money", "돈", Material.GOLD_INGOT, "#FFD84D", "#FF9F1C", "💰"),
        KILLS("kills", "처치", Material.IRON_SWORD, "#FF5A5A", "#FFC2C2", "⚔");

        public final String key, label, from, to, emoji;
        public final Material icon;

        Cat(String key, String label, Material icon, String from, String to, String emoji) {
            this.key = key;
            this.label = label;
            this.icon = icon;
            this.from = from;
            this.to = to;
            this.emoji = emoji;
        }

        String grad(String text) {
            return "<gradient:" + from + ":" + to + ">" + text + "</gradient>";
        }

        static Cat of(String s) {
            if (s == null) return null;
            for (Cat c : values()) if (c.key.equalsIgnoreCase(s) || c.label.equals(s)) return c;
            return switch (s) {
                case "캐시", "cash" -> MONEY;
                case "킬", "kill" -> KILLS;
                default -> null;
            };
        }
    }

    /** 순위 계산용 요약 */
    public record Entry(UUID id, String name, long money, int level, long exp, int collection, int kills) {
        long value(Cat c) {
            return switch (c) {
                case MONEY -> money;
                case KILLS -> kills;
            };
        }

        String shown(Cat c) {
            return switch (c) {
                case MONEY -> Msg.num(money) + "원";
                case KILLS -> Msg.num(kills) + "킬";
            };
        }
    }

    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();
    private final Map<Cat, List<Entry>> sorted = new EnumMap<>(Cat.class);
    private final Map<String, Long> recentKill = new HashMap<>();
    private final Map<String, Long> confirm = new HashMap<>();
    private boolean dirty;
    private int tick;

    public RankingModule(WaveCore plugin) {
        super(plugin);
    }

    private File file() {
        return new File(plugin.getDataFolder(), "ranking.yml");
    }

    @Override
    public void enable() {
        cmd("순위", null, this::rankCmd, (s, a) -> a.length <= 1 ? List.of("돈", "처치", "보상") : List.of(), "ranking", "랭킹");
        if (file().exists()) load();
        else rebuild();
        resort();
    }

    @Override
    public void disable() {
        for (Player p : Bukkit.getOnlinePlayers()) put(data(p));
        save();
    }

    /* ======================= 요약 값 ======================= */

    public void put(PlayerData d) {
        String name = d.name == null || d.name.isEmpty() ? String.valueOf(Bukkit.getOfflinePlayer(d.uuid).getName()) : d.name;
        entries.put(d.uuid, new Entry(d.uuid, name, d.money, d.level, d.exp, d.collected.size(), d.kills));
        dirty = true;
    }

    /** 모든 플레이어 데이터를 읽어 다시 만듦 (처음 켤 때 · 시즌 초기화 뒤) */
    public void rebuild() {
        entries.clear();
        plugin.store().scanAll(this::put);
        for (Player p : Bukkit.getOnlinePlayers()) put(data(p));
        resort();
        save();
    }

    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file());
        ConfigurationSection sec = y.getConfigurationSection("players");
        if (sec == null) return;
        for (String k : sec.getKeys(false)) {
            try {
                UUID id = UUID.fromString(k);
                ConfigurationSection e = sec.getConfigurationSection(k);
                if (e == null) continue;
                entries.put(id, new Entry(id, e.getString("name", "?"), e.getLong("money"), e.getInt("level", 1), e.getLong("exp"),
                        e.getInt("collection"), e.getInt("kills")));
            } catch (IllegalArgumentException ignored) {   // 잘못된 줄은 건너뜀
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("순위 계산용 요약 (자동으로 만들어짐). 직접 고치지 마세요."));
        for (Entry e : entries.values()) {
            String b = "players." + e.id() + ".";
            y.set(b + "name", e.name());
            y.set(b + "money", e.money());
            y.set(b + "level", e.level());
            y.set(b + "exp", e.exp());
            y.set(b + "collection", e.collection());
            y.set(b + "kills", e.kills());
        }
        try {
            y.save(file());
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().warning("ranking.yml 을 저장하지 못했습니다: " + ex.getMessage());
        }
    }

    /** 접속 중인 사람 값을 반영해 다시 정렬 */
    public void refreshOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) put(data(p));
        resort();
    }

    private boolean excluded(Entry e) {
        for (String n : plugin.getConfig().getStringList("ranking.exclude")) if (n.equalsIgnoreCase(e.name())) return true;
        GlobalData.Ban b = global().bans.get(e.id());
        return b != null && (b.permanent() || b.until > System.currentTimeMillis());
    }

    /** 다시 정렬 (값이 0 인 사람은 순위에 넣지 않음) */
    public void resort() {
        int size = Math.max(10, Math.min(1000, plugin.getConfig().getInt("ranking.size", 200)));
        List<Entry> all = new ArrayList<>();
        for (Entry e : entries.values()) if (!excluded(e)) all.add(e);
        for (Cat c : Cat.values()) {
            Comparator<Entry> cmp = Comparator.comparingLong((Entry e) -> e.value(c)).reversed()
                    .thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER);
            List<Entry> list = new ArrayList<>();
            for (Entry e : all) if (e.value(c) > 0) list.add(e);
            list.sort(cmp);
            synchronized (sorted) {
                sorted.put(c, List.copyOf(list.subList(0, Math.min(size, list.size()))));
            }
        }
    }

    public List<Entry> top(Cat c) {
        synchronized (sorted) {
            return sorted.getOrDefault(c, List.of());
        }
    }

    /** 순위 (1부터). 범위 밖이면 0 */
    public int rankOf(Cat c, UUID id) {
        List<Entry> list = top(c);
        for (int i = 0; i < list.size(); i++) if (list.get(i).id().equals(id)) return i + 1;
        return 0;
    }

    @Override
    public void secondTick() {
        int every = Math.max(10, plugin.getConfig().getInt("ranking.update-seconds", 60));
        if (++tick % every != 0) return;
        refreshOnline();
        if (dirty) save();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        put(data(e.getPlayer()));
    }

    /** 처치 수: 같은 곳(부계정) 끼리 · 같은 상대를 10분 안에 다시 처치한 것은 세지 않음 */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer == victim) return;
        AltModule alt = plugin.module(AltModule.class);
        if (alt != null && alt.sameNetwork(killer.getUniqueId(), victim.getUniqueId())) return;
        String key = killer.getUniqueId() + ">" + victim.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = recentKill.get(key);
        if (last != null && now - last < 10 * 60_000L) return;
        recentKill.put(key, now);
        if (recentKill.size() > 5000) recentKill.values().removeIf(t -> now - t > 10 * 60_000L);
        PlayerData d = data(killer);
        d.kills++;
        plugin.store().saveAuto(d);
    }

    /* ======================= TOP 10 입장 메시지 ======================= */

    /** 등급 이름: 1 · 2 · 3위는 TOP 1/2/3, 4~10위는 Ranking */
    public static String tier(int rank) {
        return rank <= 3 ? "TOP " + rank : "Ranking";
    }

    private static final Map<Cat, List<String>> JOIN_LINES = Map.of(
            Cat.MONEY, List.of("님이 두둑한 지갑과 함께 도착했습니다!", "님이 금고 문을 열고 입장했습니다!", "님이 입장했습니다. 오늘도 큰손 등장!",
                    "님이 반짝이는 금화를 흩뿌리며 나타났습니다!"),
            Cat.KILLS, List.of("님이 전장에 발을 들였습니다!", "님이 칼을 뽑아 들고 입장했습니다!", "님이 사냥을 시작합니다. 모두 조심하세요!",
                    "님이 돌아왔습니다. 오늘의 사냥감은 누구일까요?"));

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        if (!plugin.getConfig().getBoolean("ranking.join-message", true)) return;
        Player p = e.getPlayer();
        Cat best = null;
        int bestRank = 0;
        for (Cat c : Cat.values()) {
            int r = rankOf(c, p.getUniqueId());
            if (r > 0 && r <= 10 && (bestRank == 0 || r < bestRank)) {
                best = c;
                bestRank = r;
            }
        }
        if (best == null) return;
        Cat c = best;
        int r = bestRank;
        later(5, () -> {
            if (!p.isOnline()) return;
            PlayerData d = data(p);
            CoreModule core = plugin.module(CoreModule.class);
            var name = core == null ? Msg.c(nickName(d.nickColor, p.getName(), "&f")) : core.nameTag(p, nickName(d.nickColor, p.getName(), "&f"));
            List<String> lines = JOIN_LINES.get(c);
            String line = lines.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(lines.size()));
            String medal = r == 1 ? "👑" : r == 2 ? "🥈" : r == 3 ? "🥉" : "🏆";
            String tag = "<b>" + c.grad("[" + medal + " " + c.label + " " + tier(r) + (r > 3 ? " #" + r : "") + "]") + "</b>";
            if (r == 1) {
                // 1위: 위아래 장식 줄 + 양옆 반짝임 + 모두에게 효과음
                String bar = "<color:" + c.from + "><st>                                        </st></color>";
                Msg.broadcast(bar);
                Msg.broadcastFill(" <color:" + c.to + "><obf>D</obf></color> " + tag + " %name% " + c.grad(line) + " <color:" + c.to + "><obf>D</obf></color>", "%name%", name);
                Msg.broadcast(bar);
                for (Player o : Bukkit.getOnlinePlayers()) Msg.sound(o, "ui.toast.challenge_complete", 0.5f, 1.3f);
                kr.wave.core.util.Fx.burst(p.getLocation().add(0, 1, 0), org.bukkit.Particle.TOTEM_OF_UNDYING, 60, 0.45);
            } else if (r <= 3) {
                Msg.broadcastFill(tag + " %name% " + c.grad(line), "%name%", name);
                for (Player o : Bukkit.getOnlinePlayers()) Msg.sound(o, "block.note_block.chime", 0.5f, 1.4f);
                kr.wave.core.util.Fx.burst(p.getLocation().add(0, 1, 0), org.bukkit.Particle.END_ROD, 25, 0.12);
            } else {
                Msg.broadcastFill(tag + " %name% <gray>" + line + "</gray>", "%name%", name);
            }
        });
    }

    /* ======================= 순위 창 ======================= */

    private void rankCmd(CommandSender s, String l, String[] a) {
        if (a.length >= 1 && a[0].equals("보상")) {
            rewardCmd(s, a);
            return;
        }
        if (!Cmd.isPlayer(s)) {
            for (Cat c : Cat.values()) {
                Msg.send(s, "&6[순위] " + c.label);
                List<Entry> list = top(c);
                for (int i = 0; i < Math.min(10, list.size()); i++) Msg.send(s, " &e" + (i + 1) + ". &f" + list.get(i).name() + " &7" + list.get(i).shown(c));
            }
            return;
        }
        Cat c = a.length >= 1 ? Cat.of(a[0]) : Cat.MONEY;
        open((Player) s, c == null ? Cat.MONEY : c, 1);
    }

    private static final int PER = 36;

    public void open(Player p, Cat c, int page) {
        refreshOnline();
        List<Entry> list = top(c);
        int pages = Math.max(1, (list.size() + PER - 1) / PER);
        int pg = Math.max(1, Math.min(page, pages));
        Gui g = new Gui(6, "&8&l순위 · " + c.label + " (" + pg + "/" + pages + ")");
        int[] tabs = {2, 6};
        for (int i = 0; i < Cat.values().length; i++) {
            Cat t = Cat.values()[i];
            It tab = It.of(t.icon).hide().name("<b>" + t.grad(t.label + " 순위") + "</b>").lore(t == c ? "&a보는 중" : "&e클릭하여 보기");
            if (t == c) tab.ench(Enchantment.UNBREAKING, 1);
            g.set(tabs[i], tab.build(), e -> later(1, () -> open(p, t, 1)));
        }
        int mine = rankOf(c, p.getUniqueId());
        Entry me = entries.get(p.getUniqueId());
        g.set(4, It.of(Material.PLAYER_HEAD).skull(p).name("&e내 순위").lore("&7" + c.label + " : " + (mine > 0 ? "&f" + mine + "위" : "&8순위 밖"),
                "&7기록 : &f" + (me == null ? "-" : me.shown(c)), "", "&8TOP 10 은 시즌이 끝나면", "&8순위 트로피와 보상을 받습니다.").build());
        for (int i = 0; i < PER; i++) {
            int idx = (pg - 1) * PER + i;
            if (idx >= list.size()) break;
            Entry e = list.get(idx);
            int rank = idx + 1;
            boolean self = e.id().equals(p.getUniqueId());
            Material m = rank == 1 ? Material.GOLD_BLOCK : rank == 2 ? Material.IRON_BLOCK : rank == 3 ? Material.COPPER_BLOCK
                    : rank <= 10 ? Material.AMETHYST_SHARD : self ? Material.LIME_DYE : Material.PAPER;
            String color = rank == 1 ? "&6&l" : rank == 2 ? "&f&l" : rank == 3 ? "<color:#E07A4F><b>" : rank <= 10 ? "&d" : "&7";
            It it = It.of(m).name(color + "#" + rank + " &f" + e.name() + (self ? " &a(나)" : ""))
                    .lore("&7" + c.label + " : &f" + e.shown(c));
            if (rank <= 10) it.lore("", "&6🏆 TOP 10 &7· 시즌 종료 보상 대상");
            if (self) it.ench(Enchantment.UNBREAKING, 1);
            g.set(9 + i, it.hide().build());
        }
        if (list.isEmpty()) g.set(22, It.of(Material.BARRIER).name("&7아직 순위가 없습니다.").build());
        g.pages(p, pg, pages, n -> later(1, () -> open(p, c, n)));
        g.nav(p, null).fill().open(p);
    }

    /* ======================= TOP 10 보상 ======================= */

    /** 트로피에 새길 시즌 이름 (config ranking.season-name, 비어 있으면 fallback) */
    public String seasonName(String fallback) {
        String s = plugin.getConfig().getString("ranking.season-name", "");
        return s == null || s.isBlank() ? fallback : s;
    }

    /** 지금 TOP 10 (분야별) */
    public Map<Cat, List<Entry>> snapshotTop10() {
        refreshOnline();
        Map<Cat, List<Entry>> out = new EnumMap<>(Cat.class);
        for (Cat c : Cat.values()) {
            List<Entry> l = top(c);
            out.put(c, List.copyOf(l.subList(0, Math.min(10, l.size()))));
        }
        return out;
    }

    private void rewardCmd(CommandSender s, String[] a) {
        if (!s.hasPermission(ADMIN)) {
            Msg.send(s, "&c권한이 없습니다.");
            return;
        }
        if (a.length >= 2 && a[1].equals("확인")) {
            Long until = confirm.remove(s.getName());
            if (until == null || until < System.currentTimeMillis()) {
                Msg.send(s, "&c먼저 &f/순위 보상 &c으로 미리보기를 확인한 뒤 60초 안에 입력하세요.");
                return;
            }
            int n = payout(snapshotTop10(), seasonName(LocalDate.now().toString()));
            Msg.send(s, "&a[순위] &7TOP 10 보상을 &f" + n + "명&7에게 우편함으로 보냈습니다.");
            return;
        }
        Map<Cat, List<Entry>> snap = snapshotTop10();
        Msg.send(s, "&6[순위] &7지금 순위로 TOP 10 보상을 보냅니다. (우편함 · 접속하지 않아도 받음)");
        for (Cat c : Cat.values()) {
            List<Entry> l = snap.get(c);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < l.size(); i++) sb.append(i == 0 ? "" : "&7, ").append("&f").append(i + 1).append(". ").append(l.get(i).name());
            Msg.send(s, " &e" + c.label + " &8: " + (l.isEmpty() ? "&8없음" : sb));
        }
        String last = plugin.getConfig().getString("ranking.last-payout", "");
        if (!last.isEmpty()) Msg.send(s, " &c마지막 지급 : " + last + " &7(같은 시즌에 두 번 주지 않게 확인하세요)");
        Msg.send(s, "&7보내려면 60초 안에 &f/순위 보상 확인");
        confirm.put(s.getName(), System.currentTimeMillis() + 60_000);
    }

    /** TOP 10 에게 트로피 + 보상. 받은 사람 수 (중복 포함 아님: 분야별로 받음) */
    public int payout(Map<Cat, List<Entry>> snap, String season) {
        int count = 0;
        StringBuilder log = new StringBuilder();
        for (Cat c : Cat.values()) {
            List<Entry> l = snap.getOrDefault(c, List.of());
            for (int i = 0; i < l.size(); i++) {
                Entry e = l.get(i);
                int rank = i + 1;
                OfflinePlayer op = Bukkit.getOfflinePlayer(e.id());
                PlayerData d = plugin.store().get(op);
                d.mailbox.add(Items.rankTrophy(c.label, c.from, c.to, rank, e.name(), e.shown(c), season));
                ConfigurationSection r = rewardFor(rank);
                if (r != null) {
                    long money = r.getLong("money", 0);
                    if (money > 0) {
                        d.money += money;
                        d.moneyTotal += money;
                    }
                    CrateModule crates = plugin.module(CrateModule.class);
                    ConfigurationSection keys = r.getConfigurationSection("keys");
                    if (keys != null && crates != null) for (String k : keys.getKeys(false)) {
                        var crate = crates.crate(k);
                        if (crate != null) crates.giveKeys(op, crate, keys.getInt(k), false);
                        else plugin.getLogger().warning("ranking.rewards: 없는 상자 '" + k + "'");
                    }
                    for (String cmd : r.getStringList("commands"))
                        console(cmd.replace("{player}", e.name()).replace("{rank}", String.valueOf(rank)).replace("{category}", c.label));
                }
                plugin.store().saveAuto(d);
                Player online = op.getPlayer();
                if (online != null) {
                    Msg.send(online, "<b>" + c.grad("[🏆 " + c.label + " " + tier(rank) + (rank > 3 ? " #" + rank : "") + "]") + "</b> &f시즌 순위 보상이 &e우편함&f으로 도착했습니다!");
                    Msg.sound(online, "ui.toast.challenge_complete", 0.8f, 1.2f);
                }
                log.append(c.label).append(' ').append(rank).append(". ").append(e.name()).append('\n');
                count++;
            }
        }
        plugin.getConfig().set("ranking.last-payout", kr.wave.core.util.Dur.now() + " (" + season + ")");
        plugin.saveConfig();
        plugin.getLogger().info("[순위] TOP 10 보상 지급 (" + season + ") " + count + "건");
        Discord.send("season", "🏆 순위 보상 지급", season, Discord.GREEN, "지급", count + "건", "TOP 10", log.length() > 900 ? log.substring(0, 900) + "…" : log.toString());
        return count;
    }

    /** config ranking.rewards 의 "1", "2-3", "4-10" 같은 칸 */
    private ConfigurationSection rewardFor(int rank) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("ranking.rewards");
        if (sec == null) return null;
        for (String k : sec.getKeys(false)) {
            String[] p = k.split("-");
            try {
                int lo = Integer.parseInt(p[0].trim()), hi = p.length > 1 ? Integer.parseInt(p[1].trim()) : lo;
                if (rank >= lo && rank <= hi) return sec.getConfigurationSection(k);
            } catch (NumberFormatException ignored) {
                plugin.getLogger().warning("ranking.rewards: 순위 범위 '" + k + "' 를 읽을 수 없습니다 (예: 1, 2-3, 4-10)");
            }
        }
        return null;
    }
}
