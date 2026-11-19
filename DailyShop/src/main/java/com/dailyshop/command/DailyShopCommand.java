package com.dailyshop.command;

import com.dailyshop.DailyShop;
import com.dailyshop.ShopManager;
import com.dailyshop.gui.ShopMenu;
import kr.wave.common.Items;
import kr.wave.common.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

/** /dailyshop (/일일상점) 과 /dailyshop admin ... */
public class DailyShopCommand implements TabExecutor {

    private final DailyShop plugin;

    public DailyShopCommand(DailyShop plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command command, String label, String[] a) {
        if (a.length == 0) {
            if (!(s instanceof Player p)) {
                s.sendMessage("게임 안에서만 사용할 수 있습니다. 관리: /dailyshop admin");
                return true;
            }
            new ShopMenu(plugin, p).open();
            return true;
        }
        if (!a[0].equalsIgnoreCase("admin") || !s.hasPermission("dailyshop.admin")) {
            Text.send(s, "&7사용법: /일일상점");
            return true;
        }
        try {
            admin(s, a);
        } catch (NumberFormatException e) {
            Text.send(s, "&c숫자를 올바르게 입력하세요.");
        }
        return true;
    }

    private void admin(CommandSender s, String[] a) {
        ShopManager shop = plugin.shop();
        String sub = a.length >= 2 ? a[1].toLowerCase(Locale.ROOT) : "help";
        switch (sub) {
            case "add" -> {
                if (a.length < 4) {
                    Text.send(s, "&c/dailyshop admin add <id> <가격> [하루한도] [가중치] [premium]");
                    return;
                }
                if (!(s instanceof Player p)) {
                    Text.send(s, "&c게임 안에서 아이템을 손에 들고 사용하세요.");
                    return;
                }
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) {
                    Text.send(s, "&c손에 아이템을 들어주세요.");
                    return;
                }
                long cost = Long.parseLong(a[3]);
                int limit = a.length >= 5 ? Integer.parseInt(a[4]) : 1;
                int weight = a.length >= 6 ? Integer.parseInt(a[5]) : 10;
                boolean premiumOnly = a.length >= 7 && (a[6].equalsIgnoreCase("premium") || a[6].equalsIgnoreCase("true"));
                if (!shop.put(a[2], hand, cost, limit, weight, premiumOnly)) {
                    Text.send(s, "&c저장하지 못했습니다. 서버 콘솔의 오류를 확인하세요.");
                    return;
                }
                Text.send(s, "&a상품 '" + a[2] + "' 등록: " + Items.describe(hand) + " &a" + shop.money(cost) + ", 하루 "
                        + (limit > 0 ? limit + "회" : "무제한") + ", 가중치 " + weight + (premiumOnly ? ", 프리미엄 전용" : ""));
            }
            case "remove" -> {
                if (a.length < 3) {
                    Text.send(s, "&c/dailyshop admin remove <id>");
                    return;
                }
                if (shop.remove(a[2])) Text.send(s, "&a'" + a[2] + "' 삭제 완료.");
                else Text.send(s, "&c그런 상품이 없습니다.");
            }
            case "list" -> {
                Text.send(s, "&e상품 목록 &7(가중치가 클수록 자주 진열)");
                for (ShopManager.Entry e : shop.pool()) {
                    s.sendMessage(Text.color("&7- &f" + e.id + " &7: " + Items.describe(e.item) + " &6" + shop.money(e.cost)
                            + " &7한도 " + (e.limit > 0 ? e.limit : "∞") + " / 가중치 " + e.weight + " " + e.rarity()
                            + (e.premiumOnly ? " &6[프리미엄]" : "")));
                }
                Text.send(s, "&e오늘 진열: &f" + String.join(", ", shop.today().stream().map(e -> e.id).toList()));
            }
            case "reroll" -> {
                shop.reroll();
                Text.send(s, "&a오늘 진열을 다시 뽑았습니다. (구매 기록 초기화)");
            }
            case "reload" -> {
                plugin.reloadAll();
                Text.send(s, plugin.warnings().reloadMessage());
            }
            default -> {
                Text.send(s, "&6일일 상점 관리 명령어");
                for (String l : new String[]{
                        "&e/dailyshop admin add <id> <가격> [하루한도] [가중치] [premium] &7- 손에 든 아이템 등록",
                        "&e/dailyshop admin remove <id> &7- 상품 삭제",
                        "&e/dailyshop admin list &7- 상품 목록과 오늘 진열",
                        "&e/dailyshop admin reroll &7- 오늘 진열 다시 뽑기",
                        "&e/dailyshop admin reload &7- 설정 다시 불러오기"}) {
                    s.sendMessage(Text.color(l));
                }
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command command, String alias, String[] a) {
        if (!s.hasPermission("dailyshop.admin")) return List.of();
        if (a.length == 1) return filter(List.of("admin"), a[0]);
        if (a.length == 2 && a[0].equalsIgnoreCase("admin")) {
            return filter(List.of("add", "remove", "list", "reroll", "reload"), a[1]);
        }
        if (a.length == 3 && a[1].equalsIgnoreCase("remove")) {
            return filter(plugin.shop().pool().stream().map(e -> e.id).toList(), a[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> opts, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return opts.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }
}
