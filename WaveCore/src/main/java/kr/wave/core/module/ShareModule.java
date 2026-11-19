package kr.wave.core.module;

import kr.wave.core.WaveCore;
import kr.wave.core.util.Gui;
import kr.wave.core.util.It;
import kr.wave.core.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 채팅으로 아이템 · 인벤토리 · 엔더 상자 보여주기
 * - [item] [i] [아이템] [손]          : 손에 든 아이템 (마우스를 올리면 아이템 설명)
 * - [inventory] [inv] [인벤] [인벤토리] : 인벤토리 (클릭하면 보내는 순간의 모습을 보기 전용 창으로)
 * - [enderchest] [ec] [엔더] [엔더상자] : 엔더 상자 (위와 같음)
 * 문장 안에 섞어 쓰면 그 자리에 그대로 들어갑니다. 예: "내 엔더상자 [ec] 어때?"
 */
public class ShareModule extends Module {

    private static final Pattern TOKEN = Pattern.compile("\\[(item|i|아이템|손|inventory|inv|인벤|인벤토리|enderchest|ec|엔더|엔더상자)]", Pattern.CASE_INSENSITIVE);
    /** 한 메시지에 바꿔 주는 최대 개수 (도배 방지) */
    private static final int MAX_TOKENS = 3;
    /** 공유한 인벤토리 · 엔더 상자를 볼 수 있는 시간 (서버 틱, 10분) */
    private static final long KEEP_TICKS = 20 * 60 * 10;

    private record Snapshot(String owner, boolean ender, ItemStack[] items, long created) {
    }

    private final Map<String, Snapshot> shared = new ConcurrentHashMap<>();

    public ShareModule(WaveCore plugin) {
        super(plugin);
    }

    @Override
    public void enable() {
        cmd("공유보기", null, (s, l, a) -> {
            if (!(s instanceof Player p) || a.length < 1) return;
            open(p, a[0]);
        }, "viewshare");
    }

    private int sweep;

    @Override
    public void secondTick() {
        if (++sweep < 60) return;
        sweep = 0;
        long now = org.bukkit.Bukkit.getCurrentTick();
        shared.values().removeIf(sn -> now - sn.created > KEEP_TICKS);
    }

    /** 채팅 글자를 컴포넌트로: [item] 등은 그 자리에 아이템 · 보기 링크로 바꿈 */
    public Component render(Player p, String text) {
        Matcher m = TOKEN.matcher(text);
        Component out = Component.empty();
        int last = 0, used = 0;
        while (m.find() && used < MAX_TOKENS) {
            if (m.start() > last) out = out.append(Component.text(text.substring(last, m.start())));
            String key = m.group(1).toLowerCase();
            out = out.append(switch (key) {
                case "item", "i", "아이템", "손" -> item(p);
                case "inventory", "inv", "인벤", "인벤토리" -> link(p, false);
                default -> link(p, true);
            });
            last = m.end();
            used++;
        }
        if (last < text.length()) out = out.append(Component.text(text.substring(last)));
        return out;
    }

    /** 손에 든 아이템: [이름 x개] + 마우스를 올리면 아이템 설명 */
    private Component item(Player p) {
        ItemStack it = p.getInventory().getItemInMainHand();
        if (It.isAir(it)) return Component.text("[빈 손]", NamedTextColor.GRAY);
        var meta = it.getItemMeta();
        Component name = meta != null && meta.hasDisplayName() ? meta.displayName() : Component.translatable(it.translationKey());
        Component shown = Component.text("[", NamedTextColor.WHITE).append(name)
                .append(Component.text(it.getAmount() > 1 ? " x" + it.getAmount() + "]" : "]", NamedTextColor.WHITE));
        shown = shown.decoration(TextDecoration.ITALIC, false);
        try {
            return shown.hoverEvent(it.asHoverEvent());   // 게임 안 아이템 설명 그대로
        } catch (Throwable t) {   // 지원하지 않는 서버: 이름 · 설명을 글자로
            Component tip = name;
            if (meta != null && meta.lore() != null) for (Component l : meta.lore()) tip = tip.append(Component.newline()).append(l);
            return shown.hoverEvent(HoverEvent.showText(tip));
        }
    }

    /** 인벤토리 · 엔더 상자: 지금 모습을 저장하고 클릭하면 열리는 링크 */
    private Component link(Player p, boolean ender) {
        ItemStack[] items;
        if (ender) items = clone(p.getEnderChest().getContents());
        else {
            PlayerInventory inv = p.getInventory();
            items = clone(inv.getContents());   // 0~35 가방 · 핫바, 36~39 갑옷, 40 왼손
        }
        String id = Long.toString(System.nanoTime(), 36) + Integer.toString(p.getName().hashCode() & 0xffff, 36);
        shared.put(id, new Snapshot(p.getName(), ender, items, org.bukkit.Bukkit.getCurrentTick()));
        String label = ender ? "[" + p.getName() + "의 엔더 상자]" : "[" + p.getName() + "의 인벤토리]";
        return Component.text(label, ender ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false)
                .hoverEvent(HoverEvent.showText(Msg.c("&e클릭하여 보기%nl%&7보낸 순간의 모습입니다. (10분 동안)").replaceText(b -> b.matchLiteral("%nl%").replacement("\n"))))
                .clickEvent(ClickEvent.runCommand("/공유보기 " + id));
    }

    private static ItemStack[] clone(ItemStack[] in) {
        ItemStack[] out = new ItemStack[in.length];
        for (int i = 0; i < in.length; i++) out[i] = in[i] == null ? null : in[i].clone();
        return out;
    }

    /** 보기 전용 창 (아이템을 꺼낼 수 없음) */
    private void open(Player viewer, String id) {
        Snapshot sn = shared.get(id);
        if (sn == null || org.bukkit.Bukkit.getCurrentTick() - sn.created > KEEP_TICKS) {
            Msg.send(viewer, "&c공유한 지 10분이 지나 더 이상 볼 수 없습니다.");
            return;
        }
        ItemStack pane = It.pane(Material.GRAY_STAINED_GLASS_PANE, " ");
        if (sn.ender) {
            Gui g = new Gui(4, "&8&l" + sn.owner + "의 엔더 상자");
            for (int i = 0; i < 27 && i < sn.items.length; i++) slot(g, viewer, i, sn.items[i], () -> open(viewer, id));
            for (int i = 27; i < 36; i++) g.set(i, pane);
            g.set(31, It.of(Material.BARRIER).name("&c닫기").build(), e -> viewer.closeInventory());
            g.open(viewer);
            return;
        }
        Gui g = new Gui(6, "&8&l" + sn.owner + "의 인벤토리");
        // 첫 줄: 투구 · 흉갑 · 레깅스 · 부츠 · 왼손
        String[] labels = {"&7투구 칸", "&7흉갑 칸", "&7레깅스 칸", "&7부츠 칸"};
        for (int k = 0; k < 4; k++) {
            ItemStack a = sn.items.length > 39 - k ? sn.items[39 - k] : null;
            g.set(k, a != null ? a : It.pane(Material.BLACK_STAINED_GLASS_PANE, labels[k]));
        }
        ItemStack off = sn.items.length > 40 ? sn.items[40] : null;
        g.set(5, off != null ? off : It.pane(Material.BLACK_STAINED_GLASS_PANE, "&7왼손"));
        for (int i : new int[]{4, 6, 7, 8}) g.set(i, pane);
        // 가방 (9~35) -> 9~35, 핫바 (0~8) -> 45~53
        for (int i = 9; i < 36; i++) slot(g, viewer, i, sn.items[i], () -> open(viewer, id));
        for (int i = 36; i < 45; i++) g.set(i, It.pane(Material.BLACK_STAINED_GLASS_PANE, "&7▼ 핫바"));
        for (int i = 0; i < 9; i++) slot(g, viewer, 45 + i, sn.items[i], () -> open(viewer, id));
        g.open(viewer);
    }

    /** 칸 채우기: 셜커 상자면 클릭해서 안을 볼 수 있게 */
    private void slot(Gui g, Player viewer, int slot, ItemStack it, Runnable back) {
        if (it != null && it.getItemMeta() instanceof org.bukkit.inventory.meta.BlockStateMeta bsm && bsm.getBlockState() instanceof org.bukkit.block.ShulkerBox box) {
            ItemStack shown = it.clone();
            var meta = shown.getItemMeta();
            meta.lore(Msg.append(meta.lore(), "&e» 클릭 &f안에 든 아이템 보기"));
            shown.setItemMeta(meta);
            ItemStack[] inside = clone(box.getInventory().getContents());
            Component title = meta.hasDisplayName() ? meta.displayName() : Component.translatable(it.translationKey());
            g.set(slot, shown, e -> later(1, () -> openShulker(viewer, title, inside, back)));
        } else g.set(slot, it);
    }

    /** 셜커 상자 안 (보기 전용) */
    private void openShulker(Player viewer, Component title, ItemStack[] inside, Runnable back) {
        Gui g = new Gui(4, "&8&l셜커 상자 안");
        for (int i = 0; i < 27 && i < inside.length; i++) slot(g, viewer, i, inside[i], back);
        ItemStack pane = It.pane(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 27; i < 36; i++) g.set(i, pane);
        g.set(27, It.of(Material.OAK_DOOR).name("&f뒤로 가기").lore("", "&7공유한 창으로 돌아갑니다.").build(), e -> later(1, back));
        g.set(31, It.of(Material.BARRIER).name("&c닫기").build(), e -> viewer.closeInventory());
        g.open(viewer);
    }

    /** 테스트 · 확인용 */
    public int sharedCount() {
        return shared.size();
    }

    public List<String> sharedIds() {
        return List.copyOf(shared.keySet());
    }
}
