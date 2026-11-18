package kr.wave.core.module;

import kr.wave.core.WaveCore;
import kr.wave.core.data.PlayerData;
import kr.wave.core.util.Cmd;
import kr.wave.core.util.Gui;
import kr.wave.core.util.Msg;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** backpack.sk : 개인 배낭 (2줄), 관리자 배낭/엔더상자 열기 */
public class BackpackModule extends Module {

    /** 같은 배낭을 두 곳에서 동시에 열어 아이템이 복사되지 않도록, 열린 배낭은 하나의 창을 공유 */
    private final Map<UUID, Gui> open = new HashMap<>();

    public BackpackModule(WaveCore plugin) {
        super(plugin);
    }

    @Override
    public void enable() {
        cmd("배낭", null, (s, l, a) -> {
            if (!Cmd.isPlayer(s)) return;
            Player p = (Player) s;
            openBackpack(p, p, p.getName());
            tutorial(p, "backpack");
            Msg.sound(p, "item.armor.equip_leather", 0.5f, 1.2f);
        }, "backpack");
        cmd("배낭열기", ADMIN, "§c[❌] 권한이 없습니다.", (s, l, a) -> {
            if (!Cmd.isPlayer(s)) return;
            OfflinePlayer t = Cmd.offline(Cmd.arg(a, 0));
            if (t == null) {
                Msg.send(s, "§c[💡] 올바른 사용법: /배낭열기 [유저닉네임]");
                return;
            }
            openBackpack((Player) s, t, a[0]);
            Msg.send(s, "§a[🎒] " + a[0] + "님의 배낭을 열었습니다.");
        });
        cmd("backpackreset", ADMIN, "§c[❌] 권한이 없습니다.", (s, l, a) -> {
            OfflinePlayer t = Cmd.offline(Cmd.arg(a, 0));
            if (t == null) {
                Msg.send(s, "§c[💡] 올바른 사용법: /배낭초기화 [유저닉네임]");
                return;
            }
            PlayerData d = plugin.store().get(t);
            d.backpack = new ItemStack[27];
            Gui g = open.get(t.getUniqueId());
            if (g != null) g.getInventory().clear();
            plugin.store().saveAuto(d);
            Msg.send(s, "§c[🎒] " + a[0] + "님의 배낭 데이터를 강제로 초기화했습니다.");
        }, "배낭초기화");
        cmd("엔더열기", ADMIN, "§c[❌] 권한이 없습니다.", (s, l, a) -> {
            if (!Cmd.isPlayer(s)) return;
            Player t = Cmd.online(Cmd.arg(a, 0));
            if (t == null) {
                Msg.send(s, "§c[💡] 올바른 사용법: /엔더열기 [유저닉네임] &7(접속 중인 플레이어만)");
                return;
            }
            ((Player) s).openInventory(t.getEnderChest());
            Msg.sound((Player) s, "block.ender_chest.open", 0.5f, 1);
            Msg.send(s, "§a[🎒] " + a[0] + "님의 엔더상자를 열었습니다.");
        });
        cmd("전체배낭초기화", ADMIN, (s, l, a) -> {
            for (Gui g : open.values()) g.getInventory().clear();
            int n = plugin.store().forAll(d -> d.backpack = new ItemStack[27]);
            Msg.send(s, "&e[📬] &f모든 플레이어의 배낭 데이터가 성공적으로 초기화되었습니다. &7(" + n + "명)");
            Msg.broadcast("&e[📬] &f서버 관리자에 의해 전체 배낭 데이터가 초기화되었습니다.");
        });
    }

    private void openBackpack(Player viewer, OfflinePlayer owner, String ownerName) {
        UUID id = owner.getUniqueId();
        Gui g = open.get(id);
        if (g == null) {
            PlayerData d = plugin.store().get(owner);
            g = new Gui(Math.max(2, Math.min(3, d.backpackRows)), "&8&l🎒 " + ownerName + "의 배낭").editable(true);
            for (int i = 0; i < d.backpackRows * 9; i++) g.set(i, d.backpack[i]);
            final Gui gui = g;
            g.onClose(e -> {
                PlayerData dd = plugin.store().get(id);
                ItemStack[] c = gui.getInventory().getContents();
                for (int i = 0; i < c.length && i < dd.backpack.length; i++) dd.backpack[i] = c[i] == null ? null : c[i].clone();
                plugin.store().saveAuto(dd);
                if (e.getPlayer() instanceof Player p) {
                    Msg.sound(p, "block.barrel.close", 0.5f, 1);
                    Msg.send(p, "§a[🎒] &7배낭의 내용물이 안전하게 저장되었습니다.");
                }
                // 마지막으로 보던 사람이 닫으면 공유 창 해제
                if (gui.getInventory().getViewers().size() <= 1) open.remove(id);
            });
            open.put(id, g);
        }
        g.open(viewer);
    }
}
