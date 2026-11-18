package kr.wave.core.util;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 아이템 빌더 (Skript 의 "X of Y named ... with lore ..." 대응) */
public final class It {

    private final ItemStack item;
    private final ItemMeta meta;
    private final List<String> lore = new ArrayList<>();

    private It(Material m, int amount) {
        item = new ItemStack(m, Math.max(1, Math.min(amount, 99)));
        meta = item.getItemMeta();
    }

    public static It of(Material m) {
        return new It(m, 1);
    }

    public static It of(Material m, int amount) {
        return new It(m, amount);
    }

    public It name(String s) {
        meta.displayName(Msg.item(s));
        return this;
    }

    public It lore(String... parts) {
        for (String p : parts) lore.add(p);
        return this;
    }

    public It ench(Enchantment e, int level) {
        if (meta instanceof EnchantmentStorageMeta esm && item.getType() == Material.ENCHANTED_BOOK) {
            esm.addStoredEnchant(e, level, true);
        } else {
            meta.addEnchant(e, level, true);
        }
        return this;
    }

    public It unbreakable() {
        meta.setUnbreakable(true);
        return this;
    }

    public It cmd(int id) {
        meta.setCustomModelData(id);
        return this;
    }

    public It hide() {
        meta.addItemFlags(ItemFlag.values());
        return this;
    }

    public It skull(OfflinePlayer owner) {
        if (meta instanceof SkullMeta sm) sm.setOwningPlayer(owner);
        return this;
    }

    public ItemStack build() {
        if (!lore.isEmpty()) meta.lore(Msg.lore(lore.toArray(new String[0])));
        item.setItemMeta(meta);
        return item;
    }

    /* ---------------- 지급 도우미 ---------------- */

    /** 최대 스택 크기를 넘는 수량도 나눠서 지급, 남는 건 발밑에 떨어뜨림 */
    public static void give(Player p, ItemStack it, int totalAmount) {
        if (it == null || it.getType().isAir()) return;
        int max = Math.max(1, it.getMaxStackSize());
        int left = totalAmount;
        while (left > 0) {
            ItemStack part = it.clone();
            part.setAmount(Math.min(max, left));
            left -= part.getAmount();
            drop(p, p.getInventory().addItem(part));
        }
    }

    public static void give(Player p, ItemStack... items) {
        for (ItemStack it : items) if (it != null) give(p, it, it.getAmount());
    }

    public static void give(Player p, Collection<ItemStack> items) {
        for (ItemStack it : items) if (it != null) give(p, it, it.getAmount());
    }

    private static void drop(Player p, Map<Integer, ItemStack> left) {
        for (ItemStack l : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), l);
    }

    /** 인벤토리(가방 0~35칸)의 빈 칸 수 */
    public static int freeSlots(Player p) {
        int n = 0;
        ItemStack[] c = p.getInventory().getStorageContents();
        for (int i = 0; i < Math.min(36, c.length); i++) if (c[i] == null || c[i].getType().isAir()) n++;
        return n;
    }

    /** 손에 든 아이템 1개 소모 */
    public static void takeOneFromHand(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getAmount() <= 1) p.getInventory().setItemInMainHand(null);
        else hand.setAmount(hand.getAmount() - 1);
    }

    public static boolean isAir(ItemStack it) {
        return it == null || it.getType().isAir();
    }

    public static ItemStack pane(Material m, String name) {
        return of(m).name(name).build();
    }

    public static ItemStack gray() {
        return pane(Material.BLACK_STAINED_GLASS_PANE, " ");
    }

    public static ItemStack close() {
        return of(Material.BARRIER).name("§c닫기").build();
    }
}
