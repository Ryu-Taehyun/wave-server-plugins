package kr.wave.core.module;

import kr.wave.core.Items;
import kr.wave.core.WaveCore;
import kr.wave.core.data.PlayerData;
import kr.wave.core.util.Cmd;
import kr.wave.core.util.Gui;
import kr.wave.core.util.It;
import kr.wave.core.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

/** levelsystm.sk : 플레이어 레벨 1~50 (19 이상은 레벨마다 필요 경험치 +5,000) */
public class LevelModule extends Module {

    public static final int MAX_LEVEL = 50;
    private static final long[] TABLE = {0, 200, 400, 800, 1500, 2500, 4000, 6000, 8000, 12000, 16000, 22000, 26000, 35000, 40000, 45000, 50000, 60000};

    public LevelModule(WaveCore plugin) {
        super(plugin);
    }

    @Override
    public void enable() {
        cmd("levelstats", null, (s, l, a) -> {
            if (Cmd.isPlayer(s)) gui((Player) s, 1);
        });
        cmd("레벨초기화", null, (s, l, a) -> {
            if (!Cmd.isPlayer(s)) return;
            PlayerData d = data((Player) s);
            d.level = 1;
            d.exp = 0;
            plugin.store().save(d);
        });
    }

    public static long needed(int level) {
        if (level >= 1 && level < TABLE.length) return TABLE[level];
        if (level >= TABLE.length && level < MAX_LEVEL) return TABLE[TABLE.length - 1] + 5_000L * (level - TABLE.length + 1);   // 18 -> 65,000 ... 49 -> 220,000
        return 999999;
    }

    /* ======================= 경험치 획득 ======================= */

    private void gain(Player p, long normal, long boosted, String what) {
        PlayerData d = data(p);
        long amount = d.newbiePassActive() ? boosted : normal;
        d.exp += amount;
        checkLevelUp(p, d);
        Msg.send(p, "<gradient:#CEFF74:#FFFFFF>[Level]</gradient> &f- " + what + " &7&o(+ " + amount + " EXP!)");
        plugin.store().save(d);
    }

    private void checkLevelUp(Player p, PlayerData d) {
        while (true) {
            if (d.level >= MAX_LEVEL) {
                d.exp = 0;
                return;
            }
            long need = needed(d.level);
            if (d.exp < need) return;
            d.exp -= need;
            d.level++;
            Msg.send(p, "");
            Msg.send(p, "");
            Msg.send(p, "<gradient:#BEFA4F:#FFEAEA><bold:true>[Level UP]</gradient>%nl%<gradient:#00C8FF:#FFFFFF>축하합니다! 레벨 " + d.level + "이(가) 되었습니다.</gradient></reset>%nl%&7우편함으로 레벨업 보상이 지급되었습니다");
            Msg.send(p, "");
            Msg.send(p, "");
            Msg.sound(p, "entity.player.levelup", 1, 1);
            ItemStack reward = reward(d.level, true);
            if (reward != null) plugin.module(MailModule.class).send(p.getUniqueId(), reward);
        }
    }

    /** 레벨 보상. forMail=false 면 GUI 진열용 */
    private ItemStack reward(int level, boolean forMail) {
        return switch (level) {
            case 2 -> new ItemStack(Material.EXPERIENCE_BOTTLE, 16);
            case 3 -> new ItemStack(Material.ENDER_PEARL, 8);
            case 4 -> Items.mineralPack();
            case 5 -> new ItemStack(Material.TOTEM_OF_UNDYING);
            case 6 -> Items.melon(3);
            case 7 -> new ItemStack(Material.NETHERITE_SCRAP, 10);
            case 8 -> Items.heavenKey(3, null);
            case 9 -> Items.weaponKey(3, null);
            case 10 -> Items.timePiecerWind();
            case 11 -> new ItemStack(Material.NETHERITE_INGOT, 5);
            case 12 -> new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 3);
            case 13 -> Items.sealedKey(2);
            case 14 -> Items.ruinerKey(2);
            case 15 -> {
                ItemStack box = global().saved.get("final1");
                if (box == null) yield null;
                ItemStack c = box.clone();
                c.setAmount(3);
                yield c;
            }
            case 16 -> forMail ? Items.soulSplitter(plugin, 0) : Items.soulSplitterDisplay();
            case 17 -> Items.voidBow();
            case 18 -> Items.nickPaper("red", "레벨 18 달성 보상입니다.");
            case 19 -> new ItemStack(Material.EXPERIENCE_BOTTLE, 32);
            case 20 -> Items.heavenKey(5, null);
            case 21 -> new ItemStack(Material.NETHERITE_SCRAP, 16);
            case 22 -> Items.melon(5);
            case 23 -> new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 5);
            case 24 -> Items.weaponKey(5, null);
            case 25 -> amount(Items.timeShard(), 5);
            case 26 -> new ItemStack(Material.TOTEM_OF_UNDYING, 3);
            case 27 -> new ItemStack(Material.NETHERITE_INGOT, 8);
            case 28 -> amount(Items.mineralPack(), 2);
            case 29 -> Items.heavenKey(6, null);
            case 30 -> Items.sealedKey(4);
            case 31 -> new ItemStack(Material.EXPERIENCE_BOTTLE, 64);
            case 32 -> amount(Items.altarTicket(), 5);
            case 33 -> new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 8);
            case 34 -> Items.ruinerKey(4);
            case 35 -> amount(Items.timeShard(), 10);
            case 36 -> Items.weaponKey(8, null);
            case 37 -> new ItemStack(Material.NETHERITE_INGOT, 16);
            case 38 -> Items.melon(10);
            case 39 -> new ItemStack(Material.TOTEM_OF_UNDYING, 5);
            case 40 -> Items.sealedKey(6);
            case 41 -> amount(Items.mineralPack(), 4);
            case 42 -> Items.heavenKey(10, null);
            case 43 -> new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 16);
            case 44 -> new ItemStack(Material.NETHERITE_BLOCK, 2);
            case 45 -> Items.ruinerKey(6);
            case 46 -> amount(Items.altarTicket(), 10);
            case 47 -> Items.weaponKey(12, null);
            case 48 -> amount(Items.timeShard(), 20);
            case 49 -> new ItemStack(Material.NETHER_STAR, 3);
            case 50 -> Items.legendVoucher();
            default -> null;
        };
    }

    private static ItemStack amount(ItemStack it, int n) {
        it.setAmount(Math.min(n, it.getMaxStackSize()));
        return it;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        if (e.getEntity() instanceof Player) gain(killer, 120, 190, "&c플레이어 처치");
        else if (e.getEntity() instanceof Enemy) gain(killer, 30, 45, "&e몬스터 처치");
    }

    /** 플레이어가 설치한 광석 (설치 -> 채굴 반복으로 경험치를 얻는 것 방지). 오래된 것부터 정리 */
    private final java.util.LinkedHashSet<Long> placedOres = new java.util.LinkedHashSet<>();

    private static long key(org.bukkit.block.Block b) {
        return ((long) b.getX() & 0x3FFFFFF) << 38 | ((long) b.getZ() & 0x3FFFFFF) << 12 | ((long) b.getY() & 0xFFF);
    }

    private static boolean xpOre(Material m) {
        return switch (m) {
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE, ANCIENT_DEBRIS, GOLD_ORE, DEEPSLATE_GOLD_ORE, IRON_ORE, DEEPSLATE_IRON_ORE -> true;
            default -> false;
        };
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(org.bukkit.event.block.BlockPlaceEvent e) {
        if (!xpOre(e.getBlockPlaced().getType())) return;
        placedOres.add(key(e.getBlockPlaced()) ^ e.getBlockPlaced().getWorld().getName().hashCode());
        if (placedOres.size() > 100_000) {
            var it = placedOres.iterator();
            it.next();
            it.remove();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMine(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (xpOre(e.getBlock().getType()) && placedOres.remove(key(e.getBlock()) ^ e.getBlock().getWorld().getName().hashCode())) return;
        switch (e.getBlock().getType()) {
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> gain(p, 50, 75, "&e다이아몬드 채굴");
            case ANCIENT_DEBRIS -> gain(p, 75, 110, "&e고대 잔해 채굴");
            case GOLD_ORE, DEEPSLATE_GOLD_ORE -> gain(p, 25, 40, "&e금 광석 채굴");
            case IRON_ORE, DEEPSLATE_IRON_ORE -> gain(p, 20, 35, "&e철 광석 채굴");
            default -> {
            }
        }
    }

    /* ======================= 레벨 보상 GUI ======================= */

    private void gui(Player p, int page) {
        PlayerData d = data(p);
        long remain = Math.max(0, needed(d.level) - d.exp);
        Gui g = new Gui(5, "&8&l레벨 업 보상");
        int pages = (MAX_LEVEL + 8) / 9;
        int first = (page - 1) * 9 + 1;
        for (int i = 0; i < 9; i++) {
            int lv = first + i;
            if (lv > MAX_LEVEL) break;
            ItemStack pane = d.level >= lv
                    ? It.pane(Material.LIME_DYE, "&a보상 해금됨 (레벨 " + lv + " 이상)")
                    : It.pane(Material.GRAY_DYE, "&c보상 잠김 (레벨 " + lv + " 필요)");
            g.set(9 + i, pane);
            g.set(27 + i, pane);
            ItemStack reward = lv == 1 ? It.of(Material.PAPER).name("&e서버에 오신 것을 환영합니다!").build() : reward(lv, false);
            if (reward != null) g.set(18 + i, reward);
        }
        g.set(4, It.of(Material.PLAYER_HEAD).skull(p).name("&f" + p.getName())
                .lore("&7현재 레벨 : &e" + d.level + " &8/ " + MAX_LEVEL + "%nl%" + (d.level >= MAX_LEVEL ? "&6최고 레벨 달성!" : "&7다음 레벨까지 남은 경험치 : &e" + Msg.num(remain))).build());
        g.pages(p, page, pages, n -> later(1, () -> gui(p, n)))
                .nav(p, () -> plugin.module(MenuModule.class).menu(p)).fill().open(p);
    }
}
