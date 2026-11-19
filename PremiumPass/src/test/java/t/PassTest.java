package t;

import kr.wave.test.GuiSnapshot;

import com.premiumpass.PremiumPass;
import com.premiumpass.data.PlayerData;
import com.premiumpass.gui.LevelMenu;
import com.premiumpass.gui.MainMenu;
import com.premiumpass.gui.QuestMenu;
import com.premiumpass.gui.ShopMenu;
import com.premiumpass.reward.Track;
import com.premiumpass.shop.ShopManager;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

public class PassTest {

    ServerMock server;
    PremiumPass plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(PremiumPass.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void defaultsGenerated() {
        assertTrue(new File(plugin.getDataFolder(), "rewards.yml").exists());
        assertTrue(new File(plugin.getDataFolder(), "shop.yml").exists());
        for (int lv = 1; lv <= 100; lv++) assertNotNull(plugin.rewards().get(Track.PREMIUM, lv), "level " + lv);
        assertNotNull(plugin.rewards().get(Track.FREE, 1));
        assertNull(plugin.rewards().get(Track.FREE, 2));          // 무료는 짝수 레벨 비어 있음
        assertNotNull(plugin.rewards().get(Track.FREE, 10));
        assertEquals(Material.ELYTRA, plugin.rewards().get(Track.PREMIUM, 100).items.get(0).getType());
        assertEquals(Material.DIAMOND, plugin.rewards().get(Track.FREE, 100).items.get(0).getType());
        assertEquals(26, plugin.shop().list().size());
        assertEquals(16, plugin.quests().seasonQuests().size());
        // 재로드해도 YAML 직렬화된 아이템이 유지되는지
        plugin.reloadAll();
        assertEquals(Material.ELYTRA, plugin.rewards().get(Track.PREMIUM, 100).items.get(0).getType());
        assertNull(plugin.rewards().get(Track.FREE, 2));
        assertTrue(plugin.shop().get("elytra").premiumOnly);
        assertEquals(26, plugin.shop().list().size());
    }

    @Test
    void xpLevelAndOverflow() {
        PlayerMock p = server.addPlayer();
        PlayerData d = plugin.data().get(p);
        assertEquals(1, d.level);
        assertEquals(5, d.dailyIds.size());
        plugin.pass().addXp(p, d, 2500);
        assertEquals(3, d.level);
        assertEquals(500, d.xp);
        // 100레벨까지 + 초과 1050 XP
        plugin.pass().addXp(p, d, 99L * 1000 - 2500 + 1050);
        assertEquals(100, d.level);
        assertEquals(10, d.points);         // 1000 XP / 100 = 10pt
        assertEquals(50, d.overflowXp);
        plugin.pass().addXp(p, d, 50);
        assertEquals(11, d.points);
        assertEquals(0, d.overflowXp);
    }

    @Test
    void claimAndShop() {
        PlayerMock p = server.addPlayer();
        PlayerData d = plugin.data().get(p);
        assertTrue(plugin.rewards().claim(p, d, Track.FREE, 1, false));
        assertFalse(plugin.rewards().claim(p, d, Track.FREE, 1, false));   // 중복 수령 불가
        assertFalse(plugin.rewards().claim(p, d, Track.FREE, 3, false));   // 레벨 부족
        assertFalse(plugin.rewards().claim(p, d, Track.PREMIUM, 1, false)); // 프리미엄 없음
        assertTrue(p.getInventory().contains(Material.BREAD));

        ShopManager.Entry elytra = plugin.shop().get("elytra");
        d.points = 2500;
        assertFalse(plugin.shop().buy(p, d, elytra));          // 프리미엄 전용
        assertEquals(2500, d.points);

        // 레벨 5까지 올린 뒤 프리미엄 활성화 -> 지나간 레벨도 소급 수령
        plugin.pass().addXp(p, d, 4000);
        assertEquals(5, d.level);
        assertTrue(plugin.pass().activatePremium(p, d));
        assertFalse(plugin.pass().activatePremium(p, d));
        assertEquals(5 + 2, plugin.rewards().claimAll(p, d)); // 프리미엄 1~5 + 무료 3,5
        assertTrue(p.getInventory().contains(Material.GOLDEN_APPLE));
        assertTrue(plugin.shop().buy(p, d, elytra));
        assertFalse(plugin.shop().buy(p, d, elytra));          // 시즌 1회 제한
        assertEquals(1500, d.points);
        assertTrue(p.getInventory().contains(Material.ELYTRA));
        d.points = 5;
        assertFalse(plugin.shop().buy(p, d, plugin.shop().get("diamond")));
    }

    @Test
    void questProgressAndPlacedBlocks() {
        World w = server.addSimpleWorld("world");
        PlayerMock p = server.addPlayer();
        PlayerData d = plugin.data().get(p);
        d.dailyIds.clear();
        d.dailyIds.add("ore_miner");

        Block b = w.getBlockAt(0, 10, 0);
        b.setType(Material.DIAMOND_ORE);
        server.getPluginManager().callEvent(new BlockBreakEvent(b, p));
        assertEquals(1L, d.dailyProgress.get("ore_miner"));
        assertEquals(1L, d.seasonProgress.get("diamond_miner"));

        // 직접 설치한 광석은 인정 안 됨
        Block b2 = w.getBlockAt(1, 10, 0);
        b2.setType(Material.DIAMOND_ORE);
        server.getPluginManager().callEvent(new BlockPlaceEvent(b2, b2.getState(), w.getBlockAt(1, 9, 0),
                new ItemStack(Material.DIAMOND_ORE), p, true, EquipmentSlot.HAND));
        server.getPluginManager().callEvent(new BlockBreakEvent(b2, p));
        assertEquals(1L, d.dailyProgress.get("ore_miner"));
        // 기록은 한 번만 소모 -> 같은 자리 자연 광석이 다시 생기면 인정
        server.getPluginManager().callEvent(new BlockBreakEvent(b2, p));
        assertEquals(2L, d.dailyProgress.get("ore_miner"));

        // 완료 -> XP/포인트 지급 + 시즌 COMPLETE_DAILY 진행
        for (int i = 0; i < 30; i++) server.getPluginManager().callEvent(new BlockBreakEvent(b, p));
        assertTrue(d.dailyDone.contains("ore_miner"));
        assertEquals(24L, d.dailyProgress.get("ore_miner"));
        assertEquals(400, d.xp);
        assertTrue(d.points >= 3);
        assertEquals(1L, d.seasonProgress.get("daily_master"));
        long before = d.points;
        // 프리미엄이면 포인트 +50% (3 -> 4)
        plugin.pass().activatePremium(p, d);
        d.dailyIds.add("creeper_hunter");
        d.dailyIds.remove("ore_miner");
        d.dailyIds.add("monster_hunter");
        d.dailyProgress.put("monster_hunter", 0L);
        d.dailyIds.clear();
        d.dailyIds.add("miner_stone");
        Block st = w.getBlockAt(5, 10, 5);
        st.setType(Material.STONE);
        for (int i = 0; i < 200; i++) server.getPluginManager().callEvent(new BlockBreakEvent(st, p));
        assertTrue(d.dailyDone.contains("miner_stone"));
        assertEquals(before + 3, d.points); // 2 * 1.5 = 3
    }

    @Test
    void menusOpenAndAdminCommands() {
        PlayerMock p = server.addPlayer();
        p.setOp(true);
        new MainMenu(plugin, p).open();
        new LevelMenu(plugin, p, 0).open();
        new LevelMenu(plugin, p, 2).open();
        new QuestMenu(plugin, p, true).open();
        new QuestMenu(plugin, p, false).open();
        plugin.data().get(p).points = 1200;
        new ShopMenu(plugin, p, 0).open();
        GuiSnapshot.save(p, "passshop.json");
        assertTrue(p.performCommand("pass"));
        assertTrue(p.performCommand("pass 정보"));
        assertTrue(p.performCommand("passadmin xp " + p.getName() + " 5000"));
        assertEquals(6, plugin.data().get(p).level);
        assertTrue(p.performCommand("passadmin points " + p.getName() + " set 77"));
        assertEquals(77, plugin.data().get(p).points);
        p.getInventory().setItemInMainHand(new ItemStack(Material.TRIDENT));
        assertTrue(p.performCommand("passadmin shop add trident 500 1"));
        assertNotNull(plugin.shop().get("trident"));
        assertTrue(p.performCommand("passadmin reward 3 free addhand"));
        var free3 = plugin.rewards().get(Track.FREE, 3).items;
        assertEquals(Material.TRIDENT, free3.get(free3.size() - 1).getType());
        assertTrue(p.performCommand("passadmin reward 4 free addhand")); // 비어 있던 칸에 새로 추가
        assertEquals(1, plugin.rewards().get(Track.FREE, 4).items.size());
        new com.premiumpass.gui.RewardEditorMenu(plugin, p, Track.PREMIUM, 7).open();

        // 패스권 아이템 / 명령어로 프리미엄 활성화
        ItemStack v = plugin.voucher().create(1);
        assertTrue(plugin.voucher().isVoucher(v));
        assertFalse(plugin.voucher().isVoucher(new ItemStack(Material.PAPER)));
        assertTrue(p.performCommand("passadmin premium " + p.getName() + " on"));
        assertTrue(plugin.pass().isPremium(p, plugin.data().get(p)));
        assertTrue(p.performCommand("passadmin season next 시즌 2"));
        PlayerData d = plugin.data().get(p);
        assertEquals(1, d.level);
        assertEquals(2, d.season);
        assertEquals(77, d.points); // keep-points: true
        assertFalse(plugin.pass().isPremium(p, d)); // 새 시즌엔 프리미엄 초기화
        assertTrue(p.performCommand("pass 모두수령"));
    }

    @Test
    void exportDefaultFilesAndServerRewards() throws Exception {
        var rewards = new java.io.File(plugin.getDataFolder(), "rewards.yml");
        var shop = new java.io.File(plugin.getDataFolder(), "shop.yml");
        assertTrue(rewards.exists() && shop.exists());
        String r = java.nio.file.Files.readString(rewards.toPath());
        String sh = java.nio.file.Files.readString(shop.toPath());
        assertTrue(r.contains("cashgive %player%"), "캐시 보상");
        assertTrue(r.contains("상자 give %player% TheEchoesOfNatural"), "상자 키 보상");
        assertTrue(r.contains("wave boss ticket %player%"), "이동권 보상");
        assertTrue(sh.contains("상자 give %player% combat 1"));
        // -Dexportdir=폴더 를 주면 기본 rewards.yml / shop.yml 을 그 폴더로 내보냄 (서버용 설정 만들 때)
        String dir = System.getProperty("exportdir");
        if (dir == null) return;
        var out = new java.io.File(dir);
        out.mkdirs();
        java.nio.file.Files.copy(rewards.toPath(), new java.io.File(out, "rewards.yml").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        java.nio.file.Files.copy(shop.toPath(), new java.io.File(out, "shop.yml").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private long crafted(PlayerData d) {
        return d.dailyProgress.getOrDefault("crafter", 0L) + d.seasonProgress.getOrDefault("crafter", 0L);
    }

    private org.bukkit.event.inventory.CraftItemEvent craft(PlayerMock p, boolean shift) {
        var recipe = new org.bukkit.inventory.ShapelessRecipe(new org.bukkit.NamespacedKey(plugin, "sticks"), new ItemStack(Material.STICK, 4));
        recipe.addIngredient(2, Material.OAK_PLANKS);
        // 가짜 서버는 제작대 화면을 만들지 못해서, 이벤트에 필요한 것만 돌려주는 대역 화면을 씀
        var view = (org.bukkit.inventory.InventoryView) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{org.bukkit.inventory.InventoryView.class}, (proxy, m, args) -> switch (m.getName()) {
                    case "getPlayer" -> p;
                    case "convertSlot" -> args[0];
                    case "getTopInventory", "getBottomInventory" -> p.getInventory();
                    case "getType" -> org.bukkit.event.inventory.InventoryType.WORKBENCH;
                    case "getCursor" -> null;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "CraftViewStub";
                    default -> m.getReturnType() == boolean.class ? false : m.getReturnType() == int.class ? 0 : null;
                });
        return new org.bukkit.event.inventory.CraftItemEvent(recipe, view, org.bukkit.event.inventory.InventoryType.SlotType.RESULT, 0,
                shift ? org.bukkit.event.inventory.ClickType.SHIFT_LEFT : org.bukkit.event.inventory.ClickType.LEFT,
                shift ? org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY : org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
    }

    @Test
    void shiftCraftCountsRealResult() {
        PlayerMock p = server.addPlayer("Crafter");
        server.getScheduler().performTicks(2);
        PlayerData d = plugin.data().get(p);
        p.getInventory().clear();
        // 한 번 클릭: 결과 4개
        server.getPluginManager().callEvent(craft(p, false));
        assertEquals(4, crafted(d));
        // Shift 클릭: 실제로 들어온 12개만 (재료 개수와 상관없이)
        server.getPluginManager().callEvent(craft(p, true));
        p.getInventory().addItem(new ItemStack(Material.STICK, 12));   // 서버가 제작 결과를 넣는 것처럼
        server.getScheduler().performTicks(1);
        assertEquals(16, crafted(d));
        // 인벤토리가 가득 차 아무것도 못 만들면 0
        for (int i = 0; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Material.DIRT, 64));
        server.getPluginManager().callEvent(craft(p, true));
        server.getScheduler().performTicks(1);
        assertEquals(16, crafted(d), "가득 차면 인정 안 됨");
    }

    @Test
    void preloadedButNeverJoinedIsDropped() {
        var id = java.util.UUID.randomUUID();
        preLogin(new org.bukkit.event.player.AsyncPlayerPreLoginEvent("Ghost", java.net.InetAddress.getLoopbackAddress(), id));
        assertEquals(1, plugin.data().preloadedCount());
        boolean cached = false;
        for (PlayerData x : plugin.data().cached()) if (x.uuid.equals(id)) cached = true;
        assertTrue(cached, "미리 읽음");
        server.getScheduler().performTicks(20 * 95);   // 1분 + 정리 주기
        cached = false;
        for (PlayerData x : plugin.data().cached()) if (x.uuid.equals(id)) cached = true;
        assertFalse(cached, "접속 안 하면 캐시에서 내림");
        assertEquals(0, plugin.data().preloadedCount());
        // 미리 읽은 뒤 접속한 사람은 유지
        PlayerMock real = server.addPlayer("RealJoin");
        preLogin(new org.bukkit.event.player.AsyncPlayerPreLoginEvent("RealJoin", java.net.InetAddress.getLoopbackAddress(), real.getUniqueId()));
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerJoinEvent(real, net.kyori.adventure.text.Component.empty()));
        server.getScheduler().performTicks(20 * 95);
        cached = false;
        for (PlayerData x : plugin.data().cached()) if (x.uuid.equals(real.getUniqueId())) cached = true;
        assertTrue(cached, "접속한 사람은 그대로");
    }

    @Test
    void reloadReportsInvalidConfigValues() {
        PlayerMock admin = server.addPlayer("PassAdm");
        admin.setOp(true);
        plugin.getConfig().set("daily.timezone", "Mars/Olympus");
        plugin.saveConfig();
        assertTrue(admin.performCommand("passadmin reload"));
        String msg = "";
        net.kyori.adventure.text.Component c;
        while ((c = admin.nextComponentMessage()) != null) msg += net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c) + "\n";
        assertTrue(msg.contains("잘못된 값 1개"), msg);
        assertEquals(1, plugin.warnings().count());
        plugin.getConfig().set("daily.timezone", "Asia/Seoul");
        plugin.saveConfig();
        admin.performCommand("passadmin reload");
        assertEquals(0, plugin.warnings().count());
    }

    /** 접속 전 이벤트는 실제 서버처럼 별도 스레드에서 */
    private void preLogin(org.bukkit.event.player.AsyncPlayerPreLoginEvent ev) {
        Thread t = new Thread(() -> server.getPluginManager().callEvent(ev));
        t.start();
        try {
            t.join();
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }
}
