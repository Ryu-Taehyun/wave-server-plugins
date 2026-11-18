package kr.wave.core.module;

import kr.wave.core.WaveCore;
import kr.wave.core.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** CombatLogX.sk : 전투 상태 (12초), 전투 중 접속 종료 시 사망, 명령어/보호구역 진입 금지 */
public class CombatTagModule extends Module {

    private static final String PREFIX = "&c&l[!]";
    private final Map<UUID, Integer> timers = new HashMap<>();

    public CombatTagModule(WaveCore plugin) {
        super(plugin);
    }

    private int combatTime() {
        return plugin.getConfig().getInt("combat-time", 12);
    }

    public boolean tagged(Player p) {
        return timers.containsKey(p.getUniqueId());
    }

    public void tag(Player p) {
        boolean was = tagged(p);
        timers.put(p.getUniqueId(), combatTime());
        if (!was) Msg.send(p, PREFIX + " &c전투 상태가 되었습니다. 전투 중 게임을 나가면 즉사합니다.");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = CoreModule.playerAttacker(e);
        if (attacker == null || attacker == victim) return;
        tag(victim);
        tag(attacker);
    }

    @Override
    public void secondTick() {
        Iterator<Map.Entry<UUID, Integer>> it = timers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> en = it.next();
            Player p = Bukkit.getPlayer(en.getKey());
            if (p == null) {
                it.remove();
                continue;
            }
            int left = en.getValue() - 1;
            Msg.actionBar(p, "&c&l[!] 전투 중... &7(" + left + "초)");
            if (left <= 0) {
                it.remove();
                Msg.send(p, PREFIX + " &e전투 상태가 해제되었습니다.");
            } else {
                en.setValue(left);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent e) {
        if (timers.remove(e.getPlayer().getUniqueId()) != null) e.getPlayer().setHealth(0);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        timers.remove(e.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!tagged(e.getPlayer())) return;
        e.setCancelled(true);
        Msg.send(e.getPlayer(), "&c&l[!] &c전투 중에는 명령어를 사용할 수 없습니다.");
    }

    /** WorldGuard 'protect' 구역 진입 금지 */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!tagged(e.getPlayer())) return;
        if (e.getFrom().getBlockX() == e.getTo().getBlockX() && e.getFrom().getBlockY() == e.getTo().getBlockY()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;
        String region = plugin.getConfig().getString("protect-region", "protect");
        if (!plugin.hooks().inRegion(e.getTo(), region) || plugin.hooks().inRegion(e.getFrom(), region)) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        Msg.send(p, "&c전투 중에는 보호구역에 들어갈 수 없습니다!");
        Vector back = e.getFrom().toVector().subtract(e.getTo().toVector());
        if (back.lengthSquared() > 0) p.setVelocity(back.normalize().multiply(1).setY(0.2));
    }
}
