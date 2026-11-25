package kr.wave.core.module;

import kr.wave.core.Items;
import kr.wave.core.WaveCore;
import kr.wave.core.data.PlayerData;
import kr.wave.core.util.Cmd;
import kr.wave.core.util.Fx;
import kr.wave.core.util.Gui;
import kr.wave.core.util.It;
import kr.wave.core.util.Msg;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 튜토리얼: 처음 들어온 플레이어에게 서버 기능을 하나씩 해보게 하고, 모두 마치면 보상
 * 진행은 화면 위 보스바로 안내, /튜토리얼 로 목록 확인
 * 튜토리얼 맵(worlds.tutorial 월드, 시작 위치는 '/wave setwarp tutorial')이 있으면 처음 접속한 사람을 그 맵으로 보내고,
 * 마치거나 건너뛰기 전까지 맵 밖으로 나갈 수 없음 (맵 안에서는 피해 · 배고픔 없음). 마치면 스폰으로 이동
 */
public class TutorialModule extends Module {

    /** 단계: 키, 할 일, 아이콘 */
    private record Step(String id, String text, Material icon) {
    }

    private static final List<Step> STEPS = List.of(
            new Step("menu", "&fShift + F &7로 서버 메뉴 열기", Material.COMPASS),
            new Step("warp", "&f서버 메뉴 → 이동 메뉴 &7열기", Material.ENDER_PEARL),
            new Step("shop", "&f상점 &7열기 &8(/상점)", Material.EMERALD),
            new Step("backpack", "&f배낭 &7열기 &8(/배낭)", Material.CHEST),
            new Step("crate", "&f아이템 뽑기 &7메뉴 열기 &8(/상자)", Material.MAGMA_CREAM),
            new Step("pass", "&f프리미엄 패스 &7열기 &8(/pass)", Material.EXPERIENCE_BOTTLE),
            new Step("collection", "&f컬렉션 &7확인하기 &8(/컬렉션)", Material.NETHER_STAR),
            new Step("wild", "&f야생 &7으로 이동하기 &8(이동 메뉴 → 야생)", Material.GRASS_BLOCK));

    /** 튜토리얼 맵에서 할 때: 마지막 단계는 야생 이동 대신 업적 확인 (마치면 스폰으로 보내 줌) */
    private static final List<Step> MAP_STEPS;

    static {
        List<Step> l = new java.util.ArrayList<>(STEPS.subList(0, STEPS.size() - 1));
        l.add(new Step("ach", "&f업적 &7확인하기 &8(/업적)", Material.KNOWLEDGE_BOOK));
        MAP_STEPS = List.copyOf(l);
    }

    private final Map<UUID, BossBar> bars = new HashMap<>();
    /** 튜토리얼 맵에서 나가도 되는 순간 (완료 · 건너뛰기로 내보낼 때) */
    private final java.util.Set<UUID> leaving = new java.util.HashSet<>();

    /** 지금 쓰는 단계 목록 (튜토리얼 맵이 있으면 맵용) */
    private List<Step> steps() {
        return mapSpawn() != null ? MAP_STEPS : STEPS;
    }

    /* ======================= 튜토리얼 맵 ======================= */

    /** 튜토리얼 맵 시작 위치 (맵을 쓰지 않거나 없으면 null) */
    public org.bukkit.Location mapSpawn() {
        if (!plugin.getConfig().getBoolean("tutorial.use-map", true)) return null;
        org.bukkit.Location l = global().warps.get("tutorial");
        if (l != null && l.getWorld() != null) return l;
        return spawnOf("tutorial");
    }

    public boolean inMap(Player p) {
        org.bukkit.Location m = mapSpawn();
        return m != null && p.getWorld().equals(m.getWorld());
    }

    /** 처음 접속: 튜토리얼 맵으로 보냄. 맵이 없으면 false (스폰에서 진행) */
    public boolean sendToMap(Player p) {
        if (!plugin.getConfig().getBoolean("tutorial.enabled", true)) return false;
        org.bukkit.Location m = mapSpawn();
        if (m == null) return false;
        PlayerData d = data(p);
        if (d.tutorialFinished) return false;
        d.tutorialStarted = true;
        plugin.store().save(d);
        p.teleport(m);
        updateBar(p);
        later(120, () -> {
            if (!p.isOnline() || !inMap(p)) return;
            Msg.title(p, "<gradient:#A1FF52:#45FFF6><b>튜토리얼</b></gradient>", "&f화면 위 안내를 따라 해 보세요", 10, 60, 15);
            Msg.send(p, " ");
            Msg.send(p, "<gradient:#A1FF52:#45FFF6><b>[ 튜토리얼 ]</b></gradient> &f튜토리얼 맵에 오신 것을 환영합니다!");
            Msg.send(p, "&7화면 위 안내를 하나씩 따라 하면 &e보상&7을 받고 &f스폰&7으로 이동합니다. &8(/튜토리얼 로 목록 보기)");
            Msg.send(p, " ");
            Msg.sound(p, "block.note_block.chime", 0.8f, 1.4f);
        });
        return true;
    }

    /** 튜토리얼 맵에서 스폰으로 내보냄 */
    private void leaveMap(Player p) {
        if (!inMap(p)) return;
        org.bukkit.Location dest = global().warps.get("spawn");
        if (dest == null || dest.getWorld() == null) dest = spawnOf("spawn");
        if (dest == null) return;
        leaving.add(p.getUniqueId());
        try {
            p.teleport(dest);
        } finally {
            leaving.remove(p.getUniqueId());
        }
        Msg.sound(p, "entity.enderman.teleport", 0.6f, 1);
    }

    /** 튜토리얼 중에는 맵 밖으로 못 나감 (관리자 제외) */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(org.bukkit.event.player.PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        if (leaving.contains(p.getUniqueId()) || e.getTo() == null || p.hasPermission(ADMIN)) return;
        org.bukkit.Location m = mapSpawn();
        if (m == null || !e.getFrom().getWorld().equals(m.getWorld()) || e.getTo().getWorld().equals(m.getWorld())) return;
        if (!active(data(p))) return;
        e.setCancelled(true);
        Msg.send(p, "<gradient:#A1FF52:#45FFF6><b>[ 튜토리얼 ]</b></gradient> &f튜토리얼을 모두 마치면 스폰으로 이동합니다. &7(/튜토리얼 에서 건너뛸 수도 있어요)");
        Gui.denySound(p);
    }

    /** 튜토리얼 맵 안에서는 다치지 않음 */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(org.bukkit.event.entity.EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !inMap(p)) return;
        e.setCancelled(true);
        if (e.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.VOID) {   // 맵 밖으로 떨어지면 시작 위치로
            org.bukkit.Location m = mapSpawn();
            if (m != null) p.teleport(m);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(org.bukkit.event.entity.FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && inMap(p) && e.getFoodLevel() < p.getFoodLevel()) e.setCancelled(true);
    }

    public TutorialModule(WaveCore plugin) {
        super(plugin);
    }

    @Override
    public void enable() {
        cmd("튜토리얼", null, (s, l, a) -> {
            if (Cmd.isPlayer(s)) open((Player) s);
        }, "tutorial");
        for (Player p : Bukkit.getOnlinePlayers()) updateBar(p);
    }

    @Override
    public void disable() {
        for (Map.Entry<UUID, BossBar> en : bars.entrySet()) {
            Player p = Bukkit.getPlayer(en.getKey());
            if (p != null) p.hideBossBar(en.getValue());
        }
        bars.clear();
    }

    private boolean active(PlayerData d) {
        return d.tutorialStarted && !d.tutorialFinished;
    }

    private int done(PlayerData d) {
        int n = 0;
        for (Step s : steps()) if (d.tutorialSteps.contains(s.id())) n++;
        return n;
    }

    private Step next(PlayerData d) {
        for (Step s : steps()) if (!d.tutorialSteps.contains(s.id())) return s;
        return null;
    }

    /** 다른 기능에서 호출: 이 단계를 했다 */
    public void step(Player p, String id) {
        PlayerData d = data(p);
        if (!active(d) || !d.tutorialSteps.add(id)) return;
        plugin.store().save(d);
        Step st = steps().stream().filter(s -> s.id().equals(id)).findFirst().orElse(null);
        Msg.sound(p, "entity.experience_orb.pickup", 0.7f, 1.4f);
        Msg.actionBar(p, "&a✔ 튜토리얼 &f" + (st == null ? id : st.text()) + " &a완료! &7(" + done(d) + "/" + steps().size() + ")");
        if (done(d) >= steps().size()) finish(p, d);
        updateBar(p);
    }

    private void finish(Player p, PlayerData d) {
        d.tutorialFinished = true;
        long money = plugin.getConfig().getLong("tutorial.reward-cash", 30_000);
        d.money += money;
        if (plugin.getConfig().getBoolean("tutorial.reward-newbie-package", true)) It.give(p, Items.newbiePackage());
        int tickets = plugin.getConfig().getInt("tutorial.reward-altar-tickets", 0);
        if (tickets > 0) It.give(p, Items.altarTicket(), tickets);
        plugin.store().save(d);
        Msg.send(p, " ");
        Msg.send(p, "<gradient:#A1FF52:#45FFF6><b>[ 튜토리얼 완료 ]</b></gradient> &f모든 단계를 마쳤습니다!");
        Msg.send(p, "&7보상 : &e" + Msg.num(money) + " 캐시" + (plugin.getConfig().getBoolean("tutorial.reward-newbie-package", true) ? " &7+ &a뉴비 지원 패키지" : "")
                + (tickets > 0 ? " &7+ &e천공의 제단 이동권 " + tickets + "개" : ""));
        Msg.send(p, " ");
        Msg.title(p, "<gradient:#A1FF52:#45FFF6><b>튜토리얼 완료!</b></gradient>", "&7보상이 지급되었습니다", 10, 50, 15);
        Msg.sound(p, "ui.toast.challenge_complete", 1, 1.2f);
        Fx.burst(p.getLocation().add(0, 1.2, 0), Particle.TOTEM_OF_UNDYING, 60, 0.4);
        if (inMap(p)) {
            Msg.send(p, "&73초 뒤 &f스폰&7으로 이동합니다. 즐거운 모험 되세요!");
            later(60, () -> {
                if (p.isOnline()) leaveMap(p);
            });
        }
    }

    /* ======================= 보스바 안내 ======================= */

    private void updateBar(Player p) {
        PlayerData d = data(p);
        BossBar bar = bars.get(p.getUniqueId());
        if (!active(d)) {
            if (bar != null) {
                p.hideBossBar(bar);
                bars.remove(p.getUniqueId());
            }
            return;
        }
        Step nx = next(d);
        String text = "<gradient:#A1FF52:#45FFF6><b>튜토리얼</b></gradient> &7(" + done(d) + "/" + steps().size() + ") &8· "
                + (nx == null ? "&a완료!" : nx.text()) + " &8· &7/튜토리얼";
        float prog = (float) done(d) / steps().size();
        if (bar == null) {
            bar = BossBar.bossBar(Msg.c(text), prog, BossBar.Color.GREEN, BossBar.Overlay.NOTCHED_10);
            bars.put(p.getUniqueId(), bar);
            p.showBossBar(bar);
        } else {
            bar.name(Msg.c(text));
            bar.progress(prog);
        }
    }

    /* ======================= GUI ======================= */

    public void open(Player p) {
        PlayerData d = data(p);
        if (!d.tutorialStarted && !d.tutorialFinished) {
            d.tutorialStarted = true;   // 기존 플레이어도 직접 열면 시작
            plugin.store().save(d);
            updateBar(p);
        }
        Gui g = new Gui(4, "&8&l튜토리얼");
        long money = plugin.getConfig().getLong("tutorial.reward-cash", 30_000);
        g.set(4, It.of(Material.WRITABLE_BOOK).name("<gradient:#A1FF52:#45FFF6><b>튜토리얼</b></gradient>")
                .lore("", "&7서버의 주요 기능을 하나씩 해 보세요.", "&7순서는 상관없습니다.", "",
                        "&f진행 : &e" + done(d) + " &7/ " + steps().size(),
                        "&f완료 보상 : &e" + Msg.num(money) + " 캐시" + (plugin.getConfig().getBoolean("tutorial.reward-newbie-package", true) ? " &7+ &a뉴비 지원 패키지" : ""),
                        "", d.tutorialFinished ? "&a&l튜토리얼을 완료했습니다!" : "").build());
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 22};
        for (int i = 0; i < steps().size(); i++) {
            Step s = steps().get(i);
            boolean ok = d.tutorialSteps.contains(s.id()) || d.tutorialFinished;
            It b = It.of(ok ? Material.LIME_DYE : s.icon()).name((ok ? "&a✔ " : "&e" + (i + 1) + ". ") + s.text())
                    .lore("", ok ? "&a완료" : "&7아직 하지 않았습니다.");
            if (ok) b.ench(Enchantment.UNBREAKING, 1);
            g.set(slots[i], b.hide().build());
        }
        if (!d.tutorialFinished) {
            g.set(g.last() + 8, It.of(Material.BARRIER).name("&c튜토리얼 건너뛰기").lore("", "&7보상 없이 튜토리얼을 끝냅니다.", "&cShift + 우클릭 &7으로 실행").build(), e -> {
                if (e.getClick() != ClickType.SHIFT_RIGHT) {
                    Msg.send(p, "&7건너뛰려면 &cShift + 우클릭 &7하세요. &8(보상 없음)");
                    return;
                }
                d.tutorialFinished = true;
                plugin.store().save(d);
                updateBar(p);
                p.closeInventory();
                Msg.send(p, "&7튜토리얼을 건너뛰었습니다. &8(/튜토리얼 로 다시 볼 수 있습니다)");
                leaveMap(p);
            });
            g.quiet(g.last() + 8);
        }
        g.nav(p, () -> plugin.module(MenuModule.class).menu(p)).fill().open(p);
    }

    /* ======================= 이벤트 ======================= */

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        PlayerData d = data(p);
        if (!p.hasPlayedBefore() && !d.tutorialStarted && plugin.getConfig().getBoolean("tutorial.enabled", true) && mapSpawn() == null) {
            d.tutorialStarted = true;
            plugin.store().save(d);
            later(100, () -> {
                if (!p.isOnline()) return;
                Msg.send(p, "<gradient:#A1FF52:#45FFF6><b>[ 튜토리얼 ]</b></gradient> &f화면 위 안내를 따라 해 보세요! 모두 마치면 보상을 드립니다. &7(/튜토리얼)");
                Msg.sound(p, "block.note_block.chime", 0.8f, 1.4f);
            });
        }
        later(20, () -> {
            if (p.isOnline()) updateBar(p);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        BossBar bar = bars.remove(e.getPlayer().getUniqueId());
        if (bar != null) e.getPlayer().hideBossBar(bar);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String label = e.getMessage().substring(1).split(" ", 2)[0].toLowerCase();
        if (label.contains(":")) label = label.substring(label.indexOf(':') + 1);
        if (label.equals("pass") || label.equals("패스") || label.equals("프리미엄패스")) step(e.getPlayer(), "pass");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent e) {
        if (e.getPlayer().getWorld().getName().equalsIgnoreCase(world("wild"))) step(e.getPlayer(), "wild");
    }
}
