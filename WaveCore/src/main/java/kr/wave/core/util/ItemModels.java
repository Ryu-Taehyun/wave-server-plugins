package kr.wave.core.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 예전 리소스팩(Wave 특별 아이템 텍스쳐)에서 붙였던 item_model(wave:...) 떼어 내기.
 * 리소스팩을 없앴으므로 그대로 두면 텍스쳐가 없는 아이템(보라 · 검정 체크무늬)으로 보임.
 */
public final class ItemModels {

    private ItemModels() {
    }

    /** wave: 모델이 붙어 있으면 떼어 냄. 바꿨으면 true */
    public static boolean strip(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return false;
        ItemMeta meta = it.getItemMeta();
        if (!meta.hasItemModel() || meta.getItemModel() == null || !"wave".equals(meta.getItemModel().getNamespace())) return false;
        meta.setItemModel(null);
        it.setItemMeta(meta);
        return true;
    }

    /** 여러 칸 (셜커 상자 안까지) */
    public static void stripAll(ItemStack[] items) {
        if (items == null) return;
        for (ItemStack it : items) {
            if (it == null || it.getType().isAir()) continue;
            strip(it);
            if (it.getItemMeta() instanceof org.bukkit.inventory.meta.BlockStateMeta bsm && bsm.getBlockState() instanceof org.bukkit.block.ShulkerBox box) {
                ItemStack[] in = box.getInventory().getContents();
                boolean changed = false;
                for (ItemStack x : in) changed |= strip(x);
                if (changed) {
                    box.getInventory().setContents(in);
                    bsm.setBlockState(box);
                    it.setItemMeta(bsm);
                }
            }
        }
    }
}
