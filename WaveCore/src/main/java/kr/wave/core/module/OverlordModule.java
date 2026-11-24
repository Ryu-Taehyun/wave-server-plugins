package kr.wave.core.module;

import kr.wave.core.Items;
import kr.wave.core.WaveCore;
import kr.wave.core.data.PlayerData;
import kr.wave.core.util.Cmd;
import kr.wave.core.util.Fx;
import kr.wave.core.util.Gui;
import kr.wave.core.util.It;
import kr.wave.core.util.Msg;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.function.Supplier;

/**
 * 오버 로드: Peerless 랭크 달성 후 유료 캐시 75,000 을 모을 때마다 휘장 1개, 휘장 수만큼 ★ 보상 수령
 */
public class OverlordModule extends Module {

    private static final String TITLE = "<b><gradient:#FFF176:#FFB732>OVER LORD</gradient></b>";

    /** ★ 단계별 보상 (이름, 아이콘) */
    private record Star(int n, String name, Material icon) {
    }

    private static final Star[] STARS = {
            new Star(1, "&e500,000 캐시", Material.GOLD_INGOT),
            new Star(2, "&e천공의 제단 이동권 &f10개", Material.PAPER),
            new Star(3, "<gradient:#F5E400:#95FFBE>The Echoes of Natural</gradient> &f키 30개", Material.LIME_DYE),
            new Star(4, "&a배낭 슬롯 증가 &f1줄 &7(2줄 → 3줄)", Material.CHEST),
            new Star(5, "<gradient:#29FF00:#EEEEEE>Legendary 무기 교환권</gradient>", Material.DIAMOND_SWORD),
            new Star(6, "&e750,000 캐시", Material.GOLD_BLOCK),
            new Star(7, "<gradient:#FFF176:#FFB732>오버 로드 - 전용 입장 메시지</gradient>", Material.OAK_SIGN),
            new Star(8, "<gradient:#FFF176:#FFB732>오버 로드 - 크라운 킬 알림 표식</gradient>", Material.GOLDEN_HELMET),
            new Star(9, "<gradient:#FFF176:#FFB732>오버 로드 닉네임 색상 - Gradient Gold (영구)</gradient>", Material.NAME_TAG),
            new Star(10, "<gradient:#FFF176:#FFB732>전설의 무기 교환권</gradient>", Material.NETHER_STAR),
    };
    private static final int[] SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24};

    public OverlordModule(WaveCore plugin) {
        super(plugin);
    }

    @Override
    public void enable() {
        cmd("오버로드", null, (s, l, a) -> {
            if (Cmd.isPlayer(s)) open((Player) s);
        }, "overlord");
    }

    /* ======================= 진행도 ======================= */

    private long start() {
        return plugin.getConfig().getLong("overlord.start", 200_000);
    }

    private long per() {
        return Math.max(1, plugin.getConfig().getLong("overlord.per-insignia", 75_000));
    }

    public boolean unlocked(PlayerData d) {
        return d.ranks.contains("peerless");
    }

    /** 모은 휘장 수 (Peerless 달성 기준 금액 이후 75,000 유료 캐시마다 1개, 최대 10) */
    public int insignias(PlayerData d) {
        if (!unlocked(d)) return 0;
        return (int) Math.max(0, Math.min(10, (d.exMoneyTotal - start()) / per()));
    }

    /** 다음 휘장까지 남은 유료 캐시 */
    private long untilNext(PlayerData d) {
        long over = Math.max(0, d.exMoneyTotal - start());
        return per() - over % per();
    }

    /* ======================= GUI ======================= */

    public void open(Player p) {
        PlayerData d = data(p);
        boolean unlocked = unlocked(d);
        int have = insignias(d);
        Gui g = new Gui(5, "&8&lOver Lord");
        It head = It.of(Material.PLAYER_HEAD).skull(p).name(TITLE)
                .lore("", "&7Peerless 랭크 달성 시 해제됩니다.", "&775,000 유료 캐시 획득 시 휘장 한 개가 누적됩니다.", "",
                        unlocked ? "&f상태 : &a해제됨" : "&f상태 : &c잠김 &7(Peerless 랭크 필요)",
                        "&f보유 휘장 : <gradient:#FFF176:#FFB732>" + "★".repeat(have) + "</gradient>&8" + "★".repeat(10 - have) + " &7(" + have + "/10)",
                        unlocked && have < 10 ? "&f다음 휘장까지 : &6" + Msg.num(untilNext(d)) + " &7유료 캐시" : "");
        if (unlocked) head.ench(Enchantment.UNBREAKING, 1);
        g.set(4, head.hide().build());
        for (int i = 0; i < STARS.length; i++) {
            Star st = STARS[i];
            boolean claimed = d.overlordClaimed.contains(st.n());
            boolean ready = unlocked && have >= st.n() && !claimed;
            String state = claimed ? "&a수령 완료" : ready ? "&e수령 가능" : !unlocked ? "&c잠김 (Peerless 랭크 필요)" : "&c휘장 " + st.n() + "개 필요 &7(보유 " + have + "개)";
            It b = It.of(claimed ? Material.LIME_DYE : st.icon())
                    .name((claimed ? "&a" : ready ? "<gradient:#FFF176:#FFB732>" : "&7") + "★" + st.n() + (ready ? "</gradient>" : ""))
                    .lore("", st.name(), "", "&7상태 : " + state, ready ? "&e클릭하여 수령" : "");
            if (ready) b.ench(Enchantment.UNBREAKING, 1);
            g.set(SLOTS[i], b.hide().build(), e -> claim(p, st));
        }
        g.nav(p, () -> p.performCommand("메뉴")).fill().open(p);
    }

    private void claim(Player p, Star st) {
        PlayerData d = data(p);
        if (d.overlordClaimed.contains(st.n())) {
            Msg.send(p, "&c이미 수령한 보상입니다.");
            Gui.denySound(p);
            return;
        }
        if (!unlocked(d)) {
            Msg.send(p, "&c오버 로드는 Peerless 랭크 달성 시 해제됩니다.");
            Gui.denySound(p);
            return;
        }
        if (insignias(d) < st.n()) {
            Msg.send(p, "&c휘장이 부족합니다. &7(필요 " + st.n() + "개, 보유 " + insignias(d) + "개)");
            Gui.denySound(p);
            return;
        }
        d.overlordClaimed.add(st.n());
        switch (st.n()) {
            case 1 -> d.money += 500_000;
            case 2 -> It.give(p, Items.altarTicket(), 10);
            case 3 -> {
                CrateModule cm = plugin.module(CrateModule.class);
                CrateModule.Crate c = cm == null ? null : cm.crate(plugin.getConfig().getString("special-shop.echoes-voucher.crate", "TheEchoesOfNatural"));
                if (c != null) cm.giveKeys(p, c, 30, false);
            }
            case 4 -> d.backpackRows = Math.max(d.backpackRows, 3);
            case 5 -> It.give(p, Items.legendVoucher());
            case 6 -> d.money += 750_000;
            case 7 -> {
                // 전용 입장 메시지: 받은 것만으로 적용 (CoreModule 입장 처리에서 확인)
            }
            case 8 -> {
                d.crownOwned = true;
                d.crownKill = true;
            }
            case 9 -> {
                d.nickPapers.add("gradient");
                plugin.module(CoreModule.class).applyNickColor(p, 5);
            }
            case 10 -> It.give(p, Items.mythicVoucher());
            default -> {
            }
        }
        plugin.store().save(d);
        Msg.send(p, "<gradient:#FFF176:#FFB732><b>[👑 OVER LORD]</b></gradient> &f★" + st.n() + " 보상 " + st.name() + " &f을(를) 수령했습니다!");
        Msg.sound(p, "ui.toast.challenge_complete", 0.8f, 1.2f);
        Fx.burst(p.getLocation().add(0, 1.2, 0), Particle.TOTEM_OF_UNDYING, 50, 0.4);
        Fx.ring(p.getLocation().add(0, 2.2, 0), 0.6, 20, Fx.rgb(0xFFB732), 1.2f);
        later(1, () -> open(p));
    }

    /** ★7: 전용 입장 메시지 */
    public boolean hasJoinMessage(PlayerData d) {
        return d.overlordClaimed.contains(7);
    }

    /* ======================= 무기 교환권 ======================= */

    @EventHandler(priority = EventPriority.HIGH)
    public void onVoucher(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        String n = Msg.name(p.getInventory().getItemInMainHand());
        if (n.equals(Items.N_LEGEND_VOUCHER) || n.equals(Items.N_LEGEND_VOUCHER_OLD)) {
            e.setCancelled(true);
            // 얼어붙은 제왕 -> 파괴 -> 공허 -> 철학자
            // 세트별 세로 2칸 (위: 무기, 아래: 같은 세트 방어구)
            chooseAt(p, n, "&8&lLegendary 무기 교환", 4, new int[]{10, 19, 12, 21, 14, 23, 16, 25},
                    Items::sealedSword, Items::sealedWings, Items::ruinerScythe, Items::ruinerPants,
                    Items::voidBow, Items::voidChest, Items::aristotlePickaxe, Items::russellHelmet);
        } else if (n.equals(Items.N_MYTHIC_VOUCHER) || n.equals(Items.N_MYTHIC_VOUCHER_OLD)) {
            e.setCancelled(true);
            // 스킬 -> 천공 -> 프로스트
            choose(p, n, "&8&l전설의 무기 교환", Items::newCharacteristic, Items::claymoreSd, Items::frostGrave);
        }
    }

    @SafeVarargs
    private void choose(Player p, String voucher, String title, Supplier<ItemStack>... options) {
        chooseAt(p, voucher, title, 3, new int[]{11, 13, 15}, options);
    }

    @SafeVarargs
    private void chooseAt(Player p, String voucher, String title, int rows, int[] slots, Supplier<ItemStack>... options) {
        Gui g = new Gui(rows, title);
        for (int i = 0; i < options.length && i < slots.length; i++) {
            Supplier<ItemStack> opt = options[i];
            ItemStack shown = opt.get();
            var meta = shown.getItemMeta();
            java.util.List<net.kyori.adventure.text.Component> lore = meta.lore() == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(meta.lore());
            lore = Msg.append(lore, "&e클릭하여 이 무기로 교환");
            meta.lore(lore);
            shown.setItemMeta(meta);
            g.set(slots[i], shown, e -> {
                if (!takeVoucher(p, voucher)) {
                    Msg.send(p, "&c교환권이 없습니다.");
                    Gui.denySound(p);
                    p.closeInventory();
                    return;
                }
                ItemStack got = opt.get();
                It.give(p, got);
                p.closeInventory();
                Msg.send(p, "<gradient:#FFF176:#FFB732><b>[👑]</b></gradient> &f" + Items.describeShort(got) + " &f(으)로 교환했습니다!");
                BossModule boss = plugin.module(BossModule.class);
                if (Msg.nameIs(got, Items.N_CLAYMORE_SD) && boss != null) {
                    // 제단에서 얻었을 때와 같은 연출
                    boss.claymoreFanfare(p, got, "&e" + p.getName() + " &f님이 <gradient:#FFF176:#FFB732>전설의 무기 교환권</gradient>&f으로");
                } else {
                    Msg.sound(p, "ui.toast.challenge_complete", 0.8f, 1.2f);
                    Fx.burst(p.getLocation().add(0, 1.2, 0), Particle.TOTEM_OF_UNDYING, 60, 0.4);
                }
            });
        }
        g.nav(p, null).fill().open(p);
    }

    private boolean takeVoucher(Player p, String name) {
        ItemStack[] inv = p.getInventory().getStorageContents();
        for (int i = 0; i < inv.length; i++) {
            if (!Msg.nameIs(inv[i], name)) continue;
            if (inv[i].getAmount() <= 1) p.getInventory().setItem(i, null);
            else inv[i].setAmount(inv[i].getAmount() - 1);
            return true;
        }
        return false;
    }

    /* ======================= 입장 ======================= */

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        PlayerData d = data(p);
        if (!hasJoinMessage(d) || e.joinMessage() == null) return;
        if (!d.joinStyle.equals("auto") && !d.joinStyle.equals("overlord")) return;   // 프로필 설정 → 입장 메시지
        CoreModule core = plugin.module(CoreModule.class);
        net.kyori.adventure.text.Component name = core == null ? Msg.c(nickName(d.nickColor, p.getName(), "&f"))
                : core.nameTag(p, nickName(d.nickColor, p.getName(), "&f"));
        // 한 줄: ✦ 👑 OVER LORD ✦  닉네임  님이 강림하셨습니다  (양옆 금빛 깜빡임)
        e.joinMessage(Msg.fill("<gold><obf>D</obf></gold> <b><gradient:#FFF176:#FFB732>👑 OVER LORD</gradient></b> <dark_gray>|</dark_gray> %name% "
                + "<gradient:#FFE9A8:#FFB732>님이 강림하셨습니다.</gradient> <gold><obf>D</obf></gold>", "%name%", name));
        later(10, () -> {
            if (!p.isOnline()) return;
            // 강림 연출: 발밑 황금 원 + 빛기둥 + 왕관 고리, 모두에게 종소리와 액션바
            org.bukkit.Location l = p.getLocation();
            Fx.shockwave(l.clone().add(0, 0.1, 0), Fx.rgb(0xFFB732), 3.0, 12);
            Fx.pillar(l, 8, Fx.rgb(0xFFF176), Particle.END_ROD);
            Fx.ring(l.clone().add(0, 2.3, 0), 0.55, 20, Fx.rgb(0xFFB732), 1.3f);
            Fx.burst(l.clone().add(0, 1.2, 0), Particle.TOTEM_OF_UNDYING, 40, 0.4);
            for (Player o : org.bukkit.Bukkit.getOnlinePlayers()) {
                Msg.sound(o, "block.bell.use", 0.5f, 1.0f);
                if (o != p) Msg.actionBar(o, "<gradient:#FFF176:#FFB732><b>👑 OVER LORD</b></gradient> &f" + p.getName() + " <gradient:#FFE9A8:#FFB732>님이 강림하셨습니다</gradient>");
            }
            later(6, () -> {
                for (Player o : org.bukkit.Bukkit.getOnlinePlayers()) Msg.sound(o, "block.bell.use", 0.5f, 1.4f);
            });
        });
    }
}
