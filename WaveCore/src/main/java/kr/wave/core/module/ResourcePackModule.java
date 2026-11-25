package kr.wave.core.module;

import kr.wave.core.WaveCore;
import kr.wave.core.util.Gui;
import kr.wave.core.util.ItemModels;
import kr.wave.core.util.Msg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.HexFormat;

/**
 * 리소스팩 (Wave 특별 아이템 텍스쳐)
 * - 이미 가지고 있는 특별 아이템에도 텍스쳐 연결: 접속 · 상자 열기 · 줍기 때
 * - config resource-pack.url 이 있으면 접속할 때 리소스팩을 보냄 (server.properties 로 보내도 됨)
 */
public class ResourcePackModule extends Module {

    public ResourcePackModule(WaveCore plugin) {
        super(plugin);
    }

    @Override
    public void enable() {
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        later(20, () -> {
            if (!p.isOnline()) return;
            ItemModels.applyAll(p.getInventory().getContents());
            ItemModels.applyAll(p.getEnderChest().getContents());
            sendPack(p);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (e.getInventory().getHolder() instanceof Gui) return;   // 플러그인 창은 만들 때 이미 적용
        ItemModels.applyAll(e.getInventory().getContents());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        var it = e.getItem().getItemStack();
        if (ItemModels.apply(it)) e.getItem().setItemStack(it);
    }

    private void sendPack(Player p) {
        String url = plugin.getConfig().getString("resource-pack.url", "");
        if (url == null || url.isBlank()) return;
        String sha1 = plugin.getConfig().getString("resource-pack.sha1", "");
        byte[] hash = null;
        try {
            if (sha1 != null && sha1.length() == 40) hash = HexFormat.of().parseHex(sha1);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("resource-pack.sha1 형식이 올바르지 않습니다 (40자리 16진수).");
        }
        boolean required = plugin.getConfig().getBoolean("resource-pack.required", false);
        p.setResourcePack(url, hash, Msg.c(plugin.getConfig().getString("resource-pack.prompt", "&f특별 아이템 텍스쳐 리소스팩을 받으시겠습니까?")), required);
    }
}
