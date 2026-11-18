package kr.wave.core.module;

import kr.wave.core.Items;
import kr.wave.core.WaveCore;
import kr.wave.core.data.PlayerData;
import kr.wave.core.util.Cmd;
import kr.wave.core.util.Fx;
import kr.wave.core.util.It;
import kr.wave.core.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;

/** rank.sk : 랭크 (Ace/Supreme/Exceed/Peerless), test.sk : 킬 특수 효과 */
public class RankModule extends Module {

    public RankModule(WaveCore plugin) {
        super(plugin);
    }

    @Override
    public void enable() {
        cmd("랭크업", ADMIN, (s, l, a) -> {
            Player t = Cmd.online(Cmd.arg(a, 0));
            if (t == null || a.length < 2) {
                Msg.send(s, "&c/랭크업 <플레이어> <Ace|supreme|exceed|peerless>");
                return;
            }
            rankUp(t, a[1].toLowerCase());
        });
        cmd("랭크초기화", ADMIN, this::resetCmd);
        cmd("test2", ADMIN, (s, l, a) -> {
            if (s instanceof Player p) It.give(p, Items.supremeRoulette());
            console("minecraft:team modify default prefix '§7[유저] §7- '");
            console("minecraft:team modify ace prefix '§e[Ace] §7- §7'");
            console("minecraft:team modify supreme prefix '§a[💎] §bSupreme §7- '");
            console("minecraft:team modify exceed prefix '§a[💎] §6Exceed §7- '");
            console("minecraft:team modify peerles prefix '§4[💎] §cPeerless §7- '");
            console("minecraft:scoreboard objectives add showhealth health '§c♥'");
        });
    }

    private void rankUp(Player t, String rank) {
        String label;
        switch (rank) {
            case "ace" -> label = "&eAce";
            case "supreme" -> label = "&bSupreme";
            case "exceed" -> label = "&6Exceed";
            case "peerless" -> label = "&cPeerless";
            default -> {
                return;
            }
        }
        console("lp user " + t.getName() + " parent set " + rank);
        PlayerData d = data(t);
        d.ranks.add(rank);
        plugin.store().save(d);
        Msg.broadcast("");
        Msg.broadcast("<gradient:#67FE00:#FF8A8A>[Rank U</gradient><gradient:#FF8A8A:#FF8A8A>P]</gradient> &7플레이어 &e" + t.getName() + " &7님이 " + label + " &7랭크로 승급했습니다!");
        Msg.broadcast("");
        Msg.sound(t, "entity.player.levelup", 1, 1);
    }

    private void resetCmd(CommandSender s, String l, String[] a) {
        OfflinePlayer t = Cmd.offline(Cmd.arg(a, 0));
        if (t == null) {
            Msg.send(s, "&c/랭크초기화 <플레이어>");
            return;
        }
        PlayerData d = plugin.store().get(t);
        d.ranks.clear();
        plugin.store().saveAuto(d);
        console("minecraft:team leave " + a[0]);
        console("lp user " + a[0] + " parent set default");
        Msg.send(s, "&a[System] &7플레이어 &a" + a[0] + "&7 님의 랭크가 &c초기화 &7되었습니다.");
    }

    /** 누적 유료 캐시에 따라 자동 승급 */
    @Override
    public void secondTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = data(p);
            long total = d.exMoneyTotal;
            MailModule mail = plugin.module(MailModule.class);
            if (total >= 30000 && !d.ranks.contains("ace")) {
                rankUp(p, "ace");
                console("minecraft:team join ace " + p.getName());
                d.titles.add("ace");
                mail.send(p.getUniqueId(), new ItemStack(Material.NETHERITE_INGOT, 10));
                mail.send(p.getUniqueId(), new ItemStack(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 10));
            }
            if (total >= 70000 && !d.ranks.contains("supreme")) {
                rankUp(p, "supreme");
                console("minecraft:team join supreme " + p.getName());
                d.titles.add("supreme");
                mail.send(p.getUniqueId(), Items.supremeRoulette());
            }
            if (total >= 130000 && !d.ranks.contains("exceed")) {
                rankUp(p, "exceed");
                console("minecraft:team join exceed " + p.getName());
                d.titles.add("exceed");
            }
            if (total >= 200000 && !d.ranks.contains("peerless")) {
                rankUp(p, "peerless");
                console("minecraft:team join peerles " + p.getName());
                d.titles.add("peerless");
                mail.send(p.getUniqueId(), Items.nickPaper("red", "Peerless 랭크 달성 보상입니다."));
            }
        }
    }

    /* ======================= 킬 특수 효과 ======================= */

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer == victim) return;
        Location loc = victim.getLocation();
        // 크라운 처치 알림 표식이 켜져 있으면 크라운 연출(CoreModule)만, 기존 킬 특수 효과는 쓰지 않음
        if (data(killer).crownKill) return;
        switch (data(killer).killEffect) {
            case 1 -> Fx.lightning(loc);
            case 2 -> {
                Location fl = loc.clone().subtract(0, 0.1, 0);
                Firework fw = loc.getWorld().spawn(fl, Firework.class, f -> {
                    FireworkMeta meta = f.getFireworkMeta();
                    meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.STAR).flicker(true).trail(true)
                            .withColor(Color.RED, Color.WHITE).withFade(Color.BLACK).build());
                    meta.setPower(0);
                    f.setFireworkMeta(meta);
                });
                later(1, fw::detonate);
            }
            case 3 -> {
                Fx.lightning(loc);
                Fx.p(loc, Particle.EXPLOSION_EMITTER, 20, 1, 1, 1, 0.1);
                Msg.sound(killer, "entity.wither.spawn", 2, 1);
                Msg.sound(killer, "entity.dragon_fireball.explode", 2, 1.5f);
            }
            default -> {
            }
        }
    }
}
