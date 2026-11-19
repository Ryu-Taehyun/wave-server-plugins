package com.dailyshop.gui;

import com.dailyshop.DailyShop;
import com.dailyshop.ShopManager;
import kr.wave.common.Items;
import kr.wave.common.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 일일 상점 GUI */
public class ShopMenu implements InventoryHolder {

    private static final int[] CENTER = {20, 21, 22, 23, 24, 29, 30, 31, 32, 33, 11, 12, 13, 14, 15};

    private final DailyShop plugin;
    private final Player player;
    private final Inventory inv;
    private final Map<Integer, String> slotToId = new HashMap<>();

    public ShopMenu(DailyShop plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.inv = Bukkit.createInventory(this, 54, Text.color("&e&l일일 상점"));
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public void open() {
        render();
        player.openInventory(inv);
    }

    private void render() {
        inv.clear();
        slotToId.clear();
        ShopManager shop = plugin.shop();
        boolean premium = shop.isPremium(player);

        List<ShopManager.Entry> today = shop.today();
        boolean centered = today.size() <= CENTER.length;
        for (int i = 0; i < today.size() && i < 36; i++) {
            int slot = centered ? CENTER[i] : 9 + i;
            ShopManager.Entry e = today.get(i);
            inv.setItem(slot, icon(e, premium));
            slotToId.put(slot, e.id);
        }

        List<String> info = new ArrayList<>();
        info.add("&7매일 새로운 상품이 진열됩니다.");
        info.add("&f다음 갱신까지: &e" + Text.duration(shop.untilReset()));
        if (shop.premiumAvailable() && shop.premiumDiscount() > 0) {
            info.add("");
            info.add(premium ? "&6★ 프리미엄 할인 " + shop.premiumDiscount() + "% 적용 중"
                    : "&7프리미엄 패스 보유 시 &6" + shop.premiumDiscount() + "% 할인");
        }
        inv.setItem(4, Items.icon(Material.CLOCK, "&e&l일일 상점", info));
        if (shop.cashAvailable()) {
            inv.setItem(49, Items.icon(Material.SUNFLOWER, "&6보유 " + shop.currencyName() + ": " + Text.num(shop.balance(player)),
                    "&e보유 " + shop.cashName() + " : " + Text.num(shop.cashBalance(player)), "&8━━━━━━━━━━━━━━", "&e▶ 좌클릭 &f" + shop.currencyName() + "로 구매", "&e▶ 우클릭 &f" + shop.cashName() + "로 구매"));
        } else {
            inv.setItem(49, Items.icon(Material.SUNFLOWER, "&6보유 " + shop.currencyName() + ": " + Text.num(shop.balance(player))));
        }
        if (shop.premiumAvailable()) {
            inv.setItem(48, Items.icon(Material.NETHER_STAR, "&6프리미엄 패스 열기", "&7/pass"));
        }
        ItemStack filler = Items.filler();
        for (int i = 0; i < inv.getSize(); i++) if (inv.getItem(i) == null) inv.setItem(i, filler);
    }

    private ItemStack icon(ShopManager.Entry e, boolean premium) {
        ShopManager shop = plugin.shop();
        int n = shop.bought(player, e);
        boolean soldOut = e.limit > 0 && n >= e.limit;
        if (soldOut) {
            return Items.icon(Material.BARRIER, "&c품절 &7- " + Items.describe(e.item),
                    "&7오늘 구매 한도를 모두 사용했습니다.", "&7다음 갱신: &e" + Text.duration(shop.untilReset()));
        }
        long price = shop.price(player, e);
        ItemStack it = e.item.clone();
        ItemMeta meta = it.getItemMeta();
        List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add(Text.color("&f등급 : " + e.rarity()));
        if (e.premiumOnly) lore.add(Text.color("&6★ 프리미엄 패스 전용"));
        if (e.limit > 0) lore.add(Text.color("&f일일 구매 제한 : " + (n >= e.limit ? "&c" : "&a") + n + "&7/" + e.limit));
        lore.add(Text.color("&8━━━━━━━━━━━━━━"));
        if (price != e.cost) {
            lore.add(Text.color("&f가격 : &8&m" + Text.num(e.cost) + "&r &6" + shop.money(price) + " &7(프리미엄 할인)"));
        } else {
            lore.add(Text.color("&f가격 : &6" + shop.money(price)));
        }
        if (shop.cashAvailable()) lore.add(Text.color("&f캐시 가격 : &e" + Text.num(shop.cashPrice(player, e)) + " " + shop.cashName()));
        if (e.premiumOnly && !premium) lore.add(Text.color("&c프리미엄 패스가 필요합니다"));
        else {
            lore.add(Text.color(shop.balance(player) >= price ? "&e▶ 좌클릭 &f" + shop.currencyName() + "로 구매" : "&8▶ 좌클릭 " + shop.currencyName() + " 부족"));
            if (shop.cashAvailable()) lore.add(Text.color(shop.cashBalance(player) >= shop.cashPrice(player, e) ? "&e▶ 우클릭 &f" + shop.cashName() + "로 구매" : "&8▶ 우클릭 " + shop.cashName() + " 부족"));
        }
        meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
    }

    void onClick(InventoryClickEvent e) {
        int slot = e.getSlot();
        if (slot == 48 && plugin.shop().premiumAvailable()) {
            player.closeInventory();
            player.performCommand("pass");
            return;
        }
        String id = slotToId.get(slot);
        if (id == null) return;
        ShopManager.Entry entry = plugin.shop().get(id);
        if (entry == null) return;
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1f);
        boolean right = e.getClick() == org.bukkit.event.inventory.ClickType.RIGHT || e.getClick() == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT;
        plugin.shop().buy(player, entry, right && plugin.shop().cashAvailable());
        render();
    }

    /** 상점 창 안에서 아이템을 빼거나 넣지 못하게 막는다 */
    public static class Guard implements Listener {
        @EventHandler
        public void onClick(InventoryClickEvent e) {
            Inventory top = e.getView().getTopInventory();
            if (!(top.getHolder() instanceof ShopMenu menu)) return;
            e.setCancelled(true);
            if (e.getClickedInventory() == top) menu.onClick(e);
        }

        @EventHandler
        public void onDrag(InventoryDragEvent e) {
            if (e.getView().getTopInventory().getHolder() instanceof ShopMenu) e.setCancelled(true);
        }
    }
}
