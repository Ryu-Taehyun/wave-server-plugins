package com.dailyshop.currency;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** 아이템 결제 (예: 에메랄드). 이름이나 마법부여가 없는 일반 아이템만 인정한다. */
public class ItemCurrency implements Currency {

    private final Material material;

    public ItemCurrency(Material material) {
        this.material = material;
    }

    private boolean plain(ItemStack it) {
        return it != null && it.getType() == material && it.isSimilar(new ItemStack(material));
    }

    @Override
    public String id() {
        return "ITEM(" + material.name() + ")";
    }

    @Override
    public long balance(Player p) {
        long n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) if (plain(it)) n += it.getAmount();
        return n;
    }

    @Override
    public boolean withdraw(Player p, long amount) {
        if (balance(p) < amount) return false;
        long left = amount;
        ItemStack[] contents = p.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack it = contents[i];
            if (!plain(it)) continue;
            int take = (int) Math.min(left, it.getAmount());
            it.setAmount(it.getAmount() - take);
            left -= take;
            contents[i] = it.getAmount() <= 0 ? null : it;
        }
        p.getInventory().setStorageContents(contents);
        return true;
    }
}
