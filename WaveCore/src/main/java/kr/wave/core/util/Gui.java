package kr.wave.core.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 간단한 GUI. 슬롯별 클릭 동작을 람다로 등록한다.
 * Skript 처럼 창 제목으로 구분하지 않으므로, 같은 제목의 상자/아이템 이름으로 GUI 를 속일 수 없다.
 */
public class Gui implements InventoryHolder {

    private final Inventory inv;
    private final Map<Integer, Consumer<InventoryClickEvent>> handlers = new HashMap<>();
    private boolean editable;
    private Consumer<InventoryClickEvent> anyClick;
    private Consumer<InventoryClickEvent> bottomClick;
    private Consumer<InventoryCloseEvent> onClose;
    private final Map<String, Object> data = new HashMap<>();
    private final java.util.Set<Integer> quiet = new java.util.HashSet<>();

    public Gui(int rows, String title) {
        inv = Bukkit.createInventory(this, Math.max(1, Math.min(6, rows)) * 9, Msg.c(title));
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public Gui set(int slot, ItemStack item) {
        if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, item);
        return this;
    }

    public Gui set(int slot, ItemStack item, Consumer<InventoryClickEvent> click) {
        set(slot, item);
        handlers.put(slot, click);
        return this;
    }

    public Gui on(int slot, Consumer<InventoryClickEvent> click) {
        handlers.put(slot, click);
        return this;
    }

    /** 이 칸은 클릭해도 기본 버튼 소리를 내지 않음 (직접 다른 소리를 낼 때) */
    public Gui quiet(int slot) {
        quiet.add(slot);
        return this;
    }

    /** 빈 칸을 지정한 판유리로 채움 */
    public Gui fill(Material pane) {
        ItemStack f = It.pane(pane, " ");
        for (int i = 0; i < inv.getSize(); i++) if (It.isAir(inv.getItem(i))) inv.setItem(i, f);
        return this;
    }

    /** 기본 배경: 검은 판유리 (프리미엄 패스와 동일) */
    public static final Material FILLER = Material.BLACK_STAINED_GLASS_PANE;

    public Gui fill() {
        return fill(FILLER);
    }

    /** 마지막 줄 첫 칸 번호 */
    public int last() {
        return inv.getSize() - 9;
    }

    public static void clickSound(Player p) {
        p.playSound(p.getLocation(), "ui.button.click", 0.5f, 1f);
    }

    public static void pageSound(Player p) {
        p.playSound(p.getLocation(), "item.book.page_turn", 1f, 1f);
    }

    public static void denySound(Player p) {
        p.playSound(p.getLocation(), "entity.villager.no", 0.8f, 1f);
    }

    /**
     * 맨 아래 버튼 줄: [뒤로 가기] [닫기]
     * back 이 null 이면 뒤로 가기 버튼 없음
     */
    public Gui nav(Player p, Runnable back) {
        int l = last();
        if (back != null) {
            set(l + 3, It.of(Material.OAK_DOOR).name("&7뒤로 가기").lore("&8클릭하여 이전 화면으로").build(), e ->
                    Bukkit.getScheduler().runTask(kr.wave.core.WaveCore.get(), back));
        }
        set(l + 4, It.close(), e -> p.closeInventory());
        return this;
    }

    /** 맨 아래 줄 양 끝: « 이전 페이지 / 다음 페이지 » */
    public Gui pages(Player p, int page, int pages, java.util.function.IntConsumer go) {
        int l = last();
        if (page > 1) set(l, It.of(Material.ARROW).name("&e« 이전 페이지").lore("&7" + (page - 1) + " / " + pages).build(), e -> {
            pageSound(p);
            go.accept(page - 1);
        }).quiet(l);
        if (page < pages) set(l + 8, It.of(Material.ARROW).name("&e다음 페이지 »").lore("&7" + (page + 1) + " / " + pages).build(), e -> {
            pageSound(p);
            go.accept(page + 1);
        }).quiet(l + 8);
        return this;
    }

    public Gui editable(boolean e) {
        editable = e;
        return this;
    }

    public Gui anyClick(Consumer<InventoryClickEvent> c) {
        anyClick = c;
        return this;
    }

    /** 창이 열린 상태에서 자기 인벤토리(아래 칸)를 클릭했을 때 */
    public Gui bottomClick(Consumer<InventoryClickEvent> c) {
        bottomClick = c;
        return this;
    }

    public Gui onClose(Consumer<InventoryCloseEvent> c) {
        onClose = c;
        return this;
    }

    public Gui put(String key, Object value) {
        data.put(key, value);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) data.get(key);
    }

    public void open(Player p) {
        p.openInventory(inv);
    }

    public static class Listen implements Listener {
        @EventHandler
        public void onClick(InventoryClickEvent e) {
            if (!(e.getView().getTopInventory().getHolder() instanceof Gui g)) return;
            if (!g.editable) e.setCancelled(true);
            if (e.getClickedInventory() != e.getView().getTopInventory()) {
                if (g.bottomClick != null && e.getClickedInventory() != null && e.getClick() != org.bukkit.event.inventory.ClickType.DOUBLE_CLICK) {
                    g.bottomClick.accept(e);
                }
                return;
            }
            // 더블클릭은 같은 칸에 클릭 이벤트가 한 번 더 오므로 무시 (구매/이동 두 번 실행 방지)
            if (e.getClick() == org.bukkit.event.inventory.ClickType.DOUBLE_CLICK) return;
            Consumer<InventoryClickEvent> h = g.handlers.get(e.getSlot());
            if (h != null) {
                if (!g.quiet.contains(e.getSlot()) && e.getWhoClicked() instanceof Player p) clickSound(p);
                h.accept(e);
            }
            if (g.anyClick != null) g.anyClick.accept(e);
        }

        @EventHandler
        public void onDrag(InventoryDragEvent e) {
            if (e.getView().getTopInventory().getHolder() instanceof Gui g && !g.editable) e.setCancelled(true);
        }

        @EventHandler
        public void onClose(InventoryCloseEvent e) {
            if (e.getInventory().getHolder() instanceof Gui g && g.onClose != null) g.onClose.accept(e);
        }
    }
}
