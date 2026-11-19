package com.premiumpass.shop;

import com.premiumpass.PremiumPass;
import com.premiumpass.data.PlayerData;
import kr.wave.common.Items;
import kr.wave.common.Text;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/** 프리미엄 패스 포인트 교환 상점 (shop.yml) */
public class ShopManager {

    public static final class Entry {
        public final String id;
        public ItemStack item;
        public long cost;
        /** 시즌당 구매 제한 (0 = 무제한) */
        public int limit;
        /** 프리미엄 패스 보유자만 구매 가능 */
        public boolean premiumOnly;
        /** 아이템 대신 실행할 콘솔 명령어 (%player%). 있으면 item 은 표시용 */
        public final List<String> commands = new java.util.ArrayList<>();

        public Entry(String id, ItemStack item, long cost, int limit, boolean premiumOnly) {
            this.id = id;
            this.item = item;
            this.cost = cost;
            this.limit = limit;
            this.premiumOnly = premiumOnly;
        }
    }

    private final PremiumPass plugin;
    private final File file;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public ShopManager(PremiumPass plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "shop.yml");
    }

    public void load() {
        entries.clear();
        if (!file.exists()) {
            plugin.getLogger().info("shop.yml 이 없어 야생 서버용 기본 교환 상점을 생성합니다.");
            createDefaults();
            save();
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = y.getConfigurationSection("items");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ItemStack it = sec.getItemStack(id + ".item");
            if (it == null) {
                plugin.getLogger().warning("shop.yml: '" + id + "' 의 item 이 올바르지 않습니다.");
                continue;
            }
            Entry en = new Entry(id, it, Math.max(0, sec.getLong(id + ".cost")), Math.max(0, sec.getInt(id + ".limit")),
                    sec.getBoolean(id + ".premium-only", false));
            en.commands.addAll(sec.getStringList(id + ".commands"));
            entries.put(id, en);
        }
    }

    /** 저장. 실패하면 콘솔에 남기고 false (관리자가 직접 바꾼 경우 호출한 쪽에서 알려 줌) */
    public boolean save() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of(
                "프리미엄 패스 교환 상점",
                "cost: 필요 포인트 / limit: 시즌당 구매 제한 (0 = 무제한) / premium-only: 프리미엄 패스 전용",
                "commands: 아이템 대신 실행할 콘솔 명령어 (%player% = 이름, 이때 item 은 표시용)",
                "게임 안에서 /passadmin shop add <id> <가격> [제한] 으로 손에 든 아이템을 등록할 수 있습니다."));
        for (Entry e : entries.values()) {
            y.set("items." + e.id + ".item", e.item);
            y.set("items." + e.id + ".cost", e.cost);
            y.set("items." + e.id + ".limit", e.limit);
            if (e.premiumOnly) y.set("items." + e.id + ".premium-only", true);
            if (!e.commands.isEmpty()) y.set("items." + e.id + ".commands", e.commands);
        }
        try {
            y.save(file);
            return true;
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "shop.yml 저장 실패", ex);
            return false;
        }
    }

    private void createDefaults() {
        add("exp_bottle", Items.of(Material.EXPERIENCE_BOTTLE, 32), 20, 0);
        add("golden_apple", Items.of(Material.GOLDEN_APPLE, 4), 25, 0);
        add("diamond", Items.of(Material.DIAMOND, 4), 30, 0);
        add("ender_pearl", Items.of(Material.ENDER_PEARL, 16), 30, 0);
        add("firework", Items.of(Material.FIREWORK_ROCKET, 64), 40, 0);
        add("iron_block", Items.of(Material.IRON_BLOCK, 16), 40, 0);
        add("name_tag", Items.of(Material.NAME_TAG, 3), 30, 0);
        add("saddle", Items.of(Material.SADDLE, 1), 20, 0);
        add("shulker_box", Items.of(Material.SHULKER_BOX, 1), 120, 0);
        add("totem", Items.of(Material.TOTEM_OF_UNDYING, 1), 150, 0);
        add("netherite_scrap", Items.of(Material.NETHERITE_SCRAP, 1), 200, 0);
        add("book_efficiency", Items.book(Enchantment.EFFICIENCY, 5), 150, 0);
        add("book_fortune", Items.book(Enchantment.FORTUNE, 3), 200, 0);
        add("book_protection", Items.book(Enchantment.PROTECTION, 4), 150, 0);
        add("book_mending", Items.book(Enchantment.MENDING, 1), 250, 0);
        add("enchanted_golden_apple", Items.of(Material.ENCHANTED_GOLDEN_APPLE, 1), 300, 5);
        // 서버 전용 상품 (WaveCore 명령어로 지급, 상점 물가 기준: 1포인트 ≒ 40 캐시)
        addCmd("cash_10k", Items.named(Material.GOLD_INGOT, "&e10,000 캐시", List.of("&7구매 시 캐시가 바로 지급됩니다.")), 250, 10, false, "cashgive %player% 10000");
        addCmd("key_enderpearl", Items.named(Material.GRAY_DYE, "&c천국과 지옥 룰렛 키", List.of("&7계정에 키 1개가 지급됩니다.")), 300, 5, false, "상자 give %player% enderpearl 1");
        addCmd("key_combat", Items.named(Material.PINK_DYE, "&b다양한 무기 룰렛 키", List.of("&7계정에 키 1개가 지급됩니다.")), 500, 5, false, "상자 give %player% combat 1");
        addCmd("altar_ticket", Items.named(Material.PAPER, "&e천공의 제단 이동권", List.of("&7천공의 심판자에게 도전할 수 있습니다.")), 600, 3, true, "wave boss ticket %player% 1");
        addCmd("key_echoes", Items.named(Material.LIME_DYE, "&aThe Echoes of Natural 키", List.of("&7계정에 키 1개가 지급됩니다.")), 800, 2, true, "상자 give %player% TheEchoesOfNatural 1");
        addCmd("key_sealed", Items.named(Material.LIGHT_BLUE_DYE, "&bSealed Master Power 키", List.of("&7계정에 키 1개가 지급됩니다.")), 800, 2, true, "상자 give %player% Sealedp 1");
        addCmd("key_ruiner", Items.named(Material.RED_DYE, "&cThe Ruiner's Grudge 키", List.of("&7계정에 키 1개가 지급됩니다.")), 800, 2, true, "상자 give %player% TheRuiner 1");
        add("netherite_template", Items.of(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1), 350, 0);
        add("beacon", Items.of(Material.BEACON, 1), 600, 1, true);
        add("elytra", Items.of(Material.ELYTRA, 1), 1000, 1, true);
    }

    /* ---------------- 조회/수정 ---------------- */

    public List<Entry> list() {
        return new ArrayList<>(entries.values());
    }

    public Entry get(String id) {
        return entries.get(id);
    }

    public void add(String id, ItemStack item, long cost, int limit) {
        add(id, item, cost, limit, false);
    }

    public void add(String id, ItemStack item, long cost, int limit, boolean premiumOnly) {
        entries.put(id, new Entry(id, item.clone(), cost, limit, premiumOnly));
    }

    private void addCmd(String id, ItemStack display, long cost, int limit, boolean premiumOnly, String... commands) {
        Entry e = new Entry(id, display, cost, limit, premiumOnly);
        e.commands.addAll(java.util.Arrays.asList(commands));
        entries.put(id, e);
    }

    public boolean remove(String id) {
        return entries.remove(id) != null;
    }

    /* ---------------- 구매 ---------------- */

    public boolean buy(Player p, PlayerData d, Entry e) {
        if (e.premiumOnly && !plugin.pass().isPremium(p, d)) {
            Text.send(p, "&c프리미엄 패스 전용 상품입니다.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return false;
        }
        int bought = d.purchases.getOrDefault(e.id, 0);
        if (e.limit > 0 && bought >= e.limit) {
            Text.send(p, "&c이번 시즌 구매 한도(" + e.limit + "회)에 도달했습니다.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return false;
        }
        if (d.points < e.cost) {
            Text.send(p, "&c포인트가 부족합니다. &7(필요: " + Text.num(e.cost) + " / 보유: " + Text.num(d.points) + ")");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return false;
        }
        d.points -= e.cost;
        d.purchases.put(e.id, bought + 1);
        boolean dropped = false;
        if (e.commands.isEmpty()) dropped = Items.give(p, List.of(e.item));
        else for (String c : e.commands) org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), c.replace("%player%", p.getName()));
        Text.send(p, "&a" + Items.describe(e.item) + " &a교환 완료! &7(-" + Text.num(e.cost) + " 포인트, 남은 포인트: " + Text.num(d.points) + ")");
        if (dropped) Text.send(p, "&c인벤토리가 가득 차서 아이템이 발밑에 떨어졌습니다!");
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1f);
        plugin.data().save(d);
        return true;
    }
}
