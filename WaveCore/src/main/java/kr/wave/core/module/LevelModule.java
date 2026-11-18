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

/** levelsystm.sk : 플레이어 레벨 1~18 */
public class LevelModule extends Module {

    public static final int MAX_LEVEL = 18;
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
        return level >= 1 && level < TABLE.length ? TABLE[level] : 999999;
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
            default -> null;
        };
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
        int first = page == 1 ? 1 : 10;
        for (int i = 0; i < 9; i++) {
            int lv = first + i;
            ItemStack pane = d.level >= lv
                    ? It.pane(Material.LIME_DYE, "&a보상 해금됨 (레벨 " + lv + " 이상)")
                    : It.pane(Material.GRAY_DYE, "&c보상 잠김 (레벨 " + lv + " 필요)");
            g.set(9 + i, pane);
            g.set(27 + i, pane);
            ItemStack reward = lv == 1 ? It.of(Material.PAPER).name("&e서버에 오신 것을 환영합니다!").build() : reward(lv, false);
            if (reward != null) g.set(18 + i, reward);
        }
        g.set(4, It.of(Material.PLAYER_HEAD).skull(p).name("&f" + p.getName())
                .lore("&7현재 레벨 : &e" + d.level + "%nl%&7다음 레벨까지 남은 경험치 : &e" + remain).build());
        g.pages(p, page, 2, n -> later(1, () -> gui(p, n)))
                .nav(p, () -> plugin.module(MenuModule.class).menu(p)).fill().open(p);
    }
}
