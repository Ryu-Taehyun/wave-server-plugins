package com.premiumpass.pass;

import com.premiumpass.PremiumPass;
import com.premiumpass.data.PlayerData;
import kr.wave.common.Text;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** 프리미엄 패스권 아이템: 우클릭하면 이번 시즌 프리미엄 패스가 활성화된다 */
public class PremiumVoucher implements Listener {

    private final PremiumPass plugin;
    private final NamespacedKey key;

    public PremiumVoucher(PremiumPass plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "premium_voucher");
    }

    public ItemStack create(int amount) {
        FileConfiguration c = plugin.getConfig();
        Material mat = Material.matchMaterial(c.getString("premium.voucher.material", "PAPER"));
        if (mat == null || !mat.isItem() || mat.isAir()) mat = Material.PAPER;
        ItemStack it = new ItemStack(mat, Math.max(1, amount));
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(Text.color(c.getString("premium.voucher.name", "&6&l프리미엄 패스권")));
        meta.setLore(Text.color(c.getStringList("premium.voucher.lore")));
        meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.values());
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    public boolean isVoucher(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack hand = e.getPlayer().getInventory().getItemInMainHand();
        if (!isVoucher(hand)) return;
        e.setCancelled(true);

        Player p = e.getPlayer();
        PlayerData d = plugin.data().get(p);
        plugin.pass().ensure(p, d);
        if (plugin.pass().isPremium(p, d)) {
            Text.send(p, "&c이미 이번 시즌 프리미엄 패스를 보유하고 있습니다.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        hand.setAmount(hand.getAmount() - 1);
        plugin.pass().activatePremium(p, d);
    }

    /** 패스권을 조합 재료로 쓰지 못하게 막는다 */
    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        for (ItemStack it : e.getInventory().getMatrix()) {
            if (isVoucher(it)) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }
}
