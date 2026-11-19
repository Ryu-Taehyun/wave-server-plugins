package t;

import com.dailyshop.DailyShop;
import com.dailyshop.ShopManager;
import com.dailyshop.currency.VaultCurrency;
import com.dailyshop.gui.ShopMenu;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

public class DailyShopTest {
    ServerMock server;
    DailyShop plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DailyShop.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void standaloneFallsBackToEmeralds() {
        ShopManager shop = plugin.shop();
        assertTrue(new File(plugin.getDataFolder(), "shop.yml").exists());
        assertNull(VaultCurrency.tryCreate());
        assertEquals("에메랄드", shop.currencyName());   // PremiumPass 없음 -> 아이템 결제
        assertFalse(shop.premiumAvailable());
        assertEquals(20, shop.pool().size());
        var today = shop.today();
        assertEquals(6, today.size());

        PlayerMock p = server.addPlayer();
        var e = today.stream().filter(x -> !x.premiumOnly).findFirst().orElseThrow();
        assertFalse(shop.buy(p, e));                       // 에메랄드 없음
        p.getInventory().addItem(new ItemStack(Material.EMERALD, 64), new ItemStack(Material.EMERALD, 64),
                new ItemStack(Material.EMERALD, 64), new ItemStack(Material.EMERALD, 64), new ItemStack(Material.EMERALD, 64));
        long before = shop.balance(p);
        assertEquals(320, before);
        for (int i = 0; i < e.limit; i++) assertTrue(shop.buy(p, e));
        assertFalse(shop.buy(p, e));                       // 하루 한도
        assertEquals(before - e.cost * e.limit + (e.item.getType() == Material.EMERALD ? e.item.getAmount() * e.limit : 0), shop.balance(p));
        assertEquals(e.cost, shop.price(p, e));            // 프리미엄 할인 없음

        // 프리미엄 전용 상품은 PremiumPass 없이는 구매 불가
        shop.put("vip", new ItemStack(Material.DIAMOND), 1, 1, 10, true);
        assertTrue(shop.get("vip").premiumOnly);

        // 재시작해도 진열과 구매 기록 유지
        var ids = shop.today().stream().map(x -> x.id).toList();
        plugin.reloadAll();
        assertEquals(ids, plugin.shop().today().stream().map(x -> x.id).toList());
        assertEquals(e.limit, plugin.shop().bought(p, plugin.shop().get(e.id)));

        // 다시 뽑으면 구매 기록 초기화
        plugin.shop().reroll();
        assertEquals(0, plugin.shop().bought(p, plugin.shop().get(e.id)));

        new ShopMenu(plugin, p).open();
        assertTrue(p.performCommand("dailyshop"));
        p.setOp(true);
        p.getInventory().setItemInMainHand(new ItemStack(Material.TRIDENT));
        assertTrue(p.performCommand("dailyshop admin add trident 50 1 3"));
        assertNotNull(plugin.shop().get("trident"));
        assertTrue(p.performCommand("dailyshop admin list"));
        assertTrue(p.performCommand("dailyshop admin remove trident"));
        assertNull(plugin.shop().get("trident"));
    }

    @Test
    void buyWithWaveCoreCash() {
        MockBukkit.unmock();
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin("WaveCore");   // 캐시 결제 대역 (kr.wave.core.api.WaveAPI 테스트 클래스)
        plugin = MockBukkit.load(DailyShop.class);
        ShopManager shop = plugin.shop();
        assertTrue(shop.cashAvailable(), "WaveCore 캐시 결제 사용 가능");
        var p = server.addPlayer("CashBuyer");
        var e = shop.today().get(0);
        long cashPrice = shop.cashPrice(p, e);
        assertEquals(shop.price(p, e) * 40, cashPrice, "캐시 가격 = 포인트 가격 x 40");
        kr.wave.core.api.WaveAPI.CASH.put(p.getUniqueId(), cashPrice + 5);
        p.getInventory().clear();
        assertTrue(shop.buy(p, e, true), "캐시로 구매");
        assertEquals(5L, kr.wave.core.api.WaveAPI.CASH.get(p.getUniqueId()));
        assertTrue(java.util.Arrays.stream(p.getInventory().getContents()).anyMatch(it -> it != null && it.getType() == e.item.getType()));
        // 캐시 부족
        var e2 = shop.today().get(1);
        assertFalse(shop.buy(p, e2, true));
        // GUI: 우클릭 = 캐시 구매
        kr.wave.core.api.WaveAPI.CASH.put(p.getUniqueId(), shop.cashPrice(p, e2));
        p.performCommand("dailyshop");
        Dump.inv(p, "dailyshop.json");
        var inv = p.getOpenInventory().getTopInventory();
        int slot = -1;
        for (int i = 0; i < inv.getSize(); i++) {
            var it = inv.getItem(i);
            if (it != null && it.hasItemMeta() && it.getItemMeta().getLore() != null && it.getType() == e2.item.getType()
                    && String.join(" ", it.getItemMeta().getLore()).contains("캐시 가격")) slot = i;
        }
        assertTrue(slot >= 0, "상품 설명에 캐시 가격");
        var view = p.getOpenInventory();
        var ev = new org.bukkit.event.inventory.InventoryClickEvent(view, org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER, slot,
                org.bukkit.event.inventory.ClickType.RIGHT, org.bukkit.event.inventory.InventoryAction.PICKUP_HALF);
        server.getPluginManager().callEvent(ev);
        assertEquals(0L, kr.wave.core.api.WaveAPI.CASH.get(p.getUniqueId()), "우클릭으로 캐시 구매");
    }
}
