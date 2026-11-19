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

    /* ---------------- 도박 (홀짝) GUI ---------------- */

    /** 플레이어별 현재 베팅 금액 */
    private final java.util.Map<UUID, Long> bets = new java.util.HashMap<>();
    /** 플레이어별 최근 결과 (최신이 앞) */
    private final java.util.Map<UUID, java.util.Deque<String>> history = new java.util.HashMap<>();

    private long minBet() {
        return Math.max(1, plugin.getConfig().getLong("gamble.min-bet", 1000));
    }

    private long maxBet() {
        return Math.max(minBet(), plugin.getConfig().getLong("gamble.max-bet", 1_000_000));
    }

    /** /도박 : 창 열기. /도박 <금액> 이면 그 금액으로 맞춰서 열기 */
    private void gambleCmd(CommandSender s, String l, String[] a) {
        if (!Cmd.isPlayer(s)) return;
        Player p = (Player) s;
        Long amount = null;
        for (String x : a) {
            Long n = Cmd.num(x);
            if (n != null) amount = n;
        }
        if (amount != null) bets.put(p.getUniqueId(), Math.max(minBet(), Math.min(maxBet(), amount)));
        gambleGui(p);
    }

    private long bet(Player p) {
        long b = bets.getOrDefault(p.getUniqueId(), minBet());
        return Math.max(minBet(), Math.min(maxBet(), b));
    }

    private static final String G_TAG = "&6&l[Gambling] ";

    public void gambleGui(Player p) {
        UUID id = p.getUniqueId();
        PlayerData d = data(p);
        boolean rolling = gambling.contains(id);
        long bet = bet(p);
        kr.wave.core.util.Gui g = new kr.wave.core.util.Gui(5, "&8&l홀짝 도박");
        g.put("gamble", true);
        g.set(4, It.of(org.bukkit.Material.PLAYER_HEAD).skull(p).name("&e" + p.getName())
                .lore("", "&f보유 캐시 : &e" + Msg.num(d.money) + "원", "", "&7베팅 범위 : &f" + Msg.num(minBet()) + " ~ " + Msg.num(maxBet()) + "원").build());
        long[] steps = {-100_000, -10_000, -1_000, 0, 1_000, 10_000, 100_000};
        for (int i = 0; i < steps.length; i++) {
            long st = steps[i];
            int slot = 10 + i;
            if (st == 0) {
                g.set(slot, It.of(org.bukkit.Material.GOLD_INGOT).name("&6&l베팅 금액 : &e&l" + Msg.num(bet) + "원")
                        .lore("", "&7맞히면 &a+" + Msg.num(bet) + "원&7, 틀리면 &c-" + Msg.num(bet) + "원", "", "&8좌우 버튼으로 금액을 조절합니다.").build());
                continue;
            }
            boolean plus = st > 0;
            g.set(slot, It.of(plus ? org.bukkit.Material.LIME_STAINED_GLASS_PANE : org.bukkit.Material.RED_STAINED_GLASS_PANE)
                    .name((plus ? "&a&l+" : "&c&l-") + Msg.num(Math.abs(st)) + "원").lore("", "&e» 좌클릭 &f베팅 " + (plus ? "늘리기" : "줄이기")).build(), e -> {
                if (gambling.contains(id)) return;
                bets.put(id, Math.max(minBet(), Math.min(maxBet(), bet(p) + st)));
                Msg.sound(p, "ui.button.click", 0.5f, plus ? 1.4f : 0.9f);
                gambleGui(p);
            });
        }
        g.set(20, It.of(org.bukkit.Material.IRON_NUGGET).name("&f&l최소 금액").lore("", "&7" + Msg.num(minBet()) + "원으로 맞춥니다.", "", "&e» 좌클릭 &f적용").build(), e -> {
            if (gambling.contains(id)) return;
            bets.put(id, minBet());
            gambleGui(p);
        });
        long all = Math.max(minBet(), Math.min(maxBet(), d.money));
        g.set(24, It.of(org.bukkit.Material.GOLD_BLOCK).name("&6&l최대 금액").lore("", "&7보유 캐시 안에서 최대 " + Msg.num(all) + "원으로 맞춥니다.", "", "&e» 좌클릭 &f적용").build(), e -> {
            if (gambling.contains(id)) return;
            bets.put(id, Math.max(minBet(), Math.min(maxBet(), data(p).money)));
            gambleGui(p);
        });
        g.set(22, It.of(rolling ? org.bukkit.Material.CLOCK : org.bukkit.Material.ENDER_EYE).name(rolling ? "&e&l결과를 확인하는 중..." : "&f&l홀 또는 짝을 고르세요")
                .lore("", "&7홀과 짝은 각각 &f50%&7 확률입니다.").build());
        // 최근 결과
        java.util.Deque<String> h = history.getOrDefault(id, new java.util.ArrayDeque<>());
        It book = It.of(org.bukkit.Material.BOOK).name("&f&l최근 결과").lore("");
        if (h.isEmpty()) book.lore("&8아직 기록이 없습니다.");
        for (String r : h) book.lore(r);
        g.set(31, book.build());
        g.set(29, pickButton(p, "홀", rolling), e -> roll(p, "홀"));
        g.set(33, pickButton(p, "짝", rolling), e -> roll(p, "짝"));
        g.nav(p, () -> plugin.module(MenuModule.class).menu(p)).fill().open(p);
    }

    private ItemStack pickButton(Player p, String pick, boolean rolling) {
        boolean odd = pick.equals("홀");
        return It.of(odd ? org.bukkit.Material.QUARTZ_BLOCK : org.bukkit.Material.COAL_BLOCK)
                .name((odd ? "&f&l" : "&8&l") + "[ " + pick + " ]")
                .lore("", rolling ? "&7결과를 기다리는 중입니다." : "&f베팅 : &e" + Msg.num(bet(p)) + "원", "", rolling ? "&8잠시만 기다려 주세요" : "&e» 좌클릭 &f" + pick + "에 걸기").build();
    }

    private void roll(Player p, String pick) {
        UUID id = p.getUniqueId();
        if (gambling.contains(id)) return;
        long amount = bet(p);
        PlayerData d = data(p);
        if (d.money < amount) {
            Msg.send(p, G_TAG + "&c소지금이 부족합니다. &7(보유 " + Msg.num(d.money) + "원)");
            Msg.sound(p, "entity.villager.no", 0.8f, 1f);
            return;
        }
        // 베팅금을 먼저 빼 두고, 이기면 두 배로 돌려줌 (창을 닫거나 나가도 결과는 그대로 처리)
        d.money -= amount;
        plugin.store().save(d);
        gambling.add(id);
        gambleGui(p);
        boolean odd = ThreadLocalRandom.current().nextBoolean();
        String result = odd ? "홀" : "짝";
        int[] tick = {0};
        org.bukkit.scheduler.BukkitTask[] task = new org.bukkit.scheduler.BukkitTask[1];
        task[0] = org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            tick[0] += 4;
            if (tick[0] < 60) {   // 3초 동안 홀 / 짝 번갈아 표시
                boolean show = (tick[0] / 4) % 2 == 0;
                if (p.isOnline()) {
                    var top = p.getOpenInventory().getTopInventory();
                    if (top != null && top.getHolder() instanceof kr.wave.core.util.Gui gg && gg.get("gamble") != null)
                        gg.set(22, It.of(show ? org.bukkit.Material.QUARTZ_BLOCK : org.bukkit.Material.COAL_BLOCK).name(show ? "&f&l[ 홀 ]" : "&8&l[ 짝 ]").lore("", "&7결과를 확인하는 중...").build());
                    Msg.sound(p, "block.note_block.hat", 0.6f, 0.8f + tick[0] / 60f);
                }
                return;
            }
            task[0].cancel();
            gambling.remove(id);
            PlayerData dd = data(p);
            boolean win = result.equals(pick);
            if (win) dd.money += amount * 2;
            plugin.store().saveAuto(dd);
            java.util.Deque<String> h = history.computeIfAbsent(id, k -> new java.util.ArrayDeque<>());
            h.addFirst((win ? "&a승리 " : "&c패배 ") + "&7" + pick + " → " + result + " &8(" + (win ? "+" : "-") + Msg.num(amount) + "원)");
            while (h.size() > 7) h.removeLast();
            if (!p.isOnline()) return;
            if (win) {
                Msg.send(p, G_TAG + "&a승리! &f결과: " + result + " &e+" + Msg.num(amount) + "원 &7(보유 " + Msg.num(dd.money) + "원)");
                Msg.sound(p, "entity.player.levelup", 0.7f, 1.4f);
            } else {
                Msg.send(p, G_TAG + "&c패배... &f결과: " + result + " &e-" + Msg.num(amount) + "원 &7(보유 " + Msg.num(dd.money) + "원)");
                Msg.sound(p, "block.anvil.land", 0.5f, 0.8f);
            }
            var top = p.getOpenInventory().getTopInventory();
            if (top != null && top.getHolder() instanceof kr.wave.core.util.Gui og && og.get("gamble") != null) {
                gambleGui(p);
                var t2 = p.getOpenInventory().getTopInventory();
                if (t2.getHolder() instanceof kr.wave.core.util.Gui gg)
                    gg.set(22, It.of(odd ? org.bukkit.Material.QUARTZ_BLOCK : org.bukkit.Material.COAL_BLOCK).ench(org.bukkit.enchantments.Enchantment.UNBREAKING, 1).hide()
                            .name((win ? "&a&l승리! " : "&c&l패배 ") + "&f결과 : " + (odd ? "&f&l홀" : "&8&l짝"))
                            .lore("", win ? "&a+" + Msg.num(amount) + "원" : "&c-" + Msg.num(amount) + "원", "", "&7다시 홀 또는 짝을 고르면 바로 시작합니다.").build());
            }
        }, 4L, 4L);
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
