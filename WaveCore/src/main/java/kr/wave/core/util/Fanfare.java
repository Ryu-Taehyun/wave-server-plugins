package kr.wave.core.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 특별 아이템 획득 전체 공지 (보스 전리품 'Arbiter of Heaven' 형식으로 통일)
 *   ──────── (그라데이션 줄)
 *        ✦ 제목 ✦
 *     how (예: OOO 님이 주사위 87 (으)로)
 *     [아이템 이름] 를 획득했습니다!   (이름에 마우스를 올리면 아이템 설명)
 *   ────────
 * 얻은 사람: 빛기둥 · 토템 · 타이틀 · 효과음
 */
public final class Fanfare {

    private Fanfare() {
    }

    /**
     * w: 얻은 사람 (오프라인이면 null), item: 마우스를 올렸을 때 보일 아이템, name: 채팅에 보일 아이템 이름(색 포함)
     * colors: 그라데이션 색 ("#FFF200:#F6E47C:#FFFCD4"), title: 가운데 제목, how: 앞 문장, josa: 을/를
     */
    public static void broadcast(Player w, ItemStack item, String name, String colors, String title, String how, String josa) {
        String first = colors.split(":")[0];
        if (w != null) {
            org.bukkit.Color c = Fx.rgb(Integer.parseInt(first.substring(1), 16));
            Fx.pillar(w.getLocation(), 12, c, Particle.END_ROD);
            Fx.burst(w.getLocation().add(0, 1, 0), Particle.TOTEM_OF_UNDYING, 120, 0.6);
            Msg.sound(w, "ui.toast.challenge_complete", 1, 1);
            Msg.title(w, "<gradient:" + colors + "><b>" + title + "</b></gradient>", name, 10, 70, 20);
        }
        Component it = Msg.hoverItem(item, name);
        String line = "<gradient:" + colors + "><st>                                                            </st></gradient>";
        Msg.broadcast(" ");
        Msg.broadcast(line);
        Msg.broadcast(" ");
        Msg.broadcast("            <b><gradient:" + colors + ">✦ " + title + " ✦</gradient></b>");
        Msg.broadcast(" ");
        Msg.broadcastItem("  " + how, it);
        Msg.broadcastItem("  " + Msg.ITEM + " &f" + josa + " 획득했습니다!", it);
        Msg.broadcast(" ");
        Msg.broadcast(line);
        Msg.broadcast(" ");
        for (Player o : Bukkit.getOnlinePlayers()) Msg.sound(o, "ui.toast.challenge_complete", 0.6f, 1.2f);
    }
}
