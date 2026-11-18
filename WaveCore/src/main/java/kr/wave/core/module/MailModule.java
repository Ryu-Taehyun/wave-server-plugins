package kr.wave.core.module;

import kr.wave.core.WaveCore;
import kr.wave.core.data.PlayerData;
import kr.wave.core.util.Cmd;
import kr.wave.core.util.Gui;
import kr.wave.core.util.It;
import kr.wave.core.util.Msg;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** post.sk : 우편함 */
public class MailModule extends Module {

    public MailModule(WaveCore plugin) {
        super(plugin);
    }

    @Override
    public void enable() {
        cmd("메일", ADMIN, this::mailCmd);
        cmd("우편함", null, (s, l, a) -> {
            if (Cmd.isPlayer(s)) open((Player) s);
        });
        cmd("전체우편초기화", ADMIN, (s, l, a) -> {
            int n = plugin.store().forAll(d -> d.mailbox.clear());
            Msg.send(s, "&e[📬] &f모든 플레이어의 우편함 데이터가 성공적으로 초기화되었습니다. &7(" + n + "명)");
            Msg.broadcast("&e[📬] &f서버 관리자에 의해 전체 우편함 데이터가 초기화되었습니다.");
        });
    }

    /** 다른 기능(레벨업, 랭크 보상 등)에서 우편 보내기 */
    public void send(UUID to, ItemStack item) {
        if (It.isAir(item)) return;
        PlayerData d = plugin.store().get(to);
        d.mailbox.add(item.clone());
        plugin.store().saveAuto(d);
    }

    private void mailCmd(CommandSender s, String l, String[] a) {
        if (!Cmd.isPlayer(s)) return;
        Player p = (Player) s;
        if (a.length == 0 || !a[0].equals("보내기")) {
            Msg.send(p, "&e[📬] §f올바른 명령어: /메일 보내기 [닉네임]");
            return;
        }
        OfflinePlayer t = Cmd.offline(Cmd.arg(a, 1));
        if (t == null) {
            Msg.send(p, "&c[📬] 메일을 보낼 플레이어의 닉네임을 적어주세요. (/메일 보내기 [닉네임])");
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (It.isAir(hand)) {
            Msg.send(p, "&c[📬] §c보낼 아이템을 손에 들어주세요!");
            return;
        }
        send(t.getUniqueId(), hand);
        p.getInventory().setItemInMainHand(null);
        Msg.send(p, "&e[📬] §f" + a[1] + "님에게 성공적으로 메일을 보냈습니다!");
        if (t.getPlayer() != null) {
            Player r = t.getPlayer();
            Msg.send(r, "");
            Msg.send(r, "<gradient:#00FF1F:#00FF9B><bold:true>[Mail</gradient><gradient:#00FF9B:#00FF9B><bold:true>box]</gradient>");
            Msg.send(r, "<gradient:#00FF1F:#84FFCF>새로운 우편이 도착했습니다!</gradient>");
            Msg.send(r, "");
            sendOpenLink(r, "&e/우편함 &7을 입력하여 도착한 우편을 확인하세요.");
            Msg.send(r, "");
        }
    }

    private void sendOpenLink(Player r, String text) {
        r.sendMessage(Msg.c(text).clickEvent(ClickEvent.runCommand("/우편함")).hoverEvent(HoverEvent.showText(Msg.c("&a클릭하여 우편함 열기"))));
    }

    private static final int PER_PAGE = 45;

    private void open(Player p) {
        PlayerData d = data(p);
        if (d.mailbox.isEmpty()) {
            Msg.send(p, "&c[📬] §c도착한 메일이 없습니다.");
            return;
        }
        open(p, 1);
    }

    private void open(Player p, int page) {
        PlayerData d = data(p);
        int pages = Math.max(1, (d.mailbox.size() + PER_PAGE - 1) / PER_PAGE);
        int pg = Math.max(1, Math.min(page, pages));
        Gui g = new Gui(6, "&8&l내 우편함 &7(" + pg + "/" + pages + ")");
        int start = (pg - 1) * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < d.mailbox.size(); i++) {
            final ItemStack ref = d.mailbox.get(start + i);
            ItemStack shown = ref.clone();
            g.set(i, shown, e -> {
                // 같은 틱에 여러 칸을 누르면 번호가 밀리므로, 번호가 아니라 그 우편 자체를 찾음
                int idx = -1;
                for (int k = 0; k < d.mailbox.size(); k++) if (d.mailbox.get(k) == ref) idx = k;
                if (idx < 0) return;
                ItemStack item = ref;
                if (!canHold(p, item)) {
                    Msg.send(p, "&c[📬] §c인벤토리에 공간이 부족합니다!");
                    Msg.sound(p, "entity.villager.no", 0.8f, 1f);
                    return;
                }
                d.mailbox.remove(idx);
                It.give(p, item);
                plugin.store().save(d);
                Msg.send(p, "&e[📬] §e아이템을 수령했습니다.");
                Msg.sound(p, "entity.experience_orb.pickup", 0.6f, 1.2f);
                if (d.mailbox.isEmpty()) p.closeInventory();
                else later(1, () -> open(p, pg));
            });
        }
        g.set(g.last() + 5, It.of(org.bukkit.Material.HOPPER).name("&a&l모두 수령").lore("&7인벤토리에 들어가는 만큼 모두 받습니다.").build(), e -> {
            int got = 0;
            while (!d.mailbox.isEmpty() && canHold(p, d.mailbox.get(0))) {
                It.give(p, d.mailbox.remove(0));
                got++;
            }
            plugin.store().save(d);
            Msg.send(p, got > 0 ? "&e[📬] §e아이템 " + got + "개를 수령했습니다." : "&c[📬] §c인벤토리에 공간이 부족합니다!");
            Msg.sound(p, got > 0 ? "entity.experience_orb.pickup" : "entity.villager.no", 0.6f, 1.2f);
            if (d.mailbox.isEmpty()) p.closeInventory();
            else later(1, () -> open(p, 1));
        });
        g.pages(p, pg, pages, n -> later(1, () -> open(p, n)))
                .nav(p, () -> plugin.module(MenuModule.class).menu(p)).fill().open(p);
    }

    private static boolean canHold(Player p, ItemStack item) {
        if (It.freeSlots(p) > 0) return true;
        int room = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it != null && it.isSimilar(item)) room += it.getMaxStackSize() - it.getAmount();
        }
        return room >= item.getAmount();
    }

    // 접속 시 우편 안내는 CoreModule 의 환영 안내에 포함됨
}
