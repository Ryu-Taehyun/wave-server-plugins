package com.premiumpass.command;

import com.premiumpass.PremiumPass;
import com.premiumpass.data.PlayerData;
import com.premiumpass.gui.RewardEditorMenu;
import com.premiumpass.reward.LevelReward;
import com.premiumpass.reward.Track;
import com.premiumpass.shop.ShopManager;
import com.premiumpass.util.Items;
import com.premiumpass.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /passadmin ... */
public class AdminCommand implements TabExecutor {

    private final PremiumPass plugin;

    public AdminCommand(PremiumPass plugin) {
        this.plugin = plugin;
    }

    private void help(CommandSender s) {
        Text.send(s, "&6관리자 명령어");
        String[] lines = {
                "&e/passadmin reward <레벨> <free|premium> &7- 보상 편집 GUI (아이템 넣고 닫으면 저장)",
                "&e/passadmin reward <레벨> <free|premium> addhand &7- 손에 든 아이템을 보상에 추가",
                "&e/passadmin reward <레벨> <free|premium> clear &7- 해당 보상 삭제",
                "&e/passadmin reward <레벨> <free|premium> view &7- 보상 목록 보기",
                "&e/passadmin premium <플레이어> <on|off> &7- 프리미엄 패스 활성화/해제",
                "&e/passadmin voucher <플레이어> [개수] &7- 프리미엄 패스권 아이템 지급",
                "&e/passadmin shop add <id> <가격> [시즌제한] [premium] &7- 손에 든 아이템을 상점에 등록",
                "&e/passadmin shop premium <id> <true|false> &7- 프리미엄 전용 여부 변경",
                "&e/passadmin shop remove <id> &7- 상점 아이템 삭제",
                "&e/passadmin shop price <id> <가격> [시즌제한] &7- 가격/제한 변경",
                "&e/passadmin shop list &7- 상점 목록",
                "&e/passadmin xp <플레이어> <양> &7- 패스 XP 지급",
                "&e/passadmin points <플레이어> <give|take|set> <양> &7- 포인트 관리",
                "&e/passadmin level <플레이어> <레벨> &7- 레벨 설정",
                "&e/passadmin info <플레이어> &7- 플레이어 패스 정보",
                "&e/passadmin dailyreset <플레이어> &7- 일일 퀘스트 다시 배정",
                "&e/passadmin season next [시즌 이름] &7- 다음 시즌 시작 (전원 초기화)",
                "&e/passadmin reload &7- 설정 다시 불러오기"};
        for (String l : lines) s.sendMessage(Text.color(l));
    }

    @Override
    public boolean onCommand(CommandSender s, Command command, String label, String[] a) {
        if (a.length == 0 || a[0].equalsIgnoreCase("help")) {
            help(s);
            return true;
        }
        try {
            switch (a[0].toLowerCase(Locale.ROOT)) {
                case "reward" -> reward(s, a);
                case "premium" -> premium(s, a);
                case "voucher" -> voucher(s, a);
                case "shop" -> shop(s, a);
                case "xp" -> xp(s, a);
                case "points" -> points(s, a);
                case "level" -> level(s, a);
                case "info" -> info(s, a);
                case "dailyreset" -> dailyReset(s, a);
                case "season" -> season(s, a);
                case "reload" -> {
                    plugin.reloadAll();
                    Text.send(s, "&a설정을 다시 불러왔습니다.");
                }
                default -> help(s);
            }
        } catch (NumberFormatException e) {
            Text.send(s, "&c숫자를 올바르게 입력하세요.");
        }
        return true;
    }

    /* ---------------- 보상 ---------------- */

    private void reward(CommandSender s, String[] a) {
        String usage = "&c/passadmin reward <레벨> <free|premium> [addhand|clear|view]";
        if (a.length < 3) {
            Text.send(s, usage);
            return;
        }
        int level = Integer.parseInt(a[1]);
        if (level < 1 || level > plugin.pass().maxLevel()) {
            Text.send(s, "&c레벨은 1 ~ " + plugin.pass().maxLevel() + " 사이여야 합니다.");
            return;
        }
        Track track = Track.parse(a[2]);
        if (track == null) {
            Text.send(s, usage);
            return;
        }
        String action = a.length >= 4 ? a[3].toLowerCase(Locale.ROOT) : "edit";
        String label = "레벨 " + level + " " + track.label;
        switch (action) {
            case "edit" -> {
                if (!(s instanceof Player p)) {
                    Text.send(s, "&c게임 안에서만 사용할 수 있습니다.");
                    return;
                }
                new RewardEditorMenu(plugin, p, track, level).open();
            }
            case "addhand" -> {
                ItemStack hand = handItem(s);
                if (hand == null) return;
                LevelReward r = plugin.rewards().getOrCreate(track, level);
                List<ItemStack> items = new ArrayList<>(r.items);
                items.add(hand);
                plugin.rewards().setItems(track, level, items);
                Text.send(s, "&a" + label + " 보상에 " + Items.describe(hand) + " &a추가 완료.");
            }
            case "clear" -> {
                plugin.rewards().clear(track, level);
                Text.send(s, "&a" + label + " 보상을 삭제했습니다.");
            }
            case "view" -> {
                Text.send(s, "&e" + label + " 보상:");
                for (String line : plugin.rewards().describe(plugin.rewards().get(track, level))) s.sendMessage(Text.color(line));
            }
            default -> Text.send(s, usage);
        }
    }

    /* ---------------- 프리미엄 ---------------- */

    private void premium(CommandSender s, String[] a) {
        if (a.length < 3) {
            Text.send(s, "&c/passadmin premium <플레이어> <on|off>");
            return;
        }
        OfflinePlayer t = target(s, a[1]);
        if (t == null) return;
        PlayerData d = load(t);
        if (a[2].equalsIgnoreCase("on")) {
            if (plugin.pass().activatePremium(t.getPlayer(), d)) {
                Text.send(s, "&a" + a[1] + "의 프리미엄 패스를 활성화했습니다. (" + plugin.pass().seasonName() + ")");
            } else {
                Text.send(s, "&e" + a[1] + "은(는) 이미 프리미엄 패스를 보유하고 있습니다.");
            }
        } else if (a[2].equalsIgnoreCase("off")) {
            plugin.pass().deactivatePremium(d);
            Text.send(s, "&a" + a[1] + "의 프리미엄 패스를 해제했습니다.");
        } else {
            Text.send(s, "&c/passadmin premium <플레이어> <on|off>");
            return;
        }
        finish(t, d);
    }

    private void voucher(CommandSender s, String[] a) {
        if (a.length < 2) {
            Text.send(s, "&c/passadmin voucher <플레이어> [개수]");
            return;
        }
        Player t = Bukkit.getPlayerExact(a[1]);
        if (t == null) {
            Text.send(s, "&c접속 중인 플레이어에게만 지급할 수 있습니다.");
            return;
        }
        int amount = a.length >= 3 ? Integer.parseInt(a[2]) : 1;
        Items.give(t, List.of(plugin.voucher().create(amount)));
        Text.send(t, "&6프리미엄 패스권 &ex" + amount + "&f을(를) 받았습니다! &7(우클릭으로 사용)");
        Text.send(s, "&a" + t.getName() + "에게 프리미엄 패스권 " + amount + "개를 지급했습니다.");
    }

    /* ---------------- 상점 ---------------- */

    private void shop(CommandSender s, String[] a) {
        String action = a.length >= 2 ? a[1].toLowerCase(Locale.ROOT) : "list";
        ShopManager shop = plugin.shop();
        switch (action) {
            case "add" -> {
                if (a.length < 4) {
                    Text.send(s, "&c/passadmin shop add <id> <가격> [시즌제한]");
                    return;
                }
                ItemStack hand = handItem(s);
                if (hand == null) return;
                long cost = Long.parseLong(a[3]);
                int limit = a.length >= 5 ? Integer.parseInt(a[4]) : 0;
                boolean premiumOnly = a.length >= 6 && (a[5].equalsIgnoreCase("premium") || a[5].equalsIgnoreCase("true"));
                shop.add(a[2], hand, cost, limit, premiumOnly);
                shop.save();
                Text.send(s, "&a상점에 '" + a[2] + "' (" + Items.describe(hand) + "&a) 등록: " + cost + " 포인트"
                        + (limit > 0 ? ", 시즌 " + limit + "회 제한" : "") + (premiumOnly ? ", 프리미엄 전용" : ""));
            }
            case "remove" -> {
                if (a.length < 3) {
                    Text.send(s, "&c/passadmin shop remove <id>");
                    return;
                }
                if (shop.remove(a[2])) {
                    shop.save();
                    Text.send(s, "&a'" + a[2] + "' 삭제 완료.");
                } else Text.send(s, "&c그런 상점 아이템이 없습니다.");
            }
            case "premium" -> {
                if (a.length < 4) {
                    Text.send(s, "&c/passadmin shop premium <id> <true|false>");
                    return;
                }
                ShopManager.Entry e = shop.get(a[2]);
                if (e == null) {
                    Text.send(s, "&c그런 상점 아이템이 없습니다.");
                    return;
                }
                e.premiumOnly = Boolean.parseBoolean(a[3]);
                shop.save();
                Text.send(s, "&a'" + e.id + "' 프리미엄 전용: " + e.premiumOnly);
            }
            case "price" -> {
                if (a.length < 4) {
                    Text.send(s, "&c/passadmin shop price <id> <가격> [시즌제한]");
                    return;
                }
                ShopManager.Entry e = shop.get(a[2]);
                if (e == null) {
                    Text.send(s, "&c그런 상점 아이템이 없습니다.");
                    return;
                }
                e.cost = Long.parseLong(a[3]);
                if (a.length >= 5) e.limit = Integer.parseInt(a[4]);
                shop.save();
                Text.send(s, "&a'" + e.id + "' 가격: " + e.cost + ", 제한: " + (e.limit > 0 ? e.limit : "무제한"));
            }
            default -> {
                Text.send(s, "&e교환 상점 목록:");
                for (ShopManager.Entry e : shop.list()) {
                    s.sendMessage(Text.color("&7- &f" + e.id + " &7: " + Items.describe(e.item) + " &6" + e.cost + "pt"
                            + (e.limit > 0 ? " &7(제한 " + e.limit + ")" : "") + (e.premiumOnly ? " &6[프리미엄]" : "")));
                }
            }
        }
    }

    /* ---------------- 플레이어 ---------------- */

    private void xp(CommandSender s, String[] a) {
        if (a.length < 3) {
            Text.send(s, "&c/passadmin xp <플레이어> <양>");
            return;
        }
        OfflinePlayer t = target(s, a[1]);
        if (t == null) return;
        long amount = Long.parseLong(a[2]);
        PlayerData d = load(t);
        plugin.pass().addXp(t.getPlayer(), d, amount);
        finish(t, d);
        Text.send(s, "&a" + a[1] + "에게 패스 XP " + Text.num(amount) + " 지급. (레벨 " + d.level + ")");
    }

    private void points(CommandSender s, String[] a) {
        if (a.length < 4) {
            Text.send(s, "&c/passadmin points <플레이어> <give|take|set> <양>");
            return;
        }
        OfflinePlayer t = target(s, a[1]);
        if (t == null) return;
        long amount = Long.parseLong(a[3]);
        PlayerData d = load(t);
        switch (a[2].toLowerCase(Locale.ROOT)) {
            case "give" -> plugin.pass().addPoints(t.getPlayer(), d, amount, "관리자 지급");
            case "take" -> d.points = Math.max(0, d.points - amount);
            case "set" -> d.points = Math.max(0, amount);
            default -> {
                Text.send(s, "&cgive / take / set 중 하나를 입력하세요.");
                return;
            }
        }
        finish(t, d);
        Text.send(s, "&a" + a[1] + "의 포인트: " + Text.num(d.points));
    }

    private void level(CommandSender s, String[] a) {
        if (a.length < 3) {
            Text.send(s, "&c/passadmin level <플레이어> <레벨>");
            return;
        }
        OfflinePlayer t = target(s, a[1]);
        if (t == null) return;
        PlayerData d = load(t);
        plugin.pass().setLevel(d, Integer.parseInt(a[2]));
        finish(t, d);
        Text.send(s, "&a" + a[1] + "의 패스 레벨을 " + d.level + "(으)로 설정했습니다.");
    }

    private void info(CommandSender s, String[] a) {
        if (a.length < 2) {
            Text.send(s, "&c/passadmin info <플레이어>");
            return;
        }
        OfflinePlayer t = target(s, a[1]);
        if (t == null) return;
        PlayerData d = load(t);
        Text.send(s, "&e" + a[1] + " &f패스 정보 " + (plugin.pass().isPremium(t.getPlayer(), d) ? "&6[프리미엄]" : "&7[무료]"));
        s.sendMessage(Text.color("&7 레벨 " + d.level + " / XP " + d.xp + " / 잔여 초과XP " + d.overflowXp));
        s.sendMessage(Text.color("&7 포인트 " + d.points + " (시즌 획득 " + d.seasonPointsEarned + ")"));
        s.sendMessage(Text.color("&7 수령한 보상 무료 " + d.claimedFree.size() + "개, 프리미엄 " + d.claimedPremium.size() + "개 / 일일 완료 " + d.dailyDone.size() + "/" + d.dailyIds.size()
                + " / 시즌 퀘스트 완료 " + d.seasonDone.size()));
        if (!t.isOnline()) plugin.data().saveAndRelease(d.uuid);
    }

    private void dailyReset(CommandSender s, String[] a) {
        if (a.length < 2) {
            Text.send(s, "&c/passadmin dailyreset <플레이어>");
            return;
        }
        OfflinePlayer t = target(s, a[1]);
        if (t == null) return;
        PlayerData d = load(t);
        plugin.quests().assignDaily(d, plugin.pass().todayKey());
        finish(t, d);
        Text.send(s, "&a" + a[1] + "의 일일 퀘스트를 다시 배정했습니다.");
    }

    private void season(CommandSender s, String[] a) {
        if (a.length < 2 || !a[1].equalsIgnoreCase("next")) {
            Text.send(s, "&c/passadmin season next [시즌 이름]  &7(현재: " + plugin.pass().seasonName() + ")");
            return;
        }
        String name = a.length >= 3 ? String.join(" ", java.util.Arrays.copyOfRange(a, 2, a.length)) : null;
        plugin.pass().nextSeason(name);
        Bukkit.broadcastMessage(Text.color("&6[프리미엄 패스] &e새로운 시즌 &6" + plugin.pass().seasonName() + "&e이(가) 시작되었습니다!"));
    }

    /* ---------------- 도우미 ---------------- */

    private ItemStack handItem(CommandSender s) {
        if (!(s instanceof Player p)) {
            Text.send(s, "&c게임 안에서만 사용할 수 있습니다.");
            return null;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            Text.send(s, "&c손에 아이템을 들어주세요.");
            return null;
        }
        return hand.clone();
    }

    private OfflinePlayer target(CommandSender s, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        OfflinePlayer off = Bukkit.getOfflinePlayerIfCached(name);
        if (off == null || !off.hasPlayedBefore()) {
            Text.send(s, "&c플레이어 '" + name + "'을(를) 찾을 수 없습니다.");
            return null;
        }
        return off;
    }

    private PlayerData load(OfflinePlayer t) {
        PlayerData d = plugin.data().getOrLoad(t.getUniqueId());
        plugin.pass().ensure(t.getPlayer(), d);
        return d;
    }

    private void finish(OfflinePlayer t, PlayerData d) {
        if (t.isOnline()) plugin.data().save(d);
        else plugin.data().saveAndRelease(d.uuid);
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command command, String alias, String[] a) {
        if (a.length == 1) {
            return filter(List.of("help", "reward", "premium", "voucher", "shop", "xp", "points", "level", "info", "dailyreset", "season", "reload"), a[0]);
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (a.length == 2) {
            return switch (sub) {
                case "shop" -> filter(List.of("add", "remove", "price", "premium", "list"), a[1]);
                case "season" -> filter(List.of("next"), a[1]);
                case "xp", "points", "level", "info", "dailyreset", "premium", "voucher" -> null; // 플레이어 이름 자동완성
                default -> List.of();
            };
        }
        if (a.length == 3) {
            return switch (sub) {
                case "reward" -> filter(List.of("free", "premium"), a[2]);
                case "premium" -> filter(List.of("on", "off"), a[2]);
                case "points" -> filter(List.of("give", "take", "set"), a[2]);
                case "shop" -> a[1].equalsIgnoreCase("remove") || a[1].equalsIgnoreCase("price") || a[1].equalsIgnoreCase("premium")
                        ? filter(plugin.shop().list().stream().map(e -> e.id).toList(), a[2]) : List.of();
                default -> List.of();
            };
        }
        if (a.length == 4 && sub.equals("reward")) return filter(List.of("addhand", "clear", "view"), a[3]);
        return List.of();
    }

    private static List<String> filter(List<String> opts, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return opts.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }
}
