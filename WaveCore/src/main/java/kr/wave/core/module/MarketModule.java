package kr.wave.core.module;

import kr.wave.core.Items;
import kr.wave.core.WaveCore;
import kr.wave.core.data.GlobalData;
import kr.wave.core.data.PlayerData;
import kr.wave.core.util.Cmd;
import kr.wave.core.util.Gui;
import kr.wave.core.util.It;
import kr.wave.core.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** 장터.sk : 유저 장터 */
public class MarketModule extends Module {

    private static final int PER_PAGE = 36;

    public MarketModule(WaveCore plugin) {
        super(plugin);
    }

    @Override
    public void enable() {
        cmd("판매", null, this::sellCmd);
        cmd("장터", null, (s, l, a) -> {
            if (Cmd.isPlayer(s)) open((Player) s, 1);
        });
        cmd("장터초기화", ADMIN, (s, l, a) -> {
            // 원본은 등록된 아이템이 그대로 사라졌으나, 판매자 우편함으로 돌려준다
            int n = 0;
            for (GlobalData.Listing li : new ArrayList<>(global().market)) {
                plugin.module(MailModule.class).send(li.seller, li.item);
                n++;
            }
            global().market.clear();
            plugin.store().saveGlobal();
            Msg.send(s, "&6[Market] &a장터를 초기화했습니다. &7(" + n + "개 아이템을 판매자 우편함으로 반환)");
        });
        cmd("성검지급", ADMIN, (s, l, a) -> {
            if (!Cmd.isPlayer(s)) return;
            It.give((Player) s, Items.soulSplitter(plugin, 0), Items.timePiecerWind());
        });
    }

    private void sellCmd(CommandSender s, String l, String[] a) {
        if (!Cmd.isPlayer(s)) return;
        Player p = (Player) s;
        Long price = Cmd.num(Cmd.arg(a, 0));
        if (price == null) {
            Msg.send(p, "&6[Market] &c사용법: /판매 [가격]");
            return;
        }
        if (price < 1) {
            Msg.send(p, "&6[Market] &c가격은 1 이상이어야 합니다.");
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (It.isAir(hand)) {
            Msg.send(p, "&6[Market] &c판매할 아이템을 손에 들어주세요.");
            return;
        }
        GlobalData g = global();
        GlobalData.Listing li = new GlobalData.Listing();
        li.id = g.nextListingId++;
        li.seller = p.getUniqueId();
        li.sellerName = p.getName();
        li.price = price;
        li.item = hand.clone();
        g.market.add(li);
        p.getInventory().setItemInMainHand(null);
        plugin.store().saveGlobal();
        Msg.send(p, "&6[Market] &a아이템이 장터에 등록되었습니다. &7(ID: " + li.id + ")");
    }

    private ItemStack display(GlobalData.Listing li) {
        ItemStack it = li.item.clone();
        ItemMeta meta = it.getItemMeta();
        List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore = Msg.append(lore, "&8━━━━━━━━━━━━━━", "&e가격 : &f" + Msg.num(li.price) + " Cash", "&7판매자 : &f" + li.sellerName, "", "&a좌클릭 &7구매   &c우클릭 &7내 등록 취소");
        meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private void open(Player p, int page) {
        List<GlobalData.Listing> all = new ArrayList<>(global().market);
        int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        int pg = Math.max(1, Math.min(page, pages));
        Gui g = new Gui(6, "&8&l유저 장터 &7(" + pg + "/" + pages + ")");
        int start = (pg - 1) * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < all.size(); i++) {
            GlobalData.Listing li = all.get(start + i);
            g.set(9 + i, display(li), e -> click(p, li, e.getClick(), pg));
        }
        g.set(4, It.of(Material.BOOK).name("&6유저 장터 사용법").lore("&7판매할 아이템을 들고 /판매 [금액] 을 입력하면 등록됩니다.%nl%&e좌클릭: 구매  &c우클릭: 내 등록 취소").build());
        PlayerData me = data(p);
        g.set(g.last() + 5, It.of(Material.SUNFLOWER).name("&6보유 캐시 : &e" + Msg.num(me.money) + "원").lore("&7등록된 상품: &f" + all.size() + "개").build());
        g.pages(p, pg, pages, n -> later(1, () -> open(p, n)))
                .nav(p, () -> plugin.module(MenuModule.class).menu(p)).fill().open(p);
    }

    private void click(Player p, GlobalData.Listing li, ClickType click, int page) {
        GlobalData g = global();
        if (!g.market.contains(li)) {
            Msg.send(p, "&6[Market] &c이미 판매되었거나 취소된 아이템입니다.");
            Msg.sound(p, "entity.villager.no", 0.8f, 1f);
            open(p, page);
            return;
        }
        boolean own = li.seller.equals(p.getUniqueId());
        if (click == ClickType.RIGHT) {
            if (!own) {
                Msg.send(p, "&6[Market] &c본인의 아이템만 취소할 수 있습니다.");
                Msg.sound(p, "entity.villager.no", 0.8f, 1f);
                return;
            }
            g.market.remove(li);
            plugin.store().saveGlobal();
            It.give(p, li.item.clone());
            Msg.send(p, "&6[Market] &c아이템 등록이 취소되었습니다.");
            Msg.sound(p, "entity.item.pickup", 0.6f, 0.8f);
            open(p, page);
        } else if (click == ClickType.LEFT) {
            if (own) {
                Msg.send(p, "&6[Market] &c우클릭하여 등록을 취소할 수 있습니다.");
                return;
            }
            if (It.freeSlots(p) < 1) {
                Msg.send(p, "&6[Market] 인벤토리에 공간이 부족합니다! 아이템을 비워주세요.");
                Msg.sound(p, "entity.villager.no", 0.8f, 1f);
                return;
            }
            if (!plugin.module(EconomyModule.class).take(p, li.price, false)) {
                Msg.send(p, "&6[Market] &c캐시가 부족합니다.");
                Msg.sound(p, "entity.villager.no", 0.8f, 1f);
                return;
            }
            g.market.remove(li);
            plugin.store().saveGlobal();
            PlayerData sd = plugin.store().get(li.seller);
            sd.money += li.price;
            plugin.store().saveAuto(sd);
            It.give(p, li.item.clone());
            Msg.send(p, "&6[Market] &a구매 성공!");
            Msg.sound(p, "entity.villager.yes", 0.8f, 1.2f);
            Player seller = Bukkit.getPlayer(li.seller);
            if (seller != null) Msg.send(seller, "&6[Market] &a아이템이 판매되었습니다! &7(" + li.price + " Cash)");
            open(p, page);
        }
    }
}
