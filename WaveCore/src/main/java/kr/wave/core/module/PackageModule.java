package kr.wave.core.module;

import kr.wave.core.Items;
import kr.wave.core.WaveCore;
import kr.wave.core.util.Cmd;
import kr.wave.core.util.Gui;
import kr.wave.core.util.Fx;
import kr.wave.core.util.It;
import kr.wave.core.util.Msg;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/** package.sk : 전투/인챈트 패키지, shop.sk : 뉴비 패키지, 광물 팩, Pre-Order 룰렛 */
public class PackageModule extends Module {

    private final List<Supplier<ItemStack>> rouletteRewards = new ArrayList<>();
    private final List<ItemStack> rouletteDisplay = new ArrayList<>();
    private final Set<UUID> rolling = new HashSet<>();

    public PackageModule(WaveCore plugin) {
        super(plugin);
    }

    @Override
    public void enable() {
        cmd("combatpackage1", null, (s, l, a) -> {
            if (Cmd.isPlayer(s)) preview((Player) s, true);
        });
        cmd("combatpackage2", null, (s, l, a) -> {
            if (Cmd.isPlayer(s)) preview((Player) s, false);
        });
        cmd("givepackage", ADMIN, (s, l, a) -> {
            if (Cmd.isPlayer(s)) It.give((Player) s, Items.packHigh("&7우클릭 시 오픈됩니다."));
        });
        cmd("preorder", ADMIN, (s, l, a) -> {
            if (!Cmd.isPlayer(s)) return;
            It.give((Player) s, Items.preorderTicket(), Items.nickPaper("magenta", "Pre-Order Package 4 보상입니다."), Items.mineralPack());
        });
        cmd("뽑기", ADMIN, (s, l, a) -> {
            if (Cmd.isPlayer(s)) startRoulette((Player) s);
        });
        buildRewards();
    }

    /* ======================= 전투 패키지 미리보기 ======================= */

    private void preview(Player p, boolean high) {
        Gui g = new Gui(6, high ? "&8&l고급 전투 패키지" : "&8&l중급 전투 패키지");
        if (high) {
            g.set(10, It.of(Material.NETHERITE_HELMET).ench(Enchantment.PROTECTION, 4).ench(Enchantment.UNBREAKING, 3).build());
            g.set(12, It.of(Material.NETHERITE_SWORD).ench(Enchantment.SHARPNESS, 5).ench(Enchantment.LOOTING, 3).build());
            g.set(13, pick(Material.NETHERITE_PICKAXE));
            g.set(14, It.of(Material.ELYTRA).ench(Enchantment.MENDING, 1).ench(Enchantment.UNBREAKING, 3).build());
            g.set(15, new ItemStack(Material.FIREWORK_ROCKET, 64));
            g.set(16, new ItemStack(Material.FIREWORK_ROCKET, 64));
            g.set(19, It.of(Material.NETHERITE_CHESTPLATE).ench(Enchantment.PROTECTION, 4).ench(Enchantment.UNBREAKING, 3).build());
            g.set(21, new ItemStack(Material.GOLDEN_CARROT, 64));
            g.set(22, new ItemStack(Material.GOLDEN_APPLE, 16));
            g.set(23, Items.melon(6));
            g.set(24, new ItemStack(Material.ENDER_PEARL, 16));
            g.set(25, new ItemStack(Material.ENDER_PEARL, 16));
            g.set(28, It.of(Material.NETHERITE_LEGGINGS).ench(Enchantment.PROTECTION, 4).ench(Enchantment.UNBREAKING, 3).build());
            g.set(30, new ItemStack(Material.TOTEM_OF_UNDYING));
            g.set(31, new ItemStack(Material.TOTEM_OF_UNDYING));
            g.set(32, new ItemStack(Material.EXPERIENCE_BOTTLE, 64));
            g.set(33, new ItemStack(Material.ENDER_CHEST));
            g.set(34, It.of(Material.DIAMOND_PICKAXE).ench(Enchantment.SILK_TOUCH, 1).ench(Enchantment.EFFICIENCY, 5).build());
            g.set(37, It.of(Material.NETHERITE_BOOTS).ench(Enchantment.PROTECTION, 4).ench(Enchantment.UNBREAKING, 3).build());
        } else {
            g.set(10, It.of(Material.DIAMOND_HELMET).ench(Enchantment.PROTECTION, 4).ench(Enchantment.UNBREAKING, 3).build());
            g.set(12, It.of(Material.DIAMOND_SWORD).ench(Enchantment.SHARPNESS, 4).build());
            g.set(13, pick(Material.DIAMOND_PICKAXE));
            g.set(19, It.of(Material.DIAMOND_CHESTPLATE).ench(Enchantment.PROTECTION, 4).ench(Enchantment.UNBREAKING, 3).build());
            g.set(21, new ItemStack(Material.GOLDEN_CARROT, 64));
            g.set(22, new ItemStack(Material.GOLDEN_APPLE, 8));
            g.set(23, Items.melon(3));
            g.set(24, new ItemStack(Material.ENDER_PEARL, 16));
            g.set(25, new ItemStack(Material.ENDER_PEARL, 16));
            g.set(28, It.of(Material.DIAMOND_LEGGINGS).ench(Enchantment.PROTECTION, 4).ench(Enchantment.UNBREAKING, 3).build());
            g.set(30, new ItemStack(Material.TOTEM_OF_UNDYING));
            g.set(31, new ItemStack(Material.TOTEM_OF_UNDYING));
            g.set(32, new ItemStack(Material.EXPERIENCE_BOTTLE, 48));
            g.set(33, new ItemStack(Material.ENDER_CHEST));
            g.set(34, It.of(Material.DIAMOND_PICKAXE).ench(Enchantment.SILK_TOUCH, 1).ench(Enchantment.EFFICIENCY, 3).build());
            g.set(37, It.of(Material.DIAMOND_BOOTS).ench(Enchantment.PROTECTION, 4).ench(Enchantment.UNBREAKING, 3).build());
        }
        g.nav(p, () -> p.performCommand("yurocash")).fill().open(p);
    }

    private static ItemStack pick(Material m) {
        return It.of(m).ench(Enchantment.EFFICIENCY, 5).ench(Enchantment.FORTUNE, 3).ench(Enchantment.MENDING, 1).ench(Enchantment.UNBREAKING, 3).build();
    }

    private static ItemStack armor(Material m) {
        return It.of(m).ench(Enchantment.PROTECTION, 4).ench(Enchantment.UNBREAKING, 3).build();
    }

    /* ======================= 우클릭 사용 ======================= */

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        String n = Msg.name(p.getInventory().getItemInMainHand());
        if (n.isEmpty()) return;

        if (n.equals(Items.N_PACK_HIGH)) {
            e.setCancelled(true);
            if (!space(p, 20)) return;
            It.takeOneFromHand(p);
            It.give(p, armor(Material.NETHERITE_HELMET), armor(Material.NETHERITE_CHESTPLATE), armor(Material.NETHERITE_LEGGINGS), armor(Material.NETHERITE_BOOTS),
                    It.of(Material.NETHERITE_SWORD).ench(Enchantment.SHARPNESS, 5).ench(Enchantment.LOOTING, 3).build(),
                    pick(Material.NETHERITE_PICKAXE),
                    It.of(Material.ELYTRA).ench(Enchantment.MENDING, 1).ench(Enchantment.UNBREAKING, 3).build());
            It.give(p, new ItemStack(Material.GOLDEN_CARROT), 64);
            It.give(p, new ItemStack(Material.GOLDEN_APPLE), 16);
            It.give(p, Items.melon(6));
            It.give(p, new ItemStack(Material.ENDER_PEARL), 32);
            It.give(p, new ItemStack(Material.ENDER_CHEST), It.of(Material.DIAMOND_PICKAXE).ench(Enchantment.SILK_TOUCH, 1).ench(Enchantment.EFFICIENCY, 5).build());
            It.give(p, new ItemStack(Material.EXPERIENCE_BOTTLE), 64);
            It.give(p, new ItemStack(Material.TOTEM_OF_UNDYING), 3);
            It.give(p, new ItemStack(Material.FIREWORK_ROCKET), 128);
            Msg.soundAt(p.getLocation(), "entity.player.levelup", 1, 1.5f);
            Msg.send(p, "&a고급 전투 패키지를 수령했습니다!");
            openFx(p, 0xFFD700);
        } else if (n.equals(Items.N_PACK_MID)) {
            e.setCancelled(true);
            if (!space(p, 20)) return;
            It.takeOneFromHand(p);
            It.give(p, armor(Material.DIAMOND_HELMET), armor(Material.DIAMOND_CHESTPLATE), armor(Material.DIAMOND_LEGGINGS), armor(Material.DIAMOND_BOOTS),
                    It.of(Material.DIAMOND_SWORD).ench(Enchantment.SHARPNESS, 4).build(), pick(Material.DIAMOND_PICKAXE));
            It.give(p, new ItemStack(Material.GOLDEN_CARROT), 64);
            It.give(p, new ItemStack(Material.GOLDEN_APPLE), 8);
            It.give(p, Items.melon(3));
            It.give(p, new ItemStack(Material.ENDER_PEARL), 32);
            It.give(p, new ItemStack(Material.ENDER_CHEST), It.of(Material.DIAMOND_PICKAXE).ench(Enchantment.SILK_TOUCH, 1).ench(Enchantment.EFFICIENCY, 3).build());
            It.give(p, new ItemStack(Material.EXPERIENCE_BOTTLE), 48);
            It.give(p, new ItemStack(Material.TOTEM_OF_UNDYING), 2);
            Msg.soundAt(p.getLocation(), "entity.player.levelup", 1, 1.5f);
            Msg.send(p, "&a중급 전투 패키지를 수령했습니다!");
            openFx(p, 0xFFAA00);
        } else if (n.contains(Items.N_PACK_ENCHANT)) {
            e.setCancelled(true);
            if (!space(p, 10)) return;
            It.takeOneFromHand(p);
            It.give(p, new ItemStack(Material.ENCHANTING_TABLE), 3);
            It.give(p, new ItemStack(Material.BOOKSHELF), 64);
            It.give(p, new ItemStack(Material.BOOK), 128);
            It.give(p, new ItemStack(Material.EXPERIENCE_BOTTLE), 256);
            It.give(p, new ItemStack(Material.GRINDSTONE), 5);
            Msg.soundAt(p.getLocation(), "entity.player.levelup", 1, 1.5f);
            openFx(p, 0xB388FF);
            Fx.p(p.getLocation().add(0, 1.2, 0), Particle.ENCHANT, 80, 0.8, 1.0);
        } else if (n.equals(Items.N_NEWBIE_PACKAGE)) {
            e.setCancelled(true);
            It.takeOneFromHand(p);
            It.give(p, It.of(Material.DIAMOND_HELMET).ench(Enchantment.PROTECTION, 2).name("&a[Newbie] &7다이아몬드 헬멧").build(),
                    It.of(Material.IRON_CHESTPLATE).ench(Enchantment.PROTECTION, 2).name("&a[Newbie] &7철 갑옷").build(),
                    It.of(Material.IRON_LEGGINGS).ench(Enchantment.PROTECTION, 2).name("&a[Newbie] &7철 바지").build(),
                    It.of(Material.IRON_BOOTS).ench(Enchantment.PROTECTION, 2).name("&a[Newbie] &7철 신발").build(),
                    It.of(Material.IRON_SWORD).ench(Enchantment.SHARPNESS, 2).ench(Enchantment.LOOTING, 1).name("&a[Newbie] &7철 검").build(),
                    It.of(Material.IRON_PICKAXE).ench(Enchantment.EFFICIENCY, 2).ench(Enchantment.FORTUNE, 1).name("&a[Newbie] &7철 곡괭이").build(),
                    new ItemStack(Material.FLINT_AND_STEEL), new ItemStack(Material.COOKED_BEEF, 64), new ItemStack(Material.GOLDEN_CARROT, 16),
                    new ItemStack(Material.GOLDEN_APPLE, 4), new ItemStack(Material.ENDER_PEARL, 8), new ItemStack(Material.OBSIDIAN, 8),
                    new ItemStack(Material.TOTEM_OF_UNDYING), new ItemStack(Material.ENDER_CHEST));
            Msg.send(p, "&a지급되었습니다!");
            openFx(p, 0x55FF55);
        } else if (n.equals(Items.N_MINERAL_PACK)) {
            e.setCancelled(true);
            It.takeOneFromHand(p);
            It.give(p, new ItemStack(Material.IRON_INGOT, 30), new ItemStack(Material.GOLD_INGOT, 20), new ItemStack(Material.DIAMOND, 12),
                    new ItemStack(Material.LAPIS_LAZULI, 40), new ItemStack(Material.COAL, 30));
            Fx.block(p.getLocation().add(0, 1, 0), Particle.BLOCK, Material.DIAMOND_ORE.createBlockData(), 40, 0.5);
            openFx(p, 0x5AC8FA);
        } else if (n.contains(Items.N_PREORDER_ROULETTE)) {
            e.setCancelled(true);
            if (rolling.contains(p.getUniqueId())) {
                Msg.send(p, "§c[!] 이미 룰렛이 돌아가고 있습니다!");
                return;
            }
            It.takeOneFromHand(p);
            startRoulette(p);
        }
    }

    /** 상자 개봉 연출: 색 충격파 + 반짝임 */
    private void openFx(Player p, int hex) {
        Fx.shockwave(p.getLocation().add(0, 0.1, 0), Fx.rgb(hex), 2.8, 8);
        Fx.burst(p.getLocation().add(0, 1, 0), Particle.HAPPY_VILLAGER, 20, 0.3);
        Fx.burst(p.getLocation().add(0, 1.2, 0), Particle.FIREWORK, 30, 0.12);
    }

    private boolean space(Player p, int need) {
        if (It.freeSlots(p) >= need) return true;
        Msg.send(p, "&c인벤토리 공간이 부족합니다. 최소 " + need + "칸의 공간이 필요합니다.");
        Msg.sound(p, "block.note_block.bass", 1, 1);
        return false;
    }

    /* ======================= Pre-Order 룰렛 ======================= */

    private void buildRewards() {
        add(Items::judgeHelmet);
        add(Items::claymoreJs);
        add(() -> Items.nickPaper("magenta", "Pre-Order 시작의 룰렛 보상입니다."));
        add(() -> Items.nickPaper("gradient", "Pre-Order 시작의 룰렛 보상입니다."));
        add(() -> It.of(Material.DIAMOND_PICKAXE).ench(Enchantment.EFFICIENCY, 2).ench(Enchantment.FORTUNE, 3).name("&d&l[Pre-Order] &bStarter Pickaxe")
                .lore("%nl%&c&l엔더 드래곤이 잡히기 전까지는 아이템이 드롭되지 않습니다.").build());
        add(() -> It.of(Material.DRAGON_BREATH, 10).ench(Enchantment.FORTUNE, 3).name("&7[Roulette] &c강력한 무기 룰렛")
                .lore("%nl%&7등장하는 아이템 목록:%nl%&7- &f다이아몬드 헬멧 &7(보호 4)%nl%&7- &f다이아몬드 갑옷 &7(보호 4)%nl%&7- &f다이아몬드 레깅스 &7(보호 4)%nl%&7- &f다이아몬드 부츠 &7(보호 4)%nl%&7- &f다이아몬드 검 &7(날카로움 5, 약탈 3)%nl%&7- &f네더라이트 도끼 &7(날카로움 5)%nl%&7- &f삼지창 &7(충성 3, 찌르기 5)%nl%&7- &f활 &7(힘 5, 무한, 화염)%nl%&7- &d철퇴").build());
        add(() -> Items.packHigh("&7우클릭 시 오픈됩니다."));
        add(() -> Items.packMid("&7우클릭 시 오픈됩니다."));
        add(() -> It.of(Material.NETHERITE_HOE).ench(Enchantment.INFINITY, 1).name("<italic:false><gradient:#03459B:#00E7FF:#FF0000><b>[✦] [Lv.✕]</b> Soulstripe Scythe</gradient>")
                .lore("%nl%&7등급 : <gradient:#29FF00:#EEEEEE><bold:true>Legendary</gradient>%nl%%nl%<gradient:#F500FF:#FF7272><italic:false>사신이 사용하던 영혼 수확용 대낫</gradient>%nl%%nl%<gradient:#00DDFF:#FFD8D8><gradient:#FF8686:#FF0000><italic:false>[Lv.Unknown 특성]</gradient>%nl%&7- 무기 기본 데미지가 6으로 고정됩니다%nl%&7- 타격 시 일정 확률로 적을 구속시킵니다%nl%&7- 타격 시 일정 확률로 적의 체력을 흡수합니다").build());
        add(() -> It.of(Material.ELYTRA).ench(Enchantment.UNBREAKING, 10).name("<gradient:#00B3FF:#DFDFDF><italic:false><bold:true>[Sealed]</gradient> <gradient:#D1D1D1:#DBDBDB><italic:false><bold:true>[Lv.✕]</gradient> <gradient:#DFDFDF:#919191><italic:false>얼어붙은 제왕의 날개</gradient>")
                .lore("%nl%&7등급 : <gradient:#29FF00:#EEEEEE><bold:true>Legendary</gradient>%nl%%nl%<gradient:#00DDFF:#FFD8D8><italic:false>얼어붙은 고대 제왕의 마지막 전리품").build());
        add(() -> It.of(Material.DIAMOND_SWORD).ench(Enchantment.SHARPNESS, 6).name("<gradient:#00B3FF:#DFDFDF><italic:false><bold:true>[Sealed]</gradient> <gradient:#D1D1D1:#DBDBDB><italic:false><bold:true>[Lv.✕]</gradient> <gradient:#DFDFDF:#919191><italic:false>얼어붙은 제왕의 검</gradient>")
                .lore("%nl%&7등급 : <gradient:#29FF00:#EEEEEE><bold:true>Legendary</gradient>%nl%%nl%<gradient:#00DDFF:#FFD8D8><italic:false>얼어붙은 고대 제왕의 마지막 전리품</gradient>%nl%%nl%<gradient:#00DDFF:#FFD8D8><gradient:#00DDFF:#FFFFFF><italic:false>[Lv.Unknown 특성]</gradient>%nl%&3- 타격 시 랜덤 버프가 부여됩니다%nl%&3- 우클릭 시 신속 IV를 7초간 부여받습니다 &7(쿨타임 1분)").build());
        rouletteRewards.add(() -> Items.soulSplitter(plugin, 250));
        rouletteDisplay.add(Items.soulSplitterDisplay());
    }

    private void add(Supplier<ItemStack> s) {
        rouletteRewards.add(s);
        rouletteDisplay.add(s.get());
    }

    public void startRoulette(Player p) {
        if (!rolling.add(p.getUniqueId())) {
            Msg.send(p, "§c[⚠️] 이미 룰렛이 돌아가고 있습니다!");
            return;
        }
        Gui g = new Gui(3, "&8&lPre-Order 룰렛");
        int[] line = new int[9];
        for (int i = 0; i < 9; i++) line[i] = ThreadLocalRandom.current().nextInt(rouletteRewards.size());
        for (int i = 0; i < 9; i++) {
            g.set(i, i == 4 ? It.pane(Material.RED_STAINED_GLASS_PANE, "§c▼ 당첨 바늘 ▼") : It.pane(Material.BLACK_STAINED_GLASS_PANE, " "));
            g.set(18 + i, i == 4 ? It.pane(Material.RED_STAINED_GLASS_PANE, "§c▲ 당첨 바늘 ▲") : It.pane(Material.BLACK_STAINED_GLASS_PANE, " "));
        }
        g.onClose(e -> {
            if (rolling.contains(p.getUniqueId())) Msg.send(p, "§c&l[!] 룰렛 도중 창을 닫았습니다. 보상은 잠시 후 자동으로 지급됩니다.");
        });
        g.open(p);
        step(p, g, line, 30, 2);
    }

    private void step(Player p, Gui g, int[] line, int count, int delay) {
        if (count <= 0) {
            finish(p, line[4]);
            return;
        }
        System.arraycopy(line, 1, line, 0, 8);
        line[8] = ThreadLocalRandom.current().nextInt(rouletteRewards.size());
        for (int i = 0; i < 9; i++) g.set(9 + i, rouletteDisplay.get(line[i]).clone());
        var top = p.getOpenInventory().getTopInventory();
        if (p.isOnline() && top != null && top.getHolder() == g) {
            Msg.sound(p, "block.wooden_button.click_on", 0.5f, 1.0f + count / 30f);
        }
        int next = delay + (count <= 12 ? 1 : 0) + (count <= 5 ? 2 : 0);
        later(delay, () -> step(p, g, line, count - 1, next));
    }

    private void finish(Player p, int index) {
        rolling.remove(p.getUniqueId());
        ItemStack win = rouletteRewards.get(index).get();
        if (p.isOnline()) {
            It.give(p, win);
            Msg.sound(p, "entity.player.levelup", 0.8f, 1.0f);
            String name = win.getItemMeta().hasDisplayName() ? MiniMessage.miniMessage().serialize(win.getItemMeta().displayName()) : win.getType().name();
            Msg.send(p, "&d&lPre-Order &7- §l" + name + "§a에 당첨되셨습니다!");
            Fx.burst(p.getLocation().add(0, 1, 0), Particle.TOTEM_OF_UNDYING, 80, 0.5);
            Fx.helix(p, Fx.rgb(0xFF55FF), Fx.rgb(0x55FFFF), 0.9, 2.8, 30);
        } else {
            plugin.module(MailModule.class).send(p.getUniqueId(), win);
        }
    }
}
