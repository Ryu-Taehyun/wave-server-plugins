package kr.wave.core.module;

import kr.wave.core.WaveCore;
import kr.wave.core.util.Cmd;
import kr.wave.core.util.It;
import kr.wave.core.util.Msg;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attributable;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 1v1.sk : 1대1 대전 */
public class DuelModule extends Module {

    /** 신청자 -> 대상 */
    private final Map<UUID, UUID> requests = new HashMap<>();
    private final Map<UUID, String> modes = new HashMap<>();
    /** 대전 중: 플레이어 -> 상대 */
    private final Map<UUID, UUID> opponents = new HashMap<>();

    public DuelModule(WaveCore plugin) {
        super(plugin);
    }

    @Override
    public void enable() {
        cmd("duel", null, this::duelCmd, "대전");
        cmd("accept", null, this::acceptCmd);
        cmd("deny", null, this::denyCmd);
    }

    public boolean inDuel(Player p) {
        return opponents.containsKey(p.getUniqueId());
    }

    private void duelCmd(CommandSender s, String l, String[] a) {
        if (!Cmd.isPlayer(s)) return;
        Player p = (Player) s;
        Player t = Cmd.online(Cmd.arg(a, 0));
        String mode = Cmd.arg(a, 1);
        if (mode == null || !(mode.equals("재미") || mode.equals("노멀"))) {
            Msg.send(p, "&c모드를 선택하세요! /duel [상대] [재미/노멀]");
            return;
        }
        if (t == null) {
            Msg.send(p, "&c접속 중인 플레이어가 아닙니다.");
            return;
        }
        if (t == p) {
            Msg.send(p, "&c자기 자신에게는 대전을 신청할 수 없습니다.");
            return;
        }
        if (inDuel(p)) {
            Msg.send(p, "&c당신은 이미 대전 중입니다!");
            return;
        }
        if (inDuel(t)) {
            Msg.send(p, "&c" + t.getName() + "님은 이미 다른 사람과 대전 중입니다.");
            return;
        }
        if (!canDuel(t)) {
            Msg.send(p, "&c" + t.getName() + "님은 지금 대전을 받을 수 없습니다.");
            return;
        }
        if (requests.containsKey(p.getUniqueId())) {
            Msg.send(p, "&c이미 대전 신청을 보냈습니다!");
            return;
        }
        requests.put(p.getUniqueId(), t.getUniqueId());
        modes.put(p.getUniqueId(), mode);
        Msg.send(p, "&a" + t.getName() + "님에게 " + mode + " 모드로 대전 신청을 보냈습니다.");
        Msg.send(t, " ");
        Msg.send(t, " ");
        Msg.send(t, "                        <i><b><gradient:#FF3636:#FF5353><i><b>1V1 DUEL</b></i></gradient></b></i>");
        Msg.send(t, " ");
        Msg.send(t, "      &7" + p.getName() + " 님이 대전 신청을 보냈습니다! &c(모드 : " + mode + ")");
        Msg.send(t, "               &7해당 요청은 30초 후 만료됩니다.");
        Msg.send(t, " ");
        t.sendMessage(Msg.c("              &a[대전 수락]").clickEvent(ClickEvent.runCommand("/accept " + p.getName()))
                .hoverEvent(HoverEvent.showText(Msg.c("&a클릭하여 대전 수락하기")))
                .append(Msg.c("        &c[대전 거절]").clickEvent(ClickEvent.runCommand("/deny " + p.getName()))
                        .hoverEvent(HoverEvent.showText(Msg.c("&c클릭하여 대전 거절하기")))));
        Msg.send(t, " ");
        Msg.send(t, " ");
        UUID pid = p.getUniqueId();
        UUID tid = t.getUniqueId();
        later(600, () -> {
            if (tid.equals(requests.get(pid))) {
                requests.remove(pid);
                modes.remove(pid);
                Player tp = Bukkit.getPlayer(tid);
                Player pp = Bukkit.getPlayer(pid);
                if (tp != null) Msg.send(tp, "&c" + (pp != null ? pp.getName() : "상대") + "님과의 대전 신청이 만료되었습니다.");
                if (pp != null) Msg.send(pp, "&c대전 신청이 만료되었습니다.");
            }
        });
    }

    private void acceptCmd(CommandSender s, String l, String[] a) {
        if (!Cmd.isPlayer(s)) return;
        Player p = (Player) s;
        Player req = Cmd.online(Cmd.arg(a, 0));
        if (req == null || !p.getUniqueId().equals(requests.get(req.getUniqueId()))) {
            Msg.send(p, "&c대전 신청이 없습니다.");
            return;
        }
        if (inDuel(p) || inDuel(req)) {
            Msg.send(p, "&c이미 대전 중인 플레이어가 있습니다.");
            return;
        }
        if (!canDuel(p) || !canDuel(req)) {
            Msg.send(p, "&c지금은 대전을 시작할 수 없는 상태입니다. &7(전투 중 또는 천공의 제단)");
            Msg.send(req, "&c" + p.getName() + "님과의 대전을 시작할 수 없습니다. &7(전투 중 또는 천공의 제단)");
            requests.remove(req.getUniqueId());
            modes.remove(req.getUniqueId());
            return;
        }
        requests.remove(req.getUniqueId());
        opponents.put(p.getUniqueId(), req.getUniqueId());
        opponents.put(req.getUniqueId(), p.getUniqueId());
        for (int i = 3; i >= 1; i--) {
            int sec = i;
            later((3 - i) * 20L, () -> {
                if (!p.isOnline()) return;
                Msg.title(p, "&a" + sec + "초 뒤 전장으로 이동합니다...", "&7준비하세요!", 10, 20, 10);
                Msg.soundAt(p.getLocation(), "entity.arrow.hit_player", 1, 2);
            });
        }
        later(60, () -> {
            if (!inDuel(p) || !inDuel(req)) return;
            Msg.send(p, "&a대전 시작!");
            Msg.send(req, "&a대전 시작!");
            World w = Bukkit.getWorld(world("duel"));
            if (w == null) {
                plugin.getLogger().warning("대전 월드 '" + world("duel") + "' 를 찾을 수 없습니다.");
                return;
            }
            p.teleport(pos(w, "duel.pos1", -44.55, -50.0, 32.54));
            req.teleport(pos(w, "duel.pos2", -44.55, -50.0, 100.0));
            later(1, () -> Msg.soundAt(p.getLocation(), "entity.ender_dragon.growl", 1, 1));
        });
    }

    /** 전투 태그 중이거나 천공의 제단에 있으면 대전 불가 */
    private boolean canDuel(Player p) {
        CombatTagModule ct = plugin.module(CombatTagModule.class);
        if (ct != null && ct.tagged(p)) return false;
        BossModule b = plugin.module(BossModule.class);
        return b == null || !b.restricted(p);
    }

    private Location pos(World w, String path, double x, double y, double z) {
        List<Double> c = plugin.getConfig().getDoubleList(path);
        if (c.size() >= 3) return new Location(w, c.get(0), c.get(1), c.get(2));
        return new Location(w, x, y, z);
    }

    private void denyCmd(CommandSender s, String l, String[] a) {
        if (!Cmd.isPlayer(s)) return;
        Player p = (Player) s;
        Player req = Cmd.online(Cmd.arg(a, 0));
        if (req == null || !p.getUniqueId().equals(requests.get(req.getUniqueId()))) {
            Msg.send(p, "&c거절할 대전 신청이 없습니다.");
            return;
        }
        requests.remove(req.getUniqueId());
        modes.remove(req.getUniqueId());
        Msg.send(req, "&c" + p.getName() + "님이 대전 신청을 거절했습니다.");
        Msg.send(p, "&c대전 신청을 거절했습니다.");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!inDuel(e.getPlayer())) return;
        e.setCancelled(true);
        Msg.send(e.getPlayer(), "&c대전 중에는 명령어를 사용할 수 없습니다!");
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        end(e.getEntity());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        requests.remove(id);
        modes.remove(id);
        end(e.getPlayer());
    }

    /** loser 가 졌을 때 대전 종료 */
    private void end(Player loser) {
        UUID oid = opponents.remove(loser.getUniqueId());
        if (oid == null) return;
        opponents.remove(oid);
        modes.remove(oid);
        modes.remove(loser.getUniqueId());
        Player winner = Bukkit.getPlayer(oid);
        if (winner == null) return;
        Msg.send(winner, " ");
        Msg.send(winner, " ");
        Msg.send(winner, "                         <i><b><gradient:#FF3636:#FF5353><i><b>1V1 DUEL</b></i></gradient></b></i>");
        Msg.send(winner, " ");
        Msg.send(winner, "         &7대전이 종료되었습니다! " + winner.getName() + " 님이 승리했습니다.");
        Msg.send(winner, " ");
        Msg.send(winner, "                 &77초 뒤 스폰으로 이동됩니다...");
        Msg.send(winner, " ");
        Msg.send(winner, " ");
        double max = winner instanceof Attributable ? WeaponModule.maxHealth(winner) : 20;
        winner.setHealth(Math.min(max, 20));
        later(120, () -> {
            if (winner.isOnline()) tpWorld(winner, "spawn");
        });
    }

    /** 대전 월드에서 겉날개 착용 금지 */
    @Override
    public void secondTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!inWorld(p, "duel")) continue;
            ItemStack chest = p.getInventory().getChestplate();
            if (chest != null && chest.getType() == Material.ELYTRA) {
                p.getInventory().setChestplate(null);
                It.give(p, chest);   // 바닥에 떨어뜨리지 않고 인벤토리로 (가득 차면 발밑)
                Msg.send(p, "&c대전 중에는 겉날개를 사용할 수 없습니다!");
            }
        }
    }
}
