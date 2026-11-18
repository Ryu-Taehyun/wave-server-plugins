package com.premiumpass.gui;

import com.premiumpass.PremiumPass;
import com.premiumpass.data.PlayerData;
import com.premiumpass.util.Items;
import com.premiumpass.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** 모든 GUI 의 기본 클래스 */
public abstract class Menu implements InventoryHolder {

    protected final PremiumPass plugin;
    protected final Player player;
    private final int size;
    private final String title;
    protected Inventory inv;

    protected Menu(PremiumPass plugin, Player player, int size, String title) {
        this.plugin = plugin;
        this.player = player;
        this.size = size;
        this.title = title;
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public void open() {
        if (inv == null) inv = Bukkit.createInventory(this, size, Text.color(title));
        render();
        player.openInventory(inv);
    }

    /** 인벤토리 내용 그리기 (다시 호출하면 새로고침) */
    protected abstract void render();

    public abstract void onClick(InventoryClickEvent e);

    public void onClose(InventoryCloseEvent e) {
    }

    /** true 면 아이템을 넣고 뺄 수 있다 (보상 편집기) */
    public boolean editable() {
        return false;
    }

    protected PlayerData data() {
        return plugin.data().get(player);
    }

    protected void fillEmpty() {
        ItemStack f = Items.filler();
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, f);
        }
    }

    protected void click() {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1f);
    }

    /** 패스 정보 아이콘 */
    protected ItemStack infoIcon() {
        PlayerData d = data();
        int max = plugin.pass().maxLevel();
        List<String> lore = new ArrayList<>();
        lore.add("&7" + plugin.pass().seasonName());
        lore.add(plugin.pass().isPremium(player, d) ? "&6★ 프리미엄 패스 보유" : "&7무료 패스 &8(프리미엄 미보유)");
        lore.add("");
        lore.add("&f레벨: &e" + d.level + " &7/ " + max);
        if (d.level < max) {
            long req = plugin.pass().required(d.level);
            lore.add("&fXP: &b" + Text.num(d.xp) + " &7/ " + Text.num(req));
            lore.add(Text.bar(d.xp, req, 20));
        } else {
            long per = plugin.pass().xpPerPoint();
            lore.add("&6&l최대 레벨 달성!");
            lore.add("&f포인트 전환: &b" + Text.num(d.overflowXp) + " &7/ " + Text.num(per) + " XP");
            lore.add(Text.bar(d.overflowXp, per, 20));
        }
        lore.add("");
        lore.add("&f프리미엄 패스 포인트: &6" + Text.num(d.points));
        lore.add("&7이번 시즌 획득 포인트: " + Text.num(d.seasonPointsEarned));
        return Items.icon(Material.NETHER_STAR, "&6&l프리미엄 패스", lore);
    }
}
