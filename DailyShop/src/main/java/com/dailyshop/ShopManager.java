package com.dailyshop;

import com.dailyshop.currency.Currency;
import com.dailyshop.currency.ItemCurrency;
import com.dailyshop.currency.PremiumPassCurrency;
import com.dailyshop.currency.VaultCurrency;
import kr.wave.common.Items;
import kr.wave.common.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * 일일 상점: 매일 상품 목록(shop.yml)에서 가중치에 따라 무작위로 골라 진열한다.
 * 진열은 모든 플레이어가 같고, 구매 한도는 플레이어별 / 하루 단위다.
 */
public class ShopManager {

    public static final class Entry {
        public final String id;
        public ItemStack item;
        public long cost;
        /** 플레이어당 하루 구매 한도 (0 = 무제한) */
        public int limit;
        /** 진열 가중치 (클수록 자주 나옴) */
        public int weight;
        /** PremiumPass 프리미엄 패스 보유자 전용 */
        public boolean premiumOnly;

        public Entry(String id, ItemStack item, long cost, int limit, int weight, boolean premiumOnly) {
            this.id = id;
            this.item = item;
            this.cost = cost;
            this.limit = limit;
            this.weight = Math.max(1, weight);
            this.premiumOnly = premiumOnly;
        }

        public String rarity() {
            if (weight >= 8) return "&f일반";
            if (weight >= 3) return "&b희귀";
            return "&d영웅";
        }
    }

    private final DailyShop plugin;
    private final File poolFile;
    private final File stateFile;
    private final Map<String, Entry> pool = new LinkedHashMap<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "DailyShop-IO");
        t.setDaemon(true);
        return t;
    });

    private int count;
    private int premiumDiscount;
    private boolean broadcast;
    private ZoneId zone;
    private int resetHour;
    private Currency currency;
    /** 두 번째 결제 수단: WaveCore 캐시 (우클릭 구매) */
    private Currency cash;
    private long cashPerPoint = 40;
    private String cashName = "캐시";
    private String currencyName;

    private String rotationDay = "";
    private int rerolls = 0;
    private String rotationId = "";
    private final List<String> rotation = new ArrayList<>();
    /** 플레이어별 오늘 구매 횟수 (현재 진열에 대해서만) */
    private final Map<UUID, Map<String, Integer>> bought = new HashMap<>();

    public ShopManager(DailyShop plugin) {
        this.plugin = plugin;
        this.poolFile = new File(plugin.getDataFolder(), "shop.yml");
        this.stateFile = new File(plugin.getDataFolder(), "state.yml");
    }

    /* ======================= 로드 / 저장 ======================= */

    public void load() {
        FileConfiguration c = plugin.getConfig();
        count = Math.max(1, c.getInt("count", 6));
        premiumDiscount = Math.max(0, Math.min(100, c.getInt("premium-discount", 10)));
        broadcast = c.getBoolean("broadcast-refresh", true);
        try {
            zone = ZoneId.of(c.getString("timezone", "Asia/Seoul"));
        } catch (Exception e) {
            zone = ZoneId.of("Asia/Seoul");
        }
        resetHour = Math.floorMod(c.getInt("reset-hour", 0), 24);
        currencyName = c.getString("currency.display-name", "포인트");
        currency = createCurrency(c);
        plugin.getLogger().info("결제 수단: " + currency.id());
        cash = null;
        if (c.getBoolean("cash.enabled", true)) {
            cash = com.dailyshop.currency.WaveCashCurrency.tryCreate();
            cashPerPoint = Math.max(1, c.getLong("cash.per-point", 40));
            cashName = c.getString("cash.display-name", "캐시");
            if (cash != null) plugin.getLogger().info("추가 결제 수단: WaveCore 캐시 (1 " + currencyName + " = " + cashPerPoint + " 캐시)");
        }

        pool.clear();
        if (!poolFile.exists()) {
            plugin.getLogger().info("shop.yml 이 없어 야생 서버용 기본 상품을 생성합니다.");
            createDefaults();
            savePool();
        } else {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(poolFile);
            ConfigurationSection sec = y.getConfigurationSection("items");
            if (sec != null) {
                for (String id : sec.getKeys(false)) {
                    ItemStack it = sec.getItemStack(id + ".item");
                    if (it == null) {
                        plugin.getLogger().warning("shop.yml: '" + id + "' 의 item 이 올바르지 않습니다.");
                        continue;
                    }
                    pool.put(id, new Entry(id, it, Math.max(0, sec.getLong(id + ".cost")),
                            Math.max(0, sec.getInt(id + ".limit", 1)), sec.getInt(id + ".weight", 10),
                            sec.getBoolean(id + ".premium-only", false)));
                }
            }
        }

        YamlConfiguration st = YamlConfiguration.loadConfiguration(stateFile);
        rotationDay = st.getString("day", "");
        rerolls = st.getInt("rerolls", 0);
        rotationId = st.getString("id", "");
        rotation.clear();
        rotation.addAll(st.getStringList("items"));
        bought.clear();
        ConfigurationSection bs = st.getConfigurationSection("bought");
        if (bs != null) {
            for (String uuid : bs.getKeys(false)) {
                ConfigurationSection ps = bs.getConfigurationSection(uuid);
                if (ps == null) continue;
                Map<String, Integer> m = new HashMap<>();
                for (String k : ps.getKeys(false)) m.put(k, ps.getInt(k));
                try {
                    bought.put(UUID.fromString(uuid), m);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("state.yml: 구매 기록의 UUID 가 올바르지 않아 건너뜁니다: " + uuid);
                }
            }
        }
        ensureRotation();
    }

    private Currency createCurrency(FileConfiguration c) {
        String type = c.getString("currency.type", "PREMIUMPASS").toUpperCase(Locale.ROOT);
        Material item = Material.matchMaterial(c.getString("currency.item", "EMERALD"));
        if (item == null || !item.isItem()) item = Material.EMERALD;
        switch (type) {
            case "PREMIUMPASS" -> {
                if (Bukkit.getPluginManager().isPluginEnabled("PremiumPass")) return new PremiumPassCurrency();
                plugin.getLogger().warning("currency.type 이 PREMIUMPASS 인데 PremiumPass 플러그인이 없습니다. ITEM(" + item + ") 으로 대신합니다.");
            }
            case "VAULT" -> {
                Currency v = VaultCurrency.tryCreate();
                if (v != null) return v;
                plugin.getLogger().warning("currency.type 이 VAULT 인데 Vault 또는 경제 플러그인이 없습니다. ITEM(" + item + ") 으로 대신합니다.");
            }
            case "ITEM" -> {
            }
            default -> plugin.getLogger().warning("알 수 없는 currency.type '" + type + "'. ITEM(" + item + ") 으로 대신합니다.");
        }
        if (!type.equals("ITEM")) currencyName = Items.koreanName(item);
        return new ItemCurrency(item);
    }

    public void savePool() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of(
                "일일 상점 상품 목록",
                "매일 이 목록에서 config.yml 의 count 개를 무작위로 골라 진열합니다.",
                "cost: 가격 / limit: 플레이어당 하루 구매 한도 (0 = 무제한)",
                "weight: 진열 확률 가중치 (8 이상 일반, 3~7 희귀, 1~2 영웅)",
                "premium-only: PremiumPass 프리미엄 패스 보유자 전용",
                "게임 안에서 /dailyshop admin add <id> <가격> [한도] [가중치] 로 손에 든 아이템을 등록할 수 있습니다."));
        for (Entry e : pool.values()) {
            String b = "items." + e.id;
            y.set(b + ".item", e.item);
            y.set(b + ".cost", e.cost);
            y.set(b + ".limit", e.limit);
            y.set(b + ".weight", e.weight);
            if (e.premiumOnly) y.set(b + ".premium-only", true);
        }
        try {
            y.save(poolFile);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "shop.yml 저장 실패", ex);
        }
    }

    private String stateYaml() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("day", rotationDay);
        y.set("rerolls", rerolls);
        y.set("id", rotationId);
        y.set("items", new ArrayList<>(rotation));
        for (Map.Entry<UUID, Map<String, Integer>> e : bought.entrySet()) {
            y.set("bought." + e.getKey(), new HashMap<>(e.getValue()));
        }
        return y.saveToString();
    }

    private void saveState() {
        final String content = stateYaml();
        io.submit(() -> write(content));
    }

    /** 파일 쓰기는 한 번에 하나만 (저장 스레드와 종료 시 메인 스레드가 겹치지 않게) */
    private final Object writeLock = new Object();

    private void write(String content) {
        synchronized (writeLock) {
            writeNow(content);
        }
    }

    private void writeNow(String content) {
        try {
            File tmp = new File(stateFile.getParentFile(), "state.yml.tmp");
            Files.writeString(tmp.toPath(), content, StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), stateFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "state.yml 저장 실패", ex);
        }
    }

    /**
     * 종료 시 저장: 남은 저장 작업이 끝나기를 기다리고, 시간 안에 안 끝나면 대기 중인 작업은 취소한 뒤
     * 마지막 상태를 한 번 더 씁니다. 파일 쓰기는 writeLock 으로 한 번에 하나만 하므로
     * 저장 스레드와 메인 스레드가 같은 파일을 동시에 쓰는 일은 없습니다.
     */
    public void shutdown() {
        io.shutdown();
        try {
            if (!io.awaitTermination(10, TimeUnit.SECONDS)) {
                int dropped = io.shutdownNow().size();   // 아래에서 최신 상태로 다시 쓰므로 취소해도 손실 없음
                plugin.getLogger().warning("저장 작업이 10초 안에 끝나지 않아 대기 중인 " + dropped + "개를 취소하고 마지막 상태를 다시 저장합니다.");
                if (!io.awaitTermination(5, TimeUnit.SECONDS)) plugin.getLogger().warning("저장 스레드가 아직 끝나지 않았습니다. 진행 중인 쓰기가 끝난 뒤 이어서 저장합니다.");
            }
        } catch (InterruptedException e) {
            io.shutdownNow();
            Thread.currentThread().interrupt();
        }
        write(stateYaml());
    }

    private void createDefaults() {
        // 일반 (weight 8~10)
        add("iron_block", Items.of(Material.IRON_BLOCK, 8), 20, 2, 10);
        add("gold_block", Items.of(Material.GOLD_BLOCK, 4), 15, 2, 10);
        add("diamond", Items.of(Material.DIAMOND, 2), 12, 3, 10);
        add("exp_bottle", Items.of(Material.EXPERIENCE_BOTTLE, 16), 8, 3, 10);
        add("golden_apple", Items.of(Material.GOLDEN_APPLE, 2), 10, 2, 10);
        add("golden_carrot", Items.of(Material.GOLDEN_CARROT, 32), 10, 2, 10);
        add("ender_pearl", Items.of(Material.ENDER_PEARL, 8), 12, 2, 10);
        add("firework", Items.of(Material.FIREWORK_ROCKET, 32), 15, 2, 10);
        add("obsidian", Items.of(Material.OBSIDIAN, 16), 12, 2, 10);
        add("slime_ball", Items.of(Material.SLIME_BALL, 16), 10, 2, 8);
        add("emerald", Items.of(Material.EMERALD, 16), 15, 2, 8);
        // 희귀 (weight 4~5)
        add("blaze_rod", Items.of(Material.BLAZE_ROD, 8), 15, 1, 5);
        add("name_tag", Items.of(Material.NAME_TAG, 2), 15, 1, 5);
        add("book_efficiency", Items.book(Enchantment.EFFICIENCY, 5), 80, 1, 4);
        add("book_protection", Items.book(Enchantment.PROTECTION, 4), 80, 1, 4);
        add("shulker_shell", Items.of(Material.SHULKER_SHELL, 2), 60, 1, 4);
        // 영웅 (weight 1~2)
        add("totem", Items.of(Material.TOTEM_OF_UNDYING, 1), 100, 1, 2);
        add("book_mending", Items.book(Enchantment.MENDING, 1), 180, 1, 2);
        add("netherite_scrap", Items.of(Material.NETHERITE_SCRAP, 1), 150, 1, 1);
        pool.put("enchanted_golden_apple", new Entry("enchanted_golden_apple",
                Items.of(Material.ENCHANTED_GOLDEN_APPLE, 1), 250, 1, 1, true));
    }

    private void add(String id, ItemStack item, long cost, int limit, int weight) {
        pool.put(id, new Entry(id, item, cost, limit, weight, false));
    }

    /* ======================= 시간 ======================= */

    public String todayKey() {
        return LocalDateTime.now(zone).minusHours(resetHour).toLocalDate().toString();
    }

    public Duration untilReset() {
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime next = now.toLocalDate().atTime(resetHour, 0).atZone(zone);
        if (!next.isAfter(now)) next = next.plusDays(1);
        return Duration.between(now, next);
    }

    /* ======================= 진열 ======================= */

    /** 날짜가 바뀌었으면 새로 진열한다. @return 새로 진열했는지 */
    public boolean ensureRotation() {
        String today = todayKey();
        if (today.equals(rotationDay) && !rotation.isEmpty()) {
            rotation.removeIf(id -> !pool.containsKey(id));
            return false;
        }
        if (!today.equals(rotationDay)) rerolls = 0;
        rotationDay = today;
        roll();
        return true;
    }

    /** 관리자: 오늘 진열을 다시 뽑는다 (구매 기록 초기화) */
    public void reroll() {
        rotationDay = todayKey();
        rerolls++;
        roll();
        announce();
    }

    private void roll() {
        rotationId = rotationDay + "#" + rerolls;
        rotation.clear();
        bought.clear();
        List<Entry> candidates = new ArrayList<>(pool.values());
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        while (rotation.size() < count && !candidates.isEmpty()) {
            int total = 0;
            for (Entry e : candidates) total += e.weight;
            int r = rnd.nextInt(total);
            for (int i = 0; i < candidates.size(); i++) {
                r -= candidates.get(i).weight;
                if (r < 0) {
                    rotation.add(candidates.remove(i).id);
                    break;
                }
            }
        }
        saveState();
    }

    /** 1분마다 호출 */
    public void tick() {
        if (ensureRotation()) announce();
    }

    private void announce() {
        if (!broadcast) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            Text.send(p, "&e일일 상점이 새로운 상품으로 갱신되었습니다! &7(/일일상점)");
        }
    }

    public List<Entry> today() {
        ensureRotation();
        List<Entry> out = new ArrayList<>();
        for (String id : rotation) {
            Entry e = pool.get(id);
            if (e != null) out.add(e);
        }
        return out;
    }

    /* ======================= 조회 / 수정 ======================= */

    public List<Entry> pool() {
        return new ArrayList<>(pool.values());
    }

    public Entry get(String id) {
        return pool.get(id);
    }

    public void put(String id, ItemStack item, long cost, int limit, int weight, boolean premiumOnly) {
        pool.put(id, new Entry(id, item.clone(), cost, limit, weight, premiumOnly));
        savePool();
    }

    public boolean remove(String id) {
        if (pool.remove(id) == null) return false;
        savePool();
        rotation.remove(id);
        saveState();
        return true;
    }

    public int premiumDiscount() {
        return premiumDiscount;
    }

    public String currencyName() {
        return currencyName;
    }

    public String money(long amount) {
        return Text.num(amount) + " " + currencyName;
    }

    /* ======================= 프리미엄 / 구매 ======================= */

    public boolean premiumAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("PremiumPass");
    }

    public boolean isPremium(Player p) {
        return premiumAvailable() && PremiumPassCurrency.isPremium(p);
    }

    public long balance(Player p) {
        return currency.balance(p);
    }

    public long price(Player p, Entry e) {
        if (premiumDiscount > 0 && isPremium(p)) return Math.max(0, e.cost * (100 - premiumDiscount) / 100);
        return e.cost;
    }

    public int bought(Player p, Entry e) {
        ensureRotation();
        Map<String, Integer> m = bought.get(p.getUniqueId());
        return m == null ? 0 : m.getOrDefault(e.id, 0);
    }

    public boolean cashAvailable() {
        return cash != null;
    }

    public String cashName() {
        return cashName;
    }

    public long cashBalance(Player p) {
        return cash == null ? 0 : cash.balance(p);
    }

    /** 캐시 가격 = 포인트 가격 x 환율 (프리미엄 할인 포함) */
    public long cashPrice(Player p, Entry e) {
        return price(p, e) * cashPerPoint;
    }

    public boolean buy(Player p, Entry e) {
        return buy(p, e, false);
    }

    /** withCash: WaveCore 캐시로 구매 */
    public boolean buy(Player p, Entry e, boolean withCash) {
        if (withCash && cash == null) {
            Text.send(p, "&c캐시 결제를 사용할 수 없습니다.");
            return false;
        }
        ensureRotation();
        if (!rotation.contains(e.id)) {
            Text.send(p, "&c오늘 일일 상점에 없는 상품입니다.");
            return false;
        }
        if (e.premiumOnly && !isPremium(p)) {
            Text.send(p, "&c프리미엄 패스 전용 상품입니다.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return false;
        }
        int n = bought(p, e);
        if (e.limit > 0 && n >= e.limit) {
            Text.send(p, "&c오늘 구매 한도(" + e.limit + "회)에 도달했습니다. 내일 다시 이용해 주세요.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return false;
        }
        Currency pay = withCash ? cash : currency;
        String payName = withCash ? cashName : currencyName;
        long cost = withCash ? cashPrice(p, e) : price(p, e);
        if (!pay.withdraw(p, cost)) {
            long bal = withCash ? cashBalance(p) : balance(p);
            Text.send(p, "&c" + payName + "이(가) 부족합니다. &7(필요: " + Text.num(cost) + " " + payName + " / 보유: " + Text.num(bal) + " " + payName + ")");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return false;
        }
        bought.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>()).put(e.id, n + 1);
        saveState();
        boolean dropped = Items.give(p, List.of(e.item));
        Text.send(p, "&a" + Items.describe(e.item) + " &a구매 완료! &7(-" + Text.num(cost) + " " + payName + ", 남은 " + payName + ": "
                + Text.num(withCash ? cashBalance(p) : balance(p)) + ")");
        if (dropped) Text.send(p, "&c인벤토리가 가득 차서 아이템이 발밑에 떨어졌습니다!");
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1.2f);
        return true;
    }
}
