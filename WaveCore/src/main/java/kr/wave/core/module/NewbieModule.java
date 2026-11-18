package kr.wave.core.module;

import kr.wave.core.Items;
import kr.wave.core.WaveCore;
import kr.wave.core.data.PlayerData;
import kr.wave.core.util.Cmd;
import kr.wave.core.util.Fx;
import kr.wave.core.util.It;
import kr.wave.core.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** aboulute.sk : 양자 물질 드롭, 뉴비 헬퍼 패스/보호 월드, 크라운 표식, /testkill */
public class NewbieModule extends Module {

    private static final long PASS_MS = 3L * 60 * 60 * 1000;
    private static final Set<EntityType> QUANTUM_MOBS = Set.of(EntityType.BLAZE, EntityType.GHAST, EntityType.WITHER_SKELETON);

    public NewbieModule(WaveCore plugin) {
        super(plugin);
    }

    @Override
    public void enable() {
        cmd("뉴비보호구역", null, (s, l, a) -> {
            if (!Cmd.isPlayer(s)) return;
            Player p = (Player) s;
            if (data(p).newbiePassActive()) tpWorld(p, "newbie");
            else Msg.send(p, "&c뉴비 헬퍼 패스가 활성화되지 않았습니다.");
        }, "newbiezone");
        cmd("뉴비패스", ADMIN, (s, l, a) -> {
            if (Cmd.isPlayer(s)) It.give((Player) s, Items.newbiePass());
        });
        // 원본은 권한이 없었으나 아이템 지급/전체 공지 명령어라 관리자 전용으로 변경
        cmd("givecrown", ADMIN, (s, l, a) -> {
            if (Cmd.isPlayer(s)) It.give((Player) s, Items.crownMark());
        });
        cmd("testkill", ADMIN, (s, l, a) -> {
            if (Cmd.isPlayer(s)) plugin.module(CoreModule.class).testKillBroadcast((Player) s, "&7Test", true);
        });
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent e) {
        if (!QUANTUM_MOBS.contains(e.getEntityType()) || e.getEntity().getKiller() == null) return;
        if (ThreadLocalRandom.current().nextDouble() < 0.275) {
            e.getEntity().getWorld().dropItemNaturally(e.getEntity().getLocation(), Items.quantumMatter());
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (!inWorld(p, "newbie")) return;
        Msg.title(p, "&e&lNEWBIE PROTECTION", "&7이곳은 뉴비 보호 구역입니다. 더욱 손쉽게 아이템을 맞출수 있습니다.", 10, 20, 10);
        Msg.soundAt(p.getLocation(), "entity.player.levelup", 1, 2);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        String n = Msg.name(p.getInventory().getItemInMainHand());
        if (n.equals(Items.N_NEWBIE_PASS)) {
            e.setCancelled(true);
            It.takeOneFromHand(p);
            PlayerData d = data(p);
            d.newbiePassUntil = System.currentTimeMillis() + PASS_MS;
            plugin.store().save(d);
            Msg.send(p, " ");
            Msg.send(p, " ");
            Msg.send(p, "§a§l[💡 NEWBIE] 뉴비 헬퍼 패스가 활성화 되었습니다!");
            Msg.send(p, "&e/뉴비보호구역 &7을 입력하여 제한된 공간에 입장할 수 있습니다.");
            Msg.send(p, " ");
            Msg.send(p, " ");
            Msg.soundAt(p.getLocation(), "entity.player.levelup", 1, 1);
            // 뉴비 패스 활성화: 초록 축복
            Fx.burst(p.getLocation().add(0, 1, 0), Particle.TOTEM_OF_UNDYING, 60, 0.4);
            Fx.helix(p, Fx.rgb(0x55FF55), Fx.rgb(0xFFFF55), 0.8, 2.4, 30);
            Fx.shockwave(p.getLocation().add(0, 0.1, 0), Fx.rgb(0x55FF55), 3.0, 10);
        } else if (n.equals(Items.N_CROWN)) {
            e.setCancelled(true);
            It.takeOneFromHand(p);
            PlayerData d = data(p);
            d.crownOwned = true;
            d.crownKill = true;
            plugin.store().save(d);
            Msg.send(p, "&6[👑] &f크라운 처치 알림 표식이 적용되었습니다. &7(프로필 설정에서 켜고 끌 수 있습니다)");
            Msg.soundAt(p.getLocation(), "entity.player.levelup", 1, 2);
            // 머리 위 황금 왕관
            Location top = p.getLocation().add(0, 2.3, 0);
            Fx.ring(top, 0.45, 18, Fx.rgb(0xFFB732), 1.2f);
            for (int i = 0; i < 5; i++) {
                double a = Math.PI * 2 * i / 5;
                Fx.line(top.clone().add(Math.cos(a) * 0.45, 0, Math.sin(a) * 0.45), top.clone().add(Math.cos(a) * 0.45, 0.35, Math.sin(a) * 0.45), Fx.rgb(0xFFF176), 1.0f, 0.08);
            }
            Fx.burst(top, Particle.END_ROD, 20, 0.1);
        }
    }

    @Override
    public void secondTick() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = data(p);
            if (d.newbiePassUntil > 0 && d.newbiePassUntil <= now) {
                d.newbiePassUntil = 0;
                plugin.store().save(d);
                Msg.send(p, "&c뉴비 헬퍼 패스가 종료되었습니다.");
                Msg.sound(p, "block.note_block.bass", 1, 1);
            }
            if (inWorld(p, "newbie") && !d.newbiePassActive()) tpWorld(p, "spawn");
        }
    }
}
