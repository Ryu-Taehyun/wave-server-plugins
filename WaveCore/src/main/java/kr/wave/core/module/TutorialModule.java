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

    private final Map<UUID, BossBar> bars = new HashMap<>();

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
        for (Step s : STEPS) if (d.tutorialSteps.contains(s.id())) n++;
        return n;
    }

    private Step next(PlayerData d) {
        for (Step s : STEPS) if (!d.tutorialSteps.contains(s.id())) return s;
        return null;
    }

    /** 다른 기능에서 호출: 이 단계를 했다 */
    public void step(Player p, String id) {
        PlayerData d = data(p);
        if (!active(d) || !d.tutorialSteps.add(id)) return;
        plugin.store().save(d);
        Step st = STEPS.stream().filter(s -> s.id().equals(id)).findFirst().orElse(null);
        Msg.sound(p, "entity.experience_orb.pickup", 0.7f, 1.4f);
        Msg.actionBar(p, "&a✔ 튜토리얼 &f" + (st == null ? id : st.text()) + " &a완료! &7(" + done(d) + "/" + STEPS.size() + ")");
        if (done(d) >= STEPS.size()) finish(p, d);
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
        String text = "<gradient:#A1FF52:#45FFF6><b>튜토리얼</b></gradient> &7(" + done(d) + "/" + STEPS.size() + ") &8· "
                + (nx == null ? "&a완료!" : nx.text()) + " &8· &7/튜토리얼";
        float prog = (float) done(d) / STEPS.size();
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
                        "&f진행 : &e" + done(d) + " &7/ " + STEPS.size(),
                        "&f완료 보상 : &e" + Msg.num(money) + " 캐시" + (plugin.getConfig().getBoolean("tutorial.reward-newbie-package", true) ? " &7+ &a뉴비 지원 패키지" : ""),
                        "", d.tutorialFinished ? "&a&l튜토리얼을 완료했습니다!" : "").build());
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 22};
        for (int i = 0; i < STEPS.size(); i++) {
            Step s = STEPS.get(i);
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
        if (!p.hasPlayedBefore() && !d.tutorialStarted && plugin.getConfig().getBoolean("tutorial.enabled", true)) {
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
