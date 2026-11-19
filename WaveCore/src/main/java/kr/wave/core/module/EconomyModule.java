package kr.wave.core.module;

import kr.wave.core.Items;
import kr.wave.core.WaveCore;
import kr.wave.core.data.PlayerData;
import kr.wave.core.util.Cmd;
import kr.wave.core.util.It;
import kr.wave.core.util.Msg;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** 캐시 / 유료 캐시 / 수표 / 도박 / 최고급 수박 조각 */
public class EconomyModule extends Module {

    private final Set<UUID> gambling = new HashSet<>();

    public EconomyModule(WaveCore plugin) {
        super(plugin);
    }

    @Override
    public void enable() {
        cmd("cashgive", ADMIN, (s, l, a) -> give(s, a, false));
        cmd("excashgive", ADMIN, (s, l, a) -> give(s, a, true));
        cmd("cashreset", ADMIN, (s, l, a) -> reset(s, a, false), "돈초기화");
        cmd("excashreset", ADMIN, (s, l, a) -> reset(s, a, true), "유료캐시초기화");
        cmd("cash", null, (s, l, a) -> {
            if (Cmd.isPlayer(s)) Msg.send(s, "§e[🪙] &7현재 보유 중인 캐시: &e" + Msg.num(data((Player) s).money) + "원");
        }, "캐시");
        cmd("excash", null, (s, l, a) -> {
            if (Cmd.isPlayer(s)) Msg.send(s, "§6[🪙] &7현재 보유 중인 유료 캐시: &6" + Msg.num(data((Player) s).exMoney) + "원");
        }, "유료캐시");
        cmd("cashcheck", null, this::checkCmd, "수표출금", "수표");
        cmd("돈확인", ADMIN, "&c이 명령어는 관리자만 사용할 수 있습니다.", (s, l, a) -> {
            OfflinePlayer t = Cmd.offline(Cmd.arg(a, 0));
            if (t == null) {
                Msg.send(s, "&e사용법: /돈확인 [플레이어닉네임]");
                return;
            }
            PlayerData d = plugin.store().get(t);
            Msg.send(s, "&e[관리] &f" + a[0] + " &e님의 현재 보유 금액은 &f" + d.money + "원 &e입니다.");
            if (!t.isOnline()) plugin.store().saveRelease(d);
        });
        cmd("도박", null, this::gambleCmd);
    }

    private void give(CommandSender s, String[] a, boolean paid) {
        if (a.length < 2) return;
        OfflinePlayer t = Cmd.offline(a[0]);
        Long amount = Cmd.num(a[1]);
        if (t == null || amount == null) {
            Msg.send(s, "&c플레이어 또는 금액이 올바르지 않습니다.");
            return;
        }
        PlayerData d = plugin.store().get(t);
        if (paid) {
            d.exMoney += amount;
            d.exMoneyTotal += amount;
            kr.wave.core.util.TxLog.paid("관리자 지급", String.valueOf(t.getName()), t.getUniqueId(), amount, d.exMoney, "지급자 " + s.getName());
            Msg.send(s, "§a[💰] &6" + a[0] + "님에게 " + amount + " 유료 캐시를 지급했습니다.");
            if (t.getPlayer() != null) Msg.send(t.getPlayer(), "§a[💰] &6서버로부터 " + amount + " 유료 캐시가 지급되었습니다. (잔액: " + d.exMoney + "원)");
        } else {
            d.money += amount;
            d.moneyTotal += amount;
            Msg.send(s, "§e[🪙] &7" + a[0] + "님에게 " + amount + "원을 지급했습니다.");
            if (t.getPlayer() != null) Msg.send(t.getPlayer(), "§e[🪙] &7서버로부터 " + amount + "원이 지급되었습니다. (잔액: " + d.money + "원)");
        }
        plugin.store().saveAuto(d);
    }

    private void reset(CommandSender s, String[] a, boolean paid) {
        OfflinePlayer t = a.length > 0 ? Cmd.offline(a[0]) : (s instanceof Player p ? p : null);
        if (t == null) {
            Msg.send(s, "&c플레이어를 찾을 수 없습니다.");
            return;
        }
        PlayerData d = plugin.store().get(t);
        if (paid) {
            kr.wave.core.util.TxLog.paid("초기화", String.valueOf(t.getName()), t.getUniqueId(), -d.exMoney, 0, "실행자 " + s.getName() + " · 누적 " + d.exMoneyTotal + " 도 0으로");
            d.exMoney = 0;
            d.exMoneyTotal = 0;
            Msg.send(s, "§e[🪙] &7플레이어 " + t.getName() + "님의 유료 캐시가 &c초기화 &7되었습니다.");
        } else {
            d.money = 0;
            d.moneyTotal = 0;
            Msg.send(s, "§e[🪙] &7플레이어 " + t.getName() + "님의 캐시가 &c초기화 &7되었습니다.");
        }
        plugin.store().saveAuto(d);
    }

    /* ---------------- 수표 ---------------- */

    private void checkCmd(CommandSender s, String l, String[] a) {
        if (!Cmd.isPlayer(s)) return;
        Player p = (Player) s;
        Long amount = Cmd.num(Cmd.arg(a, 0));
        if (amount == null) {
            Msg.send(p, "§c[🪙] 사용할 금액을 입력해주세요. (사용법: /수표 [금액])");
            return;
        }
        PlayerData d = data(p);
        if (d.money < amount) {
            Msg.send(p, "§c[🪙] 보유 중인 캐시가 부족합니다. (현재 잔액: " + d.money + "원)");
            return;
        }
        if (amount <= 0) {
            Msg.send(p, "§c[🪙] &71원 이상의 금액만 수표로 만들 수 있습니다.");
            return;
        }
        d.money -= amount;
        It.give(p, Items.check(amount, p.getName()));
        Msg.send(p, "§e[🪙] &7" + amount + " 캐시 수표를 발행했습니다.");
        plugin.store().save(d);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCheck(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType() != org.bukkit.Material.PAPER || !Msg.nameIs(hand, Items.N_CHECK)) return;
        e.setCancelled(true);
        List<net.kyori.adventure.text.Component> lore = hand.getItemMeta().lore();
        if (lore == null || lore.isEmpty()) return;
        String line = Msg.plain(lore.get(0)).replace("금액: ", "").replace(" Cash", "").trim();
        Long amount = Cmd.num(line);
        if (amount == null || amount <= 0) return;
        It.takeOneFromHand(p);
        PlayerData d = data(p);
        d.money += amount;
        Msg.send(p, "§a[🪙] &7" + amount + " 캐시를 수령했습니다. (현재 잔액: " + d.money + " Cash)");
        plugin.store().save(d);
    }

    /* ---------------- 도박 ---------------- */

    private void gambleCmd(CommandSender s, String l, String[] a) {
        if (!Cmd.isPlayer(s)) return;
        Player p = (Player) s;
        Long amount = Cmd.num(Cmd.arg(a, 1));
        String pick = Cmd.arg(a, 0);
        if (amount == null || pick == null || !(pick.equals("홀") || pick.equals("짝"))) {
            Msg.send(p, "&6[Gambling] &c사용법: /도박 <홀/짝> <금액>");
            return;
        }
        if (amount < 1000) {
            Msg.send(p, "&6[Gambling] &c최소 1,000원부터 도박이 가능합니다.");
            return;
        }
        if (data(p).money < amount) {
            Msg.send(p, "&6[Gambling] &c소지금이 부족합니다.");
            return;
        }
        if (!gambling.add(p.getUniqueId())) {
            Msg.send(p, "&6[Gambling] &c이미 결과를 기다리는 중입니다.");
            return;
        }
        Msg.send(p, "&6[Gambling] &e결과를 확인하는 중... 3초 뒤 공개됩니다....");
        Msg.soundAt(p.getLocation(), "block.note_block.pling", 1, 0.5f);
        later(20, () -> {
            if (p.isOnline()) Msg.soundAt(p.getLocation(), "block.note_block.pling", 1, 0.7f);
        });
        later(40, () -> {
            if (p.isOnline()) Msg.soundAt(p.getLocation(), "block.note_block.pling", 1, 1.0f);
        });
        later(60, () -> {
            gambling.remove(p.getUniqueId());
            PlayerData d = data(p);
            if (d.money < amount) {
                Msg.send(p, "&6[Gambling] &c소지금이 부족합니다.");
                return;
            }
            boolean odd = ThreadLocalRandom.current().nextBoolean();
            String result = odd ? "홀" : "짝";
            if (result.equals(pick)) {
                d.money += amount;
                if (p.isOnline()) {
                    Msg.soundAt(p.getLocation(), "entity.experience_orb.pickup", 1, 1.5f);
                    Msg.send(p, "&6[Gambling] &a승리! &f결과: " + result + "! &e+" + amount + "원");
                }
            } else {
                d.money -= amount;
                if (p.isOnline()) {
                    Msg.soundAt(p.getLocation(), "block.anvil.land", 0.5f, 0.8f);
                    Msg.send(p, "&6[Gambling] &c패배... &f결과: " + result + "! &e-" + amount + "원");
                }
            }
            plugin.store().saveAuto(d);
        });
    }

    /* ---------------- 최고급 수박 조각 ---------------- */

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent e) {
        if (!Msg.nameHas(e.getItem(), Items.N_MELON)) return;
        Player p = e.getPlayer();
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 8 * 20, 0));
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 7 * 20, 2));
        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 120 * 20, 1));
        later(1, () -> p.setFoodLevel(20));
    }

    /** 캐시 차감. 부족하면 false */
    public boolean take(Player p, long amount, boolean paid) {
        return take(p, amount, paid, null);
    }

    /** 캐시 차감 + 유료 캐시면 사용 내용(reason) 기록. 부족하면 false */
    public boolean take(Player p, long amount, boolean paid, String reason) {
        PlayerData d = data(p);
        if (paid) {
            if (d.exMoney < amount) return false;
            d.exMoney -= amount;
            kr.wave.core.util.TxLog.paid("사용", p.getName(), p.getUniqueId(), -amount, d.exMoney, reason);
        } else {
            if (d.money < amount) return false;
            d.money -= amount;
        }
        plugin.store().save(d);
        return true;
    }
}
