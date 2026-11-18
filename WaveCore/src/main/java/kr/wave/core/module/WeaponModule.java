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
        cmd("랜덤박스지급", ADMIN, (s, l, a) -> give(s, Items.ruinerScythe(), Items.sealedWings(), Items.ruinerPants(), Items.sealedSword(), Items.thunderAxe(), Items.chaosApocalypse(), Items.abyssHelmet(), Items.darknessWave()));
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
                } catch (Throwable ignored) {
                }
            }
            Fx.p(le.getLocation().add(0, 1, 0), Particle.END_ROD, 8, 0.3, 0.05);
        }
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
                e.setDamage(4);
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
            if (tool.contains(Items.N_SEALED_SWORD)) {
                Location mid = vl.clone().add(0, 1, 0);
                if (chance(0.20)) {
                    e.setDamage(e.getDamage() * 1.3);
                    // 얼어붙은 제왕의 일격: 얼음 파편 폭발
                    Fx.block(mid, Particle.BLOCK, Material.PACKED_ICE.createBlockData(), 40, 0.4);
                    Fx.burst(mid, Particle.SNOWFLAKE, 40, 0.15);
                    Fx.ring(vl.clone().add(0, 0.1, 0), 1.2, 28, ICE, 1.4f);
                    Fx.sphere(mid, 0.9, 30, ICE_L, 0.9f);
                    Msg.soundAt(mid, "block.glass.break", 0.8f, 1.6f);
                }
                switch (ThreadLocalRandom.current().nextInt(1, 5)) {
                    case 1 -> effect(attacker, PotionEffectType.STRENGTH, 1.5, 2);
                    case 2 -> effect(attacker, PotionEffectType.RESISTANCE, 1.5, 1);
                    case 3 -> effect(attacker, PotionEffectType.REGENERATION, 1.5, 2);
                    default -> effect(attacker, PotionEffectType.SPEED, 1.5, 2);
                }
            }
            if (tool.contains(Items.N_THUNDER) && chance(0.10)) {
                Fx.lightning(vl);
                hurt(victim, 2);
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
                    Fx.lightning(vl);
                    effect(victim, PotionEffectType.BLINDNESS, 3, 1);
                    effect(victim, PotionEffectType.SLOWNESS, 3, 10);
                    claymoreCd.put(attacker.getUniqueId(), now + (long) (plugin.getConfig().getDouble("weapons.claymore.judgment-cooldown-seconds", 30) * 1000));
                    Msg.soundAt(vl, "entity.lightning_bolt.impact", 1, 1);
                    Fx.p(vl.clone().add(0, 1.5, 0), Particle.TOTEM_OF_UNDYING, 200, 1.5, 0.05, 1.5, 0.5);
                    // 심판의 빛: 하늘에서 내려오는 황금 기둥 + 충격파
                    Fx.pillar(vl, 12, ARBITER, Particle.END_ROD);
                    Fx.shockwave(vl.clone().add(0, 0.1, 0), ARBITER, 4.0, 10);
                    Fx.ring(vl.clone().add(0, 2.4, 0), 0.5, 16, ARBITER_L, 1.2f);
                    Fx.burst(vl.clone().add(0, 1, 0), Particle.ENCHANTED_HIT, 40, 0.5);
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
                Fx.p(mid, Particle.SNOWFLAKE, 14, 0.4, 0.03);
                Fx.fade(mid, FROST, FROST_L, 1.2f, 12, 0.4);
                if (!(victim instanceof Player) && chance(0.15)) {
                    e.setDamage(999999);
                    // 아이스 블래스트: 얼음 폭발 + 눈보라 충격파
                    Fx.block(mid, Particle.BLOCK, Material.BLUE_ICE.createBlockData(), 60, 0.5);
                    Fx.burst(mid, Particle.SNOWFLAKE, 70, 0.25);
                    Fx.shockwave(vl.clone().add(0, 0.1, 0), FROST, 3.5, 8);
                    Fx.sphere(mid, 1.3, 40, FROST_L, 1.1f);
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
            boolean abyss = Msg.nameHas(vp.getInventory().getHelmet(), Items.N_ABYSS_HELMET);
            if (abyss && Msg.nameHas(vp.getInventory().getItemInMainHand(), Items.N_CHAOS)) {
                e.setDamage(e.getDamage() * plugin.getConfig().getDouble("weapons.chaos.set-damage-taken", 0.9));   // 세트 효과
            }
            if (abyss && chance(plugin.getConfig().getDouble("weapons.chaos.helmet-chance", 0.2))) {
                LivingEntity src = attacker != null ? attacker : shooter;
                if (src != null) {
                    effect(src, PotionEffectType.DARKNESS, 2, 1);
                    effect(src, PotionEffectType.SLOWNESS, 2, 1);
                    // 심연의 응시: 공격자에게 검은 줄기
                    Fx.line(vl.clone().add(0, 1.6, 0), src.getLocation().add(0, 1.4, 0), CHAOS_D, 1.1f, 0.25);
                    Fx.p(src.getLocation().add(0, 1, 0), Particle.SQUID_INK, 16, 0.3, 0.02);
                    Msg.soundAt(vl, "entity.warden.heartbeat", 1, 1.2f);
                }
            }
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

    @EventHandler(priority = EventPriority.HIGH)
    public void onSkill(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        String n = Msg.name(hand);
        long now = System.currentTimeMillis();
        UUID id = p.getUniqueId();

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
                    effect(t, PotionEffectType.SLOWNESS, 3, 2);
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
                waveCd2.put(id, now + 90_000);
                effect(p, PotionEffectType.INVISIBILITY, 5, 1);
                effect(p, PotionEffectType.SPEED, 5, 2);
                // 심연의 장막: 검은 연기에 몸을 숨김
                Fx.p(p.getLocation().add(0, 1, 0), Particle.LARGE_SMOKE, 60, 0.5, 0.02);
                Fx.helix(p, CHAOS_D, CHAOS_L, 0.7, 2.2, 30);
                Msg.sound(p, "entity.enderman.teleport", 0.8f, 0.6f);
                Msg.send(p, "&8&l[Chaos] &72번 스킬 &5심연의 장막&7을 사용했습니다.");
            }
        } else if (n.contains(Items.N_SEALED_SWORD) && (hand.getType() == Material.DIAMOND_SWORD || hand.getType() == Material.NETHERITE_SWORD)) {
            if (sealedCd.getOrDefault(id, 0L) > now) {
                Msg.send(p, "&c아직 사용 할 수 없습니다.");
                e.setCancelled(true);
                return;
            }
            effect(p, PotionEffectType.SPEED, 7, 4);
            Msg.sound(p, "block.enchantment_table.use", 1, 2);
            // 얼어붙은 제왕의 질주: 얼음 나선 + 서리 충격파
            Fx.helix(p, ICE, ICE_L, 0.9, 2.5, 30);
            Fx.burst(p.getLocation().add(0, 1, 0), Particle.SNOWFLAKE, 50, 0.15);
            Fx.shockwave(p.getLocation().add(0, 0.1, 0), ICE, 3.0, 8);
            Msg.send(p, "&e&l특수 능력을 사용했습니다.");
            sealedCd.put(id, now + 45_000);
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
                // 새로운 시대의 각인: 등 뒤의 초승달 + 남색 나선
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

    /**
     * 액션바 한 칸: 이름 » [■■■□□□] 12초  /  이름 » ✔ 사용 가능 (우클릭)
     * 쿨타임이 막 끝난 순간(1초 안)에는 짧은 알림음을 낸다.
     */
    private static String skill(Player p, String label, Map<UUID, Long> cd, long now, long totalMs, String color, String hint, int width) {
        long end = cd.getOrDefault(p.getUniqueId(), 0L);
        long ms = end - now;
        if (ms <= 0) {
            if (end > 0 && -ms < 1000) p.playSound(p.getLocation(), "block.note_block.pling", 0.6f, 1.8f);   // 다시 사용 가능
            return label + " &8» &a&l✔ 사용 가능 &8(" + hint + ")";
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
            int n = a + b + c;
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
            if (legs.contains(Items.N_RUINER_PANTS)) effect(p, PotionEffectType.RESISTANCE, 1.5, 1);
            if (chest.contains(Items.N_VOID_CHEST)) effect(p, PotionEffectType.STRENGTH, 1.5, 1);
            if (helmet.contains(Items.N_RUSSELL)) effect(p, PotionEffectType.HASTE, 1.5, 3);
            if (helmet.contains(Items.N_RUSSELL)) Fx.p(p.getLocation().add(0, 2.2, 0), Particle.ENCHANT, 3, 0.3, 0.3);
            if (chest.contains(Items.N_VOID_CHEST)) Fx.p(p.getLocation().add(0, 1, 0), Particle.REVERSE_PORTAL, 3, 0.3, 0.01);
            if (legs.contains(Items.N_RUINER_PANTS)) Fx.dust(p.getLocation().add(0, 0.6, 0), RUIN_D, 0.9f, 3, 0.25);
            if (inWorld(p, "nether") && boots.equals(Items.C_REAPER_HEART)) {
                Fx.p(p.getLocation().add(0, 0.1, 0), Particle.SOUL_FIRE_FLAME, 3, 0.2, 0.01);
                effect(p, PotionEffectType.SPEED, 1.5, 3);
                effect(p, PotionEffectType.FIRE_RESISTANCE, 1.5, 1);
            }

            if (tool.contains(Items.N_CHAOS) && inWorld(p, "nether")) effect(p, PotionEffectType.FIRE_RESISTANCE, 1.5, 1);
            if (helmet.contains(Items.N_ABYSS_HELMET)) effect(p, PotionEffectType.NIGHT_VISION, 15, 1);   // 15초: 깜빡임 없이 유지

            repairLore(p, hand);

            // 스킬 쿨타임 액션바: [이름] [진행 막대] 남은 시간 / 사용 가능
            String bar = null;
            if (tool.contains(Items.N_SEALED_SWORD)) {
                bar = skill(p, "<gradient:#00DDFF:#FFFFFF><b>❄ 제왕의 질주</b></gradient>", sealedCd, now, 45_000, "&b", "우클릭", 12);
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
                        + "  &8│  " + skill(p, "<gradient:#E0E0E0:#727272><b>심연의 장막</b></gradient>", waveCd2, now, 90_000, "&7", "웅크리기+우클릭", 6);
            } else if (Items.isNewChar(tool)) {
                bar = skill(p, "<gradient:#1E2586:#C8B0B0><b>🌙 월광의 힘</b></gradient>", newCharCd1, now, 90_000, "&9", "우클릭", 6)
                        + "  &8│  " + skill(p, "<gradient:#FAD4D4:#C8B0B0><b>달빛 치유</b></gradient>", newCharCd2, now, 90_000, "&d", "웅크리기+우클릭", 6);
            } else if (tool.contains(Items.N_CLAYMORE_SD)) {
                bar = skill(p, "<gradient:#FFF200:#FFFCD4><b>⚡ 심판의 빛</b></gradient>", claymoreCd, now,
                        (long) (plugin.getConfig().getDouble("weapons.claymore.judgment-cooldown-seconds", 30) * 1000), "&e", "타격 시 발동", 12);
            }
            if (bar != null) Msg.actionBar(p, bar);

            // 최대 체력 / 이동 속도 (기본 하트 5칸, 특수 무기 소지 시 +5칸)
            boolean claymore = hasClaymore(p);
            boolean frost = hasFrost(p);
            if (tool.contains("천공의 심판자 클레이모어")) Fx.dust(p.getLocation().add(0, 1.2, 0), ARBITER, 0.8f, 3, 0.35);
            if (tool.contains("프로스트 그레이브")) Fx.dust(p.getLocation().add(0, 1.2, 0), FROST_SKY, 0.8f, 3, 0.35);
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
