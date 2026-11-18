package kr.wave.core.module;

import kr.wave.core.Items;
import kr.wave.core.WaveCore;
import kr.wave.core.data.PlayerData;
import kr.wave.core.util.Gui;
import kr.wave.core.util.It;
import kr.wave.core.util.Msg;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.function.Consumer;

/** 상점.sk : /상점 과 하위 상점들 */
public class ShopModule extends Module {

    public ShopModule(WaveCore plugin) {
        super(plugin);
    }

    @Override
    public void enable() {
        cmd("상점", null, (s, l, a) -> open(s, this::mainShop), "shop");
        cmd("패스레벨구매", null, (s, l, a) -> open(s, this::passLevels), "passlevel");
        cmd("newbie", null, (s, l, a) -> open(s, this::newbieShop));
        cmd("enchantbook", null, (s, l, a) -> open(s, this::enchantShop));
        cmd("eat", null, (s, l, a) -> open(s, this::foodShop));
        cmd("roulette", null, (s, l, a) -> open(s, this::rouletteShop));
        cmd("yurocash", null, (s, l, a) -> open(s, this::paidShop));
        cmd("ore", null, (s, l, a) -> open(s, this::oreShop));
        cmd("combatshop", null, (s, l, a) -> open(s, this::combatShop));
    }

    private void open(org.bukkit.command.CommandSender s, Consumer<Player> opener) {
        if (s instanceof Player p) opener.accept(p);
        else Msg.send(s, "&c게임 안에서만 사용할 수 있습니다.");
    }

    private ItemStack head(Player p) {
        PlayerData d = data(p);
        return It.of(Material.PLAYER_HEAD).skull(p).name("&e" + p.getName())
                .lore("&7보유 중인 캐시 : &e" + Msg.num(d.money) + "원%nl%&7보유 중인 유료 캐시 : &6" + Msg.num(d.exMoney) + "원").build();
    }

    private void run(Player p, String command) {
        later(1, () -> p.performCommand(command));
    }

    /** 하위 상점 공통: 맨 위 가운데 내 정보, 맨 아래 [뒤로 가기 → 상점] [닫기] */
    private void finish(Gui g, Player p) {
        g.set(4, head(p));
        g.nav(p, () -> mainShop(p)).fill().open(p);
    }

    /** 구매/판매 후 열린 상점의 내 정보 갱신 */
    private void refreshHead(Player p) {
        var top = p.getOpenInventory().getTopInventory();
        if (top != null && top.getHolder() instanceof Gui g) g.set(4, head(p));
    }

    /** 구매 공통 처리. paid = 유료 캐시 */
    private void buy(Player p, long price, boolean paid, Runnable give, String done) {
        if (It.freeSlots(p) < 1) {
            Msg.send(p, "&c&l[!] &c인벤토리 공간이 부족합니다.");
            Msg.sound(p, "entity.villager.no", 0.8f, 1f);
            return;
        }
        if (!plugin.module(EconomyModule.class).take(p, price, paid)) {
            Msg.send(p, paid ? "§c[❌] 유료 캐시가 부족합니다!" : "§c[❌] 캐시가 부족합니다! (현재 캐시: " + Msg.num(data(p).money) + "원)");
            Msg.sound(p, "entity.villager.no", 0.5f, 1);
            return;
        }
        give.run();
        Msg.send(p, done);
        Msg.sound(p, "entity.experience_orb.pickup", 0.5f, 1);
        refreshHead(p);
    }

    /** 가격 표시 통일: 구분선 / 가격 / 클릭 안내 */
    private static String price(long price, boolean paid) {
        return "&8━━━━━━━━━━━━━━%nl%" + priceLine(price, paid) + "%nl%&e» 좌클릭 &f구매";
    }

    private static String priceLine(long price, boolean paid) {
        return "&f가격 : " + (paid ? "&6" : "&a") + Msg.num(price) + (paid ? " 유료 캐시" : " 캐시");
    }

    /** 왼쪽 줄 머리표 (분류 이름) */
    private static ItemStack label(Material m, String name, String desc) {
        return It.of(m).name(name).lore("&7" + desc).hide().build();
    }

    private static boolean left(InventoryClickEvent e) {
        return e.getClick() == ClickType.LEFT;
    }

    private static boolean right(InventoryClickEvent e) {
        return e.getClick() == ClickType.RIGHT;
    }

    /* ======================= 메인 ======================= */

    public void openMain(Player p) {
        mainShop(p);
    }

    /** 상점 분류 버튼 (아이템 뽑기 상점과 같은 형식: 그라데이션 굵은 이름 + "클릭 시 이동됩니다.") */
    private static ItemStack category(Material m, String from, String to, String name) {
        return It.of(m).name("<b><gradient:" + from + ":" + to + "><b><italic:false>" + name + "</b></gradient></b>")
                .lore("&8&l↳ &7&l클릭 시 이동됩니다.").hide().build();
    }

    private void mainShop(Player p) {
        tutorial(p, "shop");
        Gui g = new Gui(5, "&8&l상점");
        g.set(4, head(p));
        g.set(11, category(Material.ENCHANTED_BOOK, "#C77DFF", "#F3D9FF", "인챈트 북 상점"), e -> run(p, "enchantbook"));
        g.set(13, category(Material.MAGMA_CREAM, "#99E982", "#ACFDCD", "아이템 뽑기 상점"), e -> later(1, () -> plugin.module(CrateModule.class).openMenu(p)));
        g.set(15, category(Material.NETHER_STAR, "#FFD84D", "#FF9F1C", "스페셜 상점"), e -> later(1, () -> specialShop(p)));
        g.set(21, category(Material.STONE_PICKAXE, "#A1FF52", "#E2FFC9", "뉴비 상점"), e -> run(p, "newbie"));
        g.set(23, category(Material.APPLE, "#FF9A62", "#FFD3B5", "음식 상점"), e -> run(p, "eat"));
        g.set(29, category(Material.DIAMOND_ORE, "#45D9FF", "#C7F4FF", "광물 상점"), e -> run(p, "ore"));
        g.set(31, category(Material.CLOCK, "#FF8AD8", "#FFD6F2", "룰렛 키 상점"), e -> run(p, "roulette"));
        g.set(33, category(Material.IRON_SWORD, "#FF5555", "#FFB4B4", "무기 상점"), e -> run(p, "combatshop"));
        g.nav(p, () -> plugin.module(MenuModule.class).menu(p)).fill().open(p);
    }

    /* ======================= 스페셜 상점 ======================= */

    private void specialShop(Player p) {
        Gui g = new Gui(3, "&8&l스페셜 상점");
        var cfg = plugin.getConfig();
        long t1 = cfg.getLong("special-shop.altar-ticket.price-1", 5000), t10 = cfg.getLong("special-shop.altar-ticket.price-10", 45000);
        long e1 = cfg.getLong("special-shop.echoes-voucher.price-1", 5000), e10 = cfg.getLong("special-shop.echoes-voucher.price-10", 45000);
        CrateModule crates = plugin.module(CrateModule.class);
        CrateModule.Crate echoes = crates == null ? null : crates.crate(cfg.getString("special-shop.echoes-voucher.crate", "TheEchoesOfNatural"));
        // 천공의 제단 이동권
        g.set(10, offer(Items.altarTicket(), 1, t1), e -> {
            if (left(e)) buy(p, t1, true, () -> It.give(p, Items.altarTicket()), "§a[💰] &7천공의 제단 이동권 1개를 " + Msg.num(t1) + " 유료 캐시에 구매했습니다.");
        });
        g.set(11, offer(Items.altarTicket(), 10, t10), e -> {
            if (left(e)) buy(p, t10, true, () -> It.give(p, Items.altarTicket(), 10), "§a[💰] &7천공의 제단 이동권 10개를 " + Msg.num(t10) + " 유료 캐시에 구매했습니다.");
        });
        // 스페셜 테마 상자 교환권: The Echoes of Natural / Catastrophe of Chaos
        long c1 = cfg.getLong("special-shop.chaos-voucher.price-1", 5000), c10 = cfg.getLong("special-shop.chaos-voucher.price-10", 45000);
        CrateModule.Crate chaos = crates == null ? null : crates.crate(cfg.getString("special-shop.chaos-voucher.crate", "CatastropheOfChaos"));
        if (echoes != null) {
            keyOffer(g, p, 13, echoes.id(), 1, e1, true);
            keyOffer(g, p, 14, echoes.id(), 10, e10, true);
        }
        if (chaos != null) {
            keyOffer(g, p, 15, chaos.id(), 1, c1, true);
            keyOffer(g, p, 16, chaos.id(), 10, c10, true);
        }
        finish(g, p);
    }

    /** 판매 아이콘: 원본 아이템 + 묶음 수량 + 가격 */
    private static ItemStack offer(ItemStack base, int amount, long price) {
        ItemStack it = base.clone();
        it.setAmount(amount);
        ItemMeta meta = it.getItemMeta();
        java.util.List<net.kyori.adventure.text.Component> lore = meta.lore() == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(meta.lore());
        lore = Msg.append(lore, price(price, true).replace("%nl%&f가격", "%nl%&f수량 : &e" + amount + "개" + (amount >= 10 ? " &a(할인)" : "") + "%nl%&f가격"));
        meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    /** 상자 키 판매 칸: 키는 아이템이 아니라 계정에 쌓임 (인벤토리 공간 필요 없음) */
    private void keyOffer(Gui g, Player p, int slot, String crateId, int amount, long price, boolean paid) {
        CrateModule cm = plugin.module(CrateModule.class);
        CrateModule.Crate c = cm == null ? null : cm.crate(crateId);
        if (c == null) return;
        g.set(slot, cm.shopIcon(c, amount, priceLine(price, paid) + (amount >= 10 ? " &a(할인)" : "") + "%nl%&e» 좌클릭 &f구매"), e -> {
            if (!left(e)) return;
            if (!plugin.module(EconomyModule.class).take(p, price, paid)) {
                Msg.send(p, paid ? "§c[❌] 유료 캐시가 부족합니다!" : "§c[❌] 캐시가 부족합니다! (현재 캐시: " + Msg.num(data(p).money) + "원)");
                Msg.sound(p, "entity.villager.no", 0.8f, 1f);
                return;
            }
            cm.giveKeys(p, c, amount, false);
            Msg.send(p, "§a[💰] " + cm.keyLabel(c) + " &7" + amount + "개를 " + Msg.num(price) + (paid ? " 유료 캐시" : " 캐시")
                    + "에 구매했습니다. &8(보유 " + cm.virtualKeys(p, c) + "개 · 계정에 지급)");
            Msg.sound(p, "entity.experience_orb.pickup", 0.5f, 1);
            refreshHead(p);
            later(1, () -> {
                var top = p.getOpenInventory().getTopInventory();
                if (top != null && top.getHolder() instanceof Gui gg) gg.set(slot, cm.shopIcon(c, amount, priceLine(price, paid) + (amount >= 10 ? " &a(할인)" : "") + "%nl%&e» 좌클릭 &f구매"));
            });
        });
    }

    /** 상자 가상 키 지급 (예전에는 CrazyCrates 명령어로 지급) */
    private void crateKey(Player p, String crate, int n) {
        CrateModule cm = plugin.module(CrateModule.class);
        CrateModule.Crate c = cm == null ? null : cm.crate(crate);
        if (c != null) cm.giveKeys(p, c, n, false);
        else plugin.getLogger().warning("상자 '" + crate + "' 를 찾을 수 없어 키를 지급하지 못했습니다: " + p.getName());
    }

    /* ======================= 뉴비 상점 ======================= */

    private void newbieShop(Player p) {
        Gui g = new Gui(3, "&8&l뉴비 상점");
        g.set(11, It.of(Material.BOOK).name("&a[Newbie] &7&l뉴비 지원 패키지")
                .lore("%nl%&a우클릭 시 오픈됩니다. 서버를 처음 시작할 때 매우 용이합니다.", "", "" + price(20000, false)).build(), e -> {
            if (left(e)) buy(p, 20000, false, () -> It.give(p, Items.newbiePackage()),
                    "§a[💰] &7뉴비 지원 패키지를 20,000 캐시에 구매했습니다.");
        });
        g.set(15, It.of(Material.BONE_MEAL).name("&a[Newbie] &7광물 팩")
                .lore("&7다양한 광물이 들어 있습니다!%nl%%nl%&a우클릭 시 오픈됩니다.", "", "" + price(13000, false)).build(), e -> {
            if (left(e)) buy(p, 13000, false, () -> It.give(p, Items.mineralPack()),
                    "§a[💰] &7광물 팩을 13,000 캐시에 구매했습니다.");
        });
        finish(g, p);
    }

    /* ======================= 인챈트 북 ======================= */

    private void enchantShop(Player p) {
        Gui g = new Gui(3, "&8&l인챈트 북 상점");
        book(g, p, 10, Enchantment.SHARPNESS, 5, 17500, "날카로움 5");
        book(g, p, 11, Enchantment.PROTECTION, 4, 17500, "보호 4");
        book(g, p, 12, Enchantment.LOOTING, 3, 17500, "약탈 3");
        book(g, p, 13, Enchantment.SWEEPING_EDGE, 3, 15000, "휩쓸기 3");
        book(g, p, 14, Enchantment.MENDING, 1, 20000, "수선");
        book(g, p, 15, Enchantment.UNBREAKING, 3, 17500, "내구성 3");
        book(g, p, 16, Enchantment.FEATHER_FALLING, 4, 15000, "가벼운 착지 4");
        finish(g, p);
    }

    private void book(Gui g, Player p, int slot, Enchantment ench, int lvl, long price, String label) {
        g.set(slot, It.of(Material.ENCHANTED_BOOK).ench(ench, lvl).lore(price(price, false)).build(), e -> {
            if (left(e)) buy(p, price, false, () -> It.give(p, It.of(Material.ENCHANTED_BOOK).ench(ench, lvl).build()),
                    "§a[💰] &f인챈트 북 (" + label + ") &7를 " + Msg.num(price) + " 캐시에 구매했습니다.");
        });
    }

    /* ======================= 음식 ======================= */

    private void foodShop(Player p) {
        Gui g = new Gui(3, "&8&l음식 상점");
        g.set(10, It.of(Material.GOLDEN_APPLE).lore("%nl%" + price(1200, false)).build(),
                e -> buy(p, 1200, false, () -> It.give(p, new ItemStack(Material.GOLDEN_APPLE)), "§a[💰] &7황금 사과를 1,200 캐시에 구매했습니다."));
        g.set(12, It.of(Material.COOKED_BEEF, 64).lore("%nl%" + price(1500, false)).build(),
                e -> buy(p, 1500, false, () -> It.give(p, new ItemStack(Material.COOKED_BEEF, 64)), "§a[💰] &7스테이크 64개를 1,500 캐시에 구매했습니다."));
        g.set(14, It.of(Material.MELON_SLICE).ench(Enchantment.DEPTH_STRIDER, 1).name("&d&l최고급 수박 조각").lore("%nl%" + price(5000, false)).build(),
                e -> buy(p, 5000, false, () -> It.give(p, Items.melon(1)), "§a[💰] &d&l최고급 수박 조각&7을 5,000 캐시에 구매했습니다."));
        g.set(16, It.of(Material.GOLDEN_CARROT, 64).lore("%nl%" + price(3000, false)).build(),
                e -> buy(p, 3000, false, () -> It.give(p, new ItemStack(Material.GOLDEN_CARROT, 64)), "§a[💰] &7황금 당근 64를 3,000 캐시에 구매했습니다."));
        finish(g, p);
    }

    /* ======================= 뽑기 상점 ======================= */

    private void rouletteShop(Player p) {
        Gui g = new Gui(3, "&8&l룰렛 키 상점");
        keyOffer(g, p, 11, "combat", 1, 22500, false);
        keyOffer(g, p, 15, "enderpearl", 1, 12000, false);
        finish(g, p);
    }

    /* ======================= 유료 캐시 상점 ======================= */

    private void paidShop(Player p) {
        Gui g = new Gui(5, "&8&l유료 캐시 상점");
        g.set(10, Items.packHigh(price(45000, true) + "%nl%&e» 우클릭 &f구성품 보기"), e -> {
            if (left(e)) buy(p, 45000, true, () -> It.give(p, Items.packHigh("&7우클릭 시 아이템 구성품을 확인할 수 있습니다.")),
                    "§a[💰] &c&l[PVP] &e&l고급 전투 패키지&7를 45,000 유료 캐시에 구매했습니다.");
            else if (right(e)) run(p, "combatpackage1");
        });
        g.set(11, Items.packMid(price(30000, true) + "%nl%&e» 우클릭 &f구성품 보기"), e -> {
            if (left(e)) buy(p, 30000, true, () -> It.give(p, Items.packMid("&7우클릭 시 아이템 구성품을 확인할 수 있습니다.")),
                    "§a[💰] &c&l[PVP] &6&l중급 전투 패키지&7를 30,000 유료 캐시에 구매했습니다.");
            else if (right(e)) run(p, "combatpackage2");
        });
        g.set(12, Items.soulSplitterDisplay("", price(20000, true)), e -> {
            if (left(e)) buy(p, 20000, true, () -> It.give(p, Items.soulSplitter(plugin, 0)),
                    "§a[💰] " + Items.SOUL_SPLITTER_NAME + "&7를 20,000 유료 캐시에 구매했습니다.");
        });
        g.set(13, Items.timePiecerWind("", price(10000, true)), e -> {
            if (left(e)) buy(p, 10000, true, () -> It.give(p, Items.timePiecerWind()),
                    "§a[💰] " + Items.TIME_WIND_NAME + "&7를 10,000 유료 캐시에 구매했습니다.");
        });
        ItemStack box1 = global().saved.get("box1");
        if (box1 != null) g.set(14, box1.clone());
        keyOffer(g, p, 19, "combat", 1, 1750, true);
        keyOffer(g, p, 20, "enderpearl", 1, 1000, true);
        keyOffer(g, p, 21, "Sealedp", 1, 5000, true);
        keyOffer(g, p, 22, "TheRuiner", 1, 5000, true);
        String echoesId = plugin.getConfig().getString("special-shop.echoes-voucher.crate", "TheEchoesOfNatural");
        keyOffer(g, p, 23, echoesId, 1, plugin.getConfig().getLong("special-shop.echoes-voucher.price-1", 5000), true);
        keyOffer(g, p, 24, plugin.getConfig().getString("special-shop.chaos-voucher.crate", "CatastropheOfChaos"), 1,
                plugin.getConfig().getLong("special-shop.chaos-voucher.price-1", 5000), true);
        // 뉴비 헬퍼 패스: 계정당 1회만 (영구)
        boolean boughtPass = data(p).purchases.getOrDefault("newbie-pass", 0) >= 1;
        g.set(28, It.of(Material.PAPER).ench(Enchantment.FORTUNE, 10).name("&a&l[💡 NEWBIE] 뉴비 헬퍼 패스")
                .lore("%nl%&7사용 시 &a뉴비 보호 월드&7에 입장할 수 있으며, 경험치 획득량이 &a1.5배&7 상승합니다.", "", "&f영구 구매 제한 : " + (boughtPass ? "&c1" : "&a0") + "&7/1",
                        boughtPass ? "%nl%&8━━━━━━━━━━━━━━%nl%&c이미 구매했습니다." : price(3000, true)).build(), e -> {
            if (!left(e)) return;
            PlayerData pd = data(p);
            if (pd.purchases.getOrDefault("newbie-pass", 0) >= 1) {
                Msg.send(p, "&c[!] 뉴비 헬퍼 패스는 계정당 1회만 구매할 수 있습니다.");
                Msg.sound(p, "entity.villager.no", 0.8f, 1f);
                return;
            }
            buy(p, 3000, true, () -> {
                It.give(p, Items.newbiePass());
                pd.purchases.merge("newbie-pass", 1, Integer::sum);
                plugin.store().save(pd);
            }, "§a[💰] &7뉴비 헬퍼 패스를 3,000 유료 캐시에 구매했습니다. &8(계정당 1회)");
            later(1, () -> paidShop(p));
        });
        // 프리미엄 패스 (PremiumPass 연동)
        if (kr.wave.core.util.PassBridge.available()) {
            long passPrice = plugin.getConfig().getLong("premium-pass.voucher-price", 25_000);
            ItemStack v = kr.wave.core.util.PassBridge.voucher(1);
            if (v != null) {
                g.set(29, offer(v, 1, passPrice), e -> {
                    if (!left(e)) return;
                    ItemStack give = kr.wave.core.util.PassBridge.voucher(1);
                    if (give != null) buy(p, passPrice, true, () -> It.give(p, give), "§a[💰] &6프리미엄 패스권&7을 " + Msg.num(passPrice) + " 유료 캐시에 구매했습니다. &8(우클릭하여 사용)");
                });
            }
        }
        g.set(4, head(p));
        g.set(9, label(Material.CHEST, "&6&l패키지 · 무기", "전투 패키지와 특수 무기"));
        g.set(18, label(Material.TRIPWIRE_HOOK, "&6&l상자 키", "구매한 키는 계정에 쌓입니다."));
        g.set(27, label(Material.NAME_TAG, "&6&l패스", "뉴비 헬퍼 패스 · 프리미엄 패스권"));
        g.nav(p, () -> plugin.module(MenuModule.class).menu(p)).fill().open(p);
    }

    /** 프리미엄 패스 레벨 구매 (유료 캐시) */
    public void passLevels(Player p) {
        if (!kr.wave.core.util.PassBridge.available()) {
            Msg.send(p, "&c프리미엄 패스를 사용할 수 없습니다.");
            return;
        }
        long price = plugin.getConfig().getLong("premium-pass.level-price", 1750);
        int level = kr.wave.core.util.PassBridge.level(p), max = kr.wave.core.util.PassBridge.maxLevel();
        int room = Math.max(0, max - level);
        Gui g = new Gui(3, "&8&l패스 레벨 구매");
        g.set(4, It.of(Material.EXPERIENCE_BOTTLE).name("&a&l프리미엄 패스 레벨")
                .lore("", "&f현재 레벨 : &e" + level + " &7/ " + max, "&f보유 유료 캐시 : &6" + Msg.num(data(p).exMoney), "&f1레벨당 : &6" + Msg.num(price) + " 유료 캐시", "",
                        "&8올린 레벨의 보상은 /pass 에서 받을 수 있습니다.").build());
        int[][] opts = {{11, 1}, {13, 5}, {15, 10}};
        for (int[] o : opts) {
            int n = Math.min(o[1], room);
            long cost = price * n;
            ItemStack icon = It.of(n > 0 ? Material.LIME_DYE : Material.GRAY_DYE, Math.max(1, o[1])).name("&a&l+" + o[1] + " 레벨")
                    .lore(n > 0
                            ? new String[]{"", "&f레벨 : &e" + level + " &7→ &e" + (level + n) + (n < o[1] ? " &8(최대 레벨까지 " + n + "레벨만)" : ""),
                                    "&8━━━━━━━━━━━━━━", "&f가격 : &6" + Msg.num(cost) + " 유료 캐시", "&e» 좌클릭 &f구매"}
                            : new String[]{"", "&c이미 최대 레벨입니다."}).build();
            g.set(o[0], icon, e -> {
                int can = Math.min(o[1], Math.max(0, kr.wave.core.util.PassBridge.maxLevel() - kr.wave.core.util.PassBridge.level(p)));
                if (can <= 0) {
                    Msg.send(p, "&c이미 최대 레벨입니다.");
                    Msg.sound(p, "entity.villager.no", 0.8f, 1f);
                    return;
                }
                long c = price * can;
                if (!plugin.module(EconomyModule.class).take(p, c, true)) {
                    Msg.send(p, "§c[❌] 유료 캐시가 부족합니다! &7(필요 " + Msg.num(c) + " / 보유 " + Msg.num(data(p).exMoney) + ")");
                    Msg.sound(p, "entity.villager.no", 0.8f, 1f);
                    return;
                }
                int up = kr.wave.core.util.PassBridge.addLevels(p, can);
                if (up < can) {   // 연동 실패 등 -> 남은 만큼 환불
                    PlayerData d = data(p);
                    d.exMoney += price * (can - Math.max(0, up));
                    plugin.store().save(d);
                }
                Msg.send(p, "§a[💰] &7패스 레벨 &e+" + Math.max(0, up) + " &7구매 완료 &8(-" + Msg.num(price * Math.max(0, up)) + " 유료 캐시)");
                later(1, () -> passLevels(p));
            });
        }
        g.nav(p, () -> p.performCommand("pass")).fill().open(p);   // 뒤로 가기 = 프리미엄 패스 메뉴
    }

    /* ======================= 광물 상점 (판매) ======================= */

    private static final Object[][] ORES = {
            {10, Material.IRON_INGOT, 100L, "철"},
            {11, Material.GOLD_INGOT, 150L, "금"},
            {12, Material.LAPIS_LAZULI, 50L, "청금석"},
            {13, Material.EMERALD, 300L, "에메랄드"},
            {14, Material.DIAMOND, 300L, "다이아몬드"},
    };

    private void oreShop(Player p) {
        Gui g = new Gui(3, "&8&l광물 상점");
        for (Object[] o : ORES) {
            int slot = (int) o[0];
            Material m = (Material) o[1];
            long price = (long) o[2];
            String name = (String) o[3];
            g.set(slot, It.of(m).lore("", "&8━━━━━━━━━━━━━━", "&f판매 가격 : &a" + Msg.num(price) + " 캐시 &7(1개)", "&e» 우클릭 &f1개 판매", "&e» Shift + 우클릭 &f전부 판매").build(), e -> {
                boolean all = e.getClick() == ClickType.SHIFT_RIGHT;
                if (!right(e) && !all) return;
                int have = 0;
                for (ItemStack it : p.getInventory().getStorageContents()) if (it != null && it.isSimilar(new ItemStack(m))) have += it.getAmount();
                if (have < 1) {
                    Msg.send(p, "§c[❌] 인벤토리에 " + name + "이(가) 없습니다!");
                    Msg.sound(p, "entity.villager.no", 0.5f, 1);
                    return;
                }
                int n = all ? have : 1;
                p.getInventory().removeItem(new ItemStack(m, n));
                PlayerData d = data(p);
                d.money += price * n;
                plugin.store().save(d);
                Msg.send(p, "§e[💰] &7" + name + " " + n + "개를 " + Msg.num(price * n) + " 캐시에 판매했습니다.");
                Msg.sound(p, "entity.experience_orb.pickup", 0.5f, 1);
                refreshHead(p);
            });
        }
        g.set(16, It.of(Material.HOPPER).name("&c&l광물 전부 판매").lore("", "&7인벤토리에 있는 위 광물을 한 번에 판매합니다.", "", "&e» 클릭 &f전부 판매").build(), e -> {
            long total = 0;
            for (Object[] o : ORES) {
                Material m = (Material) o[1];
                int count = 0;
                for (ItemStack it : p.getInventory().getStorageContents()) {
                    if (it != null && it.isSimilar(new ItemStack(m))) count += it.getAmount();
                }
                if (count > 0) {
                    p.getInventory().removeItem(new ItemStack(m, count));
                    total += count * (long) o[2];
                }
            }
            if (total > 0) {
                PlayerData d = data(p);
                d.money += total;
                plugin.store().save(d);
                Msg.send(p, "§a[💰] &7광물을 전부 판매해 &e" + Msg.num(total) + " &7캐시를 받았습니다.");
                refreshHead(p);
            } else {
                Msg.send(p, "§c[❌] 판매할 광물이 없습니다.");
            }
        });
        finish(g, p);
    }

    /* ======================= 무기 상점 ======================= */

    private void combatShop(Player p) {
        Gui g = new Gui(5, "&8&l무기 상점");
        g.set(9, label(Material.NETHER_STAR, "&6&l특수 무기", "서버 전용 능력이 있는 무기"));
        g.set(18, label(Material.IRON_SWORD, "&6&l전투 장비", "인챈트 없는 기본 장비"));
        g.set(27, label(Material.ARROW, "&6&l소모품", "전투에 필요한 소모품"));
        // 특수 무기
        g.set(10, Items.soulSplitterDisplay("", price(85000, false)), e -> {
            if (left(e)) buy(p, 85000, false, () -> It.give(p, Items.soulSplitter(plugin, 0)),
                    "§a[💰] " + Items.SOUL_SPLITTER_NAME + "&7를 85,000 캐시에 구매했습니다.");
        });
        g.set(11, Items.timePiecerWind("", price(62500, false)), e -> {
            if (left(e)) buy(p, 62500, false, () -> It.give(p, Items.timePiecerWind()),
                    "§a[💰] " + Items.TIME_WIND_NAME + "&7를 62,500 캐시에 구매했습니다.");
        });
        g.set(12, Items.emberBrand("", price(70000, false)), e -> {
            if (left(e)) buy(p, 70000, false, () -> It.give(p, Items.emberBrand()),
                    "§a[💰] " + Items.EMBER_NAME + "&7을 70,000 캐시에 구매했습니다.");
        });
        g.set(13, Items.umbralChain("", price(55000, false)), e -> {
            if (left(e)) buy(p, 55000, false, () -> It.give(p, Items.umbralChain()),
                    "§a[💰] " + Items.SHADE_NAME + "&7을 55,000 캐시에 구매했습니다.");
        });
        g.set(14, Items.echoBell("", price(50000, false)), e -> {
            if (left(e)) buy(p, 50000, false, () -> It.give(p, Items.echoBell()),
                    "§a[💰] " + Items.BELL_NAME + "&7을 50,000 캐시에 구매했습니다.");
        });
        // 전투 장비 (인챈트 없는 기본 장비)
        simple(g, p, 19, new ItemStack(Material.IRON_AXE), 2500, "§a[💰] &7철 도끼를 2,500 캐시에 구매했습니다.");
        simple(g, p, 20, new ItemStack(Material.FISHING_ROD), 800, "§a[💰] &7낚싯대를 800 캐시에 구매했습니다.");
        simple(g, p, 21, new ItemStack(Material.BOW), 1500, "§a[💰] &7활을 1,500 캐시에 구매했습니다.");
        simple(g, p, 22, new ItemStack(Material.CROSSBOW), 2000, "§a[💰] &7쇠뇌를 2,000 캐시에 구매했습니다.");
        simple(g, p, 23, new ItemStack(Material.SHIELD), 1250, "§a[💰] &7방패를 1,250 캐시에 구매했습니다.");
        // 소모품
        simple(g, p, 28, new ItemStack(Material.TOTEM_OF_UNDYING), 4000, "§a[💰] &a불사의 토템&7을 4,000 캐시에 구매했습니다.");
        simple(g, p, 29, new ItemStack(Material.ARROW, 32), 200, "§a[💰] &7화살 32개를 200 캐시에 구매했습니다.");
        simple(g, p, 30, new ItemStack(Material.SPECTRAL_ARROW, 32), 350, "§a[💰] &7분광 화살 32개를 350 캐시에 구매했습니다.");
        simple(g, p, 31, new ItemStack(Material.EXPERIENCE_BOTTLE, 48), 1250, "§a[💰] &a경험치 병 &748개를 1,250 캐시에 구매했습니다.");
        simple(g, p, 32, new ItemStack(Material.ENDER_PEARL, 8), 1000, "§a[💰] &7엔더 진주 8개를 1,000 캐시에 구매했습니다.");
        simple(g, p, 33, new ItemStack(Material.FIREWORK_ROCKET, 32), 700, "§a[💰] &7폭죽 로켓 32개를 700 캐시에 구매했습니다.");
        simple(g, p, 34, new ItemStack(Material.WIND_CHARGE, 16), 1600, "§a[💰] &7돌풍구 16개를 1,600 캐시에 구매했습니다.");
        finish(g, p);
    }

    private void simple(Gui g, Player p, int slot, ItemStack item, long price, String done) {
        ItemStack display = item.clone();
        var meta = display.getItemMeta();
        meta.lore(Msg.lore("", price(price, false)));
        display.setItemMeta(meta);
        g.set(slot, display, e -> buy(p, price, false, () -> It.give(p, item.clone()), done));
    }
}
