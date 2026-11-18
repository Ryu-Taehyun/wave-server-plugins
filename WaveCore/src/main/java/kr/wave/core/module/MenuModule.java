package kr.wave.core.module;

import kr.wave.core.WaveCore;
import kr.wave.core.data.GlobalData;
import kr.wave.core.data.PlayerData;
import kr.wave.core.util.Cmd;
import kr.wave.core.util.Gui;
import kr.wave.core.util.It;
import kr.wave.core.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** shop.sk : /메뉴, /워프, 프로필(킬 효과, 닉네임 색상, 랭크, 칭호), Shift+F, 30분 공지, /report */
public class MenuModule extends Module {

    private final Map<UUID, Long> reportCooldown = new HashMap<>();
    private int ticks;

    public MenuModule(WaveCore plugin) {
        super(plugin);
    }

    @Override
    public void enable() {
        cmd("메뉴", null, (s, l, a) -> {
            if (Cmd.isPlayer(s)) menu((Player) s);
        }, "menu");
        cmd("워프", null, (s, l, a) -> {
            if (Cmd.isPlayer(s)) warp((Player) s);
        }, "warp");
        cmd("report", null, this::reportCmd);
        cmd("공지테스트", ADMIN, (s, l, a) -> announceNext(), "announcetest");
    }

    /** 메뉴 버튼으로 명령어 실행 (performCommand 는 명령어 차단 이벤트를 거치지 않으므로 여기서 직접 확인) */
    private void run(Player p, String command) {
        if (blocked(p)) return;   // 전투 중 / 천공의 제단 (performCommand 는 명령어 차단 이벤트를 거치지 않음)
        later(1, () -> p.performCommand(command));
    }

    /** 천공의 제단 제한 중이면 메뉴 사용 불가 */
    private boolean blocked(Player p) {
        CombatTagModule ct = plugin.module(CombatTagModule.class);
        if (ct != null && ct.tagged(p)) {
            p.closeInventory();
            Msg.send(p, "&c&l[!] &c전투 중에는 메뉴를 사용할 수 없습니다.");
            Msg.sound(p, "entity.villager.no", 0.8f, 1f);
            return true;
        }
        BossModule b = plugin.module(BossModule.class);
        if (b == null || !b.restricted(p)) return false;
        p.closeInventory();
        Msg.send(p, "&c천공의 제단에서는 메뉴를 사용할 수 없습니다.");
        Msg.sound(p, "entity.villager.no", 0.8f, 1f);
        return true;
    }

    /* ======================= 메뉴 ======================= */

    private ItemStack infoHead(Player p) {
        PlayerData d = data(p);
        GlobalData.Team team = global().teamOf(p.getUniqueId());
        return It.of(Material.PLAYER_HEAD).skull(p).name("&e" + p.getName())
                .lore("&7랭크 : " + plugin.hooks().prefix(p) + "%nl%&7소속된 팀 : " + (team == null ? "&c없음" : Msg.esc(team.display))
                        + "%nl%&7레벨 : &e" + d.level
                        + "%nl%&7보유 중인 캐시 : &e" + Msg.num(d.money) + "원%nl%&7보유 중인 유료 캐시 : &6" + Msg.num(d.exMoney) + "원").build();
    }

    public void menu(Player p) {
        if (blocked(p)) return;
        tutorial(p, "menu");
        PlayerData d = data(p);
        Gui g = new Gui(4, "&8&l서버 메뉴");
        g.set(4, infoHead(p));
        // 1줄: 보관/이동/거래
        g.set(10, It.of(Material.BELL).name("&e&l우편함").lore(d.mailbox.isEmpty()
                ? "&8아이템 저장%nl%&c&l발송된 우편이 없습니다." : "&8아이템 저장%nl%&a&l확인하지 않은 우편이 있습니다! &7(" + d.mailbox.size() + "개)").build(), e -> run(p, "우편함"));
        g.set(11, It.of(Material.COMPASS).name("&a&l이동 메뉴 열기").lore("&8차원 이동%nl%&7클릭하면 워프 메뉴가 열립니다.").build(), e -> later(1, () -> warp(p)));
        g.set(12, It.of(Material.IRON_INGOT).name("&a&l상점 메뉴 열기").lore("&8거래%nl%&7다양한 아이템을 구매할 수 있습니다.").build(), e -> run(p, "상점"));
        g.set(13, It.of(Material.GOLD_INGOT).name("&d&l유료 캐시 상점").lore("&8거래%nl%&7유료 캐시로 아이템을 구매할 수 있습니다").build(), e -> run(p, "yurocash"));
        g.set(14, It.of(Material.ENDER_EYE).name("<b><gradient:#99E982:#ACFDCD><b><italic:false>아이템 뽑기</b></gradient></b>").lore("&8상점%nl%&7아이템 뽑기를 할 수 있습니다.").build(), e -> {
            CrateModule cm = plugin.module(CrateModule.class);
            if (!blocked(p) && cm != null) later(1, () -> cm.openMenu(p));
        });
        g.set(15, It.of(Material.EMERALD).name("&6&l유저 장터").lore("&8거래%nl%&7유저들끼리 아이템을 사고 팔 수 있습니다.").build(), e -> run(p, "장터"));
        g.set(16, It.of(Material.CHEST).name("&a&l배낭 열기").lore("&8아이템 저장%nl%&7개인 배낭을 오픈합니다.").build(), e -> run(p, "backpack"));
        // 2줄: 나 / 콘텐츠
        g.set(19, It.of(Material.NAME_TAG).name("&a&l프로필 설정").lore("&8개인 설정%nl%&7치장품, 레벨 등등을 확인할 수 있습니다.").build(), e -> later(1, () -> profile(p)));
        g.set(20, It.of(Material.BOOK).name("&c&l플레이어 레벨").lore("&8도전 과제%nl%&7플레이어 레벨을 확인합니다.").build(), e -> run(p, "levelstats"));
        g.set(21, It.of(Material.NETHER_STAR).name("&d&l컬렉션").lore("&8도전 과제%nl%&7수집 진행 상황을 확인할 수 있습니다.").build(), e -> run(p, "컬렉션"));
        g.set(22, It.of(Material.EXPERIENCE_BOTTLE).name("<b><gradient:#FFD84D:#FF9F1C><italic:false>프리미엄 패스</gradient></b>").lore("&8시즌%nl%&7레벨 보상, 일일/시즌 퀘스트, 교환 상점을 확인합니다.").build(), e -> {
            tutorial(p, "pass");
            run(p, "pass");
        });
        boolean dailyShop = Bukkit.getPluginManager().isPluginEnabled("DailyShop");
        g.set(23, It.of(Material.GOLD_NUGGET).name("&e&l일일 상점").lore("&8거래%nl%&7매일 바뀌는 특가 상품을 구매합니다." + (dailyShop ? "" : "%nl%&c현재 이용할 수 없습니다.")).build(), e -> {
            if (dailyShop) run(p, "dailyshop");
            else Msg.sound(p, "entity.villager.no", 0.8f, 1f);
        });
        BossModule boss = plugin.module(BossModule.class);
        It altar = It.of(Material.END_CRYSTAL).name("<b><gradient:#FFF200:#F6E47C:#FFFCD4><italic:false>천공의 제단</gradient></b>")
                .lore("&8보스%nl%&7천공의 심판자 정보와 입장 메뉴입니다.%nl%%nl%" + (boss == null ? "" : boss.statusLine()));
        if (boss != null && boss.descended()) altar.ench(org.bukkit.enchantments.Enchantment.INFINITY, 1);
        g.set(24, altar.build(), e -> {
            if (boss != null) later(1, () -> boss.openAltar(p, () -> menu(p)));
        });
        if (!d.tutorialFinished) {
            g.set(35, It.of(Material.WRITABLE_BOOK).name("<gradient:#A1FF52:#45FFF6><b>튜토리얼</b></gradient>").lore("&7서버 기능을 따라 해 보고", "&7보상을 받으세요.", "", "&e클릭하여 열기").build(),
                    e -> later(1, () -> plugin.module(TutorialModule.class).open(p)));
        }
        g.set(25, It.of(Material.IRON_HELMET).name("<b><gradient:#C0FEC4:#79AB96><italic:false>TEAM SYSTEM</gradient></b>").lore("&8전투%nl%&7팀을 만들고 가입할 수 있습니다.").build(), e -> run(p, "팀"));
        g.nav(p, null).fill().open(p);
    }

    public void warp(Player p) {
        if (blocked(p)) return;
        tutorial(p, "warp");
        PlayerData d = data(p);
        Gui g = new Gui(4, "&8&l워프 메뉴");
        Location l = p.getLocation();
        g.set(4, It.of(Material.FILLED_MAP).name("&e&l현재 위치")
                .lore("&7월드 : &f" + worldLabel(l.getWorld().getName()), "&7좌표 : &f" + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ(),
                        "", "&8모든 이동은 3초 대기 후 진행되며,", "&8전투 중에는 이동할 수 없습니다.").build());
        // 1줄: 기본 이동
        g.set(10, It.of(Material.COMPASS).name("&a&l스폰으로 이동").lore("&8기본", "&7서버 스폰으로 이동합니다.").build(), e -> run(p, "spawn"));
        g.set(12, It.of(Material.GRASS_BLOCK).name("&3&l야생으로 이동").lore("&8야생", "&7야생 월드의 랜덤 위치로 이동합니다.").build(), e -> run(p, "랜덤스폰"));
        g.set(14, It.of(Material.RED_WOOL).name("&c&l보호구역으로 이동").lore("&8안전", "&7보호구역으로 이동합니다.").build(), e -> run(p, "보호구역"));
        g.set(16, It.of(Material.RECOVERY_COMPASS).name("&6&l이전 위치로 이동").lore("&8복귀", "&7마지막으로 죽은 곳 또는", "&7이동하기 전 위치로 돌아갑니다.").build(), e -> run(p, "back"));
        // 2줄: 집 / 콘텐츠
        g.set(19, It.of(Material.RED_BED).name("&e&l집으로 이동").lore("&8집", "&7설정한 집으로 이동합니다.", "&7집이 여러 개면 목록이 표시됩니다.").build(), e -> run(p, "home"));
        g.set(21, It.of(Material.WHITE_BED).name("&f&l현재 위치를 집으로 설정").lore("&8집", "&7지금 서 있는 곳을 집으로 저장합니다.", "&8랭크가 오르면 집 개수가 늘어납니다.", "", "&c이미 설정한 집은 덮어씁니다.").build(), e -> run(p, "sethome"));
        BossModule boss = plugin.module(BossModule.class);
        It altar = It.of(Material.END_CRYSTAL).name("<b><gradient:#FFF200:#F6E47C:#FFFCD4><italic:false>천공의 제단</gradient></b>")
                .lore("&8보스", "&7천공의 제단 이동권으로 입장합니다.", "", boss == null ? "" : boss.statusLine());
        if (boss != null && boss.descended()) altar.ench(org.bukkit.enchantments.Enchantment.INFINITY, 1);
        g.set(23, altar.build(), e -> {
            if (boss != null) later(1, () -> boss.openAltar(p, () -> warp(p)));
        });
        boolean newbie = d.newbiePassActive();
        g.set(25, It.of(newbie ? Material.LIME_WOOL : Material.GRAY_WOOL).name((newbie ? "&a&l" : "&7&l") + "뉴비 보호구역")
                .lore("&8뉴비", "&7뉴비 헬퍼 패스 사용자 전용 구역입니다.", "", newbie ? "&e클릭하여 이동" : "&c뉴비 헬퍼 패스가 필요합니다.").build(), e -> {
            if (newbie) run(p, "뉴비보호구역");
            else Msg.sound(p, "entity.villager.no", 0.8f, 1f);
        });
        g.nav(p, () -> menu(p)).fill().open(p);
    }

    private String worldLabel(String world) {
        if (world.equals(world("spawn"))) return "스폰";
        if (world.equals(world("wild"))) return "야생";
        if (world.equals(world("nether"))) return "네더";
        if (world.equals(world("end"))) return "엔드";
        if (world.equals(world("newbie"))) return "뉴비 보호구역";
        if (world.equalsIgnoreCase(plugin.getConfig().getString("boss.lock-world", "sky"))) return "천공의 제단";
        return world;
    }

    /* ======================= 프로필 ======================= */

    private void profile(Player p) {
        PlayerData d = data(p);
        String color = switch (d.nickColor) {
            case 1 -> "&cRed";
            case 2 -> "&6Gold";
            case 3 -> "&5Purple";
            case 4 -> "&dMagenta";
            case 5 -> GRADIENT_GOLD + "Gradient Gold</gradient>";
            case 6 -> GLACIER + "Glacier</gradient>";
            default -> "&c설정되지 않음";
        };
        Gui g = new Gui(3, "&8&l프로필");
        g.set(4, It.of(Material.PLAYER_HEAD).skull(p).name("&e" + p.getName())
                .lore("&7현재 칭호 : " + plugin.hooks().prefix(p) + "%nl%&7설정된 닉네임 색상 : " + color).build());
        g.set(10, It.of(Material.IRON_SWORD).name("&6&l킬 특수 효과 설정").lore("&7킬 특수 효과와 크라운 처치 알림 표식을 설정합니다.").build(), e -> later(1, () -> killEffects(p)));
        g.set(12, It.of(Material.PAPER).name("&6&l닉네임 색상 설정").lore("&7닉네임 색상을 설정합니다.").build(), e -> later(1, () -> nickColors(p)));
        g.set(14, It.of(Material.EMERALD).name("&6&l랭크 확인").lore("&7랭크를 확인합니다.").build(), e -> later(1, () -> ranks(p)));
        g.set(16, It.of(Material.BOOK).name("&6&l칭호 설정").lore("&7칭호를 설정할 수 있습니다.").build(), e -> later(1, () -> titles(p)));
        g.nav(p, () -> menu(p)).fill().open(p);
    }

    /** 킬 특수 효과 / 크라운 표식 버튼: 상태 + 클릭 안내 (모두 같은 형식) */
    private static ItemStack toggleIcon(Material owned, Material missing, String name, boolean has, boolean active, String preview, String howToGet) {
        String state = !has ? "&c미보유" : active ? "&a적용 중" : "&7해제됨";
        It b = It.of(has ? owned : missing).name(name).lore("", "&7상태 : " + state, "", preview, "",
                has ? (active ? "&e클릭하여 해제" : "&e클릭하여 적용") : "&8" + howToGet);
        if (has && active) b.ench(Enchantment.UNBREAKING, 1);
        return b.hide().build();
    }

    private void killEffects(Player p) {
        PlayerData d = data(p);
        Gui g = new Gui(3, "&8&l킬 특수 효과 설정");
        Object[][] fx = {
                {10, "&6킬 특수 효과 &7- &7Lightning", "supreme", 1, "Lightning", "Supreme", "&7처치 시 번개가 내리칩니다."},
                {12, "&6킬 특수 효과 &7- &eFirework (Red, White)", "exceed", 2, "Firework", "Exceed", "&7처치 시 빨강·하양 폭죽이 터집니다."},
                {14, "&6킬 특수 효과 &7- &6Explosion", "peerless", 3, "Explosion", "Peerless", "&7처치 시 폭발 효과가 일어납니다."},
        };
        for (Object[] f : fx) {
            boolean has = d.ranks.contains((String) f[2]);
            boolean active = d.killEffect == (int) f[3];
            g.set((int) f[0], toggleIcon(Material.ENCHANTED_BOOK, Material.BOOK, (String) f[1], has, active, (String) f[6], f[5] + " 랭크 달성 보상입니다."), e -> {
                if (!d.ranks.contains((String) f[2])) {
                    Msg.send(p, "&c[!] " + f[5] + " 랭크를 달성하지 않았습니다.");
                    Msg.sound(p, "entity.villager.no", 0.8f, 1f);
                    return;
                }
                boolean on = d.killEffect != (int) f[3];
                d.killEffect = on ? (int) f[3] : 0;
                plugin.store().save(d);
                Msg.send(p, "&e[!] &7킬 특수 효과 - " + f[4] + (on ? " &a적용&7되었습니다." : " &c해제&7되었습니다."));
                Msg.sound(p, on ? "entity.player.levelup" : "block.note_block.bass", 0.6f, on ? 1.8f : 1f);
                later(1, () -> killEffects(p));
            });
        }
        // 크라운 처치 알림 표식
        g.set(16, toggleIcon(Material.GOLDEN_HELMET, Material.CHAINMAIL_HELMET, "<gradient:#FFF176:#FFB732><italic:false><b>[👑] 크라운 처치 알림 표식</b></gradient>",
                d.crownOwned, d.crownKill, "&7처치 알림 앞에 &6&l👑 &7가 붙습니다.", "오버 로드 ⭐8 달성 보상입니다."), e -> {
            if (!d.crownOwned) {
                Msg.send(p, "&c[!] 크라운 처치 알림 표식을 보유하고 있지 않습니다.");
                Msg.sound(p, "entity.villager.no", 0.8f, 1f);
                return;
            }
            d.crownKill = !d.crownKill;
            plugin.store().save(d);
            Msg.send(p, "&e[!] &7크라운 처치 알림 표식이" + (d.crownKill ? " &a적용&7되었습니다." : " &c해제&7되었습니다."));
            Msg.sound(p, d.crownKill ? "entity.player.levelup" : "block.note_block.bass", 0.6f, d.crownKill ? 1.8f : 1f);
            later(1, () -> killEffects(p));
        });
        g.nav(p, () -> profile(p)).fill().open(p);
    }

    private static ItemStack glow(ItemStack it) {
        var meta = it.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
        return it;
    }

    private void nickColors(Player p) {
        PlayerData d = data(p);
        Gui g = new Gui(4, "&8&l닉네임 색상");
        Object[][] colors = {
                {10, "red", 1, "&6닉네임 색상 &7- &c&lRed", "Peerless 랭크 달성 보상입니다.", "&c"},
                {12, "gold", 2, "&6닉네임 색상 &7- &6&lGold", "The Astral Fate 올 수집 보상입니다.", "&6"},
                {14, "purple", 3, "&6닉네임 색상 &7- &5&lPurple", "End Rusher 이벤트 달성 보상입니다.", "&5"},
                {16, "magenta", 4, "&6닉네임 색상 &7- &d&lMagenta", "Pre-Order Package 4 보상입니다.", "&d"},
                {20, "gradient", 5, "&6닉네임 색상 &7- <b><gradient:#FFF176:#FFB732>Gradient Gold</gradient></b>", "Pre-Order 시작의 룰렛 보상입니다.", ""},
                {24, "glacier", 6, "&6닉네임 색상 &7- <b><gradient:#39C7FF:#C2FFF5>Glacier</gradient></b>", "S6 Glacier Storm 보상입니다.", ""},
        };
        for (Object[] c : colors) {
            String key = (String) c[1];
            boolean has = d.nickPapers.contains(key);
            boolean active = d.nickColor == (int) c[2];
            g.set((int) c[0], toggleIcon(Material.NAME_TAG, Material.PAPER, (String) c[3], has, active,
                    "&7미리보기 : " + nickName((int) c[2], p.getName(), "&f") + "%nl%&8채팅, 킬 알림, 탭리스트, 입장 메시지에 적용", (String) c[4]), e -> {
                if (!d.nickPapers.contains(key)) {
                    Msg.send(p, "&c[!] 보유 중이 아닌 닉네임 색상입니다.");
                    Msg.sound(p, "entity.villager.no", 0.8f, 1f);
                    return;
                }
                boolean on = d.nickColor != (int) c[2];
                plugin.module(CoreModule.class).applyNickColor(p, on ? (int) c[2] : 0);
                Msg.send(p, "&e[!] &7닉네임 색상 - " + nickName((int) c[2], key.substring(0, 1).toUpperCase() + key.substring(1), "&f") + (on ? " &a적용&7되었습니다." : " &c해제&7되었습니다."));
                Msg.sound(p, on ? "entity.player.levelup" : "block.note_block.bass", 0.6f, on ? 1.8f : 1f);
                later(1, () -> nickColors(p));
            });
        }
        g.nav(p, () -> profile(p)).fill().open(p);
    }

    private void ranks(Player p) {
        PlayerData d = data(p);
        Gui g = new Gui(4, "&8&l플레이어 랭크");
        g.set(4, It.of(Material.BOOK).name("&a현재 누적 캐시 : " + Msg.num(d.exMoneyTotal) + " Cash").build());
        OverlordModule ol = plugin.module(OverlordModule.class);
        if (ol != null) {
            boolean un = ol.unlocked(d);
            int have = ol.insignias(d);
            It o = It.of(un ? Material.NETHER_STAR : Material.GRAY_DYE).name("<b><gradient:#FFF176:#FFB732><italic:false>[👑] OVER LORD</gradient></b>")
                    .lore("", "&7Peerless 랭크 달성 시 해제됩니다.", "&775,000 유료 캐시 획득 시 휘장 한 개가 누적됩니다.", "",
                            "&f보유 휘장 : <gradient:#FFF176:#FFB732>" + "★".repeat(have) + "</gradient>&8" + "★".repeat(10 - have),
                            "", un ? "&e클릭하여 오버 로드 보상 보기" : "&c잠김 &7(클릭하여 보상 목록 보기)");
            if (un) o.ench(Enchantment.UNBREAKING, 1);
            g.set(22, o.hide().build(), e -> later(1, () -> ol.open(p)));
        }
        String done = "%nl%%nl%&a&l달성했습니다!";
        String notYet = "%nl%%nl%&c&l아직 달성 조건을 만족하지 못했습니다.";
        g.set(10, It.of(Material.COPPER_INGOT).name("&7[Rank] &aAce").lore("%nl%&e&l획득 조건 : 30,000 Cash 이상 누적 획득%nl%%nl%&7달성 보상:%nl%&e&l미지의 구역 입장 가능%nl%&f네더라이트 주괴 x 10%nl%&f네더라이트 업그레이드 형판 x 10"
                + (d.ranks.contains("ace") ? done : notYet)).build());
        g.set(12, It.of(Material.IRON_INGOT).name("&e[💎] &7[Rank] &bSupreme").lore("%nl%&e&l획득 조건 : 70,000 Cash 이상 누적 획득%nl%%nl%&7달성 보상:%nl%&6킬 특수 효과 &7- Lightning%nl%&e[💎] &6Rank Supreme 달성 룰렛"
                + (d.ranks.contains("supreme") ? done : notYet)).build());
        g.set(14, It.of(Material.GOLD_INGOT).name("&a[💎] &7[Rank] &6Exceed").lore("%nl%&e&l획득 조건 : 130,000 Cash 이상 누적 획득%nl%%nl%&7달성 보상:%nl%&6킬 특수 효과 &7- &eFirework (Red, White)%nl%&e[💎] &6Rank Exceed 달성 룰렛%nl%&esethome 개수 증가 &7(1 → 2)"
                + (d.ranks.contains("exceed") ? done : notYet)).build());
        g.set(16, It.of(Material.DIAMOND).name("&4[💎] &7[Rank] &cPeerless").lore("%nl%&e&l획득 조건 : 200,000 Cash 이상 누적 획득%nl%%nl%&7달성 보상:%nl%&6킬 특수 효과 &7- &6Explosion%nl%&4[💎] &cRank Peerless 달성 룰렛%nl%&esethome 개수 증가 &7(1 → 3)%nl%&a&l닉네임 색상 변경권 &c&l(Red)"
                + (d.ranks.contains("peerless") ? done : notYet)).build());
        g.nav(p, () -> profile(p)).fill().open(p);
    }

    private void titles(Player p) {
        PlayerData d = data(p);
        Gui g = new Gui(4, "&8&l칭호 설정");
        Object[][] t = {
                {10, "ace", "&e[Ace]", "&7획득 방법 : 랭크 &e[Ace] &7를 달성하기", "&e[Ace]"},
                {11, "supreme", "&a[💎] &bSupreme", "&7획득 방법 : 랭크 &a[💎] &bSupreme &7을 달성하기", "\"&a[💎] &bSupreme\""},
                {12, "exceed", "&a[💎] &6Exceed", "&7획득 방법 : 랭크 &a[💎] &6Exceed &7를 달성하기", "\"&a[💎] &6Exceed\""},
                {13, "peerless", "&4[💎] &cPeerless", "&7획득 방법 : 랭크 &4[💎] &cPeerless &7를 달성하기", "\"&4[💎] &cPeerless\""},
                {19, "sealed", "<b><gradient:#00E7FF:#FFFFFF><italic:false>[✦] 얼어붙은 제왕</gradient></b>", "&7획득 방법 :%nl%&7Sealed Master Power 컬렉션의 모든 아이템을 수집하세요.", "\"<b><gradient:#00E7FF:#FFFFFF>[✦] 얼어붙은 제왕</gradient></b>\""},
                {20, "destroy", "<b><gradient:#FF0000:#530427><italic:false>[✦] 파멸의 지배자</gradient></b>", "&7획득 방법 :%nl%&7The Ruiner's Grudge 컬렉션의 모든 아이템을 수집하세요.", "\"<b><gradient:#FF0000:#530427>[✦] 파멸의 지배자</gradient></b>\""},
                {16, "nether", "<b><gradient:#670B0B:#FD4141><italic:false><b>[✧] 네더 로드</b></gradient></b>", "&7획득 방법 :%nl%&c???", "\"<b><gradient:#670B0B:#FD4141><italic:false><b>[✧] 네더 로드</b></gradient></b>\""},
                {15, "7days", "&7[칭호] &e&l[💦] 파수꾼", "%nl%&7획득 방법 :%nl%&77일 연속 출석하기", "\"&e&l[💦] 파수꾼\""},
                {21, "void", "<b><gradient:#C2C2C2:#404247><italic:false>[✦] 공허의 방랑자</gradient></b>", "&7획득 방법 :%nl%&7Whisper of the Void 컬렉션의 모든 아이템을 수집하세요.", "\"<b><gradient:#C2C2C2:#404247>[✦] 공허의 방랑자</gradient></b>\""},
                {22, "philosopher", "<b><gradient:#2193b0:#E7F0F2><italic:false>[✦] 사유의 현자</gradient></b>", "&7획득 방법 :%nl%&7The Philosopher's Literature 컬렉션의 모든 아이템을 수집하세요.", "\"<b><gradient:#2193b0:#E7F0F2>[✦] 사유의 현자</gradient></b>\""},
                {23, "inscribed", "<b><gradient:#1E2586:#FAD4D4><italic:false>[🌙] 새 시대의 각인자</gradient></b>", "&7획득 방법 :%nl%&7Prologue of Inscribed 컬렉션의 모든 아이템을 수집하세요.", "\"<b><gradient:#1E2586:#FAD4D4>[🌙] 새 시대의 각인자</gradient></b>\""},
                {24, "arbiter", "<b><gradient:#FFF200:#FFFCD4><italic:false>[⚡] 천공의 심판자</gradient></b>", "&7획득 방법 :%nl%&7Arbiter of Heaven 컬렉션의 모든 아이템을 수집하세요.", "\"<b><gradient:#FFF200:#FFFCD4>[⚡] 천공의 심판자</gradient></b>\""},
                {25, "glacier", "<b><gradient:#39C7FF:#C2FFF5><italic:false>[❆] 빙하의 군주</gradient></b>", "&7획득 방법 :%nl%&7Glacier Storm 컬렉션의 모든 아이템을 수집하세요.", "\"<b><gradient:#39C7FF:#C2FFF5>[❆] 빙하의 군주</gradient></b>\""},
        };
        for (Object[] row : t) {
            String key = (String) row[1];
            boolean has = d.titles.contains(key);
            boolean active = key.equals(d.activeTitle);
            g.set((int) row[0], toggleIcon(Material.ENCHANTED_BOOK, Material.BOOK, (String) row[2], has, active,
                    "&7미리보기 : " + row[2] + " &7- &f" + p.getName(), Msg.plainOf(((String) row[3]).replace("%nl%", " "))), e -> {
                if (!d.titles.contains(key)) {
                    Msg.send(p, "&c[!] 보유 중이 아닌 칭호입니다.");
                    Msg.sound(p, "entity.villager.no", 0.8f, 1f);
                    return;
                }
                boolean on = !key.equals(d.activeTitle);
                if (on) {
                    plugin.module(CoreModule.class).applyTitle(p, (String) row[4]);
                    d.activeTitle = key;
                } else {
                    console("lp user " + p.getName() + " meta clear");
                    d.activeTitle = null;
                }
                plugin.store().save(d);
                Msg.send(p, "&e[!] &7칭호 " + row[2] + (on ? " &a적용&7되었습니다." : " &c해제&7되었습니다."));
                Msg.sound(p, on ? "entity.player.levelup" : "block.note_block.bass", 0.6f, on ? 1.8f : 1f);
                later(1, () -> titles(p));
            });
        }
        g.set(14, toggleIcon(Material.ENCHANTED_BOOK, Material.BOOK, "<b><gradient:#4B0082:#800080><italic:false>[✧] 엔드 디멘셔널</gradient></b>",
                false, false, "&7미리보기 : <b><gradient:#4B0082:#800080>[✧] 엔드 디멘셔널</gradient></b>", "획득 방법 : ???"));
        g.nav(p, () -> profile(p));
        g.fill().open(p);
    }

    /* ======================= Shift + F ======================= */

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (!e.getPlayer().isSneaking()) return;
        e.setCancelled(true);
        menu(e.getPlayer());
    }

    /* ======================= 주기 공지 ======================= */

    /** 공지 한 개: 머리표 색, 제목, 설명 줄, 버튼(라벨, 명령어, 바로 실행 여부, 설명) */
    private record Notice(String tag, String title, java.util.List<String> lines, String button, String command, boolean run, String hover) {
    }

    private int announceIndex;

    /** 공지 목록 (config announcements.order 순서, 매번 다음 공지로 돌아가며) */
    private java.util.List<Notice> notices() {
        java.util.List<Notice> out = new java.util.ArrayList<>();
        java.util.List<String> order = plugin.getConfig().getStringList("announcements.order");
        if (order.isEmpty()) order = java.util.List.of("menu", "boss", "pass", "dailyshop", "rules", "vote", "discord");
        for (String key : order) {
            Notice n = notice(key);
            if (n != null) out.add(n);
        }
        for (java.util.Map<?, ?> m : plugin.getConfig().getMapList("announcements.custom")) {
            Object title = m.get("title");
            if (title == null) continue;
            java.util.List<String> lines = new java.util.ArrayList<>();
            if (m.get("lines") instanceof java.util.List<?> l) for (Object o : l) lines.add(String.valueOf(o));
            Object cmd = m.get("command");
            out.add(new Notice(m.get("tag") == null ? "&b&l[ 공지 ]" : String.valueOf(m.get("tag")), String.valueOf(title), lines,
                    m.get("button") == null ? null : String.valueOf(m.get("button")), cmd == null ? null : String.valueOf(cmd), true, "&a클릭"));
        }
        return out;
    }

    private Notice notice(String key) {
        return switch (key) {
            case "menu" -> new Notice("<gradient:#A1FF52:#45FFF6><b>[ TIP ]</b></gradient>", "&f메뉴를 빠르게 여는 방법",
                    java.util.List.of("&7웅크린 상태(&fShift&7)에서 &fF 키&7를 누르면 어디서나 서버 메뉴가 열립니다."),
                    "[메뉴 열기]", "/메뉴", true, "&a클릭하여 서버 메뉴 열기");
            case "boss" -> {
                BossModule b = plugin.module(BossModule.class);
                if (b == null) yield null;
                boolean on = b.descended();
                yield new Notice("<gradient:#FFF200:#FFFCD4><b>[ ☀ 천공의 제단 ]</b></gradient>",
                        on ? "&e천공의 심판자가 강림해 있습니다!" : "&f천공의 심판자에게 도전하세요",
                        java.util.List.of(b.statusLine(),
                                "&7처치하면 전리품을 주사위로 나눠 가지며, 아주 드물게 &6천공의 심판자 클레이모어&7가 나옵니다."),
                        "[천공의 제단]", "/천공의제단", true, "&a클릭하여 천공의 제단 메뉴 열기");
            }
            case "pass" -> new Notice("<gradient:#FFD84D:#FF9F1C><b>[ 프리미엄 패스 ]</b></gradient>", "&f오늘의 일일 퀘스트는 확인하셨나요?",
                    java.util.List.of("&7퀘스트를 깨고 패스 레벨을 올려 보상을 받아 가세요."),
                    "[패스 열기]", "/pass", true, "&a클릭하여 프리미엄 패스 열기");
            case "dailyshop" -> Bukkit.getPluginManager().isPluginEnabled("DailyShop")
                    ? new Notice("<gradient:#FFE066:#FFB300><b>[ 일일 상점 ]</b></gradient>", "&f오늘의 특가 상품이 준비되어 있습니다",
                    java.util.List.of("&7상품은 매일 바뀌며, 하루 구매 수량이 정해져 있습니다."),
                    "[일일 상점]", "/dailyshop", true, "&a클릭하여 일일 상점 열기")
                    : null;
            case "rules" -> new Notice("<gradient:#FF5555:#FFB4B4><b>[ Security Notice ]</b></gradient>", "&f서버 규칙을 지켜 주세요!",
                    java.util.List.of("&7비매너 행위나 핵 사용은 즉시 처벌됩니다.", "&7문제가 되는 유저를 보면 &e/report &7로 신고해 주세요."),
                    "[신고하기]", "/report ", false, "&a클릭하여 /report 입력");
            case "vote" -> new Notice("<gradient:#7CFF00:#C8FFB0><b>[ 서버 추천 ]</b></gradient>", "&f서버를 추천하고 보상을 받으세요!",
                    java.util.List.of("&7추천 한 번이 서버에 큰 힘이 됩니다."),
                    "[추천하기]", "/마인리스트", true, "&a클릭하여 추천 링크 보기");
            case "discord" -> new Notice("<gradient:#3A35F7:#47B2FF><b>[ Discord ]</b></gradient>", "&f공식 디스코드에서 소식을 받아 보세요",
                    java.util.List.of("&7이벤트, 업데이트, 문의는 디스코드에서 진행됩니다."),
                    "[디스코드]", "/디스코드", true, "&a클릭하여 디스코드 링크 보기");
            default -> null;
        };
    }

    /** 공지 한 개를 전체에 보냄 */
    private void announce(Notice n) {
        java.util.List<net.kyori.adventure.text.Component> out = new java.util.ArrayList<>();
        out.add(Msg.c(" "));
        out.add(Msg.c(" " + n.tag() + " " + n.title()));
        for (String l : n.lines()) out.add(Msg.c("   " + l));
        if (n.button() != null && n.command() != null) {
            net.kyori.adventure.text.Component b = Msg.c("   &e&l" + n.button())
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Msg.c(n.hover())))
                    .clickEvent(n.run() ? net.kyori.adventure.text.event.ClickEvent.runCommand(n.command())
                            : net.kyori.adventure.text.event.ClickEvent.suggestCommand(n.command()));
            out.add(b);
        }
        out.add(Msg.c(" "));
        boolean sound = plugin.getConfig().getBoolean("announcements.sound", true);
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (net.kyori.adventure.text.Component c : out) p.sendMessage(c);
            if (sound) Msg.sound(p, "block.note_block.chime", 0.4f, 1.4f);
        }
        for (net.kyori.adventure.text.Component c : out) Bukkit.getConsoleSender().sendMessage(c);
    }

    /** 다음 공지 (돌아가며) */
    public void announceNext() {
        java.util.List<Notice> list = notices();
        if (list.isEmpty()) return;
        announce(list.get(Math.floorMod(announceIndex++, list.size())));
    }

    @Override
    public void secondTick() {
        if (!plugin.getConfig().getBoolean("announcements.enabled", true)) return;
        int interval = Math.max(60, (int) (plugin.getConfig().getDouble("announcements.interval-minutes", 15) * 60));
        if (++ticks < interval) return;
        ticks = 0;
        if (Bukkit.getOnlinePlayers().isEmpty()) return;
        announceNext();
    }

    /* ======================= 신고 ======================= */

    private void reportCmd(CommandSender s, String l, String[] a) {
        if (!Cmd.isPlayer(s)) return;
        Player p = (Player) s;
        long now = System.currentTimeMillis();
        if (now - reportCooldown.getOrDefault(p.getUniqueId(), 0L) < 120_000) {
            Msg.send(p, "§c아직은 이 명령어를 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.");
            return;
        }
        String target = Cmd.arg(a, 0) == null ? "" : a[0];
        String reason = Cmd.rest(a, 1) == null ? "" : Msg.esc(Cmd.rest(a, 1));
        Msg.send(p, "§c&l[&f&l Security Notice &c&l] &7플레이어 &e" + target + "&7 님의 신고가 접수되었습니다. 곧 시스템에서 처리할 예정이니 기다려 주세요!");
        String report = "&c-----------------------------------------------------%nl%%nl%§c&l[&f&l Security Notice &c&l] &c&l신고가 접수되었습니다.%nl%&7%nl%&7신고한 유저 : &a" + p.getName()
                + "%nl%&7신고 대상 플레이어 이름 : &a" + target + "%nl%&7사유 : &a" + reason + "%nl%%nl%&c-----------------------------------------------------";
        for (String name : plugin.getConfig().getStringList("report-receivers")) {
            Player r = Bukkit.getPlayerExact(name);
            if (r != null) Msg.send(r, report);
        }
        for (Player r : Bukkit.getOnlinePlayers()) {
            if (r.hasPermission("wave.report.receive") && !plugin.getConfig().getStringList("report-receivers").contains(r.getName())) Msg.send(r, report);
        }
        plugin.getLogger().info("[신고] " + p.getName() + " -> " + target + " : " + Cmd.rest(a, 1));
        reportCooldown.put(p.getUniqueId(), now);
    }
}
