package com.premiumpass.gui;

import com.premiumpass.PremiumPass;
import com.premiumpass.reward.LevelReward;
import com.premiumpass.reward.Track;
import kr.wave.common.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** 관리자용: 아이템을 넣고 닫으면 해당 레벨/트랙 보상으로 저장된다 */
public class RewardEditorMenu extends Menu {

    private final Track track;
    private final int level;

    public RewardEditorMenu(PremiumPass plugin, Player player, Track track, int level) {
        super(plugin, player, 54, "&4보상 편집 &8- 레벨 " + level + " " + track.label);
        this.track = track;
        this.level = level;
    }

    @Override
    public boolean editable() {
        return true;
    }

    @Override
    protected void render() {
        inv.clear();
        LevelReward r = plugin.rewards().get(track, level);
        if (r == null) return;
        for (int i = 0; i < r.items.size() && i < inv.getSize(); i++) inv.setItem(i, r.items.get(i).clone());
    }

    @Override
    public void onClick(InventoryClickEvent e) {
    }

    @Override
    public void onClose(InventoryCloseEvent e) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack it : inv.getContents()) {
            if (it != null && !it.getType().isAir()) items.add(it.clone());
        }
        plugin.rewards().setItems(track, level, items);
        Text.send(player, "&a레벨 &e" + level + " " + track.label + " &a보상이 저장되었습니다. &7(아이템 " + items.size() + "개)");
    }
}
