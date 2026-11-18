package com.premiumpass.command;

import com.premiumpass.PremiumPass;
import com.premiumpass.data.PlayerData;
import com.premiumpass.gui.LevelMenu;
import com.premiumpass.gui.MainMenu;
import com.premiumpass.gui.QuestMenu;
import com.premiumpass.gui.ShopMenu;
import com.premiumpass.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;

/** /pass [보상|일일|시즌|상점|정보|모두수령] */
public class PassCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("보상", "일일", "시즌", "상점", "정보", "모두수령");

    private final PremiumPass plugin;

    public PassCommand(PremiumPass plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("게임 안에서만 사용할 수 있습니다.");
            return true;
        }
        PlayerData d = plugin.data().get(p);
        plugin.pass().ensure(p, d);
        String sub = args.length == 0 ? "" : args[0].toLowerCase();
        switch (sub) {
            case "" -> new MainMenu(plugin, p).open();
            case "보상", "rewards", "levels" -> new LevelMenu(plugin, p, LevelMenu.pageOf(d.level)).open();
            case "일일", "daily" -> new QuestMenu(plugin, p, true).open();
            case "시즌", "season" -> new QuestMenu(plugin, p, false).open();
            case "상점", "shop" -> new ShopMenu(plugin, p, 0).open();
            case "모두수령", "claim" -> {
                if (plugin.rewards().claimAll(p, d) > 0) plugin.data().save(d);
            }
            case "정보", "info" -> {
                int max = plugin.pass().maxLevel();
                Text.send(p, "&6" + plugin.pass().seasonName() + " &f패스 정보 " + (plugin.pass().isPremium(p, d) ? "&6[프리미엄]" : "&7[무료]"));
                if (d.level < max) {
                    long req = plugin.pass().required(d.level);
                    p.sendMessage(Text.color("&f 레벨 &e" + d.level + "&7/" + max + "  &fXP &b" + Text.num(d.xp) + "&7/" + Text.num(req) + " " + Text.bar(d.xp, req, 15)));
                } else {
                    p.sendMessage(Text.color("&f 레벨 &6" + d.level + " (MAX)  &f포인트 전환 &b" + Text.num(d.overflowXp) + "&7/" + Text.num(plugin.pass().xpPerPoint()) + " XP"));
                }
                p.sendMessage(Text.color("&f 프리미엄 패스 포인트: &6" + Text.num(d.points)));
            }
            default -> Text.send(p, "&7사용법: /pass [보상|일일|시즌|상점|정보|모두수령]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return SUBS.stream().filter(s -> s.startsWith(args[0])).toList();
        return List.of();
    }
}
