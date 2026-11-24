package kr.wave.core.module;

import kr.wave.core.WaveCore;
import kr.wave.core.data.PlayerData;
import kr.wave.core.util.Cmd;
import kr.wave.core.util.Gui;
import kr.wave.core.util.It;
import kr.wave.core.util.Msg;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.LocalDate;
import java.util.List;

/**
 * 출석 보상
 * 하루 첫 접속 때 자동 출석. 어제도 출석했으면 연속 +1, 아니면 1일부터 다시.
 * 7일 주기로 보상(config attendance.rewards)이 커지고, 7일 연속을 처음 채우면 칭호 "파수꾼"(7days)
 * /출석 : 이번 주기 달력
 */
public class AttendanceModule extends Module {

    private static final String TAG = "<b><gradient:#FFD84D:#FF9F1C>[ 출석 ]</gradient></b> ";
    private static final long[] DEFAULT = {1000, 2000, 3000, 4000, 5000, 6000, 10000};

    public AttendanceModule(WaveCore plugin) {
        super(plugin);
    }

    @Override
    public void enable() {
        cmd("출석", null, (s, l, a) -> {
            if (Cmd.isPlayer(s)) open((Player) s);
        }, "출석체크", "attendance");
    }

    /** 연속 일차(1~7 주기)의 보상 */
    public long reward(int streak) {
        List<Long> list = plugin.getConfig().getLongList("attendance.rewards");
        int i = Math.floorMod(streak - 1, 7);
        return list.size() == 7 ? list.get(i) : DEFAULT[i];
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!plugin.getConfig().getBoolean("attendance.enabled", true)) return;
        later(40, () -> {
            if (p.isOnline()) check(p, LocalDate.now());
        });
    }

    /** 오늘 출석 처리. 이미 했으면 false */
    public boolean check(Player p, LocalDate today) {
        PlayerData d = data(p);
        String t = today.toString();
        if (t.equals(d.attendDay)) return false;
        d.attendStreak = today.minusDays(1).toString().equals(d.attendDay) ? d.attendStreak + 1 : 1;
        d.attendTotal++;
        d.attendDay = t;
        int cycle = Math.floorMod(d.attendStreak - 1, 7) + 1;
        long money = reward(d.attendStreak);
        d.money += money;
        d.moneyTotal += money;
        boolean title = d.attendStreak >= 7 && d.titles.add("7days");
        plugin.store().save(d);

        Msg.send(p, " ");
        Msg.send(p, TAG + "&f오늘 출석 완료! &7(" + d.attendStreak + "일 연속 · 누적 " + d.attendTotal + "일)");
        Msg.send(p, "   &7" + bar(cycle) + " &e" + cycle + "/7일차");
        Msg.send(p, "   &7보상 : &e" + Msg.num(money) + "원" + (cycle == 7 ? " &6&l+ 7일 보너스!" : " &8(내일 " + Msg.num(reward(d.attendStreak + 1)) + "원)"));
        if (title) Msg.send(p, "   &6칭호 획득 : &e&l[💦] 파수꾼 &7(/메뉴 → 칭호)");
        Msg.send(p, " ");
        Msg.sound(p, cycle == 7 ? "ui.toast.challenge_complete" : "entity.player.levelup", 0.6f, 1.4f);
        if (cycle == 7) Msg.title(p, "<gradient:#FFD84D:#FF9F1C><b>7일 연속 출석!</b></gradient>", "&f보너스 &e" + Msg.num(money) + "원", 5, 50, 15);
        return true;
    }

    private static String bar(int cycle) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 7; i++) sb.append(i <= cycle ? "&a■" : "&8■");
        return sb.toString();
    }

    public void open(Player p) {
        PlayerData d = data(p);
        boolean today = LocalDate.now().toString().equals(d.attendDay);
        boolean alive = today || LocalDate.now().minusDays(1).toString().equals(d.attendDay);
        int streak = alive ? d.attendStreak : 0;
        int cycle = streak == 0 ? 0 : Math.floorMod(streak - 1, 7) + 1;
        int base = streak - cycle;   // 이번 주기 시작 전까지의 연속 일수
        Gui g = new Gui(4, "&8출석 체크");
        g.set(4, It.of(Material.CLOCK).name("&6&l출석 현황").lore(
                "&7연속 출석 : &f" + streak + "일",
                "&7누적 출석 : &f" + d.attendTotal + "일",
                "&7오늘 : " + (today ? "&a출석 완료" : "&c아직 (다시 접속하면 출석)"),
                "",
                "&8하루라도 빠지면 1일차부터 다시 시작합니다.").build());
        for (int i = 1; i <= 7; i++) {
            boolean done = i <= cycle;
            boolean next = i == cycle + 1;
            long money = reward(base + i);
            Material m = done ? Material.LIME_STAINED_GLASS_PANE : next ? Material.YELLOW_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
            if (i == 7) m = done ? Material.NETHER_STAR : Material.CHEST;
            It it = It.of(m, i).name((done ? "&a&l" : next ? "&e&l" : "&7&l") + i + "일차" + (done ? " ✔" : ""))
                    .lore("&7보상 : &e" + Msg.num(money) + "원");
            if (i == 7) it.lore("&6+ 칭호 &e&l[💦] 파수꾼 &8(처음 1번)");
            it.lore("", done ? "&a받음" : next ? (today ? "&e내일 받을 수 있음" : "&e다음 접속 때 받을 수 있음") : "&8아직");
            if (done && i == 7) it.ench(org.bukkit.enchantments.Enchantment.UNBREAKING, 1).hide();
            g.set(18 + i, it.build());
        }
        g.fill().nav(p, null).open(p);
    }
}
