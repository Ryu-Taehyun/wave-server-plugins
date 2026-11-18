package com.premiumpass.gui;

import com.premiumpass.PremiumPass;
import com.premiumpass.data.PlayerData;
import com.premiumpass.quest.QuestDef;
import com.premiumpass.util.Items;
import com.premiumpass.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** 일일 / 시즌 퀘스트 목록 */
public class QuestMenu extends Menu {

    private static final int[] CENTER_SLOTS = {20, 21, 22, 23, 24, 29, 30, 31, 32, 33, 11, 12, 13, 14, 15};
    private final boolean daily;

    public QuestMenu(PremiumPass plugin, Player player, boolean daily) {
        super(plugin, player, 54, daily ? "&a&l일일 퀘스트" : "&b&l시즌 퀘스트");
        this.daily = daily;
    }

    @Override
    protected void render() {
        inv.clear();
        PlayerData d = data();
        plugin.pass().ensure(player, d);

        List<QuestDef> quests = daily ? plugin.quests().dailyQuestsOf(d) : plugin.quests().seasonQuests();
        // 4번 슬롯은 정보 아이콘, 맨 아래 줄은 버튼이므로 퀘스트는 9~44번 슬롯(최대 36개)에 배치
        boolean centered = daily && quests.size() <= CENTER_SLOTS.length;
        int done = 0;
        for (int i = 0; i < quests.size() && i < 36; i++) {
            QuestDef q = quests.get(i);
            boolean complete = daily ? d.dailyDone.contains(q.id) : d.seasonDone.contains(q.id);
            if (complete) done++;
            long prog = daily ? d.dailyProgress.getOrDefault(q.id, 0L) : d.seasonProgress.getOrDefault(q.id, 0L);
            int slot = centered ? CENTER_SLOTS[i] : 9 + i;
            inv.setItem(slot, questIcon(q, prog, complete));
        }

        List<String> info = new ArrayList<>();
        info.add("&f완료: &a" + done + " &7/ " + quests.size());
        if (daily) info.add("&f초기화까지: &e" + Text.duration(plugin.pass().untilDailyReset()));
        else info.add("&7" + plugin.pass().seasonName() + " 동안 진행됩니다.");
        inv.setItem(4, Items.icon(daily ? Material.CLOCK : Material.WRITABLE_BOOK,
                daily ? "&a&l일일 퀘스트" : "&b&l시즌 퀘스트", info));
        inv.setItem(48, Items.icon(Material.OAK_DOOR, "&7뒤로 가기"));
        inv.setItem(49, infoIcon());
        fillEmpty();
    }

    private ItemStack questIcon(QuestDef q, long prog, boolean complete) {
        List<String> lore = new ArrayList<>();
        if (!q.description.isEmpty()) lore.add("&7" + q.description);
        lore.add("&8종류: " + q.type.label);
        lore.add("");
        lore.add("&f진행: &e" + Text.num(prog) + " &7/ " + Text.num(q.amount));
        lore.add(Text.bar(prog, q.amount, 20));
        lore.add("");
        lore.add("&f보상: &b" + Text.num(q.xp) + " 패스 XP");
        if (q.points > 0) lore.add("&f      &6" + Text.num(q.points) + " 프리미엄 패스 포인트");
        lore.add("");
        lore.add(complete ? "&a✔ 완료" : "&7진행 중... (자동 지급)");
        ItemStack it = Items.icon(complete ? Material.LIME_DYE : q.icon, (complete ? "&a&m" : "") + Text.color(q.name), lore);
        if (complete) Items.glow(it);
        return it;
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        if (e.getSlot() == 48) {
            click();
            new MainMenu(plugin, player).open();
        }
    }
}
