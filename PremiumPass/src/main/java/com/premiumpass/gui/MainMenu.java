package com.premiumpass.gui;

import com.premiumpass.PremiumPass;
import com.premiumpass.data.PlayerData;
import kr.wave.common.Items;
import kr.wave.common.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

public class MainMenu extends Menu {

    public MainMenu(PremiumPass plugin, Player player) {
        super(plugin, player, 27, "&6&l프리미엄 패스");
    }

    @Override
    protected void render() {
        inv.clear();
        PlayerData d = data();
        int claimable = plugin.rewards().claimableCount(player, d);
        boolean premium = plugin.pass().isPremium(player, d);
        int dailyLeft = 0;
        for (String id : d.dailyIds) if (!d.dailyDone.contains(id)) dailyLeft++;
        long seasonLeft = plugin.quests().seasonQuests().stream().filter(q -> !d.seasonDone.contains(q.id)).count();

        inv.setItem(4, infoIcon());
        var rewards = Items.icon(Material.CHEST, "&e&l레벨 보상",
                "&7레벨별 보상을 확인하고 수령합니다.", "",
                claimable > 0 ? "&a수령 가능한 보상: " + claimable + "개" : "&7수령 가능한 보상 없음",
                "", "&e클릭하여 열기");
        if (claimable > 0) Items.glow(rewards);
        inv.setItem(10, rewards);
        inv.setItem(12, Items.icon(Material.CLOCK, "&a&l일일 퀘스트",
                "&7매일 새로운 퀘스트가 배정됩니다.", "",
                "&f남은 퀘스트: &a" + dailyLeft + "개",
                "&f초기화까지: &e" + Text.duration(plugin.pass().untilDailyReset()),
                "", "&e클릭하여 열기"));
        inv.setItem(14, Items.icon(Material.WRITABLE_BOOK, "&b&l시즌 퀘스트",
                "&7시즌 동안 도전하는 큰 퀘스트입니다.", "",
                "&f남은 퀘스트: &b" + seasonLeft + "개",
                "", "&e클릭하여 열기"));
        inv.setItem(16, Items.icon(Material.EMERALD, "&6&l교환 상점",
                "&7프리미엄 패스 포인트로", "&7아이템을 교환합니다.", "",
                "&f보유 포인트: &6" + Text.num(d.points),
                "", "&e클릭하여 열기"));
        if (levelShop()) {
            inv.setItem(24, Items.icon(Material.EXPERIENCE_BOTTLE, "&a&l레벨 구매",
                    "&7유료 캐시로 패스 레벨을 바로 올립니다.", "", "&f현재 레벨 : &e" + d.level + " &7/ " + plugin.pass().maxLevel(), "", "&e클릭하여 열기"));
        }
        if (premium) {
            inv.setItem(22, Items.glow(Items.icon(Material.NETHER_STAR, "&6&l프리미엄 패스 보유 중",
                    "&7" + plugin.pass().seasonName() + " 프리미엄 패스가 활성화되어 있습니다.",
                    "&7퀘스트 포인트 보상 &6+" + plugin.pass().questPointBonus() + "%")));
        } else {
            java.util.List<String> lore = new java.util.ArrayList<>(java.util.List.of(
                    "&7프리미엄 패스를 활성화하면",
                    "&7- 모든 레벨의 &6프리미엄 보상&7 수령 (소급 가능)",
                    "&7- 퀘스트 포인트 보상 &6+" + plugin.pass().questPointBonus() + "%",
                    "&7- 프리미엄 전용 상점 아이템", ""));
            lore.addAll(plugin.getConfig().getStringList("premium.purchase-info"));
            inv.setItem(22, Items.icon(Material.GOLD_INGOT, "&6&l프리미엄 패스 안내", lore));
        }
        fillEmpty();
    }

    /** WaveCore 의 레벨 구매(유료 캐시)가 있을 때만 버튼 표시 */
    private static boolean levelShop() {
        return org.bukkit.Bukkit.getCommandMap().getCommand("패스레벨구매") != null;
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        switch (e.getSlot()) {
            case 10 -> {
                click();
                new LevelMenu(plugin, player, LevelMenu.pageOf(data().level)).open();
            }
            case 12 -> {
                click();
                new QuestMenu(plugin, player, true).open();
            }
            case 14 -> {
                click();
                new QuestMenu(plugin, player, false).open();
            }
            case 24 -> {
                if (levelShop()) {
                    click();
                    player.closeInventory();
                    player.performCommand("패스레벨구매");
                }
            }
            case 16 -> {
                click();
                new ShopMenu(plugin, player, 0).open();
            }
            default -> {
            }
        }
    }
}
