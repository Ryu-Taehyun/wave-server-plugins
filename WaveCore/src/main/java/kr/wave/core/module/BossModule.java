package kr.wave.core.module;

import kr.wave.core.Items;
import kr.wave.core.WaveCore;
import kr.wave.core.util.Cmd;
import kr.wave.core.util.Fx;
import kr.wave.core.util.Gui;
import kr.wave.core.util.It;
import kr.wave.core.util.Msg;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 보스 '천공의 심판자'
 * - 천공의 제단(config boss.center)에서만 스폰, 처치 후 일정 시간 뒤 재등장
 * - 처치 시 확률적으로 천공의 심판자 클레이모어 → 참가자 주사위 분배
 */
public class BossModule extends Module {

    private static final String NAME = "<b><gradient:#FFF200:#F6E47C:#FFFCD4>천공의 심판자</gradient></b>";
    private static final Color GOLD = Fx.rgb(0xFFF200), GOLD_L = Fx.rgb(0xFFFCD4);

    private final NamespacedKey bossKey;
    private LivingEntity boss;
    private BossBar bar;
    private final Set<Player> barViewers = new HashSet<>();
    private final Map<UUID, Double> damage = new HashMap<>();
    private long nextSpawnAt;
    private int aliveSeconds;
    private boolean enraged;

    private LootSession loot;

    /* 전투 제한 / 귀환 */
    private int fightLeft;                                     // 보스 전투 남은 초 (0 = 없음)
    private int returnLeft;                                    // 처치 후 귀환까지 남은 초 (0 = 없음)
    private final Set<UUID> returnees = new HashSet<>();
    private final Map<UUID, Location> lastPos = new HashMap<>();  // 제단에 들어오기 전 위치
    private final Set<UUID> allowTp = new HashSet<>();            // 플러그인이 직접 보내는 이동
    private final Set<UUID> diedThisDescent = new HashSet<>();    // 이번 강림에서 제단에서 사망 -> 재입장 불가
    private final Set<UUID> respawnHandled = new HashSet<>();     // 제단 사망자의 리스폰 위치를 이 모듈이 정함

    public BossModule(WaveCore plugin) {
        super(plugin);
        bossKey = new NamespacedKey(plugin, "sky_judge");
    }

    @Override
    public void enable() {
        cmd("루팅", null, (s, l, a) -> {
            if (!Cmd.isPlayer(s)) return;
            if (loot == null || !loot.participants.contains(((Player) s).getUniqueId())) {
                Msg.send(s, "&c참여 중인 아이템 분배가 없습니다.");
                return;
            }
            loot.gui.open((Player) s);
        });
        cmd("전리품설정", ADMIN, (s, l, a) -> {
            if (Cmd.isPlayer(s)) openLootEditor((Player) s);
        }, "lootedit");
        cmd("천공의제단", null, (s, l, a) -> {
            if (Cmd.isPlayer(s)) openAltar((Player) s);   // 메뉴만 열림. 이동은 메뉴에서 이동권으로만
        }, "제단이동", "천공");
        // 서버 재시작 직후 이미 로드된 보스 다시 연결
        later(40, () -> {
            for (World w : Bukkit.getWorlds()) for (LivingEntity e : w.getLivingEntities()) relink(e);
        });
    }

    @Override
    public void disable() {
        if (bar != null) for (Player p : new ArrayList<>(barViewers)) p.hideBossBar(bar);
        if (loot != null) loot.resolveAll();
    }

    /* ======================= 설정 ======================= */

    private ConfigurationSection cfg() {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("boss");
        return c != null ? c : plugin.getConfig().createSection("boss");
    }

    private Location center() {
        ConfigurationSection c = cfg();
        List<Double> xyz = c.getDoubleList("center");
        World w = Bukkit.getWorld(c.getString("world", ""));
        if (w == null || xyz.size() < 3) return null;
        return new Location(w, xyz.get(0), xyz.get(1), xyz.get(2));
    }

    /* ======================= 관리자 (/wave boss ...) ======================= */

    public void admin(CommandSender s, String[] a) {
        String sub = a.length >= 2 ? a[1].toLowerCase() : "";
        switch (sub) {
            case "setcenter" -> {
                if (!(s instanceof Player p)) return;
                Location l = p.getLocation();
                cfg().set("world", l.getWorld().getName());
                cfg().set("center", List.of(round(l.getX()), round(l.getY()), round(l.getZ())));
                plugin.saveConfig();
                Msg.send(s, "&a천공의 제단 중심을 지금 위치로 설정했습니다. &7(" + l.getWorld().getName() + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ() + ")");
            }
            case "spawn" -> {
                if (center() == null) {
                    Msg.send(s, "&c먼저 /wave boss setcenter 로 제단 위치를 지정하세요.");
                    return;
                }
                if (alive()) {
                    Msg.send(s, "&c이미 보스가 있습니다.");
                    return;
                }
                spawn();
                Msg.send(s, "&a천공의 심판자를 소환했습니다.");
            }
            case "kill" -> {
                if (alive()) {
                    boss.remove();
                    clearBoss();
                    Msg.send(s, "&a보스를 제거했습니다. (보상 없음)");
                }
            }
            case "setentry" -> {
                if (!(s instanceof Player p)) return;
                Location l = p.getLocation();
                cfg().set("entry", List.of(round(l.getX()), round(l.getY()), round(l.getZ()), round(l.getYaw()), round(l.getPitch())));
                plugin.saveConfig();
                Msg.send(s, "&a이동권 도착 지점을 지금 위치로 설정했습니다." + (inArena(l) ? "" : " &c(제단 구역 밖입니다!)"));
            }
            case "ticket" -> {
                if (a.length < 3) {
                    Msg.send(s, "&c/wave boss ticket <플레이어> [개수]");
                    return;
                }
                Player t = Bukkit.getPlayerExact(a[2]);
                if (t == null) {
                    Msg.send(s, "&c접속 중인 플레이어가 아닙니다.");
                    return;
                }
                int n = 1;
                try {
                    if (a.length >= 4) n = Math.max(1, Math.min(2304, Integer.parseInt(a[3])));
                } catch (NumberFormatException e) {
                    Msg.send(s, "&c개수는 숫자로 적어 주세요.");
                    return;
                }
                It.give(t, Items.altarTicket(), n);
                Msg.send(s, "&a" + t.getName() + " 님에게 천공의 제단 이동권 " + n + "개를 지급했습니다.");
                Msg.send(t, "&e천공의 제단 이동권 &f" + n + "개를 받았습니다.");
            }
            case "lootedit", "loot" -> {
                if (s instanceof Player p) openLootEditor(p);
            }
            case "testloot" -> {
                if (!(s instanceof Player p)) return;
                List<ItemStack> items = rollLoot();
                if (a.length >= 3 && a[2].equalsIgnoreCase("claymore")) {
                    items.remove(items.size() - 1);
                    items.add(Items.claymoreSd());
                }
                startLoot(items, p.getLocation(), Set.of(p.getUniqueId()));
            }
            case "info" -> {
                Location c = center();
                Msg.send(s, "&6천공의 심판자");
                Msg.send(s, "&7제단: " + (c == null ? "&c미설정" : c.getWorld().getName() + " " + c.getBlockX() + " " + c.getBlockY() + " " + c.getBlockZ()));
                Msg.send(s, "&7상태: " + (alive() ? "&a살아 있음 &7(" + (int) boss.getHealth() + " HP)" :
                        "&c없음 &7(재등장까지 " + Math.max(0, (nextSpawnAt - System.currentTimeMillis()) / 1000) + "초)"));
                Msg.send(s, "&7클레이모어 확률: " + cfg().getDouble("loot.claymore-chance", cfg().getDouble("drop-chance", 0.5)) + "% &7/ 전리품 " + cfg().getInt("loot.items", 5) + "개");
            }
            default -> {
                Msg.send(s, "&e/wave boss setcenter &7- 서 있는 위치를 제단 중심(보스 스폰)으로 지정");
                Msg.send(s, "&e/wave boss spawn &7- 즉시 소환");
                Msg.send(s, "&e/wave boss kill &7- 보스 제거 (보상 없음)");
                Msg.send(s, "&e/wave boss info &7- 상태 확인");
                Msg.send(s, "&e/wave boss testloot [claymore] &7- 전리품 창 테스트 (아이템이 실제로 생성됨)");
                Msg.send(s, "&e/wave boss setentry &7- 서 있는 위치를 이동권 도착 지점으로 지정");
                Msg.send(s, "&e/wave boss lootedit &7- 전리품 설정 GUI (또는 /전리품설정)");
                Msg.send(s, "&e/wave boss ticket <플레이어> [개수] &7- 천공의 제단 이동권 지급");
            }
        }
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }

    /* ======================= 스폰 ======================= */

    private boolean alive() {
        return boss != null && boss.isValid() && !boss.isDead();
    }

    private void relink(Entity e) {
        if (e instanceof LivingEntity le && le.getPersistentDataContainer().has(bossKey, PersistentDataType.BYTE)) {
            if (alive() && boss != le) {
                le.remove(); // 중복 보스 제거
                return;
            }
            boss = le;
            if (fightLeft == 0) fightLeft = (int) (cfg().getDouble("fight-minutes", 5) * 60);
            ensureBar();
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) relink(en);
    }

    private static Attribute attr(String... keys) {
        for (String k : keys) {
            Attribute a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(k));
            if (a != null) return a;
        }
        return null;
    }

    private static void setAttr(LivingEntity e, double value, String... keys) {
        Attribute a = attr(keys);
        if (a == null) return;
        AttributeInstance ai = e.getAttribute(a);
        if (ai != null) ai.setBaseValue(value);
    }

    private ItemStack goldPiece(Material m) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        meta.setUnbreakable(true);
        it.setItemMeta(meta);
        return it;
    }

    private void spawn() {
        Location c = center();
        if (c == null) return;
        double hp = cfg().getDouble("health", 400);
        try {
            c.getChunk().addPluginChunkTicket(plugin);   // 아무도 없어도 전투 시간 동안 제단 유지
        } catch (Throwable e) {
            kr.wave.core.util.Log.once("boss:ticket", "보스 제단 청크를 고정하지 못했습니다 (아무도 없으면 제단이 멈출 수 있음): " + e);
        }
        boss = c.getWorld().spawn(c, WitherSkeleton.class, w -> {
            w.customName(Msg.c(NAME));
            w.setCustomNameVisible(true);
            try {
                w.setRemoveWhenFarAway(false);
                w.setPersistent(true);
            } catch (RuntimeException e) {
                kr.wave.core.util.Log.once("boss:persist", "보스를 계속 남아 있게 설정하지 못했습니다: " + e);
            }
            w.getPersistentDataContainer().set(bossKey, PersistentDataType.BYTE, (byte) 1);
            setAttr(w, hp, "max_health", "generic.max_health");
            setAttr(w, cfg().getDouble("damage", 12), "attack_damage", "generic.attack_damage");
            setAttr(w, 1.6, "scale", "generic.scale");
            setAttr(w, 40, "follow_range", "generic.follow_range");
            setAttr(w, 0.8, "knockback_resistance", "generic.knockback_resistance");
            setAttr(w, 0.3, "movement_speed", "generic.movement_speed");
            w.setHealth(hp);
            EntityEquipment eq = w.getEquipment();
            eq.setHelmet(goldPiece(Material.GOLDEN_HELMET));
            eq.setChestplate(goldPiece(Material.GOLDEN_CHESTPLATE));
            eq.setLeggings(goldPiece(Material.GOLDEN_LEGGINGS));
            eq.setBoots(goldPiece(Material.GOLDEN_BOOTS));
            ItemStack sword = goldPiece(Material.GOLDEN_SWORD);
            sword.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
            eq.setItemInMainHand(sword);
            eq.setHelmetDropChance(0);
            eq.setChestplateDropChance(0);
            eq.setLeggingsDropChance(0);
            eq.setBootsDropChance(0);
            eq.setItemInMainHandDropChance(0);
        });
        damage.clear();
        aliveSeconds = 0;
        enraged = false;
        returnLeft = 0;
        returnees.clear();
        diedThisDescent.clear();
        fightLeft = (int) (cfg().getDouble("fight-minutes", 5) * 60);
        ensureBar();
        // 강림 연출
        Fx.lightning(c);
        Fx.pillar(c, 25, GOLD, Particle.END_ROD);
        Fx.shockwave(c.clone().add(0, 0.1, 0), GOLD, 12, 18);
        Fx.burst(c.clone().add(0, 2, 0), Particle.TOTEM_OF_UNDYING, 150, 0.6);
        Msg.soundAt(c, "entity.wither.spawn", 3, 1.3f);
        Msg.broadcast("");
        Msg.broadcast("&6&l[!] " + NAME + " &e이(가) 천공의 제단에 강림했습니다!");
        Msg.broadcast("&7제한 시간 " + fmt((long) (cfg().getDouble("fight-minutes", 5) * 60)) + " &7· 전투가 끝날 때까지 제단에서 명령어와 텔레포트가 막힙니다.");
        Bukkit.broadcast(Msg.c("&e[☀] 천공의 제단 이동권&7을 우클릭하여 입장하세요. &8(클릭: 천공의 제단 메뉴)")
                .clickEvent(ClickEvent.runCommand("/천공의제단")).hoverEvent(HoverEvent.showText(Msg.c("&a클릭하여 천공의 제단 메뉴 열기"))));
        Msg.broadcast("");
    }

    private void ensureBar() {
        if (bar == null) bar = BossBar.bossBar(Msg.c(NAME), 1f, BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_10);
    }

    private void clearBoss() {
        boss = null;
        try {
            Location c = center();
            if (c != null) c.getChunk().removePluginChunkTicket(plugin);
        } catch (Throwable e) {
            kr.wave.core.util.Log.once("boss:unticket", "보스 제단 청크 고정을 풀지 못했습니다: " + e);
        }
        fightLeft = 0;
        if (bar != null) for (Player p : new ArrayList<>(barViewers)) p.hideBossBar(bar);
        barViewers.clear();
        nextSpawnAt = System.currentTimeMillis() + (long) (cfg().getDouble("respawn-minutes", 30) * 60_000);
    }

    /* ======================= 1초마다: 스폰, 보스바, 패턴 ======================= */

    @Override
    public void secondTick() {
        tickReturn();
        refreshAltarMenus();
        stopGliders();
        if (!cfg().getBoolean("enabled", true)) return;
        Location c = center();
        if (c == null) return;

        if (!alive()) {
            if (boss != null) clearBoss();
            // 강림한 뒤에만 이동권으로 입장할 수 있으므로, 주변 플레이어와 관계없이 시간이 되면 강림
            if (System.currentTimeMillis() >= nextSpawnAt) spawn();
            return;
        }

        // 제단 밖으로 끌려나가면 복귀
        double radius = cfg().getDouble("radius", 22);
        if (!boss.getWorld().equals(c.getWorld()) || boss.getLocation().distanceSquared(c) > radius * radius) {
            Fx.burst(boss.getLocation().add(0, 1, 0), Particle.END_ROD, 30, 0.2);
            boss.teleport(c);
        }

        // 전투 제한 시간
        // 제단에 플레이어가 아무도 없으면 시간이 흐르지 않음
        boolean anyone = false;
        for (Player p : c.getWorld().getPlayers()) {
            if (p.getGameMode() != org.bukkit.GameMode.SPECTATOR && !p.isDead() && inArena(p.getLocation())) {
                anyone = true;
                break;
            }
        }
        if (anyone && fightLeft > 0 && --fightLeft <= 0) {
            fightTimeout(c);
            return;
        }
        long left = fightLeft;
        if (fightLeft > 0) bar.name(Msg.c(NAME + " &7- " + (!anyone ? "&7" + fmt(left) + " (대기 중)" : (left <= 30 ? "&c" : "&e") + fmt(left))));

        // 보스바
        double max = maxHealth(boss);
        bar.progress((float) Math.max(0, Math.min(1, boss.getHealth() / max)));
        List<Player> near = nearby(boss.getLocation(), 50);
        for (Player p : near) if (barViewers.add(p)) p.showBossBar(bar);
        for (Player p : new ArrayList<>(barViewers)) {
            if (!near.contains(p)) {
                p.hideBossBar(bar);
                barViewers.remove(p);
            }
        }

        List<Player> targets = nearby(boss.getLocation(), radius + 6);
        if (targets.isEmpty()) return;
        aliveSeconds++;
        if (aliveSeconds % 6 == 0) judgmentLight(targets.get(ThreadLocalRandom.current().nextInt(targets.size())));
        if (aliveSeconds % 14 == 0) heavenWave();
        if (!enraged && boss.getHealth() < max * 0.5) {
            enraged = true;
            boss.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 1));
            boss.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1));
            Fx.sphere(boss.getLocation().add(0, 1.5, 0), 2.5, 80, GOLD, 1.4f);
            Fx.shockwave(boss.getLocation().add(0, 0.1, 0), GOLD_L, 8, 12);
            Msg.soundAt(boss.getLocation(), "entity.wither.ambient", 2, 0.6f);
            for (Player p : targets) Msg.send(p, NAME + " &e: &f천공의 이름으로, 심판을 내린다.");
        }
    }

    private static String fmt(long sec) {
        sec = Math.max(0, sec);
        return sec / 60 + ":" + String.format("%02d", sec % 60);
    }

    /* ======================= 전투 제한 / 귀환 / 이동 제한 ======================= */

    /** 천공 월드 (config boss.lock-world, 기본 sky) */
    private boolean inSkyWorld(Location l) {
        String w = cfg().getString("lock-world", "sky");
        return l != null && l.getWorld() != null && w != null && !w.isEmpty() && l.getWorld().getName().equalsIgnoreCase(w);
    }

    /** 제단 구역: 천공 월드 전체, 또는 (다른 월드에 제단을 둔 경우) 제단 중심 반경 */
    private boolean inArena(Location l) {
        if (inSkyWorld(l)) return true;
        Location c = center();
        if (c == null || l == null || l.getWorld() == null || !l.getWorld().equals(c.getWorld())) return false;
        double r = cfg().getDouble("arena-radius", 48);
        return l.distanceSquared(c) <= r * r;
    }

    /** 보스 전투 중이거나 처치 후 귀환 대기 중 */
    private boolean locked() {
        return alive() || returnLeft > 0;
    }

    private void fightTimeout(Location c) {
        List<Player> inside = new ArrayList<>();
        for (Player p : c.getWorld().getPlayers()) if (inArena(p.getLocation())) inside.add(p);
        Fx.pillar(boss.getLocation(), 20, GOLD_L, Particle.END_ROD);
        Msg.soundAt(boss.getLocation(), "entity.wither.death", 2, 1.6f);
        boss.remove();
        damage.clear();
        clearBoss();
        Msg.broadcast("&6&l[!] " + NAME + " &7이(가) 제한 시간이 지나 천공으로 돌아갔습니다.");
        for (Player p : inside) {
            Msg.title(p, "&c&l시간 초과", "&7이전 위치로 돌아갑니다.", 5, 40, 10);
            sendBack(p);
        }
    }

    private void tickReturn() {
        if (returnLeft <= 0) return;
        long left = --returnLeft;
        if (left <= 0) {
            Set<UUID> who = new HashSet<>(returnees);
            Location c = center();
            if (c != null) for (Player p : c.getWorld().getPlayers()) if (inArena(p.getLocation())) who.add(p.getUniqueId());
            returnLeft = 0;
            returnees.clear();
            if (loot != null) loot.resolveAll();
            for (UUID u : who) {
                Player p = Bukkit.getPlayer(u);
                if (p != null && !p.isDead() && inArena(p.getLocation())) sendBack(p);   // 이미 나간 사람(사망 등)은 그대로
            }
            return;
        }
        boolean rolling = loot != null && !loot.finished;
        for (UUID u : returnees) {
            Player p = Bukkit.getPlayer(u);
            if (p == null) continue;
            if (!rolling) Msg.actionBar(p, "&e" + left + "초 &7후 이전 위치로 이동합니다.");
        }
    }

    /** 제단에 들어오기 전 위치로 (없으면 스폰) */
    private void sendBack(Player p) {
        Location to = lastPos.remove(p.getUniqueId());
        if (to == null || to.getWorld() == null) to = global().warps.get("spawn");
        if (to == null) to = spawnOf("spawn");
        if (to == null) return;
        if (p.getOpenInventory().getTopInventory() != null && loot != null
                && p.getOpenInventory().getTopInventory().getHolder() == loot.gui) p.closeInventory();
        allowTp.add(p.getUniqueId());
        try {
            p.teleport(to);
        } finally {
            allowTp.remove(p.getUniqueId());
        }
        Msg.sound(p, "entity.enderman.teleport", 1, 1.2f);
        Msg.send(p, "&a이전 위치로 이동했습니다.");
    }

    /** 이 플레이어가 지금 제단 제한(명령어, 메뉴, 이동, 겉날개, 엔더 진주)을 받는지 */
    public boolean restricted(Player p) {
        if (p.hasPermission(ADMIN)) return false;
        Location l = p.getLocation();
        return inSkyWorld(l) || (locked() && inArena(l));
    }

    private static boolean escapeCause(PlayerTeleportEvent.TeleportCause c) {
        return c == PlayerTeleportEvent.TeleportCause.COMMAND || c == PlayerTeleportEvent.TeleportCause.PLUGIN
                || c == PlayerTeleportEvent.TeleportCause.ENDER_PEARL || c == PlayerTeleportEvent.TeleportCause.SPECTATE
                || c == PlayerTeleportEvent.TeleportCause.UNKNOWN
                || c.name().equals("CHORUS_FRUIT") || c.name().equals("CONSUMABLE_EFFECT");   // 버전마다 이름이 다름
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        if (allowTp.contains(p.getUniqueId())) return;
        boolean fromIn = inArena(e.getFrom()), toIn = inArena(e.getTo());
        if (fromIn && !p.hasPermission(ADMIN) && (inSkyWorld(e.getFrom()) || locked())) {
            // 천공 월드(항상) / 전투·분배 중 제단: 명령어, 메뉴, 플러그인, 진주, 후렴과 등 어떤 방식으로도 이동 불가
            // (귀환은 플러그인이 직접 처리)
            if (!toIn || escapeCause(e.getCause())) {
                e.setCancelled(true);
                Msg.actionBar(p, "&c천공의 제단에서는 이동할 수 없습니다.");
                return;
            }
        }
        if (!fromIn && toIn) {
            lastPos.put(p.getUniqueId(), e.getFrom().clone());
            if (p.isGliding()) p.setGliding(false);
        }
    }

    /* 겉날개 / 엔더 진주 / 후렴과 */

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onGlide(org.bukkit.event.entity.EntityToggleGlideEvent e) {
        if (e.isGliding() && e.getEntity() instanceof Player p && restricted(p)) {
            e.setCancelled(true);
            Msg.actionBar(p, "&c천공의 제단에서는 겉날개를 사용할 수 없습니다.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPearlUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (it == null || it.getType() != Material.ENDER_PEARL || !restricted(e.getPlayer())) return;
        e.setCancelled(true);
        e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        Msg.actionBar(e.getPlayer(), "&c천공의 제단에서는 엔더 진주를 사용할 수 없습니다.");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPearlLaunch(org.bukkit.event.entity.ProjectileLaunchEvent e) {
        if (e.getEntity() instanceof org.bukkit.entity.EnderPearl && e.getEntity().getShooter() instanceof Player p && restricted(p)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChorus(org.bukkit.event.player.PlayerItemConsumeEvent e) {
        if (e.getItem().getType() == Material.CHORUS_FRUIT && restricted(e.getPlayer())) {
            e.setCancelled(true);
            Msg.actionBar(e.getPlayer(), "&c천공의 제단에서는 후렴과를 먹을 수 없습니다.");
        }
    }

    /** 1초마다: 제단 안에서 이미 활공 중이면 해제 */
    private void stopGliders() {
        Location c = center();
        if (c == null) return;
        for (Player p : c.getWorld().getPlayers()) if (p.isGliding() && restricted(p)) p.setGliding(false);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        // 천공 월드에서는 항상, 다른 월드의 제단 구역은 전투/분배 중에만 명령어 차단 (채팅은 가능)
        if (!restricted(p)) return;
        String label = e.getMessage().substring(1).split(" ", 2)[0].toLowerCase();
        if (label.contains(":")) label = label.substring(label.indexOf(':') + 1);
        if (cfg().getStringList("allowed-commands").contains(label) || label.equals("루팅")) return;
        e.setCancelled(true);
        Msg.send(p, "&c천공의 제단에서는 명령어를 사용할 수 없습니다. &7(채팅은 가능)");
    }

    /* 제단에서 사망: 이번 강림에는 재입장 불가, 이전 위치(없으면 스폰)에서 부활 */

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (!inArena(p.getLocation())) return;
        if (alive() || returnLeft > 0) diedThisDescent.add(p.getUniqueId());
        respawnHandled.add(p.getUniqueId());
        // 사망하면 전리품 분배에서 제외 (준 피해 기록 삭제, 분배 중이면 포기 처리)
        damage.remove(p.getUniqueId());
        if (loot != null && !loot.finished) loot.forfeit(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerRespawn(org.bukkit.event.player.PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (!respawnHandled.contains(p.getUniqueId())) return;
        Location to = lastPos.remove(p.getUniqueId());
        if (to == null || to.getWorld() == null || inArena(to)) to = global().warps.get("spawn");
        if (to == null) to = spawnOf("spawn");
        if (to != null) e.setRespawnLocation(to);
        UUID id = p.getUniqueId();
        boolean banned = diedThisDescent.contains(id);
        later(2, () -> {
            if (!p.isOnline()) return;
            Msg.title(p, "<gradient:#FFF200:#FFFCD4><b>심판을 받았습니다</b></gradient>",
                    banned ? "&7이번 강림에는 천공의 제단에 다시 들어갈 수 없습니다." : "&7이전 위치로 돌아왔습니다.", 10, 50, 15);
            Msg.send(p, "&6[☀] &f천공의 제단에서 쓰러져 이전 위치로 돌아왔습니다." + (banned ? " &7(이번 강림에는 재입장 불가)" : ""));
        });
    }

    /** CoreModule 리스폰 처리(스폰 이동)를 건너뛸지. 한 번 확인하면 지움 */
    public boolean consumeRespawn(Player p) {
        return respawnHandled.remove(p.getUniqueId());
    }

    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
        // 전투가 끝난 뒤 천공 월드에서 접속하면 (명령어를 못 쓰므로) 이전 위치로
        Player p = e.getPlayer();
        later(5, () -> {
            if (p.isOnline() && !locked() && inArena(p.getLocation())) sendBack(p);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // 귀환 대기 중 나가면 다음 접속 때 제단에 갇히지 않도록 바로 이전 위치로
        Player p = e.getPlayer();
        if (returnLeft > 0 && returnees.contains(p.getUniqueId()) && inArena(p.getLocation())) sendBack(p);
    }

    /* ======================= 이동권 / 이동 명령어 ======================= */

    /** 이동권 도착 지점: config boss.entry, 없으면 제단 남쪽 입구 (제단 윗면 기준 2칸 아래 바닥, 26칸 남쪽) */
    private Location entry() {
        Location c = center();
        if (c == null) return null;
        List<Double> e = cfg().getDoubleList("entry");
        if (e.size() >= 3) {
            Location l = new Location(c.getWorld(), e.get(0), e.get(1), e.get(2));
            if (e.size() >= 5) {
                l.setYaw(e.get(3).floatValue());
                l.setPitch(e.get(4).floatValue());
            }
            return l;
        }
        Location l = c.clone().add(0, -2, 26);
        l.setYaw(180);
        return l;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTicketUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!Msg.nameIs(e.getPlayer().getInventory().getItemInMainHand(), Items.N_ALTAR_TICKET)) return;
        e.setCancelled(true);
        openAltar(e.getPlayer());
    }

    /* ---------------- 천공의 제단 메뉴 ---------------- */

    private static final int SLOT_BOSS = 11, SLOT_GO = 13, SLOT_LOOT = 15;

    public void openAltar(Player p) {
        openAltar(p, null);
    }

    /** back: 뒤로 가기 버튼 (null 이면 없음) */
    public void openAltar(Player p, Runnable back) {
        Gui g = new Gui(4, "&6&l천공의 제단");
        g.put("altar", true);
        renderAltar(g, p);
        g.on(SLOT_GO, e -> travel(p));
        g.on(SLOT_LOOT, e -> later(1, () -> openLootList(p, back)));
        if (p.hasPermission(ADMIN)) {
            g.set(26, It.of(Material.COMMAND_BLOCK).name("&c&l전리품 설정 &7(관리자)").lore("&7보스 전리품 목록, 수량, 비중을 편집합니다.").build(),
                    e -> later(1, () -> openLootEditor(p)));
        }
        g.nav(p, back).fill().open(p);
    }

    private void renderAltar(Gui g, Player p) {
        // 보스 정보
        It boss;
        if (alive()) {
            double max = maxHealth(this.boss);
            boss = It.of(Material.WITHER_SKELETON_SKULL).name(NAME + " &c&l[강림 중]")
                    .lore("", "&7체력 : &c" + (int) Math.ceil(this.boss.getHealth()) + " &7/ " + (int) max,
                            "&7남은 전투 시간 : &e" + fmt(fightLeft),
                            "&7참가자 : &f" + damage.size() + "명");
        } else if (returnLeft > 0) {
            boss = It.of(Material.SKELETON_SKULL).name(NAME + " &7[처치됨]").lore("", "&7전리품 분배 중입니다.");
        } else {
            long left = Math.max(0, (nextSpawnAt - System.currentTimeMillis()) / 1000);
            boss = It.of(Material.SKELETON_SKULL).name(NAME + " &7[강림 전]")
                    .lore("", "&7다음 강림까지 : &e" + (left <= 0 ? "곧" : fmt(left)), "&8강림하면 전체 알림이 나갑니다.");
        }
        g.set(SLOT_BOSS, boss.build());

        // 이동 버튼
        int tickets = countTickets(p);
        String deny = denyReason(p);
        It go = It.of(deny == null ? Material.ENDER_EYE : Material.BARRIER)
                .name(deny == null ? "&a&l천공의 제단으로 이동" : "&c&l이동할 수 없습니다")
                .lore("", "&7보유 이동권 : " + (tickets > 0 ? "&a" : "&c") + tickets + "개", "&8이동 시 이동권 1개 소모 (1회용)", "");
        go.lore(deny == null ? "&e&l클릭하여 이동" : "&c" + deny);
        if (deny == null) go.ench(Enchantment.INFINITY, 1);
        g.set(SLOT_GO, go.build());

        // 전리품 / 규칙
        ConfigurationSection lc = cfg().getConfigurationSection("loot");
        int items = lc == null ? 5 : lc.getInt("items", 5);
        g.set(SLOT_LOOT, It.of(Material.CHEST).name("&6&l전리품 & 규칙").lore("",
                "&7처치 시 참가자 전원에게 전리품 &e" + items + "개",
                "&7좌클릭 &f주사위(0~100) &7/ 우클릭 &f포기",
                "",
                "&7전투 제한 시간 &e" + fmt((long) (cfg().getDouble("fight-minutes", 5) * 60)),
                "&7처치 후 &e" + (int) cfg().getDouble("return-seconds", 60) + "초 &7뒤 이전 위치로 귀환",
                "&7천공의 제단에서는 명령어 사용 불가",
                "",
                "&e&l클릭하여 전리품 목록 보기").build());
    }

    /** 보스가 강림해 있는지 (메뉴 표시용) */
    public boolean descended() {
        return alive();
    }

    /** 서버 메뉴에 보여줄 한 줄 상태 */
    public String statusLine() {
        if (center() == null) return "&7제단이 아직 준비되지 않았습니다.";
        if (alive()) return "&c&l강림 중! &7남은 시간 &e" + fmt(fightLeft);
        if (returnLeft > 0) return "&7전리품 분배 중";
        long left = Math.max(0, (nextSpawnAt - System.currentTimeMillis()) / 1000);
        return "&7다음 강림까지 &e" + (left <= 0 ? "곧" : fmt(left));
    }

    /** 전리품 목록 (확률은 표시하지 않음) */
    public void openLootList(Player p, Runnable altarBack) {
        ConfigurationSection lc = cfg().getConfigurationSection("loot");
        List<Map<?, ?>> pool = lc == null ? List.of() : lc.getMapList("pool");
        if (pool.isEmpty()) pool = defaultPool();
        List<ItemStack> shown = new ArrayList<>();
        for (Map<?, ?> m : pool) {
            ItemStack it = lootItem(m);
            if (it == null) continue;
            int min = (int) num(m.get("min"), 1);
            int max = Math.max(min, (int) num(m.get("max"), min));
            it.setAmount(1);
            ItemMeta meta = it.getItemMeta();
            List<net.kyori.adventure.text.Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore = Msg.append(lore, "&7수량 : &f" + (min == max ? min + "개" : min + " ~ " + max + "개"));
            meta.lore(lore);
            it.setItemMeta(meta);
            shown.add(it);
        }
        ItemStack clay = Items.claymoreSd();
        ItemMeta cm = clay.getItemMeta();
        List<net.kyori.adventure.text.Component> cl = cm.lore() == null ? new ArrayList<>() : new ArrayList<>(cm.lore());
        cl = Msg.append(cl, "<gradient:#FFF200:#FFFCD4><b>✦ 희귀 전리품 ✦</b></gradient>", "&7매우 드물게 전리품에 섞여 나옵니다.");
        cm.lore(cl);
        clay.setItemMeta(cm);

        int contentRows = Math.max(1, Math.min(4, (shown.size() + 6) / 7));
        Gui g = new Gui(contentRows + 3, "&6&l천공의 전리품 목록");
        g.set(4, clay);   // 맨 윗줄 가운데: 희귀 전리품
        int idx = 0;
        for (int row = 0; row < contentRows && idx < shown.size(); row++) {
            for (int col = 1; col <= 7 && idx < shown.size(); col++) g.set((row + 1) * 9 + col, shown.get(idx++));
        }
        g.nav(p, () -> openAltar(p, altarBack)).fill().open(p);
    }

    /** 이동할 수 없는 이유 (null = 이동 가능) */
    private String denyReason(Player p) {
        if (entry() == null) return "천공의 제단이 아직 설정되지 않았습니다.";
        if (!alive()) return returnLeft > 0 ? "전리품 분배 중입니다." : "천공의 심판자가 아직 강림하지 않았습니다.";
        if (inArena(p.getLocation())) return "이미 천공의 제단에 있습니다.";
        if (diedThisDescent.contains(p.getUniqueId())) return "이번 강림에서 사망하여 다시 입장할 수 없습니다.";
        CombatTagModule ct = plugin.module(CombatTagModule.class);
        if (ct != null && ct.tagged(p)) return "전투 중에는 이동할 수 없습니다.";
        if (countTickets(p) <= 0) return "천공의 제단 이동권이 없습니다.";
        return null;
    }

    private int countTickets(Player p) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) if (Msg.nameIs(it, Items.N_ALTAR_TICKET)) n += it.getAmount();
        return n;
    }

    /** 메뉴에서 클릭: 이동권 1개를 쓰고 천공의 제단으로 이동 (관리자도 이동권 필요) */
    public boolean travel(Player p) {
        String deny = denyReason(p);
        if (deny != null) {
            Msg.send(p, "&c" + deny);
            Gui.denySound(p);
            if (p.getOpenInventory().getTopInventory() != null && p.getOpenInventory().getTopInventory().getHolder() instanceof Gui g
                    && g.get("altar") != null) renderAltar(g, p);
            return false;
        }
        Location to = entry();
        if (!takeTicket(p)) return false;
        p.closeInventory();
        Fx.pillar(p.getLocation(), 6, GOLD_L, Particle.END_ROD);
        if (!p.teleport(to)) {
            It.give(p, Items.altarTicket());   // 이동 실패 시 돌려줌
            return false;
        }
        Fx.pillar(to, 10, GOLD, Particle.END_ROD);
        Fx.shockwave(to.clone().add(0, 0.1, 0), GOLD_L, 4, 10);
        Msg.sound(p, "block.beacon.activate", 1, 1.4f);
        Msg.title(p, "<b><gradient:#FFF200:#F6E47C:#FFFCD4>천공의 제단</gradient></b>", "&c천공의 심판자와 전투 중입니다! &7(" + fmt(fightLeft) + ")", 10, 50, 15);
        Msg.send(p, "&e천공의 제단으로 이동했습니다. &7(이동권 1개 사용, 남은 전투 시간 " + fmt(fightLeft) + ")");
        return true;
    }

    /** 열려 있는 천공의 제단 메뉴를 매초 갱신 */
    private void refreshAltarMenus() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory() != null && p.getOpenInventory().getTopInventory().getHolder() instanceof Gui g
                    && g.get("altar") != null) renderAltar(g, p);
        }
    }

    /** 인벤토리에서 이동권 1개 소모 */
    private boolean takeTicket(Player p) {
        ItemStack[] inv = p.getInventory().getStorageContents();
        for (int i = 0; i < inv.length; i++) {
            ItemStack it = inv[i];
            if (!Msg.nameIs(it, Items.N_ALTAR_TICKET)) continue;
            if (it.getAmount() <= 1) p.getInventory().setItem(i, null);
            else it.setAmount(it.getAmount() - 1);
            return true;
        }
        return false;
    }

    private List<Player> nearby(Location l, double r) {
        List<Player> out = new ArrayList<>();
        for (Player p : l.getWorld().getPlayers()) {
            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR || p.isDead()) continue;
            if (p.getLocation().distanceSquared(l) <= r * r) out.add(p);
        }
        return out;
    }

    private static double maxHealth(LivingEntity e) {
        Attribute a = attr("max_health", "generic.max_health");
        AttributeInstance ai = a == null ? null : e.getAttribute(a);
        return ai == null ? 20 : ai.getValue();
    }

    /** 심판의 빛: 1.5초 예고 후 그 자리에 번개 */
    private void judgmentLight(Player target) {
        Location spot = target.getLocation().clone();
        for (int i = 0; i < 3; i++) {
            later(i * 10L, () -> {
                Fx.ring(spot.clone().add(0, 0.1, 0), 2.5, 30, GOLD, 1.4f);
                Fx.ring(spot.clone().add(0, 0.1, 0), 1.2, 16, GOLD_L, 1.1f);
            });
        }
        Msg.soundAt(spot, "block.beacon.power_select", 1.5f, 1.8f);
        later(30, () -> {
            if (!alive()) return;
            Fx.lightning(spot);
            Fx.pillar(spot, 14, GOLD, Particle.END_ROD);
            Fx.burst(spot.clone().add(0, 1, 0), Particle.ELECTRIC_SPARK, 40, 0.4);
            double dmg = cfg().getDouble("judgment-damage", 8);
            for (Player p : nearby(spot, 2.5)) p.damage(dmg, boss);
        });
    }

    /** 천공의 파동: 주변을 밀쳐내는 충격파 */
    private void heavenWave() {
        Location l = boss.getLocation();
        Fx.shockwave(l.clone().add(0, 0.2, 0), GOLD, 7, 10);
        Fx.p(l.clone().add(0, 1, 0), Particle.SONIC_BOOM, 1, 0, 0);
        Msg.soundAt(l, "entity.warden.sonic_boom", 1.2f, 1.5f);
        for (Player p : nearby(l, 6)) {
            Vector v = p.getLocation().toVector().subtract(l.toVector()).setY(0);
            if (v.lengthSquared() < 0.01) v = new Vector(1, 0, 0);
            p.setVelocity(v.normalize().multiply(1.6).setY(0.7));
            p.damage(cfg().getDouble("wave-damage", 4), boss);
        }
    }

    /* ======================= 피해 기록 / 처치 ======================= */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!alive() || e.getEntity() != boss) return;
        Player p = CoreModule.playerAttacker(e);
        if (p != null) damage.merge(p.getUniqueId(), e.getFinalDamage(), Double::sum);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent e) {
        if (!e.getEntity().getPersistentDataContainer().has(bossKey, PersistentDataType.BYTE)) return;
        e.getDrops().clear();
        e.setDroppedExp(cfg().getInt("exp", 500));
        Location l = e.getEntity().getLocation();
        Set<UUID> participants = new HashSet<>();
        for (UUID u : damage.keySet()) if (Bukkit.getPlayer(u) != null && !diedThisDescent.contains(u)) participants.add(u);
        Player killer = e.getEntity().getKiller();
        if (killer != null) participants.add(killer.getUniqueId());
        damage.clear();
        clearBoss();

        Fx.pillar(l, 20, GOLD, Particle.END_ROD);
        Fx.shockwave(l.clone().add(0, 0.1, 0), GOLD_L, 10, 16);
        Msg.soundAt(l, "ui.toast.challenge_complete", 2, 1);
        Msg.broadcast("&6&l[!] " + NAME + " &e이(가) 쓰러졌습니다! &7(참가자 " + participants.size() + "명)");

        returnees.clear();
        returnees.addAll(participants);
        for (Player p : l.getWorld().getPlayers()) if (inArena(p.getLocation())) returnees.add(p.getUniqueId());
        returnLeft = (int) cfg().getDouble("return-seconds", 60);
        if (!participants.isEmpty()) startLoot(rollLoot(), l, participants);
    }

    /* ======================= 전리품 ======================= */

    /** config boss.loot.pool 에서 가중치로 서로 다른 항목을 뽑고, 확률적으로 클레이모어를 섞는다 */
    private List<ItemStack> rollLoot() {
        ConfigurationSection lc = cfg().getConfigurationSection("loot");
        int count = lc == null ? 5 : Math.max(1, Math.min(27, lc.getInt("items", 5)));
        double claymoreChance = lc == null ? cfg().getDouble("drop-chance", 0.5) : lc.getDouble("claymore-chance", cfg().getDouble("drop-chance", 0.5));
        List<Map<?, ?>> pool = lc == null ? List.of() : lc.getMapList("pool");
        if (pool.isEmpty()) pool = defaultPool();

        List<Map<?, ?>> candidates = new ArrayList<>(pool);
        List<ItemStack> out = new ArrayList<>();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        while (out.size() < count && !candidates.isEmpty()) {
            double total = 0;
            for (Map<?, ?> m : candidates) total += num(m.get("weight"), 10);
            double pick = r.nextDouble(total);
            for (int i = 0; i < candidates.size(); i++) {
                pick -= num(candidates.get(i).get("weight"), 10);
                if (pick < 0) {
                    ItemStack it = lootItem(candidates.remove(i));
                    if (it != null) out.add(it);
                    break;
                }
            }
        }
        if (r.nextDouble(100) < claymoreChance) {
            if (out.size() >= count) out.remove(r.nextInt(out.size()));
            out.add(Items.claymoreSd());
        }
        return out;
    }

    private static double num(Object o, double def) {
        return o instanceof Number n ? n.doubleValue() : def;
    }

    private ItemStack lootItem(Map<?, ?> m) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int min = (int) num(m.get("min"), 1);
        int max = Math.max(min, (int) num(m.get("max"), min));
        int amount = r.nextInt(min, max + 1);
        Object special = m.get("special");
        Object raw = m.get("item");
        ItemStack it;
        if (raw instanceof ItemStack is && !is.getType().isAir()) {
            it = is.clone();   // GUI 에서 직접 넣은 아이템 (이름/인챈트 그대로)
        } else if (special != null) {
            it = switch (special.toString()) {
                case "quantum_matter" -> Items.quantumMatter();
                case "vibranium" -> Items.vibranium();
                case "time_shard" -> Items.timeShard();
                case "mending_book" -> It.of(Material.ENCHANTED_BOOK).ench(Enchantment.MENDING, 1).build();
                case "claymore" -> Items.claymoreSd();
                case "altar_ticket" -> Items.altarTicket();
                default -> null;
            };
        } else {
            Material mat = Material.matchMaterial(String.valueOf(m.get("material")));
            it = mat == null || !mat.isItem() ? null : new ItemStack(mat);
        }
        if (it == null) {
            plugin.getLogger().warning("boss.loot.pool 항목을 해석할 수 없습니다: " + m);
            return null;
        }
        it.setAmount(Math.max(1, Math.min(it.getMaxStackSize(), amount)));
        return it;
    }

    /**
     * 천공의 심판자 클레이모어 획득 연출 (제단 분배와 무기 교환권 공용)
     * w: 획득한 사람 (오프라인이면 null), how: "OOO 님이 주사위 N (으)로" 같은 앞 문장
     */
    public void claymoreFanfare(Player w, ItemStack claymore, String how) {
        if (w != null) {
            Fx.pillar(w.getLocation(), 12, GOLD, Particle.END_ROD);
            Fx.burst(w.getLocation().add(0, 1, 0), Particle.TOTEM_OF_UNDYING, 120, 0.6);
            Msg.sound(w, "ui.toast.challenge_complete", 1, 1);
            Msg.title(w, "<gradient:#FFF200:#F6E47C:#FFFCD4><b>Arbiter of Heaven</b></gradient>", Items.CLAYMORE_SD_NAME, 10, 70, 20);
        }
        Component item = Msg.hoverItem(claymore, Items.CLAYMORE_SD_NAME);
        String line = "<gradient:#FFF200:#F6E47C:#FFFCD4><st>                                                            </st></gradient>";
        Msg.broadcast(" ");
        Msg.broadcast(line);
        Msg.broadcast(" ");
        Msg.broadcast("            <b><gradient:#FFF200:#F6E47C:#FFFCD4>✦ Arbiter of Heaven ✦</gradient></b>");
        Msg.broadcast(" ");
        Msg.broadcastItem("  " + how, item);
        Msg.broadcastItem("  " + Msg.ITEM + " &f를 획득했습니다!", item);
        Msg.broadcast(" ");
        Msg.broadcast(line);
        Msg.broadcast(" ");
        for (Player o : Bukkit.getOnlinePlayers()) Msg.sound(o, "ui.toast.challenge_complete", 0.6f, 1.2f);
    }

    /* ======================= 전리품 설정 GUI (관리자) ======================= */

    /** config 의 전리품 목록을 편집 가능한 형태로 복사 (없으면 기본 목록) */
    private List<Map<String, Object>> editablePool() {
        ConfigurationSection lc = cfg().getConfigurationSection("loot");
        List<Map<?, ?>> pool = lc == null ? List.of() : lc.getMapList("pool");
        if (pool.isEmpty()) pool = defaultPool();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<?, ?> m : pool) {
            Map<String, Object> c = new LinkedHashMap<>();
            for (Map.Entry<?, ?> en : m.entrySet()) c.put(String.valueOf(en.getKey()), en.getValue());
            out.add(c);
        }
        return out;
    }

    private void savePool(List<Map<String, Object>> pool) {
        cfg().set("loot.pool", pool);
        plugin.saveConfig();
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** 목록에 보이는 아이콘 (수량 1개짜리 + 설정 값) */
    private ItemStack editorIcon(Map<String, Object> m, int totalWeight) {
        ItemStack it = lootItem(m);
        if (it == null) it = It.of(Material.BARRIER).name("&c알 수 없는 항목").build();
        it.setAmount(1);
        ItemMeta meta = it.getItemMeta();
        List<net.kyori.adventure.text.Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        int min = (int) num(m.get("min"), 1), max = Math.max(min, (int) num(m.get("max"), min)), w = (int) num(m.get("weight"), 10);
        lore = Msg.append(lore, "&8━━━━━━━━━━━━━━", "&7수량 : &f" + (min == max ? min + "개" : min + " ~ " + max + "개"),
                "&7비중 : &e" + w + " &8/ 전체 " + totalWeight, "", "&e클릭 &7: 수량 · 비중 편집");
        meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    public void openLootEditor(Player p) {
        if (!p.hasPermission(ADMIN)) return;
        List<Map<String, Object>> pool = editablePool();
        int total = 0;
        for (Map<String, Object> m : pool) total += (int) num(m.get("weight"), 10);
        Gui g = new Gui(6, "&4&l전리품 설정 &8(" + pool.size() + "/45)");
        for (int i = 0; i < pool.size() && i < 45; i++) {
            final int idx = i;
            g.set(i, editorIcon(pool.get(i), total), e -> later(1, () -> openEntryEditor(p, idx)));
        }
        int l = g.last();
        ConfigurationSection lc = cfg().getConfigurationSection("loot");
        int items = lc == null ? 5 : lc.getInt("items", 5);
        double clay = lc == null ? 0.5 : lc.getDouble("claymore-chance", 0.5);
        g.set(l, It.of(Material.BOOK).name("&e&l사용법").lore("",
                "&f아래 내 인벤토리의 아이템을 클릭하면", "&f그 아이템(이름·인챈트 그대로)이 목록에 추가됩니다.",
                "&7추가될 때 수량은 들고 있던 개수, 비중은 10", "", "&f목록의 아이템을 클릭하면 수량 · 비중 · 삭제", "",
                "&8비중이 클수록 자주 나옵니다. 처치마다 서로 다른", "&8항목을 '전리품 개수' 만큼 뽑습니다.", "&8변경 사항은 바로 저장됩니다.").build());
        g.set(l + 1, It.of(Material.CHEST).name("&6&l전리품 개수 : &e" + items + "개").lore("", "&a좌클릭 &7+1   &c우클릭 &7-1", "&8(1 ~ 27)").build(), e -> {
            int v = clamp(items + (e.isRightClick() ? -1 : 1), 1, 27);
            cfg().set("loot.items", v);
            plugin.saveConfig();
            later(1, () -> openLootEditor(p));
        });
        g.set(l + 2, It.of(Material.NETHERITE_SWORD).name("&6&l클레이모어 확률 : &e" + round(clay) + "%")
                .lore("", "&a좌클릭 &7+0.1%   &c우클릭 &7-0.1%", "&aShift+좌클릭 &7+1%   &cShift+우클릭 &7-1%", "&8(0 ~ 100%)").build(), e -> {
            double step = e.isShiftClick() ? 1 : 0.1;
            double v = Math.max(0, Math.min(100, round(clay + (e.isRightClick() ? -step : step))));
            cfg().set("loot.claymore-chance", v);
            plugin.saveConfig();
            later(1, () -> openLootEditor(p));
        });
        g.set(l + 6, It.of(Material.ENDER_EYE).name("&a&l미리보기").lore("", "&7지금 설정으로 전리품을 한 번 뽑아 봅니다.", "&8(아이템은 지급되지 않음)").build(), e -> {
            List<ItemStack> roll = rollLoot();
            Msg.send(p, "&a[미리보기] &7" + roll.size() + "개:");
            for (ItemStack it : roll) Msg.send(p, " &8- &f" + Items.describeShort(it));
        });
        g.set(l + 8, It.of(Material.LAVA_BUCKET).name("&c&l기본 목록으로 초기화").lore("", "&cShift + 우클릭 &7으로 실행", "&8지금 목록이 기본 13종으로 바뀝니다.").build(), e -> {
            if (e.getClick() != ClickType.SHIFT_RIGHT) {
                Msg.send(p, "&7초기화하려면 &cShift + 우클릭 &7하세요.");
                return;
            }
            List<Map<String, Object>> def = new ArrayList<>();
            for (Map<?, ?> m : defaultPool()) {
                Map<String, Object> c = new LinkedHashMap<>();
                for (Map.Entry<?, ?> en : m.entrySet()) c.put(String.valueOf(en.getKey()), en.getValue());
                def.add(c);
            }
            savePool(def);
            Msg.send(p, "&c전리품 목록을 기본값으로 초기화했습니다.");
            later(1, () -> openLootEditor(p));
        });
        // 내 인벤토리 클릭 -> 추가
        g.bottomClick(e -> {
            ItemStack src = e.getCurrentItem();
            if (It.isAir(src) && e.getClickedInventory() != null) src = e.getClickedInventory().getItem(e.getSlot());
            if (It.isAir(src)) return;
            List<Map<String, Object>> cur = editablePool();
            if (cur.size() >= 45) {
                Msg.send(p, "&c전리품 목록은 최대 45개입니다.");
                Gui.denySound(p);
                return;
            }
            ItemStack one = src.clone();
            int amount = one.getAmount();
            one.setAmount(1);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("item", one);
            m.put("min", amount);
            m.put("max", amount);
            m.put("weight", 10);
            cur.add(m);
            savePool(cur);
            Msg.send(p, "&a전리품에 추가했습니다: &f" + Items.describeShort(src));
            Msg.sound(p, "entity.item.pickup", 0.8f, 1.2f);
            later(1, () -> openLootEditor(p));
        });
        g.nav(p, null).fill().open(p);
    }

    private void openEntryEditor(Player p, int idx) {
        List<Map<String, Object>> pool = editablePool();
        if (idx < 0 || idx >= pool.size()) {
            openLootEditor(p);
            return;
        }
        Map<String, Object> m = pool.get(idx);
        int min = (int) num(m.get("min"), 1), max = Math.max(min, (int) num(m.get("max"), min)), w = (int) num(m.get("weight"), 10);
        int total = 0;
        for (Map<String, Object> x : pool) total += (int) num(x.get("weight"), 10);
        Gui g = new Gui(6, "&4&l전리품 편집");
        g.set(4, editorIcon(m, total));
        java.util.function.BiConsumer<String, Integer> change = (key, delta) -> {
            List<Map<String, Object>> cur = editablePool();
            if (idx >= cur.size()) return;
            Map<String, Object> e2 = cur.get(idx);
            int mn = (int) num(e2.get("min"), 1), mx = Math.max(mn, (int) num(e2.get("max"), mn)), wt = (int) num(e2.get("weight"), 10);
            switch (key) {
                case "min" -> {
                    mn = clamp(mn + delta, 1, 64);
                    if (mx < mn) mx = mn;
                }
                case "max" -> {
                    mx = clamp(mx + delta, 1, 64);
                    if (mn > mx) mn = mx;
                }
                default -> wt = clamp(wt + delta, 1, 1000);
            }
            e2.put("min", mn);
            e2.put("max", mx);
            e2.put("weight", wt);
            savePool(cur);
            later(1, () -> openEntryEditor(p, idx));
        };
        row(g, 10, "최소 수량", min + "개", Material.IRON_NUGGET, 1, 5, d -> change.accept("min", d));
        row(g, 19, "최대 수량", max + "개", Material.GOLD_NUGGET, 1, 5, d -> change.accept("max", d));
        row(g, 28, "비중", String.valueOf(w), Material.AMETHYST_SHARD, 1, 10, d -> change.accept("weight", d));
        g.set(g.last() + 8, It.of(Material.BARRIER).name("&c&l이 항목 삭제").lore("", "&cShift + 우클릭 &7으로 삭제").build(), e -> {
            if (e.getClick() != ClickType.SHIFT_RIGHT) {
                Msg.send(p, "&7삭제하려면 &cShift + 우클릭 &7하세요.");
                return;
            }
            List<Map<String, Object>> cur = editablePool();
            if (idx < cur.size()) {
                cur.remove(idx);
                savePool(cur);
                Msg.send(p, "&c전리품 항목을 삭제했습니다.");
                Msg.sound(p, "entity.item.break", 0.8f, 1f);
            }
            later(1, () -> openLootEditor(p));
        });
        g.quiet(g.last() + 8);
        g.nav(p, () -> openLootEditor(p)).fill().open(p);
    }

    /** [-큰] [-1] [값] [+1] [+큰] 한 줄 */
    private void row(Gui g, int start, String label, String value, Material icon, int small, int big, java.util.function.IntConsumer apply) {
        g.set(start, It.of(Material.RED_DYE).name("&c-" + big).lore("&7" + label).build(), e -> apply.accept(-big));
        g.set(start + 1, It.of(Material.PINK_DYE).name("&c-" + small).lore("&7" + label).build(), e -> apply.accept(-small));
        g.set(start + 3, It.of(icon).name("&f&l" + label + " : &e" + value).build());
        g.set(start + 5, It.of(Material.LIME_DYE).name("&a+" + small).lore("&7" + label).build(), e -> apply.accept(small));
        g.set(start + 6, It.of(Material.GREEN_DYE).name("&a+" + big).lore("&7" + label).build(), e -> apply.accept(big));
    }

    private static List<Map<?, ?>> defaultPool() {
        List<Map<?, ?>> l = new ArrayList<>();
        l.add(Map.of("material", "DIAMOND", "min", 4, "max", 10, "weight", 20));
        l.add(Map.of("material", "GOLD_BLOCK", "min", 4, "max", 8, "weight", 20));
        l.add(Map.of("material", "EXPERIENCE_BOTTLE", "min", 16, "max", 48, "weight", 20));
        l.add(Map.of("material", "EMERALD_BLOCK", "min", 2, "max", 5, "weight", 15));
        l.add(Map.of("material", "GOLDEN_APPLE", "min", 3, "max", 6, "weight", 15));
        l.add(Map.of("material", "NETHERITE_SCRAP", "min", 1, "max", 3, "weight", 10));
        l.add(Map.of("material", "ANCIENT_DEBRIS", "min", 1, "max", 2, "weight", 8));
        l.add(Map.of("material", "TOTEM_OF_UNDYING", "min", 1, "max", 1, "weight", 6));
        l.add(Map.of("special", "quantum_matter", "min", 1, "max", 3, "weight", 8));
        l.add(Map.of("special", "time_shard", "min", 2, "max", 5, "weight", 8));
        l.add(Map.of("material", "ENCHANTED_GOLDEN_APPLE", "min", 1, "max", 1, "weight", 4));
        l.add(Map.of("special", "mending_book", "min", 1, "max", 1, "weight", 4));
        l.add(Map.of("material", "NETHERITE_INGOT", "min", 1, "max", 1, "weight", 2));
        return l;
    }

    /* ======================= 주사위 분배 ======================= */

    private void startLoot(List<ItemStack> items, Location dropLoc, Set<UUID> participants) {
        if (loot != null) loot.resolveAll();
        loot = new LootSession(items, dropLoc, participants);
        loot.start();
    }

    /** 아이템 한 칸의 주사위 상태 */
    private static final class Roll {
        final ItemStack item;
        final Map<UUID, Integer> rolls = new LinkedHashMap<>();
        final Set<UUID> passed = new HashSet<>();
        boolean done;

        Roll(ItemStack item) {
            this.item = item;
        }
    }

    private final class LootSession {
        final Location dropLoc;
        final Set<UUID> participants;
        final Map<Integer, Roll> slots = new LinkedHashMap<>();
        final Gui gui;
        int secondsLeft;
        boolean finished;
        int taskId = -1;

        LootSession(List<ItemStack> items, Location dropLoc, Set<UUID> participants) {
            this.dropLoc = dropLoc.clone();
            this.participants = new HashSet<>(participants);
            this.secondsLeft = cfg().getInt("roll-seconds", 30);
            this.gui = new Gui(3, "&6&l천공의 전리품");
            List<Integer> free = new ArrayList<>();
            for (int i = 0; i < 27; i++) free.add(i);
            java.util.Collections.shuffle(free);
            for (int i = 0; i < items.size() && i < free.size(); i++) slots.put(free.get(i), new Roll(items.get(i)));
        }

        void start() {
            // 분배가 끝날 때까지 창을 닫을 수 없음 (닫으면 바로 다시 열림)
            gui.onClose(e -> {
                if (finished || !(e.getPlayer() instanceof Player p) || !participants.contains(p.getUniqueId())) return;
                later(1, () -> {
                    if (!finished && p.isOnline() && !p.isDead()
                            && (p.getOpenInventory().getTopInventory() == null || p.getOpenInventory().getTopInventory().getHolder() != gui)) gui.open(p);
                });
            });
            gui.anyClick(e -> {
                if (!(e.getWhoClicked() instanceof Player p)) return;
                Roll r = slots.get(e.getSlot());
                if (r == null || r.done) return;
                if (e.getClick() == ClickType.LEFT) roll(p, e.getSlot(), r);
                else if (e.getClick() == ClickType.RIGHT) pass(p, e.getSlot(), r);
            });
            render();
            for (UUID u : participants) {
                Player p = Bukkit.getPlayer(u);
                if (p == null) continue;
                gui.open(p);
                Msg.sound(p, "block.end_portal.spawn", 0.6f, 1.6f);
            }
            taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (finished) return;
                secondsLeft--;
                for (UUID u : participants) {
                    Player p = Bukkit.getPlayer(u);
                    if (p != null && p.getOpenInventory().getTopInventory() != null && p.getOpenInventory().getTopInventory().getHolder() == gui) {
                        Msg.actionBar(p, "&6천공의 전리품 &7- &e남은 시간 " + Math.max(0, secondsLeft) + "초");
                    }
                }
                if (secondsLeft <= 0) resolveAll();
            }, 20L, 20L).getTaskId();
        }

        void roll(Player p, int slot, Roll r) {
            if (!participants.contains(p.getUniqueId())) {
                Msg.send(p, "&c보스 처치에 참여한 사람만 굴릴 수 있습니다.");
                return;
            }
            if (r.rolls.containsKey(p.getUniqueId()) || r.passed.contains(p.getUniqueId())) {
                Msg.send(p, "&c이 아이템은 이미 선택했습니다.");
                return;
            }
            int n = ThreadLocalRandom.current().nextInt(0, 101);
            r.rolls.put(p.getUniqueId(), n);
            Msg.sound(p, "block.note_block.chime", 1, 0.5f + n / 100f);
            if (participants.size() > 1) tell("&e" + p.getName() + " &f님이 " + Items.describeShort(r.item) + " &f주사위: &6&l" + n);
            check(slot, r);
        }

        void pass(Player p, int slot, Roll r) {
            if (!participants.contains(p.getUniqueId())) return;
            if (r.rolls.containsKey(p.getUniqueId()) || r.passed.contains(p.getUniqueId())) {
                Msg.send(p, "&c이 아이템은 이미 선택했습니다.");
                return;
            }
            r.passed.add(p.getUniqueId());
            Msg.sound(p, "block.note_block.bass", 1, 0.8f);
            if (participants.size() > 1) tell("&7" + p.getName() + " 님이 " + Items.describeShort(r.item) + " &7를 포기했습니다.");
            check(slot, r);
        }

        /** 사망 등으로 분배에서 빠짐: 굴린 주사위도 무효, 남은 사람끼리 결정 */
        void forfeit(UUID id) {
            if (!participants.remove(id)) return;
            for (Roll r : slots.values()) {
                if (r.done) continue;
                r.rolls.remove(id);
                r.passed.remove(id);
            }
            Player p = Bukkit.getPlayer(id);
            if (p != null) Msg.send(p, "&c사망하여 전리품 분배에서 제외되었습니다.");
            for (Map.Entry<Integer, Roll> en : new ArrayList<>(slots.entrySet())) {
                if (finished) break;
                check(en.getKey(), en.getValue());
            }
        }

        void check(int slot, Roll r) {
            if (r.rolls.size() + r.passed.size() >= participants.size()) award(slot, r);
            render();
            if (slots.values().stream().allMatch(x -> x.done)) {
                resolveAll();
                if (returnLeft > 0) {
                    returnLeft = Math.min(returnLeft, (int) cfg().getDouble("return-after-roll-seconds", 10));
                    tell("&a모든 분배가 끝났습니다. &e" + (long) cfg().getDouble("return-after-roll-seconds", 10) + "초 &a후 이전 위치로 이동합니다.");
                }
            }
        }

        void award(int slot, Roll r) {
            if (r.done) return;
            r.done = true;
            UUID winner = null;
            int best = -1;
            for (Map.Entry<UUID, Integer> en : r.rolls.entrySet()) {   // 동점이면 먼저 굴린 사람
                if (en.getValue() > best) {
                    best = en.getValue();
                    winner = en.getKey();
                }
            }
            boolean claymore = Msg.nameIs(r.item, Items.N_CLAYMORE_SD);
            if (winner == null) {
                dropLoc.getWorld().dropItemNaturally(dropLoc, r.item);
                tell("&7" + Items.describeShort(r.item) + " &7: 모두 포기하여 제단에 떨어졌습니다.");
                return;
            }
            Player w = Bukkit.getPlayer(winner);
            String name = Bukkit.getOfflinePlayer(winner).getName();
            if (w != null) {
                It.give(w, r.item);
                Msg.sound(w, "entity.item.pickup", 1, 1.2f);
            } else {
                plugin.module(MailModule.class).send(winner, r.item);
            }
            if (claymore) {
                claymoreFanfare(w, r.item, "&e" + name + " &f님이 주사위 &6&l" + best + " &f(으)로");
            } else {
                tell("&a" + name + " &f님이 " + Items.describeShort(r.item) + " &f획득" + (participants.size() > 1 ? " &7(주사위 " + best + ")" : ""));
            }
        }

        void tell(String msg) {
            for (UUID u : participants) {
                Player p = Bukkit.getPlayer(u);
                if (p != null) Msg.send(p, msg);
            }
        }

        void render() {
            for (int i = 0; i < 27; i++) {
                Roll r = slots.get(i);
                if (r == null || r.done) {
                    gui.set(i, It.pane(Gui.FILLER, " "));
                    continue;
                }
                ItemStack shown = r.item.clone();
                ItemMeta meta = shown.getItemMeta();
                List<net.kyori.adventure.text.Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
                List<String> add = new ArrayList<>();
                add.add("");
                add.add("&a&l좌클릭 &7: 주사위 굴리기 (0 ~ 100)");
                add.add("&c&l우클릭 &7: 포기");
                if (participants.size() > 1) {
                    add.add("");
                    for (UUID u : participants) {
                        String n = Bukkit.getOfflinePlayer(u).getName();
                        Integer v = r.rolls.get(u);
                        add.add(v != null ? "&f" + n + " &7: &6" + v : r.passed.contains(u) ? "&8" + n + " : 포기" : "&7" + n + " : 선택 중...");
                    }
                }
                lore = Msg.append(lore, add.toArray(new String[0]));
                meta.lore(lore);
                shown.setItemMeta(meta);
                gui.set(i, shown);
            }
        }

        /** 제한 시간 종료 또는 새 분배 시작: 남은 아이템 모두 결정 */
        void resolveAll() {
            if (finished) return;
            finished = true;
            if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
            for (Map.Entry<Integer, Roll> en : slots.entrySet()) award(en.getKey(), en.getValue());
            for (UUID u : participants) {
                Player p = Bukkit.getPlayer(u);
                if (p != null && p.getOpenInventory().getTopInventory() != null
                        && p.getOpenInventory().getTopInventory().getHolder() == gui) p.closeInventory();
            }
            if (loot == this) loot = null;
        }
    }
}
