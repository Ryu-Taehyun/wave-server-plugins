package com.premiumpass.gui;

import com.premiumpass.PremiumPass;
import com.premiumpass.data.PlayerData;
import com.premiumpass.reward.LevelReward;
import com.premiumpass.reward.RewardManager;
import com.premiumpass.reward.Track;
import kr.wave.common.Items;
import kr.wave.common.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 레벨 보상 화면 (페이지당 8레벨)
 *
 * 0행: 정보
 * 1행: [레벨]      레벨 표시 x8
 * 2행: [무료]      무료 보상 x8
 * 3행: [프리미엄]  프리미엄 보상 x8
 * 5행: 이전 / 뒤로 / 정보 / 모두 수령 / 다음
 */
public class LevelMenu extends Menu {

    public static final int PER_PAGE = 8;
    private static final int ROW_LEVEL = 9;
    private static final int ROW_FREE = 18;
    private static final int ROW_PREMIUM = 27;

    private int page;

    public LevelMenu(PremiumPass plugin, Player player, int page) {
        super(plugin, player, 54, "&6&l프리미엄 패스 &8- 레벨 보상");
        this.page = page;
    }

    /** 플레이어의 현재 레벨이 보이는 페이지 */
    public static int pageOf(int level) {
        return Math.max(0, (level - 1) / PER_PAGE);
    }

    private int pages() {
        return Math.max(1, (plugin.pass().maxLevel() + PER_PAGE - 1) / PER_PAGE);
    }

    @Override
    protected void render() {
        inv.clear();
        PlayerData d = data();
        boolean premium = plugin.pass().isPremium(player, d);
        int max = plugin.pass().maxLevel();
        page = Math.max(0, Math.min(page, pages() - 1));

        inv.setItem(4, premiumStatus(premium));
        inv.setItem(ROW_LEVEL, Items.icon(Material.EXPERIENCE_BOTTLE, "&e&l레벨", "&7현재 레벨: &e" + d.level));
        inv.setItem(ROW_FREE, Items.icon(Material.IRON_INGOT, "&f&l무료 트랙", "&7모든 플레이어가 받을 수 있습니다."));
        inv.setItem(ROW_PREMIUM, premium
                ? Items.glow(Items.icon(Material.GOLD_INGOT, "&6&l프리미엄 트랙", "&a✔ 활성화됨"))
                : Items.icon(Material.GOLD_INGOT, "&6&l프리미엄 트랙", "&c✘ 프리미엄 패스가 필요합니다"));

        for (int i = 0; i < PER_PAGE; i++) {
            int level = page * PER_PAGE + i + 1;
            if (level > max) break;
            inv.setItem(ROW_LEVEL + 1 + i, levelMarker(d, level));
            inv.setItem(ROW_FREE + 1 + i, rewardIcon(d, Track.FREE, level, true));
            inv.setItem(ROW_PREMIUM + 1 + i, rewardIcon(d, Track.PREMIUM, level, premium));
        }

        if (page > 0) inv.setItem(45, Items.icon(Material.ARROW, "&e◀ 이전 페이지", "&7" + page + " / " + pages()));
        inv.setItem(47, Items.icon(Material.COMPASS, "&b현재 레벨로 이동", "&7레벨 " + d.level + " 이(가) 있는 페이지로 이동"));
        inv.setItem(48, Items.icon(Material.OAK_DOOR, "&7뒤로 가기"));
        inv.setItem(49, infoIcon());
        inv.setItem(50, Items.icon(Material.HOPPER, "&a&l모두 수령",
                "&7수령 가능한 보상을 한 번에 받습니다.",
                "&f수령 가능: &a" + plugin.rewards().claimableCount(player, d) + "개"));
        if (page < pages() - 1) inv.setItem(53, Items.icon(Material.ARROW, "&e다음 페이지 ▶", "&7" + (page + 2) + " / " + pages()));
        fillEmpty();
    }

    private ItemStack premiumStatus(boolean premium) {
        if (premium) {
            return Items.glow(Items.icon(Material.NETHER_STAR, "&6&l프리미엄 패스 보유 중",
                    "&7모든 프리미엄 보상을 받을 수 있습니다.",
                    "&7퀘스트 포인트 보상 &6+" + plugin.pass().questPointBonus() + "%"));
        }
        List<String> lore = new ArrayList<>();
        lore.add("&7프리미엄 패스를 활성화하면");
        lore.add("&7- 모든 레벨의 &6프리미엄 보상&7 수령");
        lore.add("&7- 이미 지나간 레벨의 보상도 소급 수령");
        lore.add("&7- 퀘스트 포인트 보상 &6+" + plugin.pass().questPointBonus() + "%");
        lore.add("&7- 프리미엄 전용 상점 아이템");
        lore.add("");
        lore.addAll(plugin.getConfig().getStringList("premium.purchase-info"));
        return Items.icon(Material.GRAY_DYE, "&7프리미엄 패스 미보유", lore);
    }

    private ItemStack levelMarker(PlayerData d, int level) {
        boolean reached = d.level >= level;
        boolean current = d.level == level;
        Material m = current ? Material.YELLOW_DYE : reached ? Material.LIME_DYE : Material.GRAY_DYE;   // 유리판은 배경(검정)에만
        List<String> lore = new ArrayList<>();
        if (current && level < plugin.pass().maxLevel()) {
            long req = plugin.pass().required(level);
            lore.add("&f다음 레벨까지: &b" + Text.num(d.xp) + " &7/ " + Text.num(req));
            lore.add(Text.bar(d.xp, req, 15));
        } else {
            lore.add(reached ? "&a달성" : "&7미달성");
        }
        String name = (level % 10 == 0 ? "&6&l★ " : current ? "&e&l" : reached ? "&a" : "&7") + "레벨 " + level;
        ItemStack it = Items.icon(m, name, lore);
        it.setAmount(Math.min(64, Math.max(1, level)));
        return it;
    }

    private ItemStack rewardIcon(PlayerData d, Track track, int level, boolean unlocked) {
        LevelReward r = plugin.rewards().get(track, level);
        boolean empty = RewardManager.isEmpty(r);
        boolean claimed = d.claimed(track).contains(level);
        boolean reached = d.level >= level;
        boolean milestone = level % 10 == 0 || level == plugin.pass().maxLevel();
        String trackName = track == Track.PREMIUM ? "&6프리미엄" : "&f무료";

        List<String> lore = new ArrayList<>();
        lore.add("&7" + Text.color(trackName) + " &7보상");
        lore.addAll(plugin.rewards().describe(r));
        lore.add("");

        Material mat;
        boolean glow = false;
        if (empty) {
            return Items.icon(Material.BLACK_STAINED_GLASS_PANE, "&8레벨 " + level + " - 보상 없음");
        } else if (claimed) {
            mat = Material.LIME_DYE;
            lore.add("&a✔ 수령 완료");
        } else if (!unlocked) {
            mat = firstMaterial(r);
            lore.add("&6🔒 프리미엄 패스 필요");
            if (reached) lore.add("&7(활성화하면 바로 받을 수 있습니다)");
        } else if (reached) {
            mat = firstMaterial(r);
            glow = true;
            lore.add("&e▶ 클릭하여 수령!");
        } else {
            mat = milestone ? firstMaterial(r) : (track == Track.PREMIUM ? Material.ORANGE_DYE : Material.RED_DYE);
            lore.add("&c✘ 잠김 &7(레벨 " + level + " 필요)");
        }

        String name = (milestone ? "&6&l★ " : "&e") + "레벨 " + level + " " + trackName;
        ItemStack icon = Items.icon(mat, name, lore);
        if (glow) Items.glow(icon);
        return icon;
    }

    private static Material firstMaterial(LevelReward r) {
        return r.items.isEmpty() ? Material.CHEST : r.items.get(0).getType();
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        int slot = e.getSlot();
        PlayerData d = data();

        Track track = null;
        int col = slot % 9;
        if (col >= 1) {
            if (slot / 9 == ROW_FREE / 9) track = Track.FREE;
            else if (slot / 9 == ROW_PREMIUM / 9) track = Track.PREMIUM;
        }
        if (track != null) {
            int level = page * PER_PAGE + col;
            if (level > plugin.pass().maxLevel()) return;
            if (plugin.rewards().claim(player, d, track, level, false)) {
                plugin.data().save(d);
                render();
            } else if (track == Track.PREMIUM && d.level >= level && !plugin.pass().isPremium(player, d)
                    && !RewardManager.isEmpty(plugin.rewards().get(track, level))) {
                Text.send(player, "&6프리미엄 패스&c가 있어야 받을 수 있는 보상입니다.");
                for (String line : plugin.getConfig().getStringList("premium.purchase-info")) player.sendMessage(Text.color(line));
            }
            return;
        }

        switch (slot) {
            case 45 -> {
                if (page > 0) {
                    page--;
                    click();
                    render();
                }
            }
            case 53 -> {
                if (page < pages() - 1) {
                    page++;
                    click();
                    render();
                }
            }
            case 47 -> {
                page = pageOf(d.level);
                click();
                render();
            }
            case 48 -> {
                click();
                new MainMenu(plugin, player).open();
            }
            case 50 -> {
                if (plugin.rewards().claimAll(player, d) > 0) {
                    plugin.data().save(d);
                    render();
                }
            }
            default -> {
            }
        }
    }
}
