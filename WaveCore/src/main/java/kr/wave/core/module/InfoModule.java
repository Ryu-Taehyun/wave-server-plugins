package kr.wave.core.module;

import kr.wave.core.WaveCore;
import kr.wave.core.util.Cmd;
import kr.wave.core.util.Msg;
import net.kyori.adventure.text.Component;
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
                "%nl%&a/스탯 , /stats [플레이어] &7- &f플레이어의 장비와 랭크를 확인할 수 있습니다.%nl%&a/장터 &7- &f유저 장터를 오픈합니다%nl%&a/대전 [플레이어] &7- &f플레이어에게 대전 신청을 보냅니다.%nl%&a/채널, /ch [global/local/team] &7- &f채팅 채널을 변경합니다.%nl%&a/도박 &7- &f홀짝 도박 창을 엽니다.%nl%&a/바카라 &7- &f바카라 창을 엽니다.%nl%&a채팅에 [item] [inv] [ec] &7- &f손에 든 아이템 · 인벤토리 · 엔더 상자를 채팅으로 보여 줍니다.%nl%&a/정보 [플레이어] &7- &f레벨 · 랭크 · 칭호 · 팀을 봅니다. &8(채팅에서 이름 클릭)%nl%&a채팅에서 이름 Shift+클릭 &7- &f귓속말 입력%nl%&a/출석 &7- &f출석 현황과 7일 보상을 봅니다.%nl%&a/경고목록 &7- &f내 경고 기록을 봅니다.%nl%%nl%"));
        cmd("마인리스트", null, (s, l, a) -> {
            if (!Cmd.isPlayer(s)) return;
            Player p = (Player) s;
            card(p, "#7CFF00", "#C8FFB0", "🗳 SERVER VOTE", "서버 추천하고 함께 키워 주세요!",
                    List.of("&7추천 한 번이 새 유저를 데려오는 가장 큰 힘이에요.", "&7하루에 한 번씩 추천할 수 있습니다."),
                    button("&a&l[ 🗳 마인리스트 추천 ]", plugin.getConfig().getString("links.minelist"), "&a마인리스트 추천 페이지 열기%nl%&8브라우저가 열립니다"),
                    button("&e&l[ 📋 마인보드 추천 ]", plugin.getConfig().getString("links.mineboard"), "&e마인보드 추천 페이지 열기%nl%&8브라우저가 열립니다"));
        }, "minelist", "추천");
        cmd("디스코드", null, (s, l, a) -> {
            if (!Cmd.isPlayer(s)) return;
            Player p = (Player) s;
            card(p, "#5865F2", "#9AA5FF", "💬 DISCORD", "공식 디스코드에 참여하세요!",
                    List.of("&f📢 &7업데이트 · 점검 공지를 가장 먼저", "&f🎉 &7이벤트 일정 · 당첨자 발표", "&f🛠 &7문의 · 신고 · 건의는 티켓으로 빠르게"),
                    button("<b><gradient:#5865F2:#9AA5FF>[ 💬 디스코드 참여하기 ]</gradient></b>", plugin.getConfig().getString("links.discord"), "&9디스코드 초대 링크 열기%nl%&8브라우저 · 디스코드 앱이 열립니다"),
                    null);
        }, "discord");
    }

    /** 채팅 안내 카드: 왼쪽 세로 줄 + 제목 + 설명 + 버튼 */
    private void card(Player p, String from, String to, String tag, String title, List<String> lines, Component b1, Component b2) {
        if (b1 == null && b2 == null) {
            Msg.send(p, "&c링크가 아직 설정되지 않았습니다. 관리자에게 문의해 주세요.");
            return;
        }
        String bar = "<color:" + from + ">▍</color> ";
        Msg.soundAt(p.getLocation(), "entity.player.levelup", 0.7f, 2);
        p.sendMessage(Component.empty());
        Msg.send(p, bar + "<b><gradient:" + from + ":" + to + ">" + tag + "</gradient></b>");
        Msg.send(p, bar + "&f&l" + title);
        Msg.send(p, bar);
        for (String line : lines) Msg.send(p, bar + line);
        Msg.send(p, bar);
        Component row = Msg.c(bar + " ");
        if (b1 != null) row = row.append(b1);
        if (b1 != null && b2 != null) row = row.append(Msg.c("  "));
        if (b2 != null) row = row.append(b2);
        p.sendMessage(row);
        Msg.send(p, bar + "&8↑ 버튼을 클릭하세요");
        p.sendMessage(Component.empty());
    }

    private static Component button(String text, String url, String hover) {
        if (url == null || url.isBlank()) return null;
        return Msg.c(text).clickEvent(ClickEvent.openUrl(url)).hoverEvent(HoverEvent.showText(Msg.c(hover)));
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
