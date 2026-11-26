package kr.wave.core.module;

import kr.wave.core.Items;
import kr.wave.core.WaveCore;
import kr.wave.core.data.GlobalData;
import kr.wave.core.data.PlayerData;
import kr.wave.core.util.ItemModels;
import kr.wave.core.util.Msg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 아이템 정리
 * - 예전 리소스팩 모델(wave:...) 떼어 내기
 * - 서버에서 없앤 아이템(사신의 영혼 · 사신의 심장) 삭제: 인벤토리 · 엔더 상자 · 배낭 · 우편함 · 사망 보관 · 시즌 유지 사본,
 *   열어 본 상자, 주운 아이템, 팀 창고 · 장터 매물 (셜커 상자 안까지). 지우면 본인에게 알림
 */
public class ItemCleanupModule extends Module {

    /** 서버에서 없앤 아이템 (이름 정확히 일치) */
    private static final List<String> REMOVED = List.of(Items.C_REAPER_SOUL, Items.C_REAPER_HEART);

    public ItemCleanupModule(WaveCore plugin) {
        super(plugin);
    }

    @Override
    public void enable() {
        later(20, this::purgeGlobal);
    }

    public static boolean removed(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return false;
        String n = Msg.name(it);
        return n != null && REMOVED.contains(n);
    }

    /** 배열에서 없앤 아이템을 지우고 (셜커 상자 안까지) 이름별 개수를 더함 */
    static void purge(ItemStack[] items, Map<String, Integer> out) {
        if (items == null) return;
        for (int i = 0; i < items.length; i++) {
            ItemStack it = items[i];
            if (it == null || it.getType().isAir()) continue;
            if (removed(it)) {
                out.merge(Msg.name(it), it.getAmount(), Integer::sum);
                items[i] = null;
                continue;
            }
            if (it.getItemMeta() instanceof org.bukkit.inventory.meta.BlockStateMeta bsm && bsm.getBlockState() instanceof org.bukkit.block.ShulkerBox box) {
                ItemStack[] in = box.getInventory().getContents();
                int before = out.values().stream().mapToInt(Integer::intValue).sum();
                purge(in, out);
                if (out.values().stream().mapToInt(Integer::intValue).sum() != before) {
                    box.getInventory().setContents(in);
                    bsm.setBlockState(box);
                    it.setItemMeta(bsm);
                }
            }
        }
    }

    private static void purge(List<ItemStack> list, Map<String, Integer> out) {
        ItemStack[] arr = list.toArray(new ItemStack[0]);
        purge(arr, out);
        list.clear();
        for (ItemStack it : arr) if (it != null) list.add(it);
    }

    /** 이 플레이어의 모든 보관 장소 정리 */
    public void purgePlayer(Player p) {
        Map<String, Integer> out = new LinkedHashMap<>();
        ItemStack[] inv = p.getInventory().getContents();
        purge(inv, out);
        if (!out.isEmpty()) p.getInventory().setContents(inv);
        int n0 = out.size();
        ItemStack[] ec = p.getEnderChest().getContents();
        purge(ec, out);
        if (out.size() != n0 || !out.isEmpty()) p.getEnderChest().setContents(ec);
        PlayerData d = data(p);
        purge(d.backpack, out);
        purge(d.mailbox, out);
        purge(d.keepItems, out);
        purge(d.seasonReturn, out);
        purge(d.respawnItems, out);
        if (removed(d.savedItem)) {
            out.merge(Msg.name(d.savedItem), d.savedItem.getAmount(), Integer::sum);
            d.savedItem = null;
        }
        report(p, out);
        if (!out.isEmpty()) plugin.store().save(d);
    }

    private void report(Player p, Map<String, Integer> out) {
        if (out.isEmpty()) return;
        StringBuilder b = new StringBuilder();
        out.forEach((k, v) -> b.append(b.length() > 0 ? ", " : "").append(k).append(" x").append(v));
        Msg.send(p, "&c&l[!] &7서버에서 없어진 아이템이 정리되었습니다: &f" + b);
        plugin.getLogger().info("[아이템 정리] " + p.getName() + ": " + b);
    }

    /** 팀 창고 · 장터 매물 */
    public void purgeGlobal() {
        Map<String, Integer> out = new LinkedHashMap<>();
        GlobalData g = global();
        for (GlobalData.Team t : g.teams.values()) purge(t.storage, out);
        for (Iterator<GlobalData.Listing> it = g.market.iterator(); it.hasNext(); ) {
            GlobalData.Listing l = it.next();
            if (removed(l.item)) {
                out.merge(Msg.name(l.item), l.item.getAmount(), Integer::sum);
                it.remove();
            }
        }
        if (out.isEmpty()) return;
        plugin.store().saveGlobal();
        plugin.getLogger().info("[아이템 정리] 팀 창고 · 장터: " + out);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        later(20, () -> {
            if (!p.isOnline()) return;
            ItemModels.stripAll(p.getInventory().getContents());
            ItemModels.stripAll(p.getEnderChest().getContents());
            purgePlayer(p);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        ItemModels.stripAll(e.getInventory().getContents());
        ItemModels.stripAll(e.getPlayer().getInventory().getContents());
        Map<String, Integer> out = new LinkedHashMap<>();
        if (!(e.getInventory().getHolder() instanceof kr.wave.core.util.Gui)) {   // 상자 · 셜커 등 (플러그인 창은 데이터에서 정리)
            ItemStack[] top = e.getInventory().getContents();
            purge(top, out);
            if (!out.isEmpty()) e.getInventory().setContents(top);
        }
        if (e.getPlayer() instanceof Player p) report(p, out);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        ItemModels.stripAll(e.getPlayer().getInventory().getContents());   // 우편함 · 장터 등에서 받은 아이템
        if (e.getPlayer() instanceof Player p) {
            Map<String, Integer> out = new LinkedHashMap<>();
            ItemStack[] inv = p.getInventory().getContents();
            purge(inv, out);
            if (!out.isEmpty()) p.getInventory().setContents(inv);
            report(p, out);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        var it = e.getItem().getItemStack();
        if (removed(it)) {
            e.setCancelled(true);
            e.getItem().remove();
            return;
        }
        if (ItemModels.strip(it)) e.getItem().setItemStack(it);
    }
}
