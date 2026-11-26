package kr.wave.core.module;

import kr.wave.core.Items;
import kr.wave.core.WaveCore;
import kr.wave.core.data.PlayerData;
import kr.wave.core.util.Cmd;
import kr.wave.core.util.Gui;
import kr.wave.core.util.Fx;
import kr.wave.core.util.It;
import kr.wave.core.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.function.Predicate;

/**
 * gui.sk    : /collection, 컬렉션 아이템 획득 공지, /resetvariable
 * sealed.sk : 시즌 아이템 소개 GUI(/arbiter 등), 파멸자의 봉인된 정수 강화 GUI
 * 시즌무기.sk : 크로노스의 칼날 강화 GUI(/chronosupgr)
 */
public class CollectionModule extends Module {

    private static final String NOT_OWNED = "&7&l상태 : &c&l획득되지 않음";

    public CollectionModule(WaveCore plugin) {
        super(plugin);
    }

    @Override
    public void enable() {
        cmd("collection", null, (s, l, a) -> {
            if (Cmd.isPlayer(s)) collection((Player) s);
        }, "컬렉션");
        cmd("resetvariable", ADMIN, (s, l, a) -> {
            OfflinePlayer t = Cmd.offline(Cmd.arg(a, 0));
            if (t == null || !"1".equals(Cmd.arg(a, 1))) {
                Msg.send(s, "&c/resetvariable <플레이어> 1");
                return;
            }
            PlayerData d = plugin.store().get(t);
            for (int i = 0; i < d.collectionAnnounced.length; i++) d.collectionAnnounced[i] = false;
            plugin.store().saveAuto(d);
            Msg.send(s, "&a[System] &7플레이어 &f" + a[0] + "&7 의 &c컬렉션 아이템 &7변수가 초기화 되었습니다.");
        });
        cmd("arbiter", null, (s, l, a) -> season(s, "S4 : Arbiter of Heaven", Gui.FILLER, Items.claymoreSd(), rules("S4", true), 0));
        cmd("arbiter2", null, (s, l, a) -> season(s, "S4 : ???", Gui.FILLER,
                It.of(Material.GOLDEN_SWORD).unbreakable().ench(Enchantment.VANISHING_CURSE, 1)
                        .name("&6&l[&a&l⚡&6&l] <gradient:#FFF200:#F6E47C:#FFFCD4><italic:false><b>[Lv.</b><b>??</b><b>] ???</b><b> </b><b></b></gradient>")
                        .lore("%nl%&7등급 : <gradient:#FFF200:#F6E47C:#FFFCD4><italic:false><b≯̷̶͢E̶̴R̷R̸O̶R̷͢/<b></b><b></b></gradient>%nl%&7???%nl%%nl%<gradient:#FFF200:#F6E47C:#FFFCD4><italic:false><b>[<obf>????</obf></b><obf><b> </b></obf><b><obf>??</obf>]</b></gradient>%nl%&e&l>> &7???%nl%&e&l>> &7???%nl%&e&l>> &7???%nl%%nl%&7공개에 필요한 획득 인원 수 : &e1 &7명").build(),
                rules("S4", true), 3));
        cmd("glacier", null, (s, l, a) -> season(s, "S6 : Glacier Storm", Gui.FILLER, Items.frostGrave(), rules("S6", true), 1));
        cmd("prologue", null, (s, l, a) -> season(s, "S4 : Inscribed", Gui.FILLER, Items.newCharacteristic(), rules("S4", false), 4));
        cmd("prologue2", null, (s, l, a) -> season(s, "S4 : Prologue", Gui.FILLER,
                It.of(Material.MUSIC_DISC_5).ench(Enchantment.VANISHING_CURSE, 1)
                        .name("<gradient:#1E2586:#C8B0B0><italic:false><b>[🌙] [Lv.?]</b><b> ???</b></gradient><gradient:#FAD4D4:#C8B0B0><italic:false><b></b></gradient>")
                        .lore("%nl%&7등급 : <gradient:#1E2586:#FAD4D4><italic:false><b>E̶̴R̷R̸O̶R̷͢/</b></gradient><gradient:#FAD4D4:#FAD4D4><italic:false><b>?</b></gradient>%nl%&7???%nl%%nl%<gradient:#1E2586:#00FBFF><italic:false><b>[<obf>새로운 시대의</obf></b><b><obf> 각인</obf></b></gradient><gradient:#00FBFF:#00FBFF><italic:false><b>]</b></gradient>%nl%<color:#313798><b>>></b></color> &7???%nl%<color:#313798><b>>></b></color> &7???%nl%%nl%&7공개에 필요한 획득 인원 수 : &e1 &7명").build(),
                rules("S4", false), 2));
        cmd("chronosupgr", null, (s, l, a) -> {
            if (Cmd.isPlayer(s)) chronosGui((Player) s);
        });
    }

    /* ======================= 컬렉션 ======================= */

    /** 컬렉션 하나: 모을 아이템들, 보상 칭호(+ 추가 보상) */
    private record Col(String key, String name, Material icon, String period, String titleKey, boolean glacierPaper,
                       java.util.List<java.util.function.Supplier<ItemStack>> items) {
    }

    private static final java.util.List<Col> COLS = java.util.List.of(
            new Col("sealed", "<gradient:#3DE8FA:#FFFFFF><bold:true><italic:false>Sealed Master Power</gradient>", Material.AMETHYST_SHARD, "2026.06.24 - 2026.07.14", "sealed", false,
                    java.util.List.of(Items::sealedSword, Items::sealedWings)),
            new Col("ruiner", "<b><gradient:#FF0000:#34342F><italic:false>The Ruiner's Grudge</gradient></b>", Material.RED_DYE, "2026.07.20 - 2026.08.14", "destroy", false,
                    java.util.List.of(Items::ruinerScythe, Items::ruinerPants)),
            new Col("void", "<b><gradient:#F9F9F9:#C2C2C2:#404247><italic:false>Whisper of the Void</gradient></b>", Material.ENDER_PEARL, "????.??.?? - ????.??.??", "void", false,
                    java.util.List.of(Items::voidBow, Items::voidChest)),
            new Col("philosopher", "<b><gradient:#2193b0:#E7F0F2><italic:false>The Philosopher's Literature</gradient></b>", Material.BOOK, "????.??.?? - ????.??.??", "philosopher", false,
                    java.util.List.of(Items::aristotlePickaxe, Items::russellHelmet)),
            new Col("inscribed", "<b><gradient:#1E2586:#FAD4D4><italic:false>Prologue of Inscribed</gradient></b>", Material.MUSIC_DISC_5, "S4 시즌", "inscribed", false,
                    java.util.List.of(Items::newCharacteristic)),
            new Col("arbiter", "<b><gradient:#FFF200:#F6E47C:#FFFCD4><italic:false>Arbiter of Heaven</gradient></b>", Material.GOLDEN_SWORD, "S4 시즌", "arbiter", false,
                    java.util.List.of(Items::claymoreSd)),
            new Col("glacier", "<b><gradient:#39C7FF:#C2FFF5:#F5DAF8><italic:false>Glacier Storm</gradient></b>", Material.BLUE_ICE, "S6 시즌", "glacier", true,
                    java.util.List.of(Items::frostGrave)));
    private static final int[] COL_SLOTS = {10, 12, 14, 16, 20, 22, 24};

    /** 아이템 하나를 구분하는 키 (이름) */
    private static String itemKey(ItemStack it) {
        return Msg.name(it);
    }

    /** 컬렉션별 아이템 키 (처음 한 번만 계산) */
    private final java.util.Map<String, java.util.List<String>> keyCache = new java.util.HashMap<>();

    private java.util.List<String> keys(Col c) {
        return keyCache.computeIfAbsent(c.key(), k -> {
            java.util.List<String> out = new java.util.ArrayList<>();
            for (var sup : c.items()) out.add(itemKey(sup.get()));
            return out;
        });
    }

    private int progress(PlayerData d, Col c) {
        int n = 0;
        for (String k : keys(c)) if (d.collected.contains(k)) n++;
        return n;
    }

    /** 지금 가진 컬렉션 아이템을 기록 (한 번 기록되면 버려도 유지) */
    private void record(Player p, PlayerData d) {
        boolean changed = false;
        java.util.Set<String> have = new java.util.HashSet<>();
        for (ItemStack it : p.getInventory().getContents()) if (it != null && it.hasItemMeta()) have.add(Msg.name(it));
        for (Col c : COLS) {
            java.util.List<String> ks = keys(c);
            for (int i = 0; i < ks.size(); i++) {
                String k = ks.get(i);
                if (have.contains(k) && d.collected.add(k)) {
                    changed = true;
                    Msg.send(p, "&d&l[컬렉션] &f" + Items.describeShort(c.items().get(i).get()) + " &f수집 완료! &7(" + Msg.plainOf(c.name()) + " " + progress(d, c) + "/" + c.items().size() + ")");
                    Msg.sound(p, "entity.player.levelup", 0.6f, 1.6f);
                    if (progress(d, c) >= c.items().size() && !d.collectionClaimed.contains(c.key()))
                        Msg.send(p, "&d&l[컬렉션] " + c.name() + " &f컬렉션을 완성했습니다! &e/컬렉션 &f에서 보상을 받으세요.");
                }
            }
        }
        if (changed) plugin.store().save(d);
    }

    private void collection(Player p) {
        tutorial(p, "collection");
        PlayerData d = data(p);
        record(p, d);
        Gui g = new Gui(5, "&8&lCollection");
        g.set(4, It.of(Material.NETHER_STAR).name("&d&l컬렉션").lore("", "&7시즌별 아이템을 모두 모으면", "&7전용 칭호북을 받을 수 있습니다.", "",
                "&8아이템을 한 번이라도 가지고 있으면 수집됩니다.").build());
        for (int i = 0; i < COLS.size(); i++) {
            Col c = COLS.get(i);
            int have = progress(d, c), total = c.items().size();
            boolean claimed = d.collectionClaimed.contains(c.key());
            boolean ready = have >= total && !claimed;
            It b = It.of(c.icon()).name(c.name()).lore("&7" + c.period(), "",
                    "&f수집 : " + (have >= total ? "&a" : "&e") + have + " &7/ " + total,
                    "&f보상 : &7칭호북 " + titleName(c.titleKey()) + (c.glacierPaper() ? " &7+ 닉네임 색상 변경권 (Glacier)" : ""),
                    "", claimed ? "&a보상 수령 완료" : ready ? "&e&l보상 수령 가능!" : "&7클릭하여 수집 현황 보기");
            if (ready || claimed) b.ench(Enchantment.UNBREAKING, 1);
            g.set(COL_SLOTS[i], b.hide().build(), e -> later(1, () -> sub(p, c)));
        }
        g.nav(p, () -> plugin.module(MenuModule.class).menu(p)).fill().open(p);
    }

    /** 다른 플레이어의 컬렉션 진행 현황 (보기 전용, 보상 수령 없음) */
    public void view(Player viewer, PlayerData d, String name, Runnable back) {
        Gui g = new Gui(5, "&8&l" + name + " 의 컬렉션");
        g.set(4, It.of(Material.PLAYER_HEAD).skull(org.bukkit.Bukkit.getOfflinePlayer(d.uuid)).name("&e" + name)
                .lore("&7컬렉션 진행 현황", "&8보기 전용").build());
        for (int i = 0; i < COLS.size(); i++) {
            Col c = COLS.get(i);
            int have = progress(d, c), total = c.items().size();
            It b = It.of(c.icon()).name(c.name()).lore("&7" + c.period(), "", "&f수집 : " + (have >= total ? "&a" : "&e") + have + " &7/ " + total, "");
            for (var sup : c.items()) {
                ItemStack it = sup.get();
                b.lore((d.collected.contains(itemKey(it)) ? "&a✔ " : "&8✘ ") + "&f" + Items.describeShort(it));
            }
            if (d.collectionClaimed.contains(c.key())) b.lore("", "&a보상 수령 완료");
            if (have >= total) b.ench(Enchantment.UNBREAKING, 1);
            g.set(COL_SLOTS[i], b.hide().build());
        }
        g.nav(viewer, back).fill().open(viewer);
    }

    private static String titleName(String key) {
        for (String[] t : Items.COLLECTION_TITLES) if (t[0].equals(key)) return t[1];
        return key;
    }

    private void sub(Player p, Col c) {
        PlayerData d = data(p);
        Gui g = new Gui(5, "&8&l" + Msg.plainOf(c.name()));
        int n = c.items().size();
        int[] slots = n == 1 ? new int[]{13} : n == 2 ? new int[]{11, 15} : new int[]{11, 13, 15};
        for (int i = 0; i < n && i < slots.length; i++) {
            ItemStack shown = c.items().get(i).get();
            boolean got = d.collected.contains(itemKey(shown));
            g.set(slots[i], withLore(shown, got ? "&7&l상태 : &a&l수집 완료" : NOT_OWNED));
        }
        int have = progress(d, c);
        boolean claimed = d.collectionClaimed.contains(c.key());
        boolean ready = have >= n && !claimed;
        ItemStack book = Items.collectionTitleBook(c.titleKey(), Msg.plainOf(c.name()));
        It reward = It.of(ready ? Material.ENCHANTED_BOOK : claimed ? Material.LIME_DYE : Material.BOOK).name("&d&l컬렉션 보상")
                .lore("", "&f- 칭호북 " + titleName(c.titleKey()), c.glacierPaper() ? "&f- 닉네임 색상 변경권 <gradient:#39C7FF:#C2FFF5>(Glacier)</gradient>" : "",
                        "", "&f수집 : " + have + " &7/ " + n, "",
                        claimed ? "&a수령 완료" : ready ? "&e&l클릭하여 보상 받기" : "&c모든 아이템을 수집해야 받을 수 있습니다.");
        if (ready) reward.ench(Enchantment.UNBREAKING, 1);
        g.set(31, reward.hide().build(), e -> {
            if (d.collectionClaimed.contains(c.key())) {
                Msg.send(p, "&c이미 보상을 받았습니다.");
                Gui.denySound(p);
                return;
            }
            if (progress(d, c) < n) {
                Msg.send(p, "&c아직 모든 아이템을 수집하지 않았습니다. &7(" + progress(d, c) + "/" + n + ")");
                Gui.denySound(p);
                return;
            }
            d.collectionClaimed.add(c.key());
            plugin.store().save(d);
            if (book != null) It.give(p, book);
            if (c.glacierPaper()) It.give(p, Items.nickPaper("glacier", "Glacier Storm 컬렉션 보상입니다."));
            Msg.send(p, "&d&l[컬렉션] " + c.name() + " &f컬렉션 보상을 받았습니다!");
            Msg.sound(p, "ui.toast.challenge_complete", 0.8f, 1.2f);
            Fx.burst(p.getLocation().add(0, 1.2, 0), Particle.TOTEM_OF_UNDYING, 60, 0.4);
            Fx.helix(p, Fx.rgb(0xD580FF), Fx.rgb(0xFFFFFF), 0.8, 2.4, 24);
            later(1, () -> sub(p, c));
        });
        // 천공의 심판자 클레이모어 · 프로스트 그레이브: 전용 킬 알림 / 파티클 켜고 끄기
        String weapon = c.key().equals("arbiter") ? "claymore" : c.key().equals("glacier") ? "frost" : null;
        if (weapon != null) {
            String wname = weapon.equals("claymore") ? "천공의 심판자 클레이모어" : "프로스트 그레이브";
            boolean log = !d.killLogOff.contains(weapon), fx = !d.fxOff.contains(weapon);
            It logBtn = It.of(log ? Material.NAME_TAG : Material.PAPER).name((log ? "&a&l" : "&7&l") + "전용 킬 알림 : " + (log ? "켜짐" : "꺼짐"))
                    .lore("", "&7" + wname + "&7로 처치하면 나오는", "&7무기 전용 두 줄 킬 알림입니다.", "&7끄면 일반 킬 알림으로 나옵니다.", "",
                            "&e» 클릭 &f" + (log ? "끄기" : "켜기"));
            if (log) logBtn.ench(Enchantment.UNBREAKING, 1);
            g.set(29, logBtn.hide().build(), e -> {
                if (!d.killLogOff.remove(weapon)) d.killLogOff.add(weapon);
                plugin.store().save(d);
                boolean now = !d.killLogOff.contains(weapon);
                Msg.send(p, "&e[!] &7" + wname + " &7전용 킬 알림이" + (now ? " &a켜졌습니다." : " &c꺼졌습니다. &7(일반 킬 알림으로 표시)"));
                Msg.sound(p, now ? "entity.player.levelup" : "block.note_block.bass", 0.6f, now ? 1.8f : 1f);
                later(1, () -> sub(p, c));
            });
            It fxBtn = It.of(fx ? Material.BLAZE_POWDER : Material.GUNPOWDER).name((fx ? "&a&l" : "&7&l") + "무기 파티클 : " + (fx ? "켜짐" : "꺼짐"))
                    .lore("", "&7" + wname + "&7를 들고 있을 때 · 타격할 때", "&7나오는 파티클 연출입니다.", "&7끄면 능력은 그대로, 파티클만 나오지 않습니다.", "",
                            "&e» 클릭 &f" + (fx ? "끄기" : "켜기"));
            if (fx) fxBtn.ench(Enchantment.UNBREAKING, 1);
            g.set(33, fxBtn.hide().build(), e -> {
                if (!d.fxOff.remove(weapon)) d.fxOff.add(weapon);
                plugin.store().save(d);
                boolean now = !d.fxOff.contains(weapon);
                Msg.send(p, "&e[!] &7" + wname + " &7파티클이" + (now ? " &a켜졌습니다." : " &c꺼졌습니다. &7(능력은 그대로)"));
                Msg.sound(p, now ? "entity.player.levelup" : "block.note_block.bass", 0.6f, now ? 1.8f : 1f);
                later(1, () -> sub(p, c));
            });
        }
        g.nav(p, () -> collection(p)).fill().open(p);
    }

    private static ItemStack withLore(ItemStack it, String extra) {
        ItemMeta meta = it.getItemMeta();
        meta.lore(Msg.append(meta.lore(), extra));
        it.setItemMeta(meta);
        return it;
    }

    /** 컬렉션 아이템을 처음 얻으면 전체 공지 (1초마다 확인) */
    @Override
    public void secondTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = data(p);
            record(p, d);
            if (d.seedNewAnnounce) seedAnnounce(p, d);
            for (Ann a : ANNS) announce(p, d, a);
        }
    }

    /** 전체 공지할 컬렉션 아이템: 공지 칸 번호, 알아보는 방법, 줄 색, 글자 색, 머리 색, 이름, 조사, 연출 색 */
    private record Ann(int idx, java.util.function.Predicate<ItemStack> is, String line, String text, String head, String fancy, String josa, int fx) {
    }

    private static java.util.function.Predicate<ItemStack> unbreakable(Material m, String plainName) {
        return it -> it.getType() == m && it.getItemMeta().isUnbreakable() && Msg.nameIs(it, plainName);
    }

    private static final List<Ann> ANNS = List.of(
            new Ann(0, unbreakable(Material.GOLDEN_SWORD, Items.C_CLAYMORE), "&a", "&6", "&a&l[!] ",
                    "&6&l[&a&l⚡&6&l] &c&l[Lv.&kjs&c&l] &4&l천공의 심판자 클레이모어", "를", 0xFFF200),
            new Ann(4, it -> it.getType() == Material.DIAMOND_SWORD && Msg.nameHas(it, Items.N_FROST), "&b", "&f", "&b&l[!] ",
                    Items.FROST_NAME, "를", 0x39C7FF),
            new Ann(5, it -> it.getType() == Material.MUSIC_DISC_5 && Items.isNewChar(Msg.name(it)), "&9", "&f", "&d&l[!] ",
                    Items.NEW_CHAR_NAME, "을", 0x8C93FF));

    private static boolean has(Player p, Ann a) {
        for (ItemStack it : p.getInventory().getContents())
            if (it != null && it.hasItemMeta() && a.is().test(it)) return true;
        return false;
    }

    /** 예전 데이터: 새로 생긴 공지 칸은 이미 가진(인벤토리 · 엔더 상자 · 배낭) 아이템이면 공지 없이 '공지함' 처리 */
    private void seedAnnounce(Player p, PlayerData d) {
        d.seedNewAnnounce = false;
        java.util.List<ItemStack> all = new java.util.ArrayList<>();
        java.util.Collections.addAll(all, p.getInventory().getContents());
        java.util.Collections.addAll(all, p.getEnderChest().getContents());
        java.util.Collections.addAll(all, d.backpack);
        for (Ann a : ANNS) {
            if (a.idx() < 4 || d.collectionAnnounced[a.idx()]) continue;
            for (ItemStack it : all) if (it != null && it.hasItemMeta() && a.is().test(it)) d.collectionAnnounced[a.idx()] = true;
        }
        plugin.store().saveAuto(d);
    }

    private void announce(Player p, PlayerData d, Ann a) {
        if (d.collectionAnnounced[a.idx()] || !has(p, a)) return;
        d.collectionAnnounced[a.idx()] = true;
        plugin.store().save(d);
        String line = a.line(), text = a.text(), fancy = a.fancy(), josa = a.josa();
        String bar = line + "-----------------------------------------------------";
        String head = a.head();
        Msg.broadcast(bar + "%nl%" + head + text + "플레이어 &b" + p.getName() + " " + text + "님이 " + fancy + " " + text + josa + " 획득했습니다!%nl%" + bar);
        Msg.send(p, "&6&l[!] &a&l컬렉션 아이템 " + fancy + " &a&l" + josa + " 획득했습니다! /collection을 입력하여 수집 진도를 확인하세요.");
        Msg.soundAt(p.getLocation(), "block.beacon.activate", 10, 1);
        Fx.lightning(p.getLocation());
        // 컬렉션 획득: 빛기둥 + 큰 충격파 + 토템
        org.bukkit.Color c = Fx.rgb(a.fx());
        Fx.pillar(p.getLocation(), 15, c, Particle.END_ROD);
        Fx.shockwave(p.getLocation().add(0, 0.1, 0), c, 6, 14);
        Fx.burst(p.getLocation().add(0, 1, 0), Particle.TOTEM_OF_UNDYING, 120, 0.6);
    }

    /* ======================= 시즌 아이템 소개 GUI ======================= */

    private static String rules(String season, boolean killMsg) {
        return "&8&l>> &7사망해도 아이템이 &e드롭되지 않습니다&7.%nl%&8&l>> &7시즌이 지나도 아이템이 &f계속 유지&7되며, &f" + season + " &7시즌에서만 획득이 가능합니다.%nl%&8&l>> &7실수로 아이템이 드롭되는 것을 방지하기 위해 인벤토리에서 아이템을 &e버릴 수 없습니다&7.%nl%&8&l>> &7"
                + (killMsg ? "해당 아이템은 &d전용 처치 알림&7이 있습니다." : "&c보스형 몬스터&7 처치 또는 &c던전 보스&7를 처치하면 일정 확률로 획득할 수 있습니다.");
    }

    /** kind: 0 arbiter, 1 glacier, 2 prologue2, 3 arbiter2, 4 prologue(inscribed) */
    private void season(org.bukkit.command.CommandSender s, String title, Material pane, ItemStack item, String rules, int kind) {
        if (!(s instanceof Player p)) return;
        Gui g = new Gui(5, title);
        for (int i = 0; i <= 8; i++) g.set(i, It.pane(pane, "&e"));
        for (int i = 36; i <= 44; i++) g.set(i, It.pane(pane, "&e"));
        g.set(22, item);
        g.set(20, It.of(Material.BOOK).name("&6스페셜 룰").lore(rules).build());
        ItemStack paper = kind <= 1 || kind == 3 ? It.of(Material.PAPER).name("&a전용 처치 알림 미리보기").build()
                : It.of(Material.PAPER).name("&c다음 액트로 이동하기").lore("&7S4 : ?").build();
        g.set(40, It.close(), e -> p.closeInventory());
        g.set(24, paper, e -> {
            p.closeInventory();
            switch (kind) {
                case 0 -> {
                    Msg.send(p, " ");
                    Msg.send(p, "<yellow><obf>D</obf></yellow> &7" + p.getName() + " &8» &7Test <yellow><obf>D</obf></yellow>");
                    Msg.send(p, "<yellow><obf>D</obf></yellow> &6&l[&a&l⚡&6&l] <gradient:#FFF200:#F6E47C:#FFFCD4><italic:false><b>[Lv.</b><obf><b>sd</b></obf><b>] 천</b><b>공의 </b><b>심판자 클레이모어</b></gradient> &7(x y z) <yellow><obf>D</obf></yellow>");
                    Msg.send(p, " ");
                }
                case 1 -> {
                    Msg.send(p, " ");
                    Msg.send(p, "<color:#B0E4F8><obf>D</obf></color> &7" + p.getName() + " &8» &7Test <color:#B0E4F8><obf>D</obf></color>");
                    Msg.send(p, "<color:#B0E4F8><obf>D</obf></color> <gradient:#39C7FF:#D2FFF8:#00FFFF><italic:false><b>[❆] [Lv.</b><obf><b>D</b></obf><obf><b>D</b></obf><b>] </b><b>프로스트 그레이브</b></gradient> &7(x y z) <color:#B0E4F8><obf>D</obf></color>");
                    Msg.send(p, " ");
                }
                case 2 -> Msg.send(p, "&c아직은 ??? 에 접근할 수 없습니다. 획득 인원 1명이 더 필요합니다.");
                case 3 -> Msg.send(p, "&c아직은 접근할 수 없습니다. 획득 인원 1명이 더 필요합니다.");
                case 4 -> later(1, () -> p.performCommand("arbiter2"));
                default -> {
                }
            }
        });
        g.open(p);
    }

    /* ======================= 크로노스의 칼날 강화 ======================= */

    private static final String SHARD = "<gradient:#BFA66E:#FFE2E2:#B2D6B6><italic:false><b>[Time] 시간의 파편</b></gradient>";
    private static final String BLADE = "<gradient:#BFA66E:#FFE2E2:#B2D6B6><italic:false><b>[Time] [L</b><b>v.↗</b><b>] 크로노스의 칼날</b></gradient>";

    /** 강화 단계별 표시용: 이번 강화로 새로 얻는 능력 */
    private static final String[] CHRONO_NEW = {"&f날카로움 5", "&e네더라이트화", "&6타격 이펙트 &7(나약함 I)", "&c날카로움 6",
            "&4타격 이펙트 &7(확률적으로 추가 데미지 1.25x)", "<gradient:#BFA66E:#B2D6B6><b>모래 폭풍</b></gradient> &7+ 이터널 등급"};
    private static final String[] STAGE_LABEL = {"&7+0", "&f+1", "&e+2", "&6+3", "&c+4", "&4+5", "<color:#CBC1A9><b>MAX</b></color>"};

    /* 단계별 재료: 시간의 파편 수, 추가 재료 (from = 현재 단계) */
    private static final int[] CHRONO_SHARDS = {5, 10, 15, 20, 25, 30};
    private static final Material[][] CHRONO_EXTRA = {
            {}, {Material.NETHERITE_SCRAP}, {Material.ENDER_EYE}, {Material.EXPERIENCE_BOTTLE},
            {Material.END_CRYSTAL, Material.ENCHANTED_GOLDEN_APPLE}, {Material.NETHER_STAR, Material.HEART_OF_THE_SEA}};
    private static final int[][] CHRONO_EXTRA_N = {{}, {3}, {10}, {128}, {32, 1}, {1, 1}};
    private static final String[][] CHRONO_EXTRA_NAME = {{}, {"네더라이트 파편"}, {"엔더의 눈"}, {"경험치 병"},
            {"엔드 수정", "마법이 부여된 황금 사과"}, {"네더의 별", "바다의 심장"}};

    /** 칼날 올리는 칸 / 강화 버튼 / 결과 미리보기 */
    private static final int C_INPUT = 11, C_BUTTON = 13, C_RESULT = 15;

    /**
     * 크로노스의 칼날 강화: 플레이어가 강화할 칼날을 직접 올림
     * 아래 인벤토리에서 칼날을 클릭하면 올라가고, 올린 칼날을 클릭하면 돌아옴. 창을 닫으면 자동으로 돌려줌.
     */
    private void chronosGui(Player p) {
        Gui g = new Gui(4, "&8&l크로노스의 칼날 강화");
        g.on(C_INPUT, e -> {
            ItemStack placed = g.get("blade");
            ItemStack cursor = e.getCursor();
            if (cursor != null && Items.chronosStage(cursor) >= 0) {   // 커서로 들고 와서 내려놓기
                e.getView().setCursor(placed);
                g.put("blade", cursor.clone());
            } else if (placed != null) {                                 // 올린 칼날 돌려받기
                g.put("blade", null);
                It.give(p, placed);
            } else return;
            Msg.sound(p, "block.armor_stand.place", 0.6f, 1.3f);
            chronosRender(g, p);
        }).quiet(C_INPUT);
        g.on(C_BUTTON, e -> chronosUpgrade(g, p)).quiet(C_BUTTON);
        g.bottomClick(e -> {
            ItemStack it = e.getCurrentItem();
            if (Items.chronosStage(it) < 0) return;
            ItemStack old = g.get("blade");
            g.put("blade", it.clone());
            e.setCurrentItem(old);   // 클릭한 칸을 비움 (이미 올린 칼날이 있으면 그 자리로 교체)
            Msg.sound(p, "block.armor_stand.place", 0.6f, 1.3f);
            chronosRender(g, p);
        });
        g.onClose(e -> {
            ItemStack placed = g.get("blade");
            g.put("blade", null);
            if (placed != null && e.getPlayer() instanceof Player pl) It.give(pl, placed);
        });
        g.nav(p, null);
        chronosRender(g, p);
        g.open(p);
    }

    /** 올린 칼날에 맞춰 화면 다시 그리기 (창은 그대로) */
    private void chronosRender(Gui g, Player p) {
        for (int i = 0; i < g.last(); i++) g.set(i, null);
        ItemStack blade = g.get("blade");
        int stage = blade == null ? -1 : Items.chronosStage(blade);
        int shards = count(p, it -> Msg.nameIs(it, Items.N_TIME_SHARD));
        g.set(4, It.of(Material.CLOCK).name(BLADE + " &f강화")
                .lore("", "&f1. &7아래 인벤토리에서 &f칼날을 클릭&7해 올립니다.", "&f2. &7가운데 &f모루&7를 눌러 강화합니다.",
                        "", "&7재료는 인벤토리에서 자동으로 사용됩니다.", "&7창을 닫으면 올린 칼날은 돌려받습니다.", "",
                        "&f보유 시간의 파편 : &e" + shards + "개").build());
        // 왼쪽: 올린 칼날 (비어 있으면 빈 칸)
        if (blade != null) {
            ItemStack show = blade.clone();
            ItemMeta m = show.getItemMeta();
            java.util.List<net.kyori.adventure.text.Component> lore = m.lore() == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(m.lore());
            lore = Msg.append(lore, "&8━━━━━━━━━━━━━━", "&f올린 칼날 : " + STAGE_LABEL[stage], "&7클릭하면 인벤토리로 돌아갑니다.");
            m.lore(lore);
            show.setItemMeta(m);
            g.set(C_INPUT, show);
        }
        // 가운데: 강화 버튼, 오른쪽: 결과 미리보기, 아래 줄: 필요한 아이템
        if (stage < 0) {
            g.set(C_BUTTON, It.of(Material.ANVIL).name("&7강화하기").lore("", "&c왼쪽 칸에 칼날을 올려 주세요.", "&8(아래 인벤토리에서 칼날 클릭)").build());
            g.set(C_RESULT, It.of(Material.GRAY_DYE).name("&7결과 미리보기").lore("", "&8칼날을 올리면 강화 결과가 표시됩니다.").build());
            g.set(22, It.of(Material.BOOK).name("&6필요한 아이템").lore("", "&8칼날을 올리면 필요한 아이템이 표시됩니다.").build());
        } else if (stage >= 6) {
            g.set(C_BUTTON, It.of(Material.ANVIL).name("<color:#CBC1A9><b>최고 단계</b></color>").lore("", "&7이미 이터널 등급입니다.").build());
            g.set(C_RESULT, It.of(Material.LIME_DYE).name("<color:#CBC1A9><b>MAX</b></color>").lore("", "&7더 강화할 수 없습니다.").build());
        } else {
            boolean ok = shards >= CHRONO_SHARDS[stage];
            java.util.List<ItemStack> mats = new java.util.ArrayList<>();
            mats.add(matIcon(Items.timeShard(), "시간의 파편", shards, CHRONO_SHARDS[stage]));
            for (int i = 0; i < CHRONO_EXTRA[stage].length; i++) {
                Material mt = CHRONO_EXTRA[stage][i];
                int have = count(p, it -> it.isSimilar(new ItemStack(mt)));
                ok &= have >= CHRONO_EXTRA_N[stage][i];
                mats.add(matIcon(new ItemStack(mt), CHRONO_EXTRA_NAME[stage][i], have, CHRONO_EXTRA_N[stage][i]));
            }
            int start = 22 - (mats.size() - 1);
            for (int i = 0; i < mats.size(); i++) g.set(start + i * 2, mats.get(i));
            g.put("ok", ok);
            g.set(C_BUTTON, It.of(Material.ANVIL).name(ok ? "&a&l» 강화하기" : "&c&l재료가 부족합니다")
                    .lore("", "&f" + Msg.plainOf(STAGE_LABEL[stage]) + " &7→ " + STAGE_LABEL[stage + 1],
                            "", ok ? "&e클릭하여 강화" : "&7아래 줄의 &c✘ &7재료를 모아 주세요.").build());
            if (ok) {
                ItemStack btn = g.getInventory().getItem(C_BUTTON);
                ItemMeta bm = btn.getItemMeta();
                bm.setEnchantmentGlintOverride(true);
                btn.setItemMeta(bm);
            }
            ItemStack res = Items.chronos(stage + 1);
            ItemMeta rm = res.getItemMeta();
            java.util.List<net.kyori.adventure.text.Component> rl = rm.lore() == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(rm.lore());
            rl = Msg.append(rl, "&8━━━━━━━━━━━━━━", "&f결과 미리보기 : " + STAGE_LABEL[stage + 1], "&f새로 얻는 능력 : " + CHRONO_NEW[stage]);
            rm.lore(rl);
            res.setItemMeta(rm);
            g.set(C_RESULT, res);
        }
        g.set(12, It.of(Material.ARROW).name("&7→").build());
        g.set(14, It.of(Material.ARROW).name("&7→").build());
        g.fill();
        if (blade == null) g.set(C_INPUT, null);   // 빈 칸으로 두어 "여기에 올린다"는 것이 보이게
    }

    /** 재료 아이콘: 이름 + 보유/필요 (충분하면 초록 ✔, 부족하면 빨강 ✘) */
    private static ItemStack matIcon(ItemStack base, String name, int have, int need) {
        boolean ok = have >= need;
        ItemStack it = base.clone();
        it.setAmount(Math.max(1, Math.min(64, need)));
        ItemMeta m = it.getItemMeta();
        m.displayName(Msg.item((ok ? "&a✔ " : "&c✘ ") + "&f" + name + " &7x" + need));
        m.lore(Msg.lore("", "&f보유 : " + (ok ? "&a" : "&c") + Msg.num(have) + " &7/ " + Msg.num(need), ok ? "&a충분합니다." : "&c" + Msg.num(need - have) + "개 더 필요합니다."));
        m.addItemFlags(org.bukkit.inventory.ItemFlag.values());
        it.setItemMeta(m);
        return it;
    }

    private void chronosUpgrade(Gui g, Player p) {
        ItemStack blade = g.get("blade");
        int from = blade == null ? -1 : Items.chronosStage(blade);
        if (from < 0 || from >= 6) {
            Gui.denySound(p);
            Msg.send(p, from < 0 ? "&c먼저 강화할 칼날을 왼쪽 칸에 올려 주세요." : "&7이미 최고 단계입니다.");
            return;
        }
        boolean ok = count(p, it -> Msg.nameIs(it, Items.N_TIME_SHARD)) >= CHRONO_SHARDS[from];
        for (int i = 0; i < CHRONO_EXTRA[from].length; i++) {
            Material mt = CHRONO_EXTRA[from][i];
            ok &= count(p, it -> it.isSimilar(new ItemStack(mt))) >= CHRONO_EXTRA_N[from][i];
        }
        if (!ok) {
            Msg.send(p, "&c레벨업에 필요한 재료가 부족합니다.");
            Gui.denySound(p);
            return;
        }
        remove(p, it -> Msg.nameIs(it, Items.N_TIME_SHARD), CHRONO_SHARDS[from]);
        for (int i = 0; i < CHRONO_EXTRA[from].length; i++) {
            Material mt = CHRONO_EXTRA[from][i];
            remove(p, it -> it.isSimilar(new ItemStack(mt)), CHRONO_EXTRA_N[from][i]);
        }
        g.put("blade", Items.chronos(from + 1));   // 올린 칼날이 그 자리에서 강화됨 (닫으면 돌려받음)
        String[] labels = {"&7(+0)", "&f(+1)", "&e(+2)", "&6(+3)", "&c(+4)", "&4(+5)", "<color:#CBC1A9><b>(MAX)</b></color>"};
        Msg.send(p, "&8-----------------------------------------------------");
        Msg.send(p, " ");
        Msg.send(p, "            " + BLADE + " " + labels[from] + " &8&l>> " + labels[from + 1]);
        Msg.send(p, " ");
        Msg.send(p, from == 5 ? "                         <color:#CBC1A9><b>이터널에 성공했습니다.</b></color>" : "                         &e&l레벨업에 성공했습니다!");
        Msg.send(p, " ");
        Msg.send(p, "&8-----------------------------------------------------");
        Msg.sound(p, "minecraft:ui.toast.challenge_complete", 1, from == 5 ? 2 : 1.5f);
        Msg.sound(p, "block.anvil.use", 0.6f, 1.2f);
        // 시간 강화: 모래 회오리 (이터널은 더 크게)
        org.bukkit.Color sand = Fx.rgb(0xBFA66E), pink = Fx.rgb(0xFFE2E2), green = Fx.rgb(0xB2D6B6);
        Fx.helix(p, sand, green, 0.9, 2.6, 30);
        Fx.block(p.getLocation().add(0, 1, 0), Particle.FALLING_DUST, Material.SAND.createBlockData(), 40, 0.6);
        if (from == 5) {
            Fx.tornado(p, new org.bukkit.Color[]{sand, pink, green}, Particle.WHITE_ASH, 60);
            Fx.burst(p.getLocation().add(0, 1, 0), Particle.TOTEM_OF_UNDYING, 120, 0.6);
            Fx.shockwave(p.getLocation().add(0, 0.1, 0), sand, 5, 12);
        }
        chronosRender(g, p);
    }

    @Override
    public void disable() {
        // 서버 종료/리로드: 강화 창을 닫아 올린 칼날을 돌려줌
        for (Player p : Bukkit.getOnlinePlayers()) {
            var top = p.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof Gui g && g.get("blade") != null) p.closeInventory();
        }
    }

    private static int count(Player p, Predicate<ItemStack> test) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) if (it != null && test.test(it)) n += it.getAmount();
        return n;
    }

    private static void remove(Player p, Predicate<ItemStack> test, int amount) {
        ItemStack[] c = p.getInventory().getStorageContents();
        for (int i = 0; i < c.length && amount > 0; i++) {
            ItemStack it = c[i];
            if (it == null || !test.test(it)) continue;
            int take = Math.min(amount, it.getAmount());
            it.setAmount(it.getAmount() - take);
            amount -= take;
            if (it.getAmount() <= 0) c[i] = null;
        }
        p.getInventory().setStorageContents(c);
    }

    /* ======================= 파멸자의 봉인된 정수 ======================= */

    @EventHandler(priority = EventPriority.HIGH)
    public void onEssence(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType() != Material.ENCHANTED_BOOK || !Msg.nameHas(hand, Items.N_RUINER_ESSENCE)) return;
        e.setCancelled(true);
        Gui g = new Gui(6, "&8&l파멸자의 봉인된 정수");
        g.set(4, It.of(Material.ENCHANTED_BOOK).name("&c적용할 검을 클릭하세요").lore("&7화염 III, 약탈 IV, 휩쓸기 V 가 부여됩니다.").build());
        ItemStack[] inv = p.getInventory().getStorageContents();
        for (int i = 0; i < 36; i++) {
            final int slot = i;
            if (inv[i] == null) continue;
            g.set(9 + i, inv[i].clone(), ev -> {
                ItemStack target = p.getInventory().getItem(slot);
                if (target == null || !target.getType().name().endsWith("_SWORD")) {
                    Msg.send(p, "&c검만 강화할 수 있습니다!");
                    return;
                }
                ItemStack essence = p.getInventory().getItemInMainHand();
                if (!Msg.nameHas(essence, Items.N_RUINER_ESSENCE)) {
                    p.closeInventory();
                    return;
                }
                target.addUnsafeEnchantment(Enchantment.FIRE_ASPECT, 3);
                target.addUnsafeEnchantment(Enchantment.LOOTING, 4);
                target.addUnsafeEnchantment(Enchantment.SWEEPING_EDGE, 5);
                p.getInventory().setItem(slot, target);
                It.takeOneFromHand(p);
                p.closeInventory();
                Msg.send(p, "&a적용되었습니다!");
                Msg.soundAt(p.getLocation(), "block.enchantment_table.use", 1, 1);
                // 파멸의 정수 주입: 붉은 나선 + 화염
                Fx.helix(p, Fx.rgb(0xFF0000), Fx.rgb(0x530427), 0.8, 2.4, 30);
                Fx.p(p.getLocation().add(0, 1, 0), Particle.FLAME, 40, 0.5, 0.05);
                Fx.p(p.getLocation().add(0, 1.2, 0), Particle.ENCHANT, 60, 0.6, 1.0);
            });
        }
        g.nav(p, null).fill().open(p);
        Msg.send(p, "&a파멸자의 봉인된 정수를 사용합니다. 적용할 아이템을 클릭하세요.");
    }
}
