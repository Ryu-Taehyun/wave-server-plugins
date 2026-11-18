package com.premiumpass.quest;

import com.premiumpass.PremiumPass;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumMap;
import java.util.Map;

/**
 * 플레이어가 직접 설치한 블록을 청크 데이터에 기록해서,
 * 같은 광석을 설치/파괴 반복하는 퀘스트 악용을 막는다.
 * BREAK_BLOCK 퀘스트 대상 블록만 기록하므로 데이터가 크게 늘지 않는다.
 */
public class PlacedBlockTracker {

    private final NamespacedKey key;
    private final PremiumPass plugin;
    private final Map<Material, Boolean> trackCache = new EnumMap<>(Material.class);

    public PlacedBlockTracker(PremiumPass plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "placed");
    }

    public void clearCache() {
        trackCache.clear();
    }

    private boolean shouldTrack(Material m) {
        return trackCache.computeIfAbsent(m, mat -> plugin.quests().isBreakTarget(mat));
    }

    private static int pack(Block b) {
        return ((b.getY() + 2048) << 8) | ((b.getZ() & 15) << 4) | (b.getX() & 15);
    }

    public void markPlaced(Block b) {
        if (!shouldTrack(b.getType())) return;
        PersistentDataContainer pdc = b.getChunk().getPersistentDataContainer();
        int[] arr = pdc.getOrDefault(key, PersistentDataType.INTEGER_ARRAY, new int[0]);
        int pos = pack(b);
        for (int v : arr) if (v == pos) return;
        int[] next = new int[arr.length + 1];
        System.arraycopy(arr, 0, next, 0, arr.length);
        next[arr.length] = pos;
        pdc.set(key, PersistentDataType.INTEGER_ARRAY, next);
    }

    /** 설치된 블록이면 기록을 지우고 true */
    public boolean consumePlaced(Block b) {
        if (!shouldTrack(b.getType())) return false;
        PersistentDataContainer pdc = b.getChunk().getPersistentDataContainer();
        int[] arr = pdc.get(key, PersistentDataType.INTEGER_ARRAY);
        if (arr == null) return false;
        int pos = pack(b);
        int idx = -1;
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == pos) {
                idx = i;
                break;
            }
        }
        if (idx < 0) return false;
        if (arr.length == 1) {
            pdc.remove(key);
        } else {
            int[] next = new int[arr.length - 1];
            System.arraycopy(arr, 0, next, 0, idx);
            System.arraycopy(arr, idx + 1, next, idx, arr.length - idx - 1);
            pdc.set(key, PersistentDataType.INTEGER_ARRAY, next);
        }
        return true;
    }
}
