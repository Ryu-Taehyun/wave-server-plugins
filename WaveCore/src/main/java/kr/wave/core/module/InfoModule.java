package kr.wave.core.module;

import kr.wave.core.WaveCore;
import kr.wave.core.util.Cmd;
import kr.wave.core.util.Msg;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** help.sk : /도움말, /마인리스트, /디스코드, 스폰 경험치 구역 */
public class InfoModule extends Module {

    private final Map<UUID, Long> zoneSince = new HashMap<>();

    public InfoModule(WaveCore plugin) {
        super(plugin);
    }

    @Override
    public void enable() {
        cmd("도움말", null, (s, l, a) -> {
            Msg.send(s, "%nl%<gradient:#00FF7C:#FFDCE8><bold:true>서버 명령어 목록</gradient></reset>%nl%%nl%&a/back &7- &f죽은 마지막 장소로 이동합니다%nl%&a/backpack &7- &f개인 아이템 창고를 오픈합니다%nl%&a/report [플레이어] [사유] &7- &f플레이어를 신고합니다%nl%&a/cash , /돈 &7- &f돈이 얼마나 있는지 확인합니다 %nl%&a/귓 , /귓속말 &7- &f플레이어에게 개인 메시지를 보냅니다%nl%");
            s.sendMessage(Msg.c("&6>> 다음 페이지로 이동하기%nl%").clickEvent(ClickEvent.runCommand("/도움말2"))
                    .hoverEvent(HoverEvent.showText(Msg.c("&a다음 페이지 열기"))));
        }, "help", "h");
        cmd("도움말2", null, (s, l, a) -> Msg.send(s,
                "%nl%&a/스탯 , /stats [플레이어] &7- &f플레이어의 장비와 랭크를 확인할 수 있습니다.%nl%&a/장터 &7- &f유저 장터를 오픈합니다%nl%&a/대전 [플레이어] &7- &f플레이어에게 대전 신청을 보냅니다.%nl%&a/채널, /ch [global/local/team] &7- &f채팅 채널을 변경합니다.%nl%&a/도박 &7- &f홀짝 도박 창을 엽니다.%nl%&a/바카라 &7- &f바카라 창을 엽니다.%nl%%nl%"));
        cmd("마인리스트", null, (s, l, a) -> {
            if (!Cmd.isPlayer(s)) return;
            Player p = (Player) s;
            Msg.soundAt(p.getLocation(), "entity.player.levelup", 1, 2);
            Msg.send(p, "");
            Msg.send(p, "");
            Msg.send(p, "<b><gradient:#7CFF00:#F9D6CE>[Minelist]</gradient></b>");
            Msg.send(p, "<b><gradient:#7CFF00:#F9D6CE>서버를 추천하고 보상을 받아보세요!</gradient></b>");
            Msg.send(p, "");
            link(p, "&e[클릭하여 마인리스트로 이동하기]", plugin.getConfig().getString("links.minelist"), "&a클릭하여 마인리스트로 이동하기");
            Msg.send(p, "");
            link(p, "&e[클릭하여 마인보드로 이동하기]", plugin.getConfig().getString("links.mineboard"), "&a클릭하여 마인보드로 이동하기");
            Msg.send(p, "");
            Msg.send(p, "");
        }, "minelist", "추천");
        cmd("디스코드", null, (s, l, a) -> {
            if (!Cmd.isPlayer(s)) return;
            Player p = (Player) s;
            Msg.soundAt(p.getLocation(), "entity.player.levelup", 1, 2);
            Msg.send(p, "");
            Msg.send(p, "");
            Msg.send(p, "<b><gradient:#3A35F7:#47B2FF>[Discord]</gradient></b>");
            Msg.send(p, "<gradient:#3A35F7:#47B2FF><bold:true>서버 공식 디스코드에 가입해주세요!</gradient>");
            Msg.send(p, "");
            link(p, "&7[클릭하여 디스코드 가입하기]", plugin.getConfig().getString("links.discord"), "&a클릭하여 디스코드에 가입하기");
            Msg.send(p, "");
            Msg.send(p, "");
        }, "discord");
    }

    private void link(Player p, String text, String url, String hover) {
        if (url == null || url.isEmpty()) return;
        p.sendMessage(Msg.c(text).clickEvent(ClickEvent.openUrl(url)).hoverEvent(HoverEvent.showText(Msg.c(hover))));
    }

    /** 스폰의 특정 구역에 20초 머물면 경험치 지급 */
    @Override
    public void secondTick() {
        ConfigurationSection z = plugin.getConfig().getConfigurationSection("xp-zone");
        if (z == null || !z.getBoolean("enabled", true)) return;
        List<Integer> min = z.getIntegerList("min");
        List<Integer> max = z.getIntegerList("max");
        if (min.size() < 3 || max.size() < 3) return;
        long now = System.currentTimeMillis();
        long need = z.getLong("seconds", 20) * 1000;
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location l = p.getLocation();
            boolean inside = p.getWorld().getName().equalsIgnoreCase(z.getString("world", world("spawn")))
                    && l.getX() >= min.get(0) && l.getX() <= max.get(0)
                    && l.getY() >= min.get(1) && l.getY() <= max.get(1)
                    && l.getZ() >= min.get(2) && l.getZ() <= max.get(2);
            if (!inside) {
                zoneSince.remove(p.getUniqueId());
                continue;
            }
            long since = zoneSince.computeIfAbsent(p.getUniqueId(), k -> now);
            if (now - since >= need) {
                int xp = ThreadLocalRandom.current().nextInt(z.getInt("min-xp", 10), z.getInt("max-xp", 50) + 1);
                p.giveExp(xp);
                Msg.send(p, "<gradient:#00D2FF:#E1F1FC><italic:false><bold:true>좋은 기운이 옵니다... </gradient>&7(" + xp + ")");
                zoneSince.put(p.getUniqueId(), now);
            }
        }
    }
}
