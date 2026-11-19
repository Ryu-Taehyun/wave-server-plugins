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
}
