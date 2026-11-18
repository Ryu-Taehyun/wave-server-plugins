package com.premiumpass.gui;

import com.premiumpass.PremiumPass;
import com.premiumpass.data.PlayerData;
import com.premiumpass.shop.ShopManager;
import com.premiumpass.util.Items;
import com.premiumpass.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** 프리미엄 패스 포인트 교환 상점 */
public class ShopMenu extends Menu {

    private static final int PER_PAGE = 45;
    private int page;
    private List<ShopManager.Entry> entries = List.of();

    public ShopMenu(PremiumPass plugin, Player player, int page) {
        super(plugin, player, 54, "&6&l프리미엄 패스 &8- 교환 상점");
        this.page = page;
    }

    private int pages() {
        return Math.max(1, (entries.size() + PER_PAGE - 1) / PER_PAGE);
    }

    @Override
    protected void render() {
        inv.clear();
        entries = plugin.shop().list();
        page = Math.max(0, Math.min(page, pages() - 1));
        PlayerData d = data();

        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= entries.size()) break;
            inv.setItem(i, shopIcon(d, entries.get(idx)));
        }

        if (page > 0) inv.setItem(45, Items.icon(Material.ARROW, "&e◀ 이전 페이지"));
        inv.setItem(48, Items.icon(Material.OAK_DOOR, "&7뒤로 가기"));
        inv.setItem(49, Items.icon(Material.SUNFLOWER, "&6보유 포인트: " + Text.num(d.points),
                "&7패스 최대 레벨 이후 얻는 XP와", "&7퀘스트 보상으로 포인트를 얻습니다."));
        if (page < pages() - 1) inv.setItem(53, Items.icon(Material.ARROW, "&e다음 페이지 ▶"));
        for (int i = 45; i < 54; i++) if (inv.getItem(i) == null) inv.setItem(i, Items.filler());
    }

    private ItemStack shopIcon(PlayerData d, ShopManager.Entry e) {
        ItemStack it = e.item.clone();
        ItemMeta meta = it.getItemMeta();
        List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        if (e.premiumOnly) lore.add(Text.color("&6★ 프리미엄 패스 전용"));
        if (e.limit > 0) {
            int bought = d.purchases.getOrDefault(e.id, 0);
            lore.add(Text.color("&f시즌 구매 제한 : " + (bought >= e.limit ? "&c" : "&a") + bought + "&7/" + e.limit));
        }
        lore.add(Text.color("&8━━━━━━━━━━━━━━"));
        lore.add(Text.color("&f가격 : &6" + Text.num(e.cost) + " 포인트"));
        if (e.premiumOnly && !plugin.pass().isPremium(player, d)) lore.add(Text.color("&c프리미엄 패스가 필요합니다"));
        else lore.add(Text.color(d.points >= e.cost ? "&e▶ 좌클릭 &f교환" : "&8▶ 좌클릭 포인트 부족"));
        meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        int slot = e.getSlot();
        if (slot < PER_PAGE) {
            int idx = page * PER_PAGE + slot;
            if (idx < entries.size()) {
                ShopManager.Entry entry = plugin.shop().get(entries.get(idx).id);
                if (entry != null && plugin.shop().buy(player, data(), entry)) render();
            }
            return;
        }
        switch (slot) {
            case 45 -> {
                if (page > 0) {
                    page--;
                    click();
                    render();
                }
            }
            case 53 -> {
                if (page < pages() - 1) {
                    page++;
                    click();
                    render();
                }
            }
            case 48 -> {
                click();
                new MainMenu(plugin, player).open();
            }
            default -> {
            }
        }
    }
}
