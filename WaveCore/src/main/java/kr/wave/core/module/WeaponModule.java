package kr.wave.core.module;

import kr.wave.core.Items;
import kr.wave.core.WaveCore;
import kr.wave.core.data.PlayerData;
import kr.wave.core.util.Cmd;
import kr.wave.core.util.Fx;
import kr.wave.core.util.Gui;
import kr.wave.core.util.It;
import kr.wave.core.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Biome;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * randombox.sk / sealed.sk / 시즌무기.sk / 장터.sk 의 특수 아이템 능력
 * - 공격/피격 효과, 우클릭 스킬과 쿨타임 액션바, 장착 효과, 드롭 방지, 최대 체력/이동 속도
 * - 망령 수확자(예전 Soul Splitter) 처치 카운터, 시간을 가르는 바람(예전 Time piecer Wind)
 * - 아이템 지급 관리자 명령어
 */
public class WeaponModule extends Module {

    private static final Attribute MAX_HEALTH = resolve("max_health", "generic.max_health");
    private static final Set<Biome> FORESTS = new HashSet<>();

    static {
        String[] names = {"FOREST", "DARK_FOREST", "FLOWER_FOREST", "BIRCH_FOREST", "TAIGA", "OLD_GROWTH_PINE_TAIGA", "OLD_GROWTH_SPRUCE_TAIGA", "SNOWY_TAIGA"};
        for (String n : names) {
            Biome b = Registry.BIOME.get(NamespacedKey.minecraft(n.toLowerCase()));
            if (b != null) FORESTS.add(b);
        }
    }

    /* 컨셉 색상 */
    private static final org.bukkit.Color GOLD = Fx.rgb(0xD4AF37), GOLD_L = Fx.rgb(0xFDFBF0);
    private static final org.bukkit.Color ICE = Fx.rgb(0x00B3FF), ICE_L = Fx.rgb(0xDFF6FF);
    private static final org.bukkit.Color AURORA1 = Fx.rgb(0x3DFFB0), AURORA2 = Fx.rgb(0x5AC8FA), AURORA3 = Fx.rgb(0xB388FF);
    private static final org.bukkit.Color THUNDER = Fx.rgb(0xF4FF6B);
    private static final org.bukkit.Color CHAOS_D = Fx.rgb(0x1F1F1F), CHAOS_L = Fx.rgb(0xBCBCBC);
    private static final org.bukkit.Color RUIN = Fx.rgb(0xFF1A1A), RUIN_D = Fx.rgb(0x530427);
    private static final org.bukkit.Color SAND = Fx.rgb(0xBFA66E), SAND_P = Fx.rgb(0xFFE2E2), SAND_G = Fx.rgb(0xB2D6B6);
    private static final org.bukkit.Color VOID_W = Fx.rgb(0xF9F9F9), VOID_D = Fx.rgb(0x404247), VOID_C = Fx.rgb(0x88F4FF);
    private static final org.bukkit.Color PHILO = Fx.rgb(0x2193B0), PHILO_L = Fx.rgb(0xE7F0F2);
    private static final org.bukkit.Color ARBITER = Fx.rgb(0xFFF200), ARBITER_L = Fx.rgb(0xFFFCD4);
    private static final org.bukkit.Color FROST = Fx.rgb(0x39C7FF), FROST_L = Fx.rgb(0xD2FFF8), FROST_SKY = Fx.rgb(0x87CEEB);
    private static final org.bukkit.Color WIND = Fx.rgb(0x00FF7C), WIND_P = Fx.rgb(0xFFAFCA);
    private static final org.bukkit.Color EMBER = Fx.rgb(0xFF7A18), EMBER_L = Fx.rgb(0xFFD36E);
    /** 잿불 낙인: "공격자|대상" -> {낙인 수, 마지막 적중 시각} */
    private final java.util.Map<String, long[]> brands = new java.util.HashMap<>();
    private static final org.bukkit.Color SOUL = Fx.rgb(0x8A5CFF), SOUL_P = Fx.rgb(0x5FFBF1);   // 망령 수확자: 보라 / 청록
    private static final org.bukkit.Color MOON = Fx.rgb(0x1E2586), MOON_P = Fx.rgb(0xFAD4D4);
    private static final org.bukkit.Color NATURE = Fx.rgb(0x95FFBE), NATURE_Y = Fx.rgb(0xF5E400);

    private final Map<UUID, Long> sealedCd = new HashMap<>();
    private final Map<UUID, Long> chronosCd = new HashMap<>();
    private final Map<UUID, Long> chaosCd = new HashMap<>();
    /** 혹한의 연쇄 발동이 끝나는 시각 */
    private final Map<UUID, Long> chainBoostUntil = new HashMap<>();
    /** 연쇄 피해 중 (연쇄가 또 연쇄를 일으키지 않게) */
    private boolean chaining;
    private final Map<UUID, Long> waveCd1 = new HashMap<>();
    private final Map<UUID, Long> waveCd2 = new HashMap<>();
    /** 범위 스킬 대상 확인용 가짜 피해 이벤트 중에는 무기 효과를 건너뜀 */
    private boolean probing;
    private final Map<UUID, Long> newCharCd1 = new HashMap<>();
    private final Map<UUID, Long> newCharCd2 = new HashMap<>();
    private final Map<UUID, Long> claymoreCd = new HashMap<>();
    private final Map<UUID, Long> sandStormUntil = new HashMap<>();
    private final Map<UUID, Boolean> lastClaymore = new HashMap<>();

    public WeaponModule(WaveCore plugin) {
        super(plugin);
    }

    private static Attribute resolve(String... keys) {
        for (String k : keys) {
            Attribute a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(k));
            if (a != null) return a;
        }
        return null;
    }

    public static double maxHealth(Player p) {
        AttributeInstance ai = MAX_HEALTH == null ? null : p.getAttribute(MAX_HEALTH);
        return ai == null ? 20 : ai.getValue();
    }

    /** 사망 시 드롭되지 않고 리스폰 때 돌려받는 아이템 */
    public static boolean isKeepOnDeath(ItemStack it) {
        String n = Msg.name(it);
        return n.contains(Items.N_CLAYMORE_SD) || n.contains(Items.N_FROST) || Items.isNewChar(n);
    }

    @Override
    public void enable() {
        cmd("아이템", ADMIN, this::giveCommand, (snd, a) -> a.length <= 1 ? new java.util.ArrayList<>(giveIds().keySet())
                : a.length == 2 ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList()
                : a.length == 3 ? java.util.List.of("1", "5", "10", "64") : java.util.List.<String>of(), "특수아이템", "wgive");
        cmd("전체지급", ADMIN, this::giveAllCommand, (snd, a) -> {
            if (a.length <= 1) {
                java.util.List<String> l = new java.util.ArrayList<>(java.util.List.of("손"));
                l.addAll(giveIds().keySet());
                return l;
            }
            return a.length == 2 ? java.util.List.of("1", "5", "10", "64") : a.length == 3 ? java.util.List.of("우편") : java.util.List.<String>of();
        }, "모두지급", "giveall");
        cmd("랜덤박스지급", ADMIN, (s, l, a) -> give(s, Items.ruinerScythe(), Items.sealedWings(), Items.ruinerPants(), Items.sealedSword(), Items.thunderAxe(), Items.chaosApocalypse(), Items.darknessWave(), Items.chaosHelmet()));
        cmd("epicitems", ADMIN, (s, l, a) -> give(s, Items.vibranium(), Items.quantumMatter(), Items.quantumSword(), Items.auroraBoots(), Items.voidChest(), Items.voidBow(),
                Items.russellHelmet(), Items.aristotlePickaxe(), Items.chronosBoots(), Items.chronos(0), Items.natureBoots()));
        cmd("chronoss", ADMIN, (s, l, a) -> give(s, Items.chronos(0), Items.chronos(1), Items.chronos(2), Items.chronos(3), Items.chronos(4), Items.chronos(5), Items.chronos(6),
                Items.timeShard(), Items.ruinerEssence(), Items.frostGrave()));
        cmd("test209292", ADMIN, (s, l, a) -> give(s, Items.newCharacteristic()));
        cmd("test1133", ADMIN, (s, l, a) -> {
            if (!(s instanceof Player p)) return;
            Msg.title(p, "<gradient:#FFF200:#F6E47C:#FFFCD4><italic:false><b>Arbiter o</b><b>f H</b><b>eaven</b></gradient>",
                    "&6&l[&a&l⚡&6&l] <gradient:#FFF200:#F6E47C:#FFFCD4><italic:false><b>[Lv.</b><obf><b>sd</b></obf><b>] 천</b><b>공의 </b><b>심판자 클레이모어</b></gradient>", 10, 60, 10);
            Msg.soundAt(p.getLocation(), "block.beacon.power_select", 1, 2);
            Fx.p(p.getLocation().add(0, 1.5, 0), Particle.TOTEM_OF_UNDYING, 300, 1.5, 0.05, 1.5, 0.5);
            Fx.lightning(p.getLocation());
            It.give(p, Items.claymoreSd());
        });
        String[][] boxes = {{"저장상자1", "box"}, {"저장상자2", "box1"}, {"저장상자3", "final"}, {"저장상자4", "final1"}};
        for (String[] b : boxes) {
            cmd(b[0], ADMIN, (s, l, a) -> {
                if (!(s instanceof Player p)) return;
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (It.isAir(hand)) global().saved.remove(b[1]);
                else global().saved.put(b[1], hand.clone());
                plugin.store().saveGlobal();
                Msg.send(p, "&a저장되었습니다. &7(" + b[0] + ")");
            });
        }
        cmd("등록", null, this::registerCmd);
        cmd("창열기", null, this::storageCmd);
        cmd("속도", ADMIN, (s, l, a) -> {
            if (!(s instanceof Player p)) return;
            Double n = Cmd.dbl(Cmd.arg(a, 0));
            if (n == null) {
                Msg.send(p, "&c/속도 <퍼센트>");
                return;
            }
            float speed = (float) Math.max(-1, Math.min(1, 0.1 + 0.1 * (n / 100)));
            p.setWalkSpeed(speed);
            Msg.send(p, "§a이동 속도가 기본 속도 대비 §e" + a[0] + "%§a 만큼 설정되었습니다. (신속 무시)");
        });
    }

    private void give(CommandSender s, ItemStack... items) {
        if (s instanceof Player p) It.give(p, items);
    }

    /* ======================= 아이템 보관함 (/등록, /창열기) ======================= */

    private void registerCmd(CommandSender s, String l, String[] a) {
        if (!Cmd.isPlayer(s)) return;
        Player p = (Player) s;
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (It.isAir(hand)) {
            Msg.send(p, "아이템을 손에 들어주세요.");
            return;
        }
        PlayerData d = data(p);
        if (d.savedItem != null) {
            Msg.send(p, "&c이미 저장된 아이템이 있습니다. /창열기 로 먼저 회수하세요.");
            return;
        }
        d.savedItem = hand.clone();
        p.getInventory().setItemInMainHand(null);
        plugin.store().save(d);
        Msg.send(p, "아이템이 성공적으로 저장되었습니다!");
    }

    private void storageCmd(CommandSender s, String l, String[] a) {
        if (!Cmd.isPlayer(s)) return;
        Player p = (Player) s;
        PlayerData d = data(p);
        Gui g = new Gui(1, "&8&l아이템 보관함");
        if (d.savedItem != null) {
            g.set(4, d.savedItem.clone(), e -> {
                if (d.savedItem == null) return;
                It.give(p, d.savedItem);
                d.savedItem = null;
                plugin.store().save(d);
                Msg.send(p, "아이템을 회수했습니다.");
                p.closeInventory();
            });
        } else {
            g.set(4, It.of(Material.STRUCTURE_VOID).name("&c저장된 아이템 없음").lore("&7손에 아이템을 들고 /등록").build());
        }
        g.set(8, It.close(), e -> p.closeInventory());
        g.fill().open(p);
    }

    /* ======================= 공격 / 피격 효과 ======================= */

    private static boolean chance(double c) {
        return ThreadLocalRandom.current().nextDouble() < c;
    }

    private static void effect(LivingEntity e, PotionEffectType type, double seconds, int level) {
        e.addPotionEffect(new PotionEffect(type, (int) Math.round(seconds * 20), level - 1));
    }

    /** 대자연의 황혼: 장착 중에는 배고픔이 줄지 않음 (먹어서 오르는 것은 그대로) */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHunger(org.bukkit.event.entity.FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (e.getFoodLevel() >= p.getFoodLevel()) return;
        if (!Msg.nameHas(p.getInventory().getBoots(), Items.N_NATURE_BOOTS)) return;
        e.setCancelled(true);
        if (p.getSaturation() < 5f) p.setSaturation(5f);   // 달리기/점프 소모(지침)도 포화도로 흡수
    }

    /** 심판의 빛 충격파 범위 안: 실명 + 구속 + 약간의 피해 (config weapons.claymore-splash) */
    private void judgmentSplash(Player attacker, LivingEntity victim, Location center) {
        double radius = plugin.getConfig().getDouble("weapons.claymore-splash.radius", 4.0);
        double dmg = plugin.getConfig().getDouble("weapons.claymore-splash.damage", 3.0);
        double blind = plugin.getConfig().getDouble("weapons.claymore-splash.blindness-seconds", 2.0);
        double slow = plugin.getConfig().getDouble("weapons.claymore-splash.slowness-seconds", 3.0);
        int slowLv = plugin.getConfig().getInt("weapons.claymore-splash.slowness-level", 3);
        kr.wave.core.data.GlobalData.Team team = plugin.store().global().teamOf(attacker.getUniqueId());
        for (org.bukkit.entity.Entity en : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(en instanceof LivingEntity le) || le == attacker || le == victim || le.isDead()) continue;
            if (en instanceof org.bukkit.entity.ArmorStand) continue;
            if (le.getLocation().distanceSquared(center) > radius * radius) continue;
            // PvP 금지 구역 안의 대상은 확인 없이 제외 (확인 공격을 보내면 WorldGuard 가 "Hey!" 경고를 보냄)
            if (plugin.hooks().pvpDenied(le.getLocation())) continue;
            if (le instanceof Player t) {
                if (t.getGameMode() == org.bukkit.GameMode.CREATIVE || t.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
                if (team != null && team.members.containsKey(t.getUniqueId())) continue;   // 팀원 제외
                CombatTagModule ct = plugin.module(CombatTagModule.class);
                if (ct != null) ct.tag(t);
            }
            effect(le, PotionEffectType.BLINDNESS, blind, 1);
            effect(le, PotionEffectType.SLOWNESS, slow, slowLv);
            if (dmg > 0) {
                if (le.getHealth() <= dmg) le.setHealth(Math.min(1, le.getHealth()));   // 범위 피해로는 죽지 않음
                else hurt(le, dmg);
                try {
                    le.playHurtAnimation(0);
                } catch (RuntimeException | LinkageError ignored) {   // 이 기능이 없는 서버 버전: 맞는 연출만 생략 (피해는 이미 들어감)
                }
            }
            Fx.p(le.getLocation().add(0, 1, 0), Particle.END_ROD, 8, 0.3, 0.05);
        }
    }

    /* ======================= /아이템 <이름> [플레이어] [개수] ======================= */

    private java.util.LinkedHashMap<String, java.util.function.Supplier<ItemStack>> giveIdCache;

    /** 지급 이름: 아이템 이름에서 [..] 머리표를 뗀 뒤 띄어쓰기를 없앤 것 (예: 혼돈의아포칼립스) */
    private java.util.LinkedHashMap<String, java.util.function.Supplier<ItemStack>> giveIds() {
        if (giveIdCache != null) return giveIdCache;
        java.util.LinkedHashMap<String, java.util.function.Supplier<ItemStack>> m = new java.util.LinkedHashMap<>();
        java.util.List<java.util.function.Supplier<ItemStack>> all = new java.util.ArrayList<>(Items.specialItems());
        all.add(() -> Items.soulSplitter(plugin, 0));
        for (var sup : all) {
            String id = giveId(Msg.name(sup.get()));
            if (id.isEmpty()) continue;
            String key = id;
            if (m.containsKey(key)) {   // 같은 이름의 다른 버전: [Lv.xx] 표시를 붙임 (예: 천공의심판자클레이모어(js))
                java.util.regex.Matcher lv = java.util.regex.Pattern.compile("\\[Lv\\.([^\\]]+)\\]").matcher(Msg.name(sup.get()));
                if (lv.find()) key = id + "(" + lv.group(1) + ")";
            }
            for (int i = 2; m.containsKey(key); i++) key = id + i;
            m.put(key, sup);
        }
        return giveIdCache = m;
    }

    static String giveId(String plain) {
        String n = plain.replaceAll("^(\\s*\\[[^\\]]*\\])+\\s*", "").replace("•", "").replace(" ", "");
        return n.isEmpty() ? plain.replace(" ", "") : n;
    }

    /** 우편 전체 지급 확인 대기: 실행자 -> (명령어 내용, 만료 시각) */
    private final Map<String, Long> giveAllConfirm = new HashMap<>();

    /**
     * /전체지급 <손|특수아이템이름> [개수] [우편]
     * - 기본: 지금 접속 중인 모든 플레이어 인벤토리에 지급 (가득 차면 남는 건 우편함으로)
     * - 우편: 접속하지 않은 사람까지 서버에 기록된 모든 플레이어의 우편함으로 지급 (같은 명령을 10초 안에 한 번 더 입력해야 실행)
     */
    private void giveAllCommand(org.bukkit.command.CommandSender s, String l, String[] a) {
        if (a.length == 0) {
            Msg.send(s, "&e/" + l + " <손|이름> [개수] [우편]");
            Msg.send(s, "&7- &f손 &7: 손에 든 아이템을 그대로 지급   &7- &f이름 &7: /아이템 과 같은 특수 아이템 이름 (Tab)");
            Msg.send(s, "&7- &f우편 &7: 오프라인 포함 전체 플레이어 우편함으로 지급 (한 번 더 입력해 확인)");
            return;
        }
        ItemStack sample;
        if (a[0].equals("손")) {
            if (!(s instanceof Player sp) || It.isAir(sp.getInventory().getItemInMainHand())) {
                Msg.send(s, "&c손에 지급할 아이템을 들고 사용하세요. &7(콘솔에서는 아이템 이름을 적어 주세요)");
                return;
            }
            sample = sp.getInventory().getItemInMainHand().clone();
        } else {
            java.util.function.Supplier<ItemStack> sup = giveIds().get(a[0]);
            if (sup == null) for (var en : giveIds().entrySet()) if (en.getKey().equalsIgnoreCase(a[0])) sup = en.getValue();
            if (sup == null) {
                Msg.send(s, "&c알 수 없는 아이템입니다: &f" + a[0] + " &7(Tab 으로 이름을 확인하세요)");
                return;
            }
            sample = sup.get();
        }
        int count = a[0].equals("손") ? sample.getAmount() : 1;
        if (a.length >= 2 && !a[1].equals("우편")) {
            try {
                count = Math.max(1, Math.min(2304, Integer.parseInt(a[1])));
            } catch (NumberFormatException ex) {
                Msg.send(s, "&c개수는 숫자로 적어 주세요.");
                return;
            }
        }
        boolean mail = java.util.Arrays.asList(a).contains("우편");
        sample.setAmount(1);
        String label = Items.describeShort(sample) + " &7x " + count;
        if (mail) {
            String key = s.getName() + "|" + String.join(" ", a);
            long now = System.currentTimeMillis();
            Long until = giveAllConfirm.get(s.getName());
            String pending = giveAllPending.get(s.getName());
            if (until == null || until < now || !key.equals(pending)) {
                giveAllConfirm.put(s.getName(), now + 10_000);
                giveAllPending.put(s.getName(), key);
                Msg.send(s, "&e[!] &f서버에 기록된 &e모든 플레이어&f의 우편함으로 " + label + " &f를 보냅니다.");
                Msg.send(s, "&e[!] &7맞으면 &f10초 안에 같은 명령어를 한 번 더 &7입력하세요.");
                return;
            }
            giveAllConfirm.remove(s.getName());
            giveAllPending.remove(s.getName());
            final int c = count;
            int n = plugin.store().forAll(d -> {
                int left = c, max = Math.max(1, sample.getMaxStackSize());
                while (left > 0) {
                    ItemStack part = sample.clone();
                    part.setAmount(Math.min(max, left));
                    left -= part.getAmount();
                    d.mailbox.add(part);
                }
            });
            for (Player o : Bukkit.getOnlinePlayers()) Msg.send(o, "&a&l[전체 지급] &f" + label + " &f이(가) &e우편함&f으로 지급되었습니다. &7(/우편함)");
            Msg.send(s, "&a[!] &f" + n + "명의 우편함으로 " + label + " &f지급을 마쳤습니다.");
            plugin.getLogger().info("[전체 지급] " + s.getName() + " -> 우편 " + n + "명 : " + Msg.plainOf(label));
            return;
        }
        int n = 0, mailed = 0;
        for (Player o : Bukkit.getOnlinePlayers()) {
            int left = count, max = Math.max(1, sample.getMaxStackSize());
            boolean toMail = false;
            while (left > 0) {
                ItemStack part = sample.clone();
                part.setAmount(Math.min(max, left));
                left -= part.getAmount();
                for (ItemStack rest : o.getInventory().addItem(part).values()) {   // 가득 차면 우편함으로
                    plugin.module(MailModule.class).send(o.getUniqueId(), rest);
                    toMail = true;
                }
            }
            if (toMail) mailed++;
            n++;
            Msg.send(o, "&a&l[전체 지급] &f" + label + " &f이(가) 지급되었습니다." + (toMail ? " &7(인벤토리가 가득 차 일부는 우편함으로)" : ""));
            Msg.sound(o, "entity.player.levelup", 0.6f, 1.6f);
        }
        Msg.send(s, "&a[!] &f접속 중인 " + n + "명에게 " + label + " &f지급했습니다." + (mailed > 0 ? " &7(" + mailed + "명은 일부 우편함으로)" : ""));
        plugin.getLogger().info("[전체 지급] " + s.getName() + " -> 접속자 " + n + "명 : " + Msg.plainOf(label));
    }

    private final Map<String, String> giveAllPending = new HashMap<>();

    private void giveCommand(org.bukkit.command.CommandSender s, String l, String[] a) {
        if (a.length == 0) {
            Msg.send(s, "&e/" + l + " <이름> [플레이어] [개수] &7- 특수 아이템 지급 (Tab 으로 이름 자동완성)");
            Msg.send(s, "&7" + String.join("&8, &7", giveIds().keySet()));
            return;
        }
        java.util.function.Supplier<ItemStack> sup = giveIds().get(a[0]);
        if (sup == null) for (var en : giveIds().entrySet()) if (en.getKey().equalsIgnoreCase(a[0])) sup = en.getValue();
        if (sup == null) {
            Msg.send(s, "&c알 수 없는 아이템입니다: &f" + a[0] + " &7(Tab 으로 이름을 확인하세요)");
            return;
        }
        Player target = a.length >= 2 ? Bukkit.getPlayerExact(a[1]) : s instanceof Player sp ? sp : null;
        if (target == null) {
            Msg.send(s, a.length >= 2 ? "&c접속 중인 플레이어가 아닙니다: &f" + a[1] : "&c콘솔에서는 플레이어 이름을 적어 주세요.");
            return;
        }
        int count = 1;
        if (a.length >= 3) {
            try {
                count = Math.max(1, Math.min(2304, Integer.parseInt(a[2])));
            } catch (NumberFormatException ex) {
                Msg.send(s, "&c개수는 숫자로 적어 주세요.");
                return;
            }
        }
        ItemStack sample = sup.get();
        if (sample.getMaxStackSize() > 1) {
            sample.setAmount(1);
            It.give(target, sample, count);
        } else for (int i = 0; i < count; i++) It.give(target, sup.get());
        Msg.send(s, "&a[!] &f" + target.getName() + "&7에게 " + Items.describeShort(sample) + " &7x " + count + " 지급했습니다.");
    }

    private static void hurt(LivingEntity e, double amount) {
        e.setHealth(Math.max(0, e.getHealth() - amount));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (probing) return;
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        Player attacker = e.getDamager() instanceof Player p ? p : null;
        Player shooter = e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player sp ? sp : null;
        Location vl = victim.getLocation();

        if (attacker != null) {
            String tool = Msg.name(attacker.getInventory().getItemInMainHand());

            // 고정 데미지 무기 먼저
            if (tool.contains(Items.N_CHAOS)) {
                e.setDamage(plugin.getConfig().getDouble("weapons.chaos.damage", 6.5));
                effect(victim, PotionEffectType.SLOWNESS, 2.5, 2);
                Location mid = vl.clone().add(0, 1, 0);
                Fx.p(mid, Particle.SOUL_FIRE_FLAME, 40, 0.8, 0.05);
                Fx.p(mid, Particle.END_ROD, 25, 0.8, 0.05);
                Fx.p(mid, Particle.SQUID_INK, 14, 0.4, 0.05);
                Fx.fade(mid, CHAOS_D, CHAOS_L, 1.4f, 24, 0.6);
                if (chance(0.10)) {
                    victim.setHealth(victim.getHealth() / 2);
                    // 혼돈의 재앙: 어둠이 빨려 들어간 뒤 터짐
                    Fx.implode(mid, CHAOS_D, 2.4, 10);
                    later(10, () -> {
                        Fx.p(mid, Particle.SONIC_BOOM, 1, 0, 0);
                        Fx.p(mid, Particle.SCULK_SOUL, 30, 0.6, 0.08);
                        Fx.shockwave(vl, CHAOS_L, 3.5, 8);
                        Msg.soundAt(mid, "entity.warden.sonic_boom", 0.6f, 1.4f);
                    });
                }
            }
            if (tool.contains(Items.N_RUINER_SCYTHE)) {
                e.setDamage(6);
                effect(victim, PotionEffectType.SLOWNESS, 2.5, 1);
                Location mid = vl.clone().add(0, 1, 0);
                Fx.p(mid, Particle.FLAME, 20, 0.6, 0.05);
                Fx.p(mid, Particle.LAVA, 8, 0.5, 0.05);
                Fx.p(mid, Particle.SWEEP_ATTACK, 2, 0.3, 0);
                if (chance(0.20)) {
                    hurt(victim, 2.5);
                    attacker.setHealth(Math.min(maxHealth(attacker), attacker.getHealth() + 5));
                    // 생명 흡수
                    Fx.p(mid, Particle.DAMAGE_INDICATOR, 10, 0.3, 0.1);
                    Fx.p(attacker.getLocation().add(0, 2.1, 0), Particle.HEART, 5, 0.4, 0);
                    Msg.soundAt(mid, "entity.player.hurt_sweet_berry_bush", 0.8f, 0.6f);
                }
            }
            if (tool.contains(Items.N_QUANTUM_SWORD) && chance(0.20)) {
                e.setDamage(e.getDamage() * 1.15);
                // 양자 붕괴: 황금 고리 + 빛 입자
                Location mid = vl.clone().add(0, 1, 0);
                Fx.ring(mid, 0.9, 24, GOLD, 1.3f);
                Fx.ring(mid.clone().add(0, 0.3, 0), 0.6, 16, GOLD_L, 1.0f);
                Fx.burst(mid, Particle.END_ROD, 18, 0.15);
                Fx.p(mid, Particle.ENCHANT, 30, 0.5, 0.6);
                Msg.soundAt(mid, "block.amethyst_block.resonate", 1, 1.6f);
            }
            if (tool.contains(Items.N_SEALED_SWORD) && !chaining) {
                long nowMs = System.currentTimeMillis();
                if (chance(plugin.getConfig().getDouble("weapons.sealed.crit-chance", 0.20))) {
                    // 제왕의 일격: 데미지 1.3배 + 얼음 파편
                    e.setDamage(e.getDamage() * plugin.getConfig().getDouble("weapons.sealed.crit-multiplier", 1.3));
                    Location mid = vl.clone().add(0, 1, 0);
                    Fx.block(mid, Particle.BLOCK, Material.PACKED_ICE.createBlockData(), 30, 0.4);
                    Fx.ring(vl.clone().add(0, 0.1, 0), 1.2, 28, ICE, 1.4f);
                    Msg.soundAt(mid, "block.glass.break", 0.8f, 1.6f);
                }
                // 냉기 연쇄: 우클릭 혹한의 연쇄 발동 중에만
                if (chainBoostUntil.getOrDefault(attacker.getUniqueId(), 0L) > nowMs) {
                    chainFrost(attacker, victim, plugin.getConfig().getInt("weapons.sealed.boost-max", 5));
                }
            }
            if (tool.contains(Items.N_THUNDER) && chance(0.10)) {
                Fx.lightning(vl);
                hurt(victim, 4);   // 고정 피해 2칸
                // 천둥의 심판: 번개 기둥 + 전기 스파크
                Fx.pillar(vl, 6, THUNDER, Particle.ELECTRIC_SPARK);
                Fx.burst(vl.clone().add(0, 1, 0), Particle.ELECTRIC_SPARK, 40, 0.4);
                Fx.shockwave(vl.clone().add(0, 0.1, 0), THUNDER, 2.5, 6);
            }
            int stage = Items.chronosStage(attacker.getInventory().getItemInMainHand());
            if (stage >= 3 && chance(0.175)) {
                effect(victim, PotionEffectType.WEAKNESS, 4, 1);
                timeClock(vl.clone().add(0, 1, 0));
            }
            if (stage >= 5 && chance(0.175)) {
                e.setDamage(e.getDamage() * 1.25);
                Location mid = vl.clone().add(0, 1, 0);
                Fx.block(mid, Particle.FALLING_DUST, Material.SAND.createBlockData(), 30, 0.5);
                Fx.burst(mid, Particle.CRIT, 25, 0.4);
                Fx.fade(mid, SAND, SAND_G, 1.3f, 20, 0.5);
            }
            if (sandStormUntil.getOrDefault(attacker.getUniqueId(), 0L) > System.currentTimeMillis()) {
                effect(victim, PotionEffectType.WEAKNESS, 7, 2);
                effect(victim, PotionEffectType.SLOWNESS, 7, 3);
                effect(victim, PotionEffectType.POISON, 7, 2);
                Location mid = vl.clone().add(0, 1, 0);
                Fx.block(mid, Particle.FALLING_DUST, Material.SAND.createBlockData(), 25, 0.6);
                Fx.p(mid, Particle.WHITE_ASH, 30, 0.6, 0.02);
                Fx.fade(mid, SAND, SAND_P, 1.2f, 16, 0.6);
            }
            // 클레이모어 · 프로스트 그레이브 파티클 (컬렉션 화면에서 무기별로 끌 수 있음)
            boolean fx = !data(attacker).fxOff.contains(tool.equals(Items.N_FROST) ? "frost" : "claymore");
            if (tool.equals(Items.N_CLAYMORE_SD)) {
                // 플레이어 타격: 방어구를 무시하고 체력을 바로 깎음 (기본 20 = 하트 10칸)
                // 남은 체력이 그 이하라면 하트 1칸(min-health)만 남김, 이미 1칸 이하면 일반 공격으로 마무리
                if (victim instanceof Player) {
                    double tdmg = plugin.getConfig().getDouble("weapons.claymore.true-damage", 20);
                    double keep = plugin.getConfig().getDouble("weapons.claymore.min-health", 2);
                    if (tdmg > 0 && victim.getHealth() > keep) {
                        victim.setHealth(Math.max(keep, victim.getHealth() - tdmg));
                        e.setDamage(0.0001);   // 맞는 연출/넉백만
                    }
                } else {
                    double hit = plugin.getConfig().getDouble("weapons.claymore.hit-damage", 0);
                    if (hit > 0) e.setDamage(hit);
                }
                long now = System.currentTimeMillis();
                if (claymoreCd.getOrDefault(attacker.getUniqueId(), 0L) <= now) {
                    if (fx) Fx.lightning(vl);
                    effect(victim, PotionEffectType.BLINDNESS, 3, 1);
                    effect(victim, PotionEffectType.SLOWNESS, 3, 10);
                    claymoreCd.put(attacker.getUniqueId(), now + (long) (plugin.getConfig().getDouble("weapons.claymore.judgment-cooldown-seconds", 30) * 1000));
                    Msg.soundAt(vl, "entity.lightning_bolt.impact", 1, 1);
                    if (fx) Fx.p(vl.clone().add(0, 1.5, 0), Particle.TOTEM_OF_UNDYING, 200, 1.5, 0.05, 1.5, 0.5);
                    // 심판의 빛: 하늘에서 내려오는 황금 기둥 + 충격파
                    if (fx) Fx.pillar(vl, 12, ARBITER, Particle.END_ROD);
                    if (fx) Fx.shockwave(vl.clone().add(0, 0.1, 0), ARBITER, 4.0, 10);
                    if (fx) Fx.ring(vl.clone().add(0, 2.4, 0), 0.5, 16, ARBITER_L, 1.2f);
                    if (fx) Fx.burst(vl.clone().add(0, 1, 0), Particle.ENCHANTED_HIT, 40, 0.5);
                    double jd = plugin.getConfig().getDouble("weapons.claymore.judgment-damage", 6);
                    double keepJ = plugin.getConfig().getDouble("weapons.claymore.min-health", 2);
                    if (victim.getHealth() <= jd + keepJ) victim.setHealth(Math.min(keepJ, victim.getHealth()));   // 심판의 빛으로는 죽지 않음
                    else hurt(victim, jd);
                    judgmentSplash(attacker, victim, vl);
                }
            }
            if (tool.equals(Items.N_FROST)) {
                effect(victim, PotionEffectType.SLOWNESS, 3, 2);
                effect(victim, PotionEffectType.WEAKNESS, 3, 1);
                Location mid = vl.clone().add(0, 1, 0);
                if (fx) Fx.p(mid, Particle.SNOWFLAKE, 14, 0.4, 0.03);
                if (fx) Fx.fade(mid, FROST, FROST_L, 1.2f, 12, 0.4);
                if (!(victim instanceof Player) && chance(0.15)) {
                    e.setDamage(999999);
                    // 아이스 블래스트: 얼음 폭발 + 눈보라 충격파
                    if (fx) Fx.block(mid, Particle.BLOCK, Material.BLUE_ICE.createBlockData(), 60, 0.5);
                    if (fx) Fx.burst(mid, Particle.SNOWFLAKE, 70, 0.25);
                    if (fx) Fx.shockwave(vl.clone().add(0, 0.1, 0), FROST, 3.5, 8);
                    if (fx) Fx.sphere(mid, 1.3, 40, FROST_L, 1.1f);
                    Msg.actionBar(attacker, "<gradient:#39C7FF:#D2FFF8:#00FFFF><italic:false><b>[❆] 즉시 처치가 발동되었습니다.</b></gradient>");
                    Msg.sound(attacker, "block.glass.break", 1, 1.5f);
                }
            }
            if (tool.contains(Items.N_EMBER)) ember(attacker, victim, e);
            boolean melee = e.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK;
            if (tool.contains("[✦] " + Items.N_SHADE_OLD)) renameLegacy(attacker, Items.umbralChain());
            if (tool.contains("[✦] " + Items.N_BELL_OLD)) renameLegacy(attacker, Items.echoBell());
            if (tool.contains("[✦] " + Items.N_TIME_WIND_OLD)) renameLegacy(attacker, Items.timePiecerWind());
            if ((tool.contains(Items.N_SHADE) || tool.contains("[✦] " + Items.N_SHADE_OLD)) && melee) shade(attacker, victim);
            if ((tool.contains(Items.N_BELL) || tool.contains("[✦] " + Items.N_BELL_OLD)) && melee) bell(attacker, victim);
            if ((tool.contains(Items.N_TIME_WIND) || tool.contains("[✦] " + Items.N_TIME_WIND_OLD)) && chance(0.275)) {
                for (Player near : victim.getWorld().getPlayers()) {
                    if (near.getLocation().distanceSquared(vl) <= 100) near.playSound(vl, "entity.wither.shoot", 1, 1);
                }
                victim.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 20, 34));
                // 시간의 바람: 초록-분홍 회오리로 띄워 올림
                Fx.helix(victim, WIND, WIND_P, 0.8, 3.5, 20);
                Fx.p(vl.clone().add(0, 0.5, 0), Particle.GUST, 3, 0.4, 0);
                Fx.p(vl.clone().add(0, 0.5, 0), Particle.CLOUD, 25, 0.5, 0.05);
                later(20, () -> {
                    if (victim.isDead()) return;
                    victim.setVelocity(new Vector(0, -5, 0));
                    // 내리꽂힌 자리에 바람 충격파
                    later(6, () -> {
                        Location land = victim.getLocation();
                        Fx.shockwave(land.clone().add(0, 0.1, 0), WIND_P, 3.0, 8);
                        Fx.p(land.clone().add(0, 0.3, 0), Particle.CLOUD, 40, 1.0, 0.08);
                        Fx.p(land.clone().add(0, 0.3, 0), Particle.GUST_EMITTER_SMALL, 1, 0, 0);
                    });
                });
            }
        }

        if (shooter != null && Msg.nameHas(shooter.getInventory().getItemInMainHand(), Items.N_VOID_BOW)) {
            effect(victim, PotionEffectType.SLOWNESS, 3, 2);
            Location mid = vl.clone().add(0, 1, 0);
            Fx.p(mid, Particle.REVERSE_PORTAL, 20, 0.4, 0.05);
            if (chance(0.25)) {
                e.setDamage(e.getDamage() * 1.5);
                Msg.soundAt(vl, "entity.wither.shoot", 1, 1.5f);
                Fx.lightning(vl);
                // 심연의 속삭임: 공허로 빨려 들어감
                Fx.implode(mid, VOID_D, 2.0, 8);
                Fx.p(mid, Particle.PORTAL, 80, 0.6, 1.0);
                later(8, () -> {
                    Fx.burst(mid, Particle.END_ROD, 30, 0.2);
                    Fx.ring(mid, 1.4, 30, VOID_C, 1.2f);
                });
            }
        }

        // 피격자 장비
        if (victim instanceof Player vp) {
            if (Msg.nameHas(vp.getInventory().getBoots(), Items.N_AURORA_BOOTS) && chance(0.20)) {
                e.setDamage(e.getDamage() * 0.8);
                // 오로라 보호막
                Location mid = vl.clone().add(0, 1, 0);
                Fx.sphere(mid, 1.1, 36, AURORA1, 1.0f);
                Fx.fade(mid, AURORA2, AURORA3, 1.3f, 24, 0.7);
                Fx.ring(vl.clone().add(0, 0.1, 0), 1.0, 20, AURORA3, 1.1f);
                Msg.soundAt(mid, "block.amethyst_cluster.hit", 1, 1.4f);
            }
            if (Msg.nameHas(vp.getInventory().getChestplate(), Items.N_VOID_CHEST) && chance(0.20)) {
                LivingEntity src = attacker != null ? attacker
                        : shooter != null ? shooter
                        : e.getDamager() instanceof LivingEntity le ? le : null;
                if (src != null) {
                    double reflect = e.getDamage();
                    later(1, () -> src.damage(reflect));
                    // 뒤틀린 차원: 공격자에게 돌아가는 공허 줄기
                    Fx.line(vl.clone().add(0, 1.2, 0), src.getLocation().add(0, 1, 0), VOID_C, 1.1f, 0.25);
                    Fx.p(src.getLocation().add(0, 1, 0), Particle.REVERSE_PORTAL, 30, 0.4, 0.1);
                }
                Fx.ring(vl.clone().add(0, 1, 0), 0.9, 22, VOID_W, 1.2f);
                Msg.soundAt(vl, "block.anvil.land", 0.5f, 2);
            }
            if (Msg.nameHas(vp.getInventory().getHelmet(), Items.N_RUSSELL) && chance(0.20)) {
                e.setDamage(0);
                Msg.soundAt(vl, "entity.illusioner.prepare_mirror", 1, 2);
                // 러셀의 패러독스: 데미지를 지워버리는 철학의 방패
                Location mid = vl.clone().add(0, 1, 0);
                Fx.sphere(mid, 1.25, 44, PHILO, 1.0f);
                Fx.sphere(mid, 0.9, 24, PHILO_L, 0.8f);
                Fx.p(mid, Particle.ENCHANT, 40, 0.6, 0.8);
                Fx.burst(mid, Particle.END_ROD, 10, 0.08);
            }
            // 원본은 서버에 프로스트 그레이브 소지자가 있으면 모든 피해가 줄어드는 버그가 있어, 소지자 본인만 감소하도록 수정
            if (hasFrost(vp)) {
                e.setDamage(e.getDamage() * 0.8);
                Fx.p(vl.clone().add(0, 1, 0), Particle.SNOWFLAKE, 6, 0.4, 0.01);
            }
        }
    }

    private static boolean hasFrost(Player p) {
        for (ItemStack it : p.getInventory().getContents()) if (Msg.nameHas(it, Items.N_FROST.substring(Items.N_FROST.indexOf("프로스트")))) return true;
        return false;
    }

    private static boolean hasClaymore(Player p) {
        for (ItemStack it : p.getInventory().getContents()) if (Msg.nameHas(it, "천공의 심판자 클레이모어")) return true;
        return false;
    }

    /* ======================= 망령 수확자 (예전 Soul Splitter) 처치 카운터 ======================= */

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        // 플레이어 또는 적대 몹 (좀비/스켈레톤뿐 아니라 슬라임, 가스트, 팬텀, 셜커 등 포함)
        if (!(e.getEntity() instanceof Player) && !(e.getEntity() instanceof org.bukkit.entity.Enemy)) return;
        ItemStack tool = killer.getInventory().getItemInMainHand();
        if (!Items.isSoulSplitter(tool)) return;
        ItemMeta meta = tool.getItemMeta();
        int id = Items.soulId(plugin, meta);
        int k = global().swordKills.merge(id, 1, Integer::sum);
        plugin.store().saveGlobal();
        meta.displayName(Msg.item(Items.SOUL_SPLITTER_NAME));   // 예전 Soul Splitter 도 망령 수확자로
        meta.lore(Msg.lore(Items.soulSplitterLore(k)));
        tool.setItemMeta(meta);
        if (k == 50) {
            tool.addUnsafeEnchantment(Enchantment.SHARPNESS, 4);
            Msg.send(killer, "<gradient:#8A5CFF:#5FFBF1><b>[♱ 망령 수확]</b></gradient> <color:#8A5CFF>첫 번째 각성</color> &7- 망령 50을 거두었습니다. &f날카로움 IV");
            Msg.soundAt(killer.getLocation(), "entity.player.levelup", 1, 1);
        } else if (k == 100) {
            tool.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
            tool.addUnsafeEnchantment(Enchantment.LOOTING, 4);
            Msg.send(killer, "<gradient:#8A5CFF:#5FFBF1><b>[♱ 망령 수확]</b></gradient> <color:#7FA8FF>두 번째 각성</color> &7- 망령 100을 거두었습니다. &f날카로움 V · 약탈 IV");
            Msg.soundAt(killer.getLocation(), "entity.player.levelup", 1, 1);
        } else if (k == 250) {
            tool.addUnsafeEnchantment(Enchantment.SHARPNESS, 6);
            tool.addUnsafeEnchantment(Enchantment.LOOTING, 5);
            Msg.send(killer, "<gradient:#8A5CFF:#5FFBF1><b>[♱ 망령 수확]</b></gradient> <color:#5FFBF1>마지막 각성</color> &7- 망령 250을 거두었습니다. &f날카로움 VI · 약탈 V");
            Msg.soundAt(killer.getLocation(), "entity.player.levelup", 1, 1);
        }
        killer.getInventory().setItemInMainHand(tool);
        // 영혼 수확
        Location dl = e.getEntity().getLocation().add(0, 1, 0);
        Fx.p(dl, Particle.SOUL, 10, 0.3, 0.03);
        Fx.line(dl, killer.getLocation().add(0, 1, 0), SOUL_P, 0.8f, 0.35);
        if (k == 50 || k == 100 || k == 250) {
            Fx.helix(killer, SOUL, SOUL_P, 1.0, 2.6, 40);
            Fx.burst(killer.getLocation().add(0, 1, 0), Particle.SOUL_FIRE_FLAME, 50, 0.12);
            Fx.shockwave(killer.getLocation().add(0, 0.1, 0), SOUL, 3.0, 10);
        }
    }

    /* ======================= 잿불 낙인검 ======================= */

    /**
     * 범위 스킬 대상: 주변의 플레이어와 적대 몹 중 실제로 때릴 수 있는 대상만.
     * 플레이어는 0 피해 이벤트로 확인해서 보호 구역(PVP 금지), 팀원, 크리에이티브는 제외된다.
     */
    private java.util.List<LivingEntity> aoeTargets(Player attacker, Location center, double radius) {
        java.util.List<LivingEntity> out = new java.util.ArrayList<>();
        for (org.bukkit.entity.Entity en : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(en instanceof LivingEntity le) || le == attacker || le.isDead()) continue;
            if (le.getLocation().distanceSquared(center) > radius * radius) continue;
            // PvP 금지 구역 안의 대상은 확인 없이 제외 (확인 공격을 보내면 WorldGuard 가 "Hey!" 경고를 보냄)
            if (plugin.hooks().pvpDenied(le.getLocation())) continue;
            if (le instanceof Player t) {
                if (t.getGameMode() == org.bukkit.GameMode.CREATIVE || t.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
                EntityDamageByEntityEvent probe = new EntityDamageByEntityEvent(attacker, t, org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                        org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).withDirectEntity(attacker).withCausingEntity(attacker).build(), 0.0);
                probing = true;
                try {
                    Bukkit.getPluginManager().callEvent(probe);
                } finally {
                    probing = false;
                }
                if (probe.isCancelled()) continue;
            } else if (!(le instanceof org.bukkit.entity.Enemy)) continue;
            out.add(le);
        }
        return out;
    }

    /** 예전 영어 이름의 보조 무기를 쓰는 순간 한국어 이름/설명으로 바꿈 */
    private static void renameLegacy(Player p, ItemStack fresh) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        ItemMeta m = hand.getItemMeta(), f = fresh.getItemMeta();
        if (m == null || f == null) return;
        m.displayName(f.displayName());
        m.lore(f.lore());
        hand.setItemMeta(m);
    }

    private void ember(Player attacker, LivingEntity victim, EntityDamageByEntityEvent e) {
        long now = System.currentTimeMillis();
        long window = (long) (plugin.getConfig().getDouble("weapons.ember.window-seconds", 4) * 1000);
        String key = attacker.getUniqueId() + "|" + victim.getUniqueId();
        long[] b = brands.get(key);
        // 연타 방지: 검 공격 속도(약 0.6초)보다 빨리 들어온 적중은 낙인을 쌓지 않음
        if (b != null && now - b[1] < plugin.getConfig().getLong("weapons.ember.min-interval-ms", 450)) return;
        if (b == null || now - b[1] > window) {
            int lo = plugin.getConfig().getInt("weapons.ember.min-stacks", 4), hi = Math.max(lo, plugin.getConfig().getInt("weapons.ember.max-stacks", 7));
            b = new long[]{0, now, ThreadLocalRandom.current().nextInt(lo, hi + 1)};
        }
        b[0]++;
        b[1] = now;
        Location mid = victim.getLocation().add(0, 1, 0);
        if (b[0] >= b[2]) {
            brands.remove(key);
            double extra = plugin.getConfig().getDouble("weapons.ember.burst-damage", 4.0);
            e.setDamage(e.getDamage() + extra);
            victim.setFireTicks(Math.max(victim.getFireTicks(), (int) (plugin.getConfig().getDouble("weapons.ember.fire-seconds", 3) * 20)));
            // 낙인 폭발: 불꽃이 모였다가 터짐
            Fx.burst(mid, Particle.FLAME, 40, 0.18);
            Fx.p(mid, Particle.LAVA, 12, 0.5, 0.05);
            Fx.p(mid, Particle.SMALL_FLAME, 30, 0.6, 0.05);
            Fx.ring(victim.getLocation().add(0, 0.1, 0), 1.3, 30, EMBER, 1.5f);
            Fx.sphere(mid, 0.8, 26, EMBER_L, 1.0f);
            Msg.soundAt(mid, "entity.blaze.shoot", 0.9f, 0.8f);
            Msg.soundAt(mid, "entity.generic.explode", 0.4f, 1.6f);
            Msg.actionBar(attacker, "<gradient:#FF3D00:#FFD36E><b>✹ 낙인 폭발!</b></gradient>");
        } else {
            brands.put(key, b);
            Fx.p(mid, Particle.SMALL_FLAME, 6 * (int) b[0], 0.35, 0.02);
            Fx.ring(mid.clone().add(0, 0.9, 0), 0.35, 8 + 3 * (int) b[0], EMBER, 0.8f);
            Msg.soundAt(mid, "item.firecharge.use", 0.4f, 1.2f + 0.3f * b[0]);
            Msg.actionBar(attacker, "&6잿불 낙인 " + "&6●".repeat((int) b[0]) + " &8(" + b[0] + " · 4~7개에서 폭발)");
        }
        if (brands.size() > 500) brands.values().removeIf(x -> now - x[1] > window);
    }

    /* ======================= 메아리 종 (보조 무기, 예전 Echo Bell) ======================= */

    private final java.util.Map<UUID, Long> bellCd = new java.util.HashMap<>();
    private static final org.bukkit.Color BELL = Fx.rgb(0xFFD86B), BELL_V = Fx.rgb(0x8A6BFF);

    /** 메아리 종: 대상에게 어둠 + 멀미, 공격자 반대쪽으로 밀쳐냄 */
    private void bell(Player attacker, LivingEntity victim) {
        long now = System.currentTimeMillis();
        Long last = bellCd.get(attacker.getUniqueId());
        if (last != null && now - last < plugin.getConfig().getLong("weapons.bell.cooldown-ms", 0)) return;
        if (!chance(plugin.getConfig().getDouble("weapons.bell.chance", 0.275))) return;
        bellCd.put(attacker.getUniqueId(), now);
        Location vl = victim.getLocation();
        for (Player near : victim.getWorld().getPlayers()) {
            if (near.getLocation().distanceSquared(vl) <= 100) {
                near.playSound(vl, "block.bell.use", 1, 0.8f);
                near.playSound(vl, "block.bell.resonate", 0.7f, 1.2f);
            }
        }
        effect(victim, PotionEffectType.DARKNESS, plugin.getConfig().getDouble("weapons.bell.darkness-seconds", 2.5), 1);
        effect(victim, PotionEffectType.NAUSEA, plugin.getConfig().getDouble("weapons.bell.nausea-seconds", 4), 1);
        Vector push = vl.toVector().subtract(attacker.getLocation().toVector()).setY(0);
        if (push.lengthSquared() < 1e-4) push = attacker.getLocation().getDirection().setY(0);
        if (push.lengthSquared() > 1e-4) victim.setVelocity(push.normalize().multiply(plugin.getConfig().getDouble("weapons.bell.knockback", 0.9)).setY(0.3));
        // 메아리: 금빛 → 보라 고리가 세 번 퍼짐
        Location mid = vl.clone().add(0, 1, 0);
        Fx.p(mid, Particle.NOTE, 8, 0.5, 1);
        for (int i = 0; i < 3; i++) {
            final int k = i;
            later(i * 4L, () -> {
                Fx.ring(vl.clone().add(0, 0.2 + k * 0.6, 0), 0.8 + k * 0.6, 20 + k * 8, k % 2 == 0 ? BELL : BELL_V, 1.2f);
                Fx.p(mid, Particle.SCULK_CHARGE_POP, 6, 0.4 + k * 0.2, 0.01);
            });
        }
    }

    /* ======================= 그림자 사슬 (보조 무기, 예전 Umbral Chain) ======================= */

    private final java.util.Map<UUID, Long> shadeCd = new java.util.HashMap<>();
    private static final org.bukkit.Color SHADE = Fx.rgb(0x4A6FA5), SHADE_L = Fx.rgb(0xE8F1F8);

    /** 그림자 사슬: 대상을 공격자 쪽으로 끌어당기고 잠시 묶음 (시간을 가르는 바람은 띄워 올림, 이건 끌어당김) */
    private void shade(Player attacker, LivingEntity victim) {
        long now = System.currentTimeMillis();
        Long last = shadeCd.get(attacker.getUniqueId());
        if (last != null && now - last < plugin.getConfig().getLong("weapons.shade.cooldown-ms", 0)) return;
        if (!chance(plugin.getConfig().getDouble("weapons.shade.chance", 0.275))) return;
        shadeCd.put(attacker.getUniqueId(), now);
        Location from = victim.getLocation().add(0, 1, 0), to = attacker.getLocation().add(0, 1, 0);
        for (Player near : victim.getWorld().getPlayers()) {
            if (near.getLocation().distanceSquared(from) <= 100) near.playSound(from, "block.chain.break", 1, 0.6f);
        }
        // 사슬이 날아가 감김: 공격자 → 대상 사슬 선 + 대상 주위 고리
        Fx.line(to, from, SHADE, 1.0f, 0.2);
        Fx.ring(victim.getLocation().add(0, 0.6, 0), 0.7, 18, SHADE, 1.1f);
        Fx.ring(victim.getLocation().add(0, 1.3, 0), 0.7, 18, SHADE_L, 1.0f);
        Fx.p(from, Particle.SQUID_INK, 14, 0.3, 0.02);
        Msg.soundAt(from, "item.trident.return", 0.8f, 0.7f);
        // 끌어당김
        later(4, () -> {
            if (victim.isDead() || !attacker.isOnline()) return;
            Vector pull = attacker.getLocation().toVector().subtract(victim.getLocation().toVector());
            double dist = pull.length();
            if (dist < 0.5) return;
            double power = plugin.getConfig().getDouble("weapons.shade.pull-strength", 1.1);
            victim.setVelocity(pull.normalize().multiply(Math.min(power, 0.25 + dist * 0.18)).setY(0.35));
            Fx.line(attacker.getLocation().add(0, 1, 0), victim.getLocation().add(0, 1, 0), SHADE_L, 0.8f, 0.25);
            Msg.soundAt(victim.getLocation(), "block.chain.place", 1, 0.8f);
        });
        // 묶임: 구속 + 점프 차단
        double bind = plugin.getConfig().getDouble("weapons.shade.bind-seconds", 1.5);
        effect(victim, PotionEffectType.SLOWNESS, bind, plugin.getConfig().getInt("weapons.shade.slow-level", 3));
        later(6, () -> {
            if (victim.isDead()) return;
            Fx.fade(victim.getLocation().add(0, 1, 0), SHADE, SHADE_L, 1.0f, 16, 0.5);
        });
    }

    /** 크로노스: 모래색 시계 고리 (눈금 12개) */
    private static void timeClock(Location c) {
        Fx.ring(c, 1.0, 36, SAND, 1.2f);
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            Fx.dust(c.clone().add(Math.cos(a) * 0.8, 0, Math.sin(a) * 0.8), SAND_P, 1.4f, 1, 0);
        }
        Fx.line(c, c.clone().add(0, 0.7, 0), SAND_G, 1.0f, 0.1);
    }

    /** 공허의 속삭임: 화살 꼬리 */
    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        if (!Msg.nameHas(e.getBow(), Items.N_VOID_BOW)) return;
        Fx.trail(e.getProjectile(), VOID_D, Particle.REVERSE_PORTAL, 100);
    }

    /* ======================= 우클릭 스킬 ======================= */

    private final Map<UUID, Long> skillBlockMsg = new HashMap<>();

    /** 왜 막혔는지 (안내 문구용) */
    private String skillBlockReason(Player p) {
        var cfg = plugin.getConfig();
        if (cfg.getBoolean("skills.block-in-spawn", true) && inWorld(p, "spawn")) return "스폰";
        for (String r : cfg.getStringList("skills.blocked-regions").isEmpty()
                ? java.util.List.of(cfg.getString("protect-region", "protect")) : cfg.getStringList("skills.blocked-regions"))
            if (plugin.hooks().inRegion(p.getLocation(), r)) return "보호구역";
        return "PvP 금지 구역";
    }

    /** 스폰 월드 · 보호구역 · PvP 금지 구역(WorldGuard)에서는 스킬 사용 불가 */
    public boolean skillBlocked(Player p) {
        var cfg = plugin.getConfig();
        Location loc = p.getLocation();
        if (cfg.getBoolean("skills.block-in-spawn", true) && inWorld(p, "spawn")) return true;
        for (String r : cfg.getStringList("skills.blocked-regions").isEmpty()
                ? java.util.List.of(cfg.getString("protect-region", "protect")) : cfg.getStringList("skills.blocked-regions"))
            if (plugin.hooks().inRegion(loc, r)) return true;
        return cfg.getBoolean("skills.block-where-pvp-denied", true) && plugin.hooks().pvpDenied(loc);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSkill(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        String n = Msg.name(hand);
        long now = System.currentTimeMillis();
        UUID id = p.getUniqueId();

        boolean skillItem = n.contains(Items.N_CHAOS) || n.contains(Items.N_DARK_WAVE) || n.contains(Items.N_SEALED_SWORD)
                || n.contains(Items.N_ETERNAL_CHRONOS) || Items.isNewChar(n);
        if (skillItem && skillBlocked(p)) {
            e.setCancelled(true);
            // 채팅이 아니라 액션바로 (연타해도 채팅이 밀리지 않게), 소리는 3초에 한 번
            Msg.actionBar(p, "&c&l[!] &c" + skillBlockReason(p) + "에서는 스킬을 사용할 수 없습니다.");
            if (now - skillBlockMsg.getOrDefault(id, 0L) > 3000) {
                skillBlockMsg.put(id, now);
                Msg.sound(p, "block.note_block.bass", 0.6f, 0.8f);
            }
            return;
        }

        if (n.contains(Items.N_CHAOS)) {
            e.setCancelled(true);   // 괭이로 땅을 갈지 않음
            if (chaosCd.getOrDefault(id, 0L) > now) {
                Msg.send(p, "&c아직 사용 할 수 없습니다.");
                return;
            }
            chaosCd.put(id, now + (long) (plugin.getConfig().getDouble("weapons.chaos.skill-cooldown-seconds", 45) * 1000));
            Location c = p.getLocation();
            double dmg = plugin.getConfig().getDouble("weapons.chaos.skill-damage", 4.0);
            for (LivingEntity t : aoeTargets(p, c, 5)) {
                if (t.getHealth() <= dmg) t.setHealth(Math.min(1, t.getHealth()));   // 스킬 피해로는 죽지 않음
                else hurt(t, dmg);
                effect(t, PotionEffectType.WITHER, 3, 2);
                Vector push = t.getLocation().toVector().subtract(c.toVector()).setY(0);
                if (push.lengthSquared() > 1e-4) t.setVelocity(push.normalize().multiply(0.9).setY(0.35));
                Fx.line(c.clone().add(0, 1, 0), t.getLocation().add(0, 1, 0), CHAOS_L, 0.9f, 0.3);
            }
            // 혼돈의 파열: 어둠이 모였다가 터짐
            Location mid = c.clone().add(0, 1, 0);
            Fx.implode(mid, CHAOS_D, 2.6, 10);
            later(10, () -> {
                Fx.shockwave(c.clone().add(0, 0.1, 0), CHAOS_L, 5.0, 12);
                Fx.p(mid, Particle.SQUID_INK, 60, 1.2, 0.05);
                Fx.p(mid, Particle.SCULK_SOUL, 30, 1.0, 0.05);
                Msg.soundAt(mid, "entity.warden.sonic_boom", 0.6f, 0.8f);
            });
            Msg.send(p, "&8&l[Chaos] &7혼돈의 파열을 사용했습니다.");
        } else if (n.contains(Items.N_DARK_WAVE) && hand.getType() == Material.MUSIC_DISC_11) {
            e.setCancelled(true);
            if (!p.isSneaking()) {
                if (waveCd1.getOrDefault(id, 0L) > now) {
                    Msg.send(p, "&c아직 사용 할 수 없습니다.");
                    return;
                }
                waveCd1.put(id, now + 60_000);
                Location c = p.getLocation();
                for (LivingEntity t : aoeTargets(p, c, 6)) {
                    effect(t, PotionEffectType.DARKNESS, 4, 1);
                    effect(t, PotionEffectType.SLOWNESS, 3, 3);
                    Vector push = t.getLocation().toVector().subtract(c.toVector()).setY(0);
                    if (push.lengthSquared() > 1e-4) t.setVelocity(push.normalize().multiply(1.1).setY(0.4));
                }
                // 어둠의 파동: 검은 고리가 세 겹으로 퍼짐
                for (int i = 0; i < 3; i++) {
                    final int k = i;
                    later(i * 4L, () -> Fx.shockwave(c.clone().add(0, 0.2 + k * 0.5, 0), k % 2 == 0 ? CHAOS_D : CHAOS_L, 2.0 + k * 2.0, 8));
                }
                Fx.p(c.clone().add(0, 1, 0), Particle.SQUID_INK, 50, 1.0, 0.05);
                Msg.soundAt(c, "entity.warden.roar", 0.5f, 1.4f);
                Msg.send(p, "&8&l[Chaos] &71번 스킬 &5어둠의 파동&7을 사용했습니다.");
            } else {
                if (waveCd2.getOrDefault(id, 0L) > now) {
                    Msg.send(p, "&c아직 사용 할 수 없습니다.");
                    return;
                }
                waveCd2.put(id, now + (long) (plugin.getConfig().getDouble("weapons.darkness-wave.veil-cooldown-seconds", 90) * 1000));
                // 어둠의 장막: 내 주변에 검은 연기가 따라다니며, 들어온 적에게 실명
                double radius = plugin.getConfig().getDouble("weapons.darkness-wave.veil-radius", 3);
                int ticks = (int) (plugin.getConfig().getDouble("weapons.darkness-wave.veil-seconds", 5) * 20);
                Msg.soundAt(p.getLocation(), "entity.warden.sonic_charge", 0.6f, 0.6f);
                for (int t = 0; t <= ticks; t += 5) {
                    final int tt = t;
                    later(t, () -> {
                        if (!p.isOnline() || p.isDead()) return;
                        Location c = p.getLocation();
                        Fx.p(c.clone().add(0, 0.8, 0), Particle.LARGE_SMOKE, 25, radius * 0.6, 0.01);
                        Fx.p(c.clone().add(0, 0.3, 0), Particle.SQUID_INK, 10, radius * 0.6, 0.01);
                        if (tt % 20 == 0) Fx.ring(c.clone().add(0, 0.1, 0), radius, 36, CHAOS_D, 1.2f);
                        for (LivingEntity tg : aoeTargets(p, c, radius)) effect(tg, PotionEffectType.BLINDNESS, 1.5, 1);
                    });
                }
                Msg.send(p, "&8&l[Chaos] &72번 스킬 &5어둠의 장막&7을 사용했습니다.");
            }
        } else if (n.contains(Items.N_SEALED_SWORD) && (hand.getType() == Material.DIAMOND_SWORD || hand.getType() == Material.NETHERITE_SWORD)) {
            e.setCancelled(true);
            if (sealedCd.getOrDefault(id, 0L) > now) {
                Msg.send(p, "&c아직 사용 할 수 없습니다.");
                return;
            }
            sealedCd.put(id, now + (long) (plugin.getConfig().getDouble("weapons.sealed.boost-cooldown-seconds", 40) * 1000));
            chainBoostUntil.put(id, now + (long) (plugin.getConfig().getDouble("weapons.sealed.boost-seconds", 6) * 1000));
            // 혹한의 연쇄: 몸에 냉기가 휘감김
            Fx.helix(p, ICE, ICE_L, 0.9, 2.5, 30);
            Fx.burst(p.getLocation().add(0, 1, 0), Particle.SNOWFLAKE, 60, 0.18);
            Fx.shockwave(p.getLocation().add(0, 0.1, 0), ICE, 3.0, 10);
            Msg.soundAt(p.getLocation(), "block.amethyst_block.resonate", 1, 0.6f);
            Msg.soundAt(p.getLocation(), "entity.player.hurt_freeze", 1, 0.8f);
            Msg.send(p, "<gradient:#00DDFF:#FFFFFF><b>[❄ Sealed]</b></gradient> &7혹한의 연쇄를 사용했습니다.");
        } else if (n.contains(Items.N_ETERNAL_CHRONOS)) {
            if (chronosCd.getOrDefault(id, 0L) > now) {
                Msg.send(p, "&c아직 사용 할 수 없습니다.");
                e.setCancelled(true);
                return;
            }
            effect(p, PotionEffectType.REGENERATION, 7, 10);
            effect(p, PotionEffectType.RESISTANCE, 7, 10);
            effect(p, PotionEffectType.STRENGTH, 7, 2);
            effect(p, PotionEffectType.GLOWING, 15, 1);
            Fx.p(p.getLocation().add(0, 1.5, 0), Particle.TOTEM_OF_UNDYING, 200, 1.5, 0.05, 1.5, 0.5);
            Msg.sound(p, "entity.generic.explode", 1, 2);
            // 모래 폭풍: 15초 동안 몸 주위를 도는 모래 회오리
            Fx.tornado(p, new org.bukkit.Color[]{SAND, SAND_P, SAND_G}, Particle.WHITE_ASH, 300);
            Fx.shockwave(p.getLocation().add(0, 0.1, 0), SAND, 4.5, 12);
            timeClock(p.getLocation().add(0, 2.4, 0));
            Msg.send(p, "&e모래 폭풍을 사용했습니다. 15초간 타격 시 강력한 디버프가 부여됩니다.");
            chronosCd.put(id, now + 60_000);
            sandStormUntil.put(id, now + 15_000);
            later(300, () -> {
                if (p.isOnline()) Msg.send(p, "&c모래 폭풍이 끝났습니다.");
            });
        } else if (Items.isNewChar(n) && hand.getType() == Material.MUSIC_DISC_5) {
            e.setCancelled(true);
            if (!n.contains(Items.N_NEW_CHAR)) {   // 예전 이름 -> 새 이름/설명
                ItemMeta nm = hand.getItemMeta(), fm = Items.newCharacteristic().getItemMeta();
                nm.displayName(fm.displayName());
                nm.lore(fm.lore());
                hand.setItemMeta(nm);
            }
            if (!p.isSneaking()) {
                if (newCharCd1.getOrDefault(id, 0L) > now) {
                    Msg.send(p, "&c아직 사용 할 수 없습니다.");
                    return;
                }
                effect(p, PotionEffectType.STRENGTH, 20, 3);
                Msg.sound(p, "block.portal.trigger", 1, 1.3f);
                Fx.p(p.getLocation(), Particle.WITCH, 300, 0.5, 1, 0.5, 0.1);
                // 초승달의 서약: 등 뒤의 초승달 + 남색 나선
                Fx.crescent(p.getEyeLocation(), MOON, MOON_P);
                later(5, () -> Fx.crescent(p.getEyeLocation(), MOON, MOON_P));
                later(10, () -> Fx.crescent(p.getEyeLocation(), MOON, MOON_P));
                Fx.helix(p, MOON, MOON_P, 0.8, 2.4, 30);
                Msg.send(p, "&5&l[🌙] &71번 스킬을 사용했습니다.");
                newCharCd1.put(id, now + 90_000);
            } else {
                if (newCharCd2.getOrDefault(id, 0L) > now) {
                    Msg.send(p, "&c아직 사용 할 수 없습니다.");
                    return;
                }
                effect(p, PotionEffectType.REGENERATION, 6, 5);
                Msg.sound(p, "block.portal.trigger", 1, 2);
                // 달빛 치유: 분홍 나선 + 하트
                Fx.helix(p, MOON_P, Fx.rgb(0xFFFFFF), 0.7, 2.2, 40);
                Fx.p(p.getLocation().add(0, 1.8, 0), Particle.HEART, 8, 0.5, 0);
                Fx.ring(p.getLocation().add(0, 0.1, 0), 1.0, 24, MOON_P, 1.1f);
                Msg.send(p, "&5&l[🌙] &72번 스킬을 사용했습니다.");
                Fx.p(p.getLocation(), Particle.WITCH, 500, 0.5, 1, 0.5, 0.1);
                newCharCd2.put(id, now + 90_000);
            }
        }
    }

    private static final net.kyori.adventure.text.TextReplacementConfig STRAY_CLOSE =
            net.kyori.adventure.text.TextReplacementConfig.builder().matchLiteral("</color>").replacement("").build();

    /** 이전 버전에서 지급되어 설명에 "</color>" 글자가 보이는 아이템을 고친다 */
    private static void repairLore(Player p, ItemStack hand) {
        if (It.isAir(hand) || !hand.hasItemMeta()) return;
        ItemMeta meta = hand.getItemMeta();
        java.util.List<net.kyori.adventure.text.Component> lore = meta.lore();
        if (lore == null) return;
        boolean broken = false;
        for (net.kyori.adventure.text.Component c : lore) if (Msg.plain(c).contains("</color>")) broken = true;
        if (!broken) return;
        java.util.List<net.kyori.adventure.text.Component> fixed = new java.util.ArrayList<>();
        for (net.kyori.adventure.text.Component c : lore) fixed.add(c.replaceText(STRAY_CLOSE));
        meta.lore(fixed);
        hand.setItemMeta(meta);
        p.getInventory().setItemInMainHand(hand);
    }

    /** 얼어붙은 제왕의 검 액션바: 혹한의 연쇄 (발동 중이면 남은 시간) */
    private String sealedBar(Player p, long now) {
        long boost = chainBoostUntil.getOrDefault(p.getUniqueId(), 0L) - now;
        if (boost > 0) return "<gradient:#00DDFF:#FFFFFF><b>❄ 혹한의 연쇄</b></gradient> &8» &b&l발동 중 &f" + ((boost + 999) / 1000) + "초";
        return skill(p, "<gradient:#00DDFF:#FFFFFF><b>❄ 혹한의 연쇄</b></gradient>", sealedCd, now,
                (long) (plugin.getConfig().getDouble("weapons.sealed.boost-cooldown-seconds", 40) * 1000), "&b", "우클릭", 12);
    }

    /** 냉기 연쇄: 맞은 대상에서 가까운 다른 적에게 차례로 튀며 피해 (최대 max 명) */
    private void chainFrost(Player attacker, LivingEntity first, int max) {
        double radius = plugin.getConfig().getDouble("weapons.sealed.chain-radius", 4);
        double dmg = plugin.getConfig().getDouble("weapons.sealed.chain-damage", 2.0);
        java.util.Set<UUID> hit = new java.util.HashSet<>();
        hit.add(first.getUniqueId());
        LivingEntity from = first;
        for (int i = 0; i < max; i++) {
            LivingEntity next = null;
            double best = Double.MAX_VALUE;
            for (LivingEntity t : aoeTargets(attacker, from.getLocation(), radius)) {
                if (hit.contains(t.getUniqueId())) continue;
                double d = t.getLocation().distanceSquared(from.getLocation());
                if (d < best) {
                    best = d;
                    next = t;
                }
            }
            if (next == null) break;
            hit.add(next.getUniqueId());
            final LivingEntity a = from, b = next;
            final int step = i;
            later(i * 2L + 1, () -> {   // 한 칸씩 이어지는 연출
                if (b.isDead()) return;
                Fx.line(a.getLocation().add(0, 1.1, 0), b.getLocation().add(0, 1.1, 0), ICE_L, 1.0f, 0.2);
                Fx.block(b.getLocation().add(0, 1, 0), Particle.BLOCK, Material.ICE.createBlockData(), 14, 0.3);
                Msg.soundAt(b.getLocation(), "block.amethyst_block.chime", 1, 1.2f + step * 0.15f);
                chaining = true;
                try {
                    b.damage(dmg, attacker);
                } finally {
                    chaining = false;
                }
            });
            from = next;
        }
        if (hit.size() > 1) Fx.burst(first.getLocation().add(0, 1, 0), Particle.SNOWFLAKE, 20, 0.12);
    }

    /**
     * 액션바 한 칸: 이름 » [■■■□□□] 12초  /  이름 » 사용 가능 (우클릭)
     * 쿨타임이 막 끝난 순간(1초 안)에는 짧은 알림음을 낸다.
     */
    private static String skill(Player p, String label, Map<UUID, Long> cd, long now, long totalMs, String color, String hint, int width) {
        long end = cd.getOrDefault(p.getUniqueId(), 0L);
        long ms = end - now;
        if (ms <= 0) {
            if (end > 0 && -ms < 1000) p.playSound(p.getLocation(), "block.note_block.pling", 0.6f, 1.8f);   // 다시 사용 가능
            return label + " &8» &a&l사용 가능 &8(" + hint + ")";
        }
        int filled = (int) Math.round(width * (1 - Math.min(1.0, (double) ms / Math.max(1, totalMs))));
        return label + " &8» &8[" + color + "▮".repeat(filled) + "&8" + "▮".repeat(width - filled) + "&8] &f" + ((ms + 999) / 1000) + "초";
    }


    /* ======================= 드롭 방지 ======================= */

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (!isKeepOnDeath(e.getItemDrop().getItemStack())) return;
        e.setCancelled(true);
        Msg.send(e.getPlayer(), "&c드롭 방지가 적용된 아이템은 버릴 수 없습니다.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastClaymore.remove(e.getPlayer().getUniqueId());
    }

    /**
     * 클레이모어 이동 속도. 월드 이동·리스폰·다른 플러그인이 속도를 되돌려도 매초 다시 맞춤.
     * 클레이모어가 없을 때는 들고 있다가 내려놓은 순간에만 기본값(0.2)으로 되돌림 (다른 속도 설정을 덮어쓰지 않도록)
     */
    private void applyWalk(Player p, boolean claymore, float fastWalk) {
        Boolean last = lastClaymore.put(p.getUniqueId(), claymore);
        if (claymore) {
            if (Math.abs(p.getWalkSpeed() - fastWalk) > 0.001f) p.setWalkSpeed(fastWalk);
        } else if (Boolean.TRUE.equals(last) || (last == null && Math.abs(p.getWalkSpeed() - fastWalk) < 0.001f)) {
            p.setWalkSpeed(0.2f);
        }
    }

    /** 월드 이동 / 리스폰 / 접속 직후 바로 다시 적용 (1초 기다리지 않음) */
    private void reapplyWalkSoon(Player p) {
        for (long delay : new long[]{2L, 20L}) {
            later(delay, () -> {
                if (!p.isOnline()) return;
                float fast = (float) plugin.getConfig().getDouble("health.claymore-walk-speed", 0.24);
                applyWalk(p, hasClaymore(p), fast);
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(org.bukkit.event.player.PlayerChangedWorldEvent e) {
        reapplyWalkSoon(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawnWalk(org.bukkit.event.player.PlayerRespawnEvent e) {
        reapplyWalkSoon(e.getPlayer());
    }

    /* ======================= 종말의 투구 ======================= */

    private static boolean chaosHelm(Player p) {
        return Msg.nameHas(p.getInventory().getHelmet(), Items.N_CHAOS_HELM);
    }

    /** 나약함 · 구속 면역 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChaosHelmImmune(org.bukkit.event.entity.EntityPotionEffectEvent e) {
        if (!(e.getEntity() instanceof Player p) || e.getNewEffect() == null) return;
        PotionEffectType t = e.getNewEffect().getType();
        if ((t.equals(PotionEffectType.WEAKNESS) || t.equals(PotionEffectType.SLOWNESS)) && chaosHelm(p)) e.setCancelled(true);
    }

    /** 체력 30% 미만이면 받는 피해 25% 감소 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChaosHelmReduce(org.bukkit.event.entity.EntityDamageEvent e) {
        if (probing || !(e.getEntity() instanceof Player p) || !chaosHelm(p)) return;
        var cfg = plugin.getConfig();
        if (p.getHealth() >= maxHealth(p) * cfg.getDouble("weapons.chaos-helmet.low-health", 0.3)) return;
        e.setDamage(e.getDamage() * (1 - cfg.getDouble("weapons.chaos-helmet.low-health-reduction", 0.20)));
        Fx.p(p.getLocation().add(0, 1, 0), Particle.SMOKE, 6, 0.3, 0.02);
    }

    /** 공격받으면 확률로 공격자 위더 I */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChaosHelmHurt(EntityDamageByEntityEvent e) {
        if (probing || !(e.getEntity() instanceof Player p) || !chaosHelm(p)) return;
        var cfg = plugin.getConfig();
        LivingEntity src = e.getDamager() instanceof LivingEntity le ? le
                : e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof LivingEntity sh ? sh : null;
        if (src == null || src == p || !chance(cfg.getDouble("weapons.chaos-helmet.wither-chance", 0.15))) return;
        effect(src, PotionEffectType.WITHER, cfg.getDouble("weapons.chaos-helmet.wither-seconds", 3), 1);
        Fx.p(src.getLocation().add(0, 1, 0), Particle.SQUID_INK, 10, 0.3, 0.03);
        Fx.fade(src.getLocation().add(0, 1, 0), CHAOS_D, CHAOS_L, 1.1f, 10, 0.4);
        Msg.soundAt(src.getLocation(), "entity.wither.shoot", 0.4f, 1.6f);
    }

    /* ======================= 재앙의 서막 바지: 죽음 거부 ======================= */

    /** 낙하 / 화염 피해 감소 (용암, 불, 불붙음, 마그마 블록, 모닥불 포함) */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRuinerReduce(org.bukkit.event.entity.EntityDamageEvent e) {
        if (probing || !(e.getEntity() instanceof Player p)) return;
        if (!Msg.nameHas(p.getInventory().getLeggings(), Items.N_RUINER_PANTS)) return;
        switch (e.getCause()) {
            case FALL, FIRE, FIRE_TICK, LAVA, HOT_FLOOR, CAMPFIRE ->
                    e.setDamage(e.getDamage() * plugin.getConfig().getDouble("weapons.ruiner-pants.fall-fire-multiplier", 0.5));
            default -> {
            }
        }
    }

    /** 치명상을 입으면 20% 확률로 버티고 체력 절반으로 돌아옴 (불사의 토템처럼, 토템은 쓰지 않음) */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethal(org.bukkit.event.entity.EntityDamageEvent e) {
        if (probing || !(e.getEntity() instanceof Player p)) return;
        if (e.getFinalDamage() < p.getHealth()) return;
        if (!Msg.nameHas(p.getInventory().getLeggings(), Items.N_RUINER_PANTS)) return;
        if (e.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.VOID || e.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.KILL) return;
        if (!chance(plugin.getConfig().getDouble("weapons.ruiner-pants.deny-death-chance", 0.2))) return;
        e.setCancelled(true);
        p.setHealth(Math.max(1, maxHealth(p) * 0.5));
        p.setFireTicks(0);
        // 죽음 거부: 붉은 기둥 + 토템 연출
        Location c = p.getLocation();
        Fx.burst(c.clone().add(0, 1, 0), Particle.TOTEM_OF_UNDYING, 60, 0.5);
        Fx.helix(p, RUIN_D, Fx.rgb(0xFF0000), 0.8, 2.6, 30);
        Fx.shockwave(c.clone().add(0, 0.1, 0), RUIN_D, 3.0, 10);
        Msg.soundAt(c, "item.totem.use", 0.8f, 0.8f);
        Msg.title(p, "<gradient:#FF0000:#530427><b>죽음을 거부했습니다</b></gradient>", "&7재앙의 서막 바지가 당신을 일으켜 세웁니다", 5, 30, 10);
    }

    /* ======================= 접속 시 특수 아이템 이름/설명 최신화 ======================= */

    /** 특수 아이템이면 이름/설명을 최신으로 바꿈 (인챈트, 내구도, 개수, 태그는 그대로). 바뀌었으면 true */
    public boolean refresh(ItemStack it) {
        if (It.isAir(it) || !it.hasItemMeta()) return false;
        String plain = Msg.name(it);
        if (plain.isEmpty()) return false;
        ItemMeta m = it.getItemMeta();
        net.kyori.adventure.text.Component name;
        java.util.List<net.kyori.adventure.text.Component> lore;
        boolean force = false;
        if (Items.isSoulSplitter(it)) {   // 처치 수가 들어간 설명
            force = !m.getPersistentDataContainer().has(new org.bukkit.NamespacedKey(plugin, "soul_id"));   // 번호를 새로 붙였으면 꼭 저장
            int id = Items.soulId(plugin, m);
            name = Msg.item(Items.SOUL_SPLITTER_NAME);
            lore = Msg.lore(Items.soulSplitterLore(global().swordKills.getOrDefault(id, 0)));
        } else {
            var sup = Items.template(plain);
            if (sup == null) return false;
            ItemMeta f = sup.get().getItemMeta();
            name = f.displayName();
            lore = f.lore();
        }
        if (!force && java.util.Objects.equals(m.displayName(), name) && java.util.Objects.equals(m.lore(), lore)) return false;
        m.displayName(name);
        m.lore(lore);
        it.setItemMeta(m);
        return true;
    }

    private int refreshAll(ItemStack[] items) {
        int n = 0;
        if (items != null) for (ItemStack it : items) if (refresh(it)) n++;
        return n;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoinRefresh(org.bukkit.event.player.PlayerJoinEvent e) {
        Player p = e.getPlayer();
        later(20, () -> {
            if (!p.isOnline()) return;
            ItemStack[] inv = p.getInventory().getContents();   // 가방 + 갑옷 + 왼손
            int a = refreshAll(inv);
            if (a > 0) p.getInventory().setContents(inv);
            ItemStack[] ender = p.getEnderChest().getContents();
            int b = refreshAll(ender);
            if (b > 0) p.getEnderChest().setContents(ender);
            kr.wave.core.data.PlayerData d = data(p);
            // 배낭 / 우편함 / 사망 보관 아이템 (같은 객체를 고치므로 목록에 그대로 반영됨)
            int c = refreshAll(d.backpack) + refreshAll(d.mailbox.toArray(new ItemStack[0])) + refreshAll(d.respawnItems.toArray(new ItemStack[0]));
            if (c > 0) plugin.store().save(d);
            // 팀 창고 (팀원 누구든 접속하면 갱신)
            kr.wave.core.data.GlobalData.Team team = global().teamOf(p.getUniqueId());
            int t = team == null ? 0 : refreshAll(team.storage);
            if (t > 0) plugin.store().saveGlobal();
            // 이름이 바뀐 칭호를 쓰고 있으면 새 이름으로 다시 적용
            String newPrefix = Items.renamedTitle(d.activeTitle);
            if (newPrefix != null) plugin.module(CoreModule.class).applyTitle(p, newPrefix);
            int n = a + b + c + t;
            if (n > 0) Msg.actionBar(p, "&7보유 중인 특수 아이템 &f" + n + "개&7의 이름과 설명이 최신으로 바뀌었습니다.");
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoinWalk(org.bukkit.event.player.PlayerJoinEvent e) {
        reapplyWalkSoon(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeWalk(org.bukkit.event.player.PlayerGameModeChangeEvent e) {
        reapplyWalkSoon(e.getPlayer());
    }

    /* ======================= 1초마다: 장착 효과, 액션바, 체력/속도 ======================= */

    @Override
    public void secondTick() {
        long now = System.currentTimeMillis();
        double base = plugin.getConfig().getDouble("health.base", 20);
        // 무기별 추가 체력. 새 항목이 없으면 예전 special-weapon-bonus 값을 사용
        double legacy = plugin.getConfig().getDouble("health.special-weapon-bonus", 6);
        double claymoreBonus = plugin.getConfig().getDouble("health.claymore-bonus", legacy);
        double frostBonus = plugin.getConfig().getDouble("health.frost-bonus", legacy);
        float fastWalk = (float) plugin.getConfig().getDouble("health.claymore-walk-speed", 0.24);

        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            ItemStack hand = p.getInventory().getItemInMainHand();
            String tool = Msg.name(hand);
            String boots = Msg.name(p.getInventory().getBoots());
            String chest = Msg.name(p.getInventory().getChestplate());
            String legs = Msg.name(p.getInventory().getLeggings());
            String helmet = Msg.name(p.getInventory().getHelmet());

            // 장착 효과
            if (boots.contains("대자연의 황혼")) {
                Fx.fade(p.getLocation().add(0, 0.2, 0), NATURE_Y, NATURE, 0.9f, 3, 0.3);
                if (FORESTS.contains(p.getLocation().getBlock().getBiome())) {
                    effect(p, PotionEffectType.REGENERATION, 1.5, 2);
                    Fx.p(p.getLocation().add(0, 1, 0), Particle.HAPPY_VILLAGER, 4, 0.5, 0);
                }
            }
            if (boots.contains(Items.N_NATURE_BOOTS)) {
                effect(p, PotionEffectType.SPEED, 1.5, 1);
                ItemStack b = p.getInventory().getBoots();
                if (Items.ensureSeasonKeep(b)) p.getInventory().setBoots(b);   // 예전에 받은 아이템 설명 갱신
            }
            if (tool.contains(Items.N_NATURE_BOOTS) && Items.ensureSeasonKeep(hand)) p.getInventory().setItemInMainHand(hand);
            boolean ruinerLegs = legs.contains(Items.N_RUINER_PANTS);
            if (ruinerLegs && p.getHealth() <= maxHealth(p) * plugin.getConfig().getDouble("weapons.ruiner-pants.low-health", 0.3)) {
                effect(p, PotionEffectType.STRENGTH, 1.5, 2);
                Fx.p(p.getLocation().add(0, 1, 0), Particle.DAMAGE_INDICATOR, 2, 0.3, 0.02);
            }
            if (chest.contains(Items.N_VOID_CHEST)) effect(p, PotionEffectType.STRENGTH, 1.5, 1);
            if (helmet.contains(Items.N_CHAOS_HELM)) {
                p.removePotionEffect(PotionEffectType.WEAKNESS);
                p.removePotionEffect(PotionEffectType.SLOWNESS);
                Fx.p(p.getLocation().add(0, 2.1, 0), Particle.SMOKE, 2, 0.2, 0.01);
            }
            if (helmet.contains(Items.N_RUSSELL)) effect(p, PotionEffectType.HASTE, 1.5, 3);
            if (helmet.contains(Items.N_RUSSELL)) Fx.p(p.getLocation().add(0, 2.2, 0), Particle.ENCHANT, 3, 0.3, 0.3);
            if (chest.contains(Items.N_VOID_CHEST)) Fx.p(p.getLocation().add(0, 1, 0), Particle.REVERSE_PORTAL, 3, 0.3, 0.01);
            if (legs.contains(Items.N_RUINER_PANTS)) Fx.dust(p.getLocation().add(0, 0.6, 0), RUIN_D, 0.9f, 3, 0.25);
            if (inWorld(p, "nether") && boots.equals(Items.C_REAPER_HEART)) {
                Fx.p(p.getLocation().add(0, 0.1, 0), Particle.SOUL_FIRE_FLAME, 3, 0.2, 0.01);
                effect(p, PotionEffectType.SPEED, 1.5, 3);
                effect(p, PotionEffectType.FIRE_RESISTANCE, 1.5, 1);
            }

            if (tool.contains(Items.N_SEALED_SWORD)) effect(p, PotionEffectType.RESISTANCE, 1.5, 1);   // 들고 있으면 저항 I

            repairLore(p, hand);

            // 스킬 쿨타임 액션바: [이름] [진행 막대] 남은 시간 / 사용 가능
            String bar = null;
            if (tool.contains(Items.N_SEALED_SWORD)) {
                bar = sealedBar(p, now);
            } else if (tool.contains(Items.N_ETERNAL_CHRONOS)) {
                long storm = sandStormUntil.getOrDefault(id, 0L) - now;
                bar = storm > 0
                        ? "<gradient:#BFA66E:#B2D6B6><b>⌛ 모래 폭풍</b></gradient> &8» &e&l발동 중 &f" + ((storm + 999) / 1000) + "초"
                        : skill(p, "<gradient:#BFA66E:#B2D6B6><b>⌛ 모래 폭풍</b></gradient>", chronosCd, now, 60_000, "&6", "우클릭", 12);
            } else if (tool.contains(Items.N_CHAOS)) {
                bar = skill(p, "<gradient:#727272:#E0E0E0><b>✦ 혼돈의 파열</b></gradient>", chaosCd, now,
                        (long) (plugin.getConfig().getDouble("weapons.chaos.skill-cooldown-seconds", 45) * 1000), "&7", "우클릭", 12);
            } else if (tool.contains(Items.N_DARK_WAVE)) {
                bar = skill(p, "<gradient:#727272:#E0E0E0><b>✦ 어둠의 파동</b></gradient>", waveCd1, now, 60_000, "&7", "우클릭", 6)
                        + "  &8│  " + skill(p, "<gradient:#E0E0E0:#727272><b>어둠의 장막</b></gradient>", waveCd2, now,
                        (long) (plugin.getConfig().getDouble("weapons.darkness-wave.veil-cooldown-seconds", 90) * 1000), "&7", "웅크리기+우클릭", 6);
            } else if (Items.isNewChar(tool)) {
                bar = skill(p, "<gradient:#1E2586:#C8B0B0><b>🌙 월광의 힘</b></gradient>", newCharCd1, now, 90_000, "&9", "우클릭", 6)
                        + "  &8│  " + skill(p, "<gradient:#FAD4D4:#C8B0B0><b>달빛 치유</b></gradient>", newCharCd2, now, 90_000, "&d", "웅크리기+우클릭", 6);
            } else if (tool.contains(Items.N_CLAYMORE_SD)) {
                bar = skill(p, "<gradient:#FFF200:#FFFCD4><b>⚡ 심판의 빛</b></gradient>", claymoreCd, now,
                        (long) (plugin.getConfig().getDouble("weapons.claymore.judgment-cooldown-seconds", 30) * 1000), "&e", "타격 시 발동", 12);
            }
            // 스킬 금지 안내를 막 띄웠으면 2초 동안 무기 액션바로 덮지 않음
            if (bar != null && System.currentTimeMillis() - skillBlockMsg.getOrDefault(id, 0L) > 2000) Msg.actionBar(p, bar);

            // 최대 체력 / 이동 속도 (기본 하트 5칸, 특수 무기 소지 시 +5칸)
            boolean claymore = hasClaymore(p);
            boolean frost = hasFrost(p);
            if (tool.contains("천공의 심판자 클레이모어") && !data(p).fxOff.contains("claymore")) Fx.dust(p.getLocation().add(0, 1.2, 0), ARBITER, 0.8f, 3, 0.35);
            if (tool.contains("프로스트 그레이브") && !data(p).fxOff.contains("frost")) Fx.dust(p.getLocation().add(0, 1.2, 0), FROST_SKY, 0.8f, 3, 0.35);
            // 두 무기를 모두 가지고 있으면 더 큰 값 하나만 적용
            double target = base + Math.max(claymore ? claymoreBonus : 0, frost ? frostBonus : 0);
            AttributeInstance ai = MAX_HEALTH == null ? null : p.getAttribute(MAX_HEALTH);
            if (ai != null && ai.getBaseValue() != target) {
                ai.setBaseValue(target);
                if (p.getHealth() > ai.getValue()) p.setHealth(ai.getValue());
            }
            applyWalk(p, claymore, fastWalk);
        }
    }
}
