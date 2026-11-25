package kr.wave.core.module;

import kr.wave.core.WaveCore;
import kr.wave.core.util.ItemModels;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * 리소스팩 제거 뒤 정리: 예전에 특별 아이템에 붙였던 wave: 모델을 떼어 냄
 * 접속 (인벤토리 · 엔더 상자), 창 열기 · 닫기 (상자 · 배낭 · 우편함 등), 줍기 때
 */
public class OldModelCleanupModule extends Module {

    public OldModelCleanupModule(WaveCore plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        later(20, () -> {
            if (!p.isOnline()) return;
            ItemModels.stripAll(p.getInventory().getContents());
            ItemModels.stripAll(p.getEnderChest().getContents());
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        ItemModels.stripAll(e.getInventory().getContents());
        ItemModels.stripAll(e.getPlayer().getInventory().getContents());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        ItemModels.stripAll(e.getPlayer().getInventory().getContents());   // 우편함 · 장터 등에서 받은 아이템
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        var it = e.getItem().getItemStack();
        if (ItemModels.strip(it)) e.getItem().setItemStack(it);
    }
}
