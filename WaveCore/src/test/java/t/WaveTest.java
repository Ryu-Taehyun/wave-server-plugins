package t;

import kr.wave.test.GuiSnapshot;

import kr.wave.core.Items;
import kr.wave.core.WaveCore;
import kr.wave.core.data.GlobalData;
import kr.wave.core.data.PlayerData;
import kr.wave.core.module.LevelModule;
import kr.wave.core.util.Gui;
import kr.wave.core.util.It;
import kr.wave.core.util.Msg;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

public class WaveTest {

    ServerMock server;
    WaveCore plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("spawn-world");
        server.addSimpleWorld("world");
        plugin = MockBukkit.load(WaveCore.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** 열린 GUI 의 슬롯을 클릭 */
    private void click(PlayerMock p, int slot, ClickType type) {
        InventoryView view = p.getOpenInventory();
        assertTrue(view.getTopInventory().getHolder() instanceof Gui, "GUI 가 열려 있어야 함");
        InventoryClickEvent e = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(e);
        assertTrue(e.isCancelled() || ((Gui) view.getTopInventory().getHolder()) != null);
    }

    /** 열린 GUI 아래 자기 인벤토리 칸 클릭 (0~8 핫바, 9~35 가방) */
    private void clickBottom(PlayerMock p, int invSlot) {
        InventoryView view = p.getOpenInventory();
        int top = view.getTopInventory().getSize();
        int raw = top + invSlot;   // MockBukkit: 위 창 크기 + 플레이어 칸 번호
        InventoryClickEvent e = new InventoryClickEvent(view, invSlot < 9 ? InventoryType.SlotType.QUICKBAR : InventoryType.SlotType.CONTAINER, raw, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(e);
    }

    private static boolean guiOpen(PlayerMock p) {
        var top = p.getOpenInventory().getTopInventory();
        return top != null && top.getHolder() instanceof Gui;
    }

    private String title(PlayerMock p) {
        return Msg.plain(p.getOpenInventory().title());
    }

    @Test
    void textConversion() {
        assertEquals("[⚡] [Lv.js] 천공의 심판자 클레이모어", Items.C_CLAYMORE);
        assertEquals("[💡 NEWBIE] 뉴비 헬퍼 패스", Msg.name(Items.newbiePass()));
        assertEquals(Items.N_CLAYMORE_SD, Msg.name(Items.claymoreSd()));
        assertEquals(Items.N_FROST, Msg.name(Items.frostGrave()));
        assertEquals(Items.N_SEALED_SWORD, Msg.name(Items.sealedSword()));
        assertEquals("[PVP] 고급 전투 패키지", Msg.name(Items.packHigh("x")));
        for (int i = 0; i <= 6; i++) assertEquals(i, Items.chronosStage(Items.chronos(i)), "stage " + i);
        // 태그가 아닌 << 는 글자로 남음
        assertTrue(Msg.plainOf("&b&l<< &7[1.20-1.21.11]").startsWith("<< "));
        // 짝 없는 </color> 가 글자로 남지 않음 (프로스트 그레이브 설명)
        for (var line : Items.frostGrave().getItemMeta().lore()) {
            assertFalse(Msg.plain(line).contains("</"), Msg.plain(line));
        }
        assertTrue(Msg.plain(Items.frostGrave().getItemMeta().lore().get(6)).contains("받는 데미지 감소 (0.8x)"));
        // 정상적인 태그는 유지
        assertEquals("가나다", Msg.plainOf("<color:#39C7FF>가</color>나<gradient:#FFF:#000>다</gradient>"));
    }

    @Test
    void firstJoinAndEconomy() {
        PlayerMock p = server.addPlayer("Alice");
        PlayerData d = plugin.store().get(p);
        assertEquals(1, d.level);
        assertTrue(d.newbieStart > 0, "첫 접속 뉴비 보호 시작");
        server.getScheduler().performTicks(5);
        assertTrue(p.getInventory().contains(Material.IRON_SWORD), "첫 접속 지급품");

        PlayerMock admin = server.addPlayer("Admin");
        admin.setOp(true);
        assertTrue(admin.performCommand("cashgive Alice 50000"));
        assertEquals(50000, d.money);
        assertEquals(50000, d.moneyTotal);
        assertTrue(admin.performCommand("excashgive Alice 80000"));
        assertEquals(80000, d.exMoney);

        // 수표 발행 -> 사용
        assertTrue(p.performCommand("수표 1000"));
        assertEquals(49000, d.money);
        ItemStack check = null;
        for (ItemStack it : p.getInventory().getContents()) if (Msg.nameIs(it, Items.N_CHECK)) check = it;
        assertNotNull(check);
        p.getInventory().setItemInMainHand(check);
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerInteractEvent(p, org.bukkit.event.block.Action.RIGHT_CLICK_AIR, check, null, org.bukkit.block.BlockFace.UP, org.bukkit.inventory.EquipmentSlot.HAND));
        assertEquals(50000, d.money, "수표 사용 후 원래 금액");

        // 권한 없는 사용자는 관리자 명령어 불가
        p.performCommand("cashgive Alice 999999");
        assertEquals(50000, d.money);

        // 자동 랭크 (누적 유료 캐시 80000 -> Ace, Supreme)
        server.getScheduler().performTicks(25);
        assertTrue(d.ranks.contains("ace"));
        assertTrue(d.ranks.contains("supreme"));
        assertFalse(d.ranks.contains("exceed"));
        assertEquals(3, d.mailbox.size(), "Ace 보상 2 + Supreme 룰렛 1");
    }

    @Test
    void shopPurchaseAndMailbox() {
        PlayerMock p = server.addPlayer("Bob");
        PlayerData d = plugin.store().get(p);
        d.money = 2000;
        assertTrue(p.performCommand("eat"));
        assertEquals("음식 상점", title(p));
        click(p, 10, ClickType.LEFT); // 황금 사과 1200
        assertEquals(800, d.money);
        assertTrue(p.getInventory().contains(Material.GOLDEN_APPLE));
        click(p, 10, ClickType.LEFT); // 잔액 부족
        assertEquals(800, d.money);

        // 광물 판매 (전부 판매)
        p.getInventory().addItem(new ItemStack(Material.DIAMOND, 3), new ItemStack(Material.IRON_INGOT, 2));
        assertTrue(p.performCommand("ore"));
        click(p, 16, ClickType.LEFT);
        assertEquals(800 + 900 + 200, d.money);
        p.getInventory().addItem(new ItemStack(Material.DIAMOND, 4));
        click(p, 14, ClickType.RIGHT);          // 1개
        assertEquals(1900 + 300, d.money);
        click(p, 14, ClickType.SHIFT_RIGHT);    // 나머지 3개 전부
        assertEquals(1900 + 1200, d.money);
        assertFalse(p.getInventory().contains(Material.DIAMOND));

        // 우편함
        d.mailbox.add(new ItemStack(Material.EMERALD, 5));
        assertTrue(p.performCommand("우편함"));
        click(p, 0, ClickType.LEFT);
        assertTrue(d.mailbox.isEmpty());
        assertTrue(p.getInventory().contains(Material.EMERALD, 5));
    }

    @Test
    void levelUpGivesMail() {
        World w = server.getWorld("world");
        PlayerMock p = server.addPlayer("Carl");
        PlayerData d = plugin.store().get(p);
        Block ore = w.getBlockAt(0, 10, 0);
        for (int i = 0; i < 4; i++) {
            ore.setType(Material.DIAMOND_ORE);
            server.getPluginManager().callEvent(new BlockBreakEvent(ore, p));
        }
        // 200 경험치 -> 레벨 2 (16 경험치 병 우편)
        assertEquals(2, d.level);
        assertEquals(0, d.exp);
        assertEquals(1, d.mailbox.size());
        assertEquals(Material.EXPERIENCE_BOTTLE, d.mailbox.get(0).getType());
        assertEquals(400, LevelModule.needed(2));
        assertTrue(p.performCommand("levelstats"));
        assertEquals("레벨 업 보상", title(p));
        click(p, 44, ClickType.LEFT);
    }

    @Test
    void teamAndMarket() {
        PlayerMock a = server.addPlayer("Dan");
        PlayerMock b = server.addPlayer("Eve");
        PlayerData da = plugin.store().get(a);
        PlayerData db = plugin.store().get(b);
        da.money = 50000;
        assertTrue(a.performCommand("팀 생성 블루"));
        assertEquals(10000, da.money);
        GlobalData.Team t = plugin.store().global().teamOf(a.getUniqueId());
        assertNotNull(t);
        a.performCommand("팀 초대 Eve");
        b.performCommand("팀 수락");
        assertEquals(t, plugin.store().global().teamOf(b.getUniqueId()));

        // 장터: 음수 가격 거부, 등록, 구매
        a.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_BLOCK, 2));
        a.performCommand("판매 -5");
        assertTrue(plugin.store().global().market.isEmpty());
        a.performCommand("판매 3000");
        assertEquals(1, plugin.store().global().market.size());
        db.money = 5000;
        assertTrue(b.performCommand("장터"));
        click(b, 9, ClickType.LEFT);
        assertTrue(plugin.store().global().market.isEmpty());
        assertEquals(2000, db.money);
        assertEquals(13000, da.money);
        assertTrue(b.getInventory().contains(Material.DIAMOND_BLOCK, 2));
    }

    @Test
    void deathKeepsProtectedItems() {
        PlayerMock p = server.addPlayer("Finn");
        PlayerData d = plugin.store().get(p);
        d.newbieStart = 0; // 뉴비 보호 해제
        p.getInventory().clear();
        p.getInventory().addItem(Items.claymoreSd(), new ItemStack(Material.DIRT, 10));
        p.setHealth(0);
        assertEquals(1, d.respawnItems.size(), "클레이모어만 보관");
        assertEquals(Items.N_CLAYMORE_SD, Msg.name(d.respawnItems.get(0)));
    }

    @Test
    void chronosUpgrade() {
        PlayerMock p = server.addPlayer("Gina");
        p.getInventory().clear();
        p.getInventory().addItem(Items.chronos(0));
        ItemStack shards = Items.timeShard();
        shards.setAmount(5);
        p.getInventory().addItem(shards);
        assertTrue(p.performCommand("chronosupgr"));
        var top = p.getOpenInventory().getTopInventory();
        assertNull(top.getItem(11), "칼날 올리는 칸은 비어 있음");
        click(p, 13, ClickType.LEFT);                  // 칼날 없이 강화 -> 거부
        int bladeSlot = -1;
        for (int i = 0; i < 36; i++) if (Items.chronosStage(p.getInventory().getItem(i)) >= 0) bladeSlot = i;
        clickBottom(p, bladeSlot);                     // 인벤토리에서 칼날 클릭 -> 올라감
        assertEquals(0, Items.chronosStage(top.getItem(11)));
        assertNull(p.getInventory().getItem(bladeSlot), "칼날은 인벤토리에서 빠짐");
        assertTrue(Msg.name(top.getItem(22)).contains("시간의 파편") && Msg.name(top.getItem(22)).contains("✔"), Msg.name(top.getItem(22)));
        click(p, 13, ClickType.LEFT);                  // 강화
        assertEquals(1, Items.chronosStage(top.getItem(11)), "올린 칼날이 그 자리에서 강화");
        assertFalse(java.util.Arrays.stream(p.getInventory().getContents()).anyMatch(it -> Msg.nameIs(it, Items.N_TIME_SHARD)), "파편 사용");
        click(p, 11, ClickType.LEFT);                  // 올린 칼날 돌려받기
        assertNull(top.getItem(11));
        int stage = -1, blades = 0;
        for (ItemStack it : p.getInventory().getContents()) if (Items.chronosStage(it) >= 0) { stage = Items.chronosStage(it); blades++; }
        assertEquals(1, stage);
        assertEquals(1, blades, "복제 없음");
        // 다시 올리고 창 닫기 -> 돌려받음
        p.performCommand("chronosupgr");
        for (int i = 0; i < 36; i++) if (Items.chronosStage(p.getInventory().getItem(i)) >= 0) bladeSlot = i;
        clickBottom(p, bladeSlot);
        p.closeInventory();
        blades = 0;
        for (ItemStack it : p.getInventory().getContents()) if (Items.chronosStage(it) >= 0) blades++;
        assertEquals(1, blades, "닫으면 돌려받음");
    }

    @Test
    void menusOpen() {
        PlayerMock p = server.addPlayer("Hank");
        String[] cmds = {"메뉴", "워프", "상점", "newbie", "enchantbook", "eat", "roulette", "yurocash", "ore", "combatshop",
                "collection", "arbiter", "glacier", "prologue", "combatpackage1", "combatpackage2", "배낭", "창열기", "levelstats"};
        for (String c : cmds) {
            assertTrue(p.performCommand(c), c);
            assertTrue(p.getOpenInventory().getTopInventory().getHolder() instanceof Gui, "GUI 열림: " + c);
            p.closeInventory();
        }
        assertTrue(p.performCommand("도움말"));
        assertTrue(p.performCommand("cash"));
        assertTrue(p.performCommand("stats Hank"));
    }

    @Test
    void navigationButtons() {
        PlayerMock p = server.addPlayer("Ivy");
        PlayerData d = plugin.store().get(p);
        d.money = 5000;
        // 음식 상점 -> 뒤로 -> 상점 -> 뒤로 -> 메뉴
        assertTrue(p.performCommand("eat"));
        Gui g = (Gui) p.getOpenInventory().getTopInventory().getHolder();
        assertEquals(Material.OAK_DOOR, g.getInventory().getItem(g.last() + 3).getType());
        assertEquals(Material.BARRIER, g.getInventory().getItem(g.last() + 4).getType());
        assertEquals(Material.PLAYER_HEAD, g.getInventory().getItem(4).getType());
        click(p, g.last() + 3, ClickType.LEFT);
        server.getScheduler().performTicks(2);
        assertEquals("상점", title(p));
        Gui shop = (Gui) p.getOpenInventory().getTopInventory().getHolder();
        click(p, shop.last() + 3, ClickType.LEFT);
        server.getScheduler().performTicks(2);
        assertEquals("서버 메뉴", title(p));
        // 구매 후 내 정보 갱신
        assertTrue(p.performCommand("eat"));
        click(p, 10, ClickType.LEFT);
        Gui food = (Gui) p.getOpenInventory().getTopInventory().getHolder();
        String lore = Msg.plain(food.getInventory().getItem(4).getItemMeta().lore().get(0));
        assertTrue(lore.contains("3,800"), lore);
        // 프로필 -> 칭호 -> 뒤로 -> 프로필
        p.performCommand("메뉴");
        Gui menu = (Gui) p.getOpenInventory().getTopInventory().getHolder();
        click(p, 19, ClickType.LEFT);
        server.getScheduler().performTicks(2);
        assertEquals("프로필", title(p));
        click(p, 16, ClickType.LEFT);
        server.getScheduler().performTicks(2);
        assertEquals("칭호 설정", title(p));
        Gui t = (Gui) p.getOpenInventory().getTopInventory().getHolder();
        click(p, t.last() + 3, ClickType.LEFT);
        server.getScheduler().performTicks(2);
        assertEquals("프로필", title(p));
        // 우편함 페이지
        for (int i = 0; i < 50; i++) d.mailbox.add(new ItemStack(Material.STONE));
        p.performCommand("우편함");
        assertTrue(title(p).contains("1/2"));
        Gui mail = (Gui) p.getOpenInventory().getTopInventory().getHolder();
        click(p, mail.last() + 8, ClickType.LEFT);
        server.getScheduler().performTicks(2);
        assertTrue(title(p).contains("2/2"));
    }

    @Test
    void specialEffectsRunWithoutErrors() {
        World w = server.getWorld("world");
        PlayerMock p = server.addPlayer("Jay");
        PlayerMock v = server.addPlayer("Kim");
        p.teleport(new org.bukkit.Location(w, 0, 64, 0));
        v.teleport(new org.bukkit.Location(w, 2, 64, 0));
        ItemStack[] weapons = {Items.quantumSword(), Items.sealedSword(), Items.thunderAxe(), Items.chaosApocalypse(), Items.ruinerScythe(),
                Items.chronos(5), Items.chronos(6), Items.claymoreSd(), Items.frostGrave(), Items.timePiecerWind()};
        v.getInventory().setBoots(Items.auroraBoots());
        v.getInventory().setChestplate(Items.voidChest());
        v.getInventory().setHelmet(Items.russellHelmet());
        for (int round = 0; round < 15; round++) {
            for (ItemStack wpn : weapons) {
                p.getInventory().setItemInMainHand(wpn);
                v.setHealth(20);
                var ev = new org.bukkit.event.entity.EntityDamageByEntityEvent(p, v, org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                        org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).withCausingEntity(p).withDirectEntity(p).build(), 5.0);
                server.getPluginManager().callEvent(ev);
            }
        }
        // 우클릭 스킬
        for (ItemStack skill : new ItemStack[]{Items.sealedSword(), Items.chronos(6), Items.newCharacteristic()}) {
            p.getInventory().setItemInMainHand(skill);
            server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerInteractEvent(p, org.bukkit.event.block.Action.RIGHT_CLICK_AIR, skill, null, org.bukkit.block.BlockFace.UP, org.bukkit.inventory.EquipmentSlot.HAND));
        }
        p.setSneaking(true);
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerInteractEvent(p, org.bukkit.event.block.Action.RIGHT_CLICK_AIR, Items.newCharacteristic(), null, org.bukkit.block.BlockFace.UP, org.bukkit.inventory.EquipmentSlot.HAND));
        // 애니메이션(회오리 15초 등)과 1초 오라가 끝까지 돌아가도 오류 없음
        server.getScheduler().performTicks(400);
        assertTrue(p.isOnline());
    }

    @Test
    void weaponHealthBonusSeparate() {
        plugin.getConfig().set("health.base", 20.0);
        plugin.getConfig().set("health.claymore-bonus", 6.0);
        plugin.getConfig().set("health.frost-bonus", 8.0);
        PlayerMock p = server.addPlayer("Lee");
        p.getInventory().clear();
        server.getScheduler().performTicks(21);
        assertEquals(20.0, kr.wave.core.module.WeaponModule.maxHealth(p), 0.01);
        p.getInventory().addItem(Items.claymoreSd());
        server.getScheduler().performTicks(20);
        assertEquals(26.0, kr.wave.core.module.WeaponModule.maxHealth(p), 0.01);
        assertEquals(0.25f, p.getWalkSpeed(), 0.001f);
        p.getInventory().addItem(Items.frostGrave());
        server.getScheduler().performTicks(20);
        assertEquals(28.0, kr.wave.core.module.WeaponModule.maxHealth(p), 0.01); // 둘 다: 큰 값(8) 하나만
        p.getInventory().clear();
        p.getInventory().addItem(Items.frostGrave());
        server.getScheduler().performTicks(20);
        assertEquals(28.0, kr.wave.core.module.WeaponModule.maxHealth(p), 0.01);
        assertEquals(0.2f, p.getWalkSpeed(), 0.001f);
    }

    private org.bukkit.entity.LivingEntity findBoss(World w) {
        for (var e : w.getLivingEntities()) if (e instanceof org.bukkit.entity.WitherSkeleton && !e.isDead()) return e;
        return null;
    }

    private void hit(PlayerMock p, org.bukkit.entity.LivingEntity boss) {
        var ev = new org.bukkit.event.entity.EntityDamageByEntityEvent(p, boss, org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).withCausingEntity(p).withDirectEntity(p).build(), 10.0);
        server.getPluginManager().callEvent(ev);
    }

    /** 열린 전리품 창에서 아이템 칸 번호들 */
    private java.util.List<Integer> lootSlots(PlayerMock p) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        var inv = p.getOpenInventory().getTopInventory();
        for (int i = 0; i < 27; i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && it.getType() != Gui.FILLER) out.add(i);
        }
        return out;
    }

    private int countInInv(PlayerMock p, Material m) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) if (it != null && it.getType() == m) n += it.getAmount();
        return n;
    }

    @Test
    void bossSoloKillOpensLoot() {
        World w = server.getWorld("world");
        PlayerMock p = server.addPlayer("Solo");
        p.setOp(true);
        p.teleport(new org.bukkit.Location(w, 0.5, 100, 0.5));
        assertTrue(p.performCommand("wave boss setcenter"));
        assertTrue(p.performCommand("wave boss spawn"));
        var boss = findBoss(w);
        assertNotNull(boss);
        assertEquals(400.0, boss.getHealth(), 0.01);
        hit(p, boss);
        server.getScheduler().performTicks(40);
        p.getInventory().clear();
        boss.setHealth(0);
        server.getScheduler().performTicks(1);

        // 혼자여도 창이 열리고 5개가 무작위 칸에
        assertTrue(guiOpen(p));
        assertEquals("천공의 전리품", title(p));
        var slots = lootSlots(p);
        assertEquals(5, slots.size(), "전리품 5개");
        // 좌클릭 = 바로 획득, 우클릭 = 포기(제단에 떨어짐)
        int first = slots.get(0);
        ItemStack want = p.getOpenInventory().getTopInventory().getItem(first).clone();
        click(p, first, ClickType.LEFT);
        assertTrue(countInInv(p, want.getType()) >= want.getAmount(), "좌클릭한 아이템 획득");
        assertEquals(4, lootSlots(p).size());
        click(p, slots.get(1), ClickType.RIGHT);
        assertEquals(3, lootSlots(p).size());
        long onGround = w.getEntitiesByClass(org.bukkit.entity.Item.class).size();
        assertEquals(1, onGround, "포기한 아이템은 바닥에");
        for (int sl : slots.subList(2, 5)) click(p, sl, ClickType.LEFT);
        assertFalse(guiOpen(p), "전부 결정되면 창 닫힘");
    }

    @Test
    void lootTwoPlayersAndClaymore() {
        World w = server.getWorld("world");
        PlayerMock a = server.addPlayer("Rin");
        a.setOp(true);
        a.teleport(new org.bukkit.Location(w, 0.5, 100, 0.5));
        a.performCommand("wave boss setcenter");
        a.performCommand("wave boss spawn");
        var boss = findBoss(w);
        PlayerMock b = server.addPlayer("Sol");
        PlayerMock c = server.addPlayer("Tae");
        server.getScheduler().performTicks(5);   // 첫 접속 처리(스폰 이동) 끝내기
        hit(a, boss);
        hit(b, boss);
        a.getInventory().clear();
        b.getInventory().clear();
        boss.setHealth(0);
        server.getScheduler().performTicks(1);
        assertTrue(guiOpen(a));
        assertTrue(guiOpen(b));
        assertFalse(guiOpen(c), "참여 안 한 사람은 창 없음");
        int slot = lootSlots(a).get(0);
        Material m = a.getOpenInventory().getTopInventory().getItem(slot).getType();
        click(a, slot, ClickType.LEFT);   // a 주사위
        assertEquals(5, lootSlots(a).size(), "b 가 아직 선택 안 함");
        click(b, slot, ClickType.RIGHT);  // b 포기 -> a 획득
        assertTrue(countInInv(a, m) > 0);
        assertEquals(0, countInInv(b, m));

        // 클레이모어가 섞인 전리품 (testloot claymore)
        server.getScheduler().performTicks(20 * 31); // 이전 분배 종료
        PlayerMock d = server.addPlayer("Uma");
        d.setOp(true);
        server.getScheduler().performTicks(5);
        assertTrue(d.performCommand("wave boss testloot claymore"));
        var ds = lootSlots(d);
        assertEquals(5, ds.size());
        int clay = -1;
        for (int sl : ds) if (Msg.nameIs(d.getOpenInventory().getTopInventory().getItem(sl), Items.N_CLAYMORE_SD)) clay = sl;
        assertTrue(clay >= 0, "클레이모어 포함");
        click(d, clay, ClickType.LEFT);
        boolean got = false;
        for (ItemStack it : d.getInventory().getContents()) if (Msg.nameIs(it, Items.N_CLAYMORE_SD)) got = true;
        assertTrue(got);
        String all = drain(d);
        assertTrue(all.contains("Arbiter of Heaven"), all);
        assertFalse(all.contains("전리품이 나타났습니다"), all);
        assertFalse(all.contains("전리품에 있습니다"), all);
    }

    @Test
    void lootTimeoutDropsRest() {
        World w = server.getWorld("world");
        PlayerMock admin = server.addPlayer("Op2");
        admin.setOp(true);
        server.getScheduler().performTicks(5);
        admin.teleport(new org.bukkit.Location(w, 0.5, 100, 0.5));
        admin.performCommand("wave boss setcenter");
        assertTrue(admin.performCommand("wave boss testloot"));
        assertEquals(5, lootSlots(admin).size());
        server.getScheduler().performTicks(20 * 31);
        assertFalse(guiOpen(admin));
        assertEquals(5, w.getEntitiesByClass(org.bukkit.entity.Item.class).size(), "아무도 안 고른 5개는 제단에");
    }

    private static String drain(PlayerMock p) {
        StringBuilder sb = new StringBuilder();
        net.kyori.adventure.text.Component c;
        while ((c = p.nextComponentMessage()) != null) sb.append(Msg.plain(c)).append('\n');
        return sb.toString();
    }

    private static boolean hasHover(net.kyori.adventure.text.Component c) {
        if (c.hoverEvent() != null) return true;
        for (var ch : c.children()) if (hasHover(ch)) return true;
        return false;
    }

    @Test
    void killBroadcastHoverAndPassMenu() {
        World w = server.getWorld("world");
        PlayerMock k = server.addPlayer("Kai");
        PlayerMock v = server.addPlayer("Vee");
        server.getScheduler().performTicks(5);
        drain(k);
        for (ItemStack tool : new ItemStack[]{Items.claymoreSd(), Items.frostGrave(), Items.quantumSword()}) {
            k.getInventory().setItemInMainHand(tool);
            v.setHealth(0.0);
            var ev = new org.bukkit.event.entity.PlayerDeathEvent(v, org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).withCausingEntity(k).withDirectEntity(k).build(), new java.util.ArrayList<>(), 0, null);
            v.setKiller(k);
            server.getPluginManager().callEvent(ev);
            boolean hover = false;
            net.kyori.adventure.text.Component c;
            StringBuilder sb = new StringBuilder();
            while ((c = k.nextComponentMessage()) != null) { sb.append(Msg.plain(c)).append('\n'); if (hasHover(c)) hover = true; }
            assertTrue(hover, "아이템 이름에 마우스 정보: " + Msg.name(tool) + "\n" + sb);
            assertTrue(sb.toString().contains(Msg.name(tool)), sb.toString());
            v.setHealth(20.0);
        }
        // Shift+F 메뉴에 프리미엄 패스
        k.setSneaking(true);
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerSwapHandItemsEvent(k, new ItemStack(Material.AIR), new ItemStack(Material.AIR)));
        assertTrue(guiOpen(k));
        ItemStack pass = k.getOpenInventory().getTopInventory().getItem(22);
        assertEquals(Material.EXPERIENCE_BOTTLE, pass.getType());
        assertTrue(Msg.name(pass).contains("프리미엄 패스"));
    }

    /** 제단 밖(100,64,100)에서 들어와 보스를 소환하고 때린 상태 */
    private org.bukkit.entity.LivingEntity arenaSetup(PlayerMock admin, PlayerMock p, World w) {
        admin.setOp(true);
        server.getScheduler().performTicks(5);
        admin.teleport(new org.bukkit.Location(w, 0.5, 100, 0.5));
        admin.performCommand("wave boss setcenter");
        admin.teleport(new org.bukkit.Location(w, 500, 64, 500));
        p.teleport(new org.bukkit.Location(w, 100, 64, 100));
        p.teleport(new org.bukkit.Location(w, 3, 100, 3));         // 제단으로 이동 -> 이전 위치 기록
        admin.performCommand("wave boss spawn");
        var boss = findBoss(w);
        hit(p, boss);
        return boss;
    }

    @Test
    void arenaLockAndReturnAfterAllRolled() {
        World w = server.getWorld("world");
        PlayerMock admin = server.addPlayer("Adm");
        PlayerMock p = server.addPlayer("Pip");
        var boss = arenaSetup(admin, p, w);

        // 전투 중 명령어 / 텔레포트 차단
        var cmd = new org.bukkit.event.player.PlayerCommandPreprocessEvent(p, "/spawn");
        server.getPluginManager().callEvent(cmd);
        assertTrue(cmd.isCancelled(), "명령어 차단");
        var ok = new org.bukkit.event.player.PlayerCommandPreprocessEvent(p, "/루팅");
        server.getPluginManager().callEvent(ok);
        assertFalse(ok.isCancelled(), "/루팅 허용");
        p.teleport(new org.bukkit.Location(w, 300, 64, 300), org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.COMMAND);
        assertTrue(p.getLocation().distance(new org.bukkit.Location(w, 3, 100, 3)) < 1, "텔레포트 차단: " + p.getLocation());
        p.teleport(new org.bukkit.Location(w, 300, 64, 300), org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.ENDER_PEARL);
        assertTrue(p.getLocation().distance(new org.bukkit.Location(w, 3, 100, 3)) < 1, "진주로 탈출 차단");

        p.getInventory().clear();
        boss.setHealth(0);
        server.getScheduler().performTicks(1);
        assertTrue(guiOpen(p));
        // 창을 닫아도 다시 열림
        p.closeInventory();
        server.getScheduler().performTicks(2);
        assertTrue(guiOpen(p), "닫아도 다시 열림");
        // 분배 중에도 차단
        var cmd2 = new org.bukkit.event.player.PlayerCommandPreprocessEvent(p, "/home");
        server.getPluginManager().callEvent(cmd2);
        assertTrue(cmd2.isCancelled());

        for (int sl : lootSlots(p)) click(p, sl, ClickType.LEFT);   // 전부 굴림
        assertFalse(guiOpen(p), "분배 끝나면 창 닫힘");
        server.getScheduler().performTicks(20 * 8);
        assertTrue(p.getLocation().distance(new org.bukkit.Location(w, 3, 100, 3)) < 1, "10초 전에는 그대로");
        server.getScheduler().performTicks(20 * 4);
        assertEquals(new org.bukkit.Location(w, 100, 64, 100), p.getLocation(), "10초 뒤 이전 위치로");
        // 귀환 후에는 자유
        var cmd3 = new org.bukkit.event.player.PlayerCommandPreprocessEvent(p, "/spawn");
        server.getPluginManager().callEvent(cmd3);
        assertFalse(cmd3.isCancelled());
    }

    @Test
    void returnAfterOneMinuteWithoutRolling() {
        World w = server.getWorld("world");
        PlayerMock admin = server.addPlayer("Adm2");
        PlayerMock p = server.addPlayer("Quo");
        var boss = arenaSetup(admin, p, w);
        boss.setHealth(0);
        server.getScheduler().performTicks(1);
        assertTrue(guiOpen(p));
        server.getScheduler().performTicks(20 * 31);
        assertFalse(guiOpen(p), "30초 뒤 분배 종료");
        assertTrue(p.getLocation().distance(new org.bukkit.Location(w, 3, 100, 3)) < 1, "1분 전에는 그대로");
        server.getScheduler().performTicks(20 * 31);
        assertEquals(new org.bukkit.Location(w, 100, 64, 100), p.getLocation(), "1분 뒤 이전 위치로");
    }

    @Test
    void fightTimeLimitFiveMinutes() {
        World w = server.getWorld("world");
        PlayerMock admin = server.addPlayer("Adm3");
        PlayerMock p = server.addPlayer("Ray");
        arenaSetup(admin, p, w);
        for (int i = 0; i < 48; i++) { p.setHealth(20.0); server.getScheduler().performTicks(100); }   // 4분 (보스 공격에 죽지 않게)
        assertFalse(p.isDead());
        assertNotNull(findBoss(w), "4분엔 아직 있음");
        for (int i = 0; i < 13; i++) { p.setHealth(20.0); server.getScheduler().performTicks(100); }
        assertNull(findBoss(w), "5분 뒤 보스 사라짐");
        assertEquals(new org.bukkit.Location(w, 100, 64, 100), p.getLocation(), "이전 위치로");
    }

    private static int tickets(PlayerMock p) {
        int cnt = 0;
        for (ItemStack it : p.getInventory().getContents()) if (Msg.nameIs(it, Items.N_ALTAR_TICKET)) cnt += it.getAmount();
        return cnt;
    }

    private void rightClickTicket(PlayerMock p) {
        ItemStack t = null;
        for (ItemStack it : p.getInventory().getStorageContents()) if (Msg.nameIs(it, Items.N_ALTAR_TICKET)) t = it;
        p.getInventory().setItemInMainHand(t);
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerInteractEvent(p, org.bukkit.event.block.Action.RIGHT_CLICK_AIR, p.getInventory().getItemInMainHand(), null, org.bukkit.block.BlockFace.UP, org.bukkit.inventory.EquipmentSlot.HAND));
    }

    @Test
    void altarTicketMenuOnly() {
        World w = server.getWorld("world");
        PlayerMock admin = server.addPlayer("AdmT");
        PlayerMock p = server.addPlayer("Tom");
        admin.setOp(true);
        server.getScheduler().performTicks(5);
        admin.teleport(new org.bukkit.Location(w, 0.5, 100, 0.5));
        admin.performCommand("wave boss setcenter");
        admin.teleport(new org.bukkit.Location(w, 500, 64, 500));
        org.bukkit.Location home = new org.bukkit.Location(w, 200, 64, 200);
        p.teleport(home);
        p.getInventory().clear();
        assertTrue(admin.performCommand("wave boss ticket Tom 2"));
        assertEquals(2, tickets(p));

        // 명령어: 메뉴만 열리고 이동 안 함
        assertTrue(p.performCommand("천공의제단"));
        assertTrue(guiOpen(p));
        assertEquals("천공의 제단", title(p));
        assertEquals(home, p.getLocation());
        assertEquals(2, tickets(p));

        // 강림 전: 메뉴에서 클릭해도 거부
        assertNull(findBoss(w));
        assertEquals(Material.BARRIER, p.getOpenInventory().getTopInventory().getItem(13).getType());
        click(p, 13, ClickType.LEFT);
        assertEquals(home, p.getLocation(), "강림 전 거부");
        assertEquals(2, tickets(p), "거부 시 소모 안 됨");
        p.closeInventory();

        // 강림 후: 우클릭 -> 메뉴 -> 클릭 = 이동, 1개 소모
        admin.performCommand("wave boss spawn");
        rightClickTicket(p);
        assertTrue(guiOpen(p), "우클릭은 메뉴만 연다");
        assertEquals(home, p.getLocation());
        assertEquals(2, tickets(p));
        assertEquals(Material.ENDER_EYE, p.getOpenInventory().getTopInventory().getItem(13).getType());
        click(p, 13, ClickType.LEFT);
        assertEquals(new org.bukkit.Location(w, 0.5, 98, 26.5, 180, 0), p.getLocation(), "남쪽 입구 도착");
        assertEquals(1, tickets(p), "1회용: 1개 소모");

        // 관리자도 이동권 없으면 거부
        admin.getInventory().clear();
        assertTrue(admin.performCommand("천공의제단"));
        click(admin, 13, ClickType.LEFT);
        assertEquals(new org.bukkit.Location(w, 500, 64, 500), admin.getLocation(), "관리자도 이동권 필요");

        // 처치 후 귀환 -> 이동권 쓰기 전 위치
        var boss = findBoss(w);
        hit(p, boss);
        boss.setHealth(0);
        server.getScheduler().performTicks(20 * 62);
        assertEquals(home, p.getLocation(), "이동권으로 오기 전 위치로 귀환");
    }

    @Test
    void bossDescendsWithoutPlayersNearby() {
        World w = server.getWorld("world");
        PlayerMock admin = server.addPlayer("AdmS");
        admin.setOp(true);
        server.getScheduler().performTicks(5);
        admin.teleport(new org.bukkit.Location(w, 0.5, 100, 0.5));
        admin.performCommand("wave boss setcenter");
        admin.teleport(new org.bukkit.Location(w, 900, 64, 900));   // 제단 근처에 아무도 없음
        server.getScheduler().performTicks(40);
        assertNotNull(findBoss(w), "시간이 되면 아무도 없어도 강림");
    }

    @Test
    void skyWorldBlocksAllCommands() {
        World sky = server.addSimpleWorld("sky");
        PlayerMock p = server.addPlayer("Sky1");
        PlayerMock op = server.addPlayer("SkyOp");
        op.setOp(true);
        server.getScheduler().performTicks(10);
        p.teleport(new org.bukkit.Location(sky, 0, 100, 0));
        op.teleport(new org.bukkit.Location(sky, 0, 100, 0));
        for (String c : new String[]{"/spawn", "/home", "/msg a b", "/tpa x", "/essentials:spawn", "/메뉴"}) {
            var e = new org.bukkit.event.player.PlayerCommandPreprocessEvent(p, c);
            server.getPluginManager().callEvent(e);
            assertTrue(e.isCancelled(), "천공 월드 명령어 차단: " + c);
        }
        var opCmd = new org.bukkit.event.player.PlayerCommandPreprocessEvent(op, "/wave boss info");
        server.getPluginManager().callEvent(opCmd);
        assertFalse(opCmd.isCancelled(), "관리자 예외");
        // 다른 월드에서는 정상
        PlayerMock q = server.addPlayer("Ground");
        server.getScheduler().performTicks(5);
        q.teleport(new org.bukkit.Location(server.getWorld("world"), 0, 64, 0));
        var e = new org.bukkit.event.player.PlayerCommandPreprocessEvent(q, "/spawn");
        server.getPluginManager().callEvent(e);
        assertFalse(e.isCancelled());
    }

    private static String allText(org.bukkit.inventory.Inventory inv) {
        StringBuilder sb = new StringBuilder();
        for (ItemStack it : inv.getContents()) {
            if (it == null || !it.hasItemMeta()) continue;
            sb.append(Msg.name(it)).append('\n');
            if (it.getItemMeta().lore() != null) for (var l : it.getItemMeta().lore()) sb.append(Msg.plain(l)).append('\n');
        }
        return sb.toString();
    }

    @Test
    void altarMenuLayoutAndLootList() {
        World w = server.getWorld("world");
        PlayerMock admin = server.addPlayer("AdmL");
        admin.setOp(true);
        server.getScheduler().performTicks(5);
        admin.teleport(new org.bukkit.Location(w, 0.5, 100, 0.5));
        admin.performCommand("wave boss setcenter");
        admin.teleport(new org.bukkit.Location(w, 500, 64, 500));
        assertTrue(admin.performCommand("천공의제단"));
        var inv = admin.getOpenInventory().getTopInventory();
        assertEquals(36, inv.getSize(), "4줄");
        assertEquals(Material.BARRIER, inv.getItem(31).getType(), "닫기 버튼 맨 아래 가운데");
        for (int i = 27; i < 36; i++) if (i != 31) assertEquals(Gui.FILLER, inv.getItem(i).getType(), "아래 줄은 닫기만");
        assertEquals(Material.CHEST, inv.getItem(15).getType());
        String t = allText(inv);
        assertFalse(t.contains("%") || t.contains("확률"), t);

        click(admin, 15, ClickType.LEFT);
        server.getScheduler().performTicks(2);
        assertEquals("천공의 전리품 목록", title(admin));
        var li = admin.getOpenInventory().getTopInventory();
        String lt = allText(li);
        assertFalse(lt.contains("%") || lt.contains("확률"), lt);
        assertTrue(Msg.nameIs(li.getItem(4), Items.N_CLAYMORE_SD));
        assertTrue(lt.contains("수량"), lt);
        int n = 0;
        for (ItemStack it : li.getContents()) if (it != null && it.getType() != Gui.FILLER && it.getType() != Material.OAK_DOOR && it.getType() != Material.BARRIER) n++;
        assertEquals(14, n, "기본 13종 + 클레이모어");
        // 뒤로 가기 -> 천공의 제단
        int back = li.getSize() - 9 + 3;
        assertEquals(Material.OAK_DOOR, li.getItem(back).getType());
        click(admin, back, ClickType.LEFT);
        server.getScheduler().performTicks(2);
        assertEquals("천공의 제단", title(admin));
    }

    @Test
    void mainMenuHasDailyShopAndAltar() {
        World w = server.getWorld("world");
        PlayerMock admin = server.addPlayer("AdmM");
        admin.setOp(true);
        server.getScheduler().performTicks(5);
        admin.teleport(new org.bukkit.Location(w, 0.5, 100, 0.5));
        admin.performCommand("wave boss setcenter");
        admin.teleport(new org.bukkit.Location(w, 500, 64, 500));
        admin.setSneaking(true);
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerSwapHandItemsEvent(admin, new ItemStack(Material.AIR), new ItemStack(Material.AIR)));
        var inv = admin.getOpenInventory().getTopInventory();
        assertEquals(Material.GOLD_NUGGET, inv.getItem(23).getType());
        assertTrue(Msg.name(inv.getItem(23)).contains("일일 상점"));
        assertEquals(Material.END_CRYSTAL, inv.getItem(24).getType());
        assertTrue(allText(inv).contains("다음 강림까지"), allText(inv));
        // 천공의 제단 -> 뒤로 가기 -> 서버 메뉴
        click(admin, 24, ClickType.LEFT);
        server.getScheduler().performTicks(2);
        assertEquals("천공의 제단", title(admin));
        var ai = admin.getOpenInventory().getTopInventory();
        assertEquals(Material.OAK_DOOR, ai.getItem(30).getType());
        assertEquals(Material.BARRIER, ai.getItem(31).getType());
        // 전리품 목록 -> 뒤로 -> 제단(뒤로 버튼 유지)
        click(admin, 15, ClickType.LEFT);
        server.getScheduler().performTicks(2);
        var li = admin.getOpenInventory().getTopInventory();
        click(admin, li.getSize() - 6, ClickType.LEFT);
        server.getScheduler().performTicks(2);
        assertEquals("천공의 제단", title(admin));
        assertEquals(Material.OAK_DOOR, admin.getOpenInventory().getTopInventory().getItem(30).getType());
        click(admin, 30, ClickType.LEFT);
        server.getScheduler().performTicks(2);
        assertEquals("서버 메뉴", title(admin));
        // 버튼 클릭 소리
        admin.assertSoundHeard("ui.button.click");
    }

    @Test
    void skyWorldBlocksMenuElytraPearl() {
        World sky = server.addSimpleWorld("sky");
        PlayerMock p = server.addPlayer("Sky2");
        PlayerMock op = server.addPlayer("SkyOp2");
        op.setOp(true);
        server.getScheduler().performTicks(10);
        org.bukkit.Location in = new org.bukkit.Location(sky, 0, 100, 0);
        p.teleport(in);
        op.teleport(in);

        // Shift+F 메뉴 차단
        p.closeInventory();
        p.setSneaking(true);
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerSwapHandItemsEvent(p, new ItemStack(Material.AIR), new ItemStack(Material.AIR)));
        server.getScheduler().performTicks(2);
        assertFalse(guiOpen(p), "제단에서 Shift+F 메뉴 차단");
        // 관리자는 가능
        op.setSneaking(true);
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerSwapHandItemsEvent(op, new ItemStack(Material.AIR), new ItemStack(Material.AIR)));
        assertTrue(guiOpen(op));

        // 엔더 진주 사용 차단
        ItemStack pearl = new ItemStack(Material.ENDER_PEARL, 16);
        p.getInventory().setItemInMainHand(pearl);
        var use = new org.bukkit.event.player.PlayerInteractEvent(p, org.bukkit.event.block.Action.RIGHT_CLICK_AIR, pearl, null, org.bukkit.block.BlockFace.UP, org.bukkit.inventory.EquipmentSlot.HAND);
        server.getPluginManager().callEvent(use);
        assertEquals(org.bukkit.event.Event.Result.DENY, use.useItemInHand(), "진주 사용 차단");
        // 진주/플러그인 이동 차단 (제단 안쪽으로도, 밖으로도)
        p.teleport(new org.bukkit.Location(sky, 5, 100, 5), org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.ENDER_PEARL);
        assertEquals(in, p.getLocation());
        p.teleport(new org.bukkit.Location(server.getWorld("world"), 0, 64, 0), org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
        assertEquals(in, p.getLocation(), "보스가 없어도 천공 월드 밖으로 이동 불가");

        // 겉날개 차단
        var glide = new org.bukkit.event.entity.EntityToggleGlideEvent(p, true);
        server.getPluginManager().callEvent(glide);
        assertTrue(glide.isCancelled(), "겉날개 차단");
        var glideOp = new org.bukkit.event.entity.EntityToggleGlideEvent(op, true);
        server.getPluginManager().callEvent(glideOp);
        assertFalse(glideOp.isCancelled());

        // 후렴과
        var chorus = new org.bukkit.event.player.PlayerItemConsumeEvent(p, new ItemStack(Material.CHORUS_FRUIT), org.bukkit.inventory.EquipmentSlot.HAND);
        server.getPluginManager().callEvent(chorus);
        assertTrue(chorus.isCancelled());
    }

    @Test
    void fightTimerPausesWhenAltarEmpty() {
        World w = server.getWorld("world");
        PlayerMock admin = server.addPlayer("AdmP");
        admin.setOp(true);
        server.getScheduler().performTicks(5);
        admin.teleport(new org.bukkit.Location(w, 0.5, 100, 0.5));
        admin.performCommand("wave boss setcenter");
        admin.performCommand("wave boss spawn");
        admin.teleport(new org.bukkit.Location(w, 900, 64, 900));   // 아무도 없음
        server.getScheduler().performTicks(20 * 60 * 7);
        assertNotNull(findBoss(w), "아무도 없으면 5분이 흐르지 않음");
        // 들어오면 그때부터 5분
        PlayerMock p = server.addPlayer("Pau");
        server.getScheduler().performTicks(5);
        p.teleport(new org.bukkit.Location(w, 100, 64, 100));
        p.teleport(new org.bukkit.Location(w, 3, 100, 3));
        for (int i = 0; i < 61; i++) { p.setHealth(20.0); server.getScheduler().performTicks(100); }
        assertNull(findBoss(w), "들어온 뒤 5분이 지나면 사라짐");
    }

    @Test
    void claymoreSplashBlindSlowDamage() {
        World w = server.getWorld("world");
        PlayerMock a = server.addPlayer("Atk");
        PlayerMock v = server.addPlayer("Vic");
        PlayerMock near = server.addPlayer("Near");
        PlayerMock far = server.addPlayer("Far");
        server.getScheduler().performTicks(5);
        a.teleport(new org.bukkit.Location(w, 0, 64, 0));
        v.teleport(new org.bukkit.Location(w, 2, 64, 0));
        near.teleport(new org.bukkit.Location(w, 4, 64, 1));   // 피격자에게서 약 2.2칸
        far.teleport(new org.bukkit.Location(w, 20, 64, 0));
        var zombie = w.spawn(new org.bukkit.Location(w, 2, 64, 2), org.bukkit.entity.Zombie.class);
        for (PlayerMock pm : new PlayerMock[]{v, near, far}) pm.setHealth(20.0);
        double zHp = zombie.getHealth();
        a.getInventory().setItemInMainHand(Items.claymoreSd());
        var ev = new org.bukkit.event.entity.EntityDamageByEntityEvent(a, v, org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).withCausingEntity(a).withDirectEntity(a).build(), 5.0);
        server.getPluginManager().callEvent(ev);
        for (org.bukkit.entity.LivingEntity le : new org.bukkit.entity.LivingEntity[]{near, zombie}) {
            assertTrue(le.hasPotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS), "실명: " + le.getName());
            assertTrue(le.hasPotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS), "구속: " + le.getName());
        }
        assertEquals(17.0, near.getHealth(), 0.01, "약간의 피해 (3)");
        assertEquals(zHp - 3, zombie.getHealth(), 0.01);
        assertFalse(far.hasPotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS), "범위 밖");
        assertEquals(20.0, far.getHealth(), 0.01);
        assertFalse(a.hasPotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS), "공격자 제외");
    }

    @Test
    void natureBootsSeasonKeepLore() {
        ItemStack b = Items.natureBoots();
        StringBuilder sb = new StringBuilder();
        for (var l : b.getItemMeta().lore()) sb.append(Msg.plain(l)).append('\n');
        assertTrue(sb.toString().contains("해당 아이템은 시즌이 지나도 유지됩니다."), sb.toString());
        // 예전에 받은 신발(문구 없음)도 착용하면 갱신
        PlayerMock p = server.addPlayer("Boot");
        server.getScheduler().performTicks(5);
        ItemStack old = Items.natureBoots();
        var meta = old.getItemMeta();
        var lore = new java.util.ArrayList<>(meta.lore());
        lore.removeIf(c -> Msg.plain(c).contains("시즌이 지나도"));
        meta.lore(lore);
        old.setItemMeta(meta);
        p.getInventory().setBoots(old);
        server.getScheduler().performTicks(25);
        int count = 0;
        for (var l : p.getInventory().getBoots().getItemMeta().lore()) if (Msg.plain(l).contains("시즌이 지나도 유지")) count++;
        assertEquals(1, count);
    }

    @Test
    void warpMenuFourRows() {
        PlayerMock p = server.addPlayer("Wrp");
        server.getScheduler().performTicks(5);
        assertTrue(p.performCommand("워프"));
        var inv = p.getOpenInventory().getTopInventory();
        assertEquals(36, inv.getSize(), "4줄");
        assertEquals(Material.FILLED_MAP, inv.getItem(4).getType());
        assertEquals(Material.COMPASS, inv.getItem(10).getType());
        assertEquals(Material.GRASS_BLOCK, inv.getItem(12).getType());
        assertEquals(Material.RED_WOOL, inv.getItem(14).getType());
        assertEquals(Material.RECOVERY_COMPASS, inv.getItem(16).getType());
        assertEquals(Material.RED_BED, inv.getItem(19).getType());
        assertEquals(Material.WHITE_BED, inv.getItem(21).getType());
        assertEquals(Material.END_CRYSTAL, inv.getItem(23).getType());
        assertEquals(Material.GRAY_WOOL, inv.getItem(25).getType(), "뉴비 패스 없음");
        assertEquals(Material.OAK_DOOR, inv.getItem(30).getType());
        assertEquals(Material.BARRIER, inv.getItem(31).getType());
        // 천공의 제단 -> 뒤로 -> 워프
        click(p, 23, ClickType.LEFT);
        server.getScheduler().performTicks(2);
        assertEquals("천공의 제단", title(p));
        click(p, 30, ClickType.LEFT);
        server.getScheduler().performTicks(2);
        assertEquals("워프 메뉴", title(p));
        // 전투 중에는 메뉴 버튼으로 이동 불가
        plugin.module(kr.wave.core.module.CombatTagModule.class).tag(p);
        click(p, 10, ClickType.LEFT);
        assertFalse(guiOpen(p), "전투 중 -> 창 닫고 거부");
        // 뒤로 -> 서버 메뉴
        assertTrue(p.performCommand("메뉴"));
    }

    @Test
    void anvilCannotForgeServerItemNames() {
        assertTrue(kr.wave.core.module.CoreModule.protectedName("[☀] 천공의 제단 이동권"));
        assertTrue(kr.wave.core.module.CoreModule.protectedName("[⚡] [Lv.sd] 천공의 심판자 클레이모어"));
        assertTrue(kr.wave.core.module.CoreModule.protectedName("[PVP] 고급 전투 패키지"));
        assertTrue(kr.wave.core.module.CoreModule.protectedName("[칭호] [Ace]"));
        assertTrue(kr.wave.core.module.CoreModule.protectedName("닉네임 색상 변경권 (Red)"));
        assertTrue(kr.wave.core.module.CoreModule.protectedName("[🪙] 캐시 수표"));
        assertFalse(kr.wave.core.module.CoreModule.protectedName("내 다이아 검"));
        assertFalse(kr.wave.core.module.CoreModule.protectedName("Sword of Steve"));
    }

    @Test
    void mailClickTakesTheRightItem() {
        PlayerMock p = server.addPlayer("Mailer");
        server.getScheduler().performTicks(5);
        PlayerData d = plugin.store().get(p);
        p.getInventory().clear();
        d.mailbox.add(new ItemStack(Material.DIAMOND, 1));
        d.mailbox.add(new ItemStack(Material.EMERALD, 2));
        d.mailbox.add(new ItemStack(Material.GOLD_INGOT, 3));
        assertTrue(p.performCommand("우편함"));
        click(p, 0, ClickType.LEFT);   // 다이아
        click(p, 1, ClickType.LEFT);   // 같은 틱에 두 번째 칸(에메랄드) - 예전엔 금괴가 나왔음
        assertEquals(1, countInInv(p, Material.DIAMOND));
        assertEquals(2, countInInv(p, Material.EMERALD));
        assertEquals(0, countInInv(p, Material.GOLD_INGOT));
        assertEquals(1, d.mailbox.size());
    }

    @Test
    void placedOreGivesNoLevelXp() {
        World w = server.getWorld("world");
        PlayerMock p = server.addPlayer("Miner2");
        server.getScheduler().performTicks(5);
        PlayerData d = plugin.store().get(p);
        long before = d.exp;
        var b = w.getBlockAt(10, 60, 10);
        b.setType(Material.DIAMOND_ORE);
        server.getPluginManager().callEvent(new org.bukkit.event.block.BlockPlaceEvent(b, b.getState(), w.getBlockAt(10, 59, 10), new ItemStack(Material.DIAMOND_ORE), p, true, org.bukkit.inventory.EquipmentSlot.HAND));
        server.getPluginManager().callEvent(new org.bukkit.event.block.BlockBreakEvent(b, p));
        assertEquals(before, d.exp, "설치한 광석은 경험치 없음");
        var natural = w.getBlockAt(12, 60, 12);
        natural.setType(Material.DIAMOND_ORE);
        server.getPluginManager().callEvent(new org.bukkit.event.block.BlockBreakEvent(natural, p));
        assertTrue(d.exp > before || d.level > 1, "자연 광석은 경험치");
    }

    @Test
    void welcomePanelAndJoinMessage() {
        PlayerMock other = server.addPlayer("Old1");
        server.getScheduler().performTicks(5);
        drain(other);
        PlayerMock p = server.addPlayer("Fresh1");
        PlayerData d = plugin.store().get(p);
        d.mailbox.add(new ItemStack(Material.STONE));
        server.getScheduler().performTicks(80);
        String mine = drain(p);
        assertTrue(mine.contains("읽지 않은 우편이 1개"), mine);
        assertFalse(mine.contains("Shift + F"), "간결하게: 우편/강림만");
        assertFalse(mine.contains("레벨 "), mine);
        String others = drain(other);
        assertTrue(others.contains("Fresh1"), others);
    }

    @Test
    void duelAcceptBlockedWhenBusy() {
        PlayerMock a = server.addPlayer("DuA");
        PlayerMock b = server.addPlayer("DuB");
        server.getScheduler().performTicks(5);
        assertTrue(a.performCommand("duel DuB 노멀"));
        plugin.module(kr.wave.core.module.CombatTagModule.class).tag(b);
        drain(b);
        assertTrue(b.performCommand("accept DuA"));
        assertFalse(plugin.module(kr.wave.core.module.DuelModule.class).inDuel(b), "전투 중이면 대전 시작 안 됨");
    }

    /** 이 글자 조각이 실제로 어떤 효과로 보이는지 (부모 스타일 상속 포함) */
    private static boolean obfuscatedText(net.kyori.adventure.text.Component c, String needle, boolean parentObf) {
        var st = c.decoration(net.kyori.adventure.text.format.TextDecoration.OBFUSCATED);
        boolean obf = st == net.kyori.adventure.text.format.TextDecoration.State.TRUE
                || (st == net.kyori.adventure.text.format.TextDecoration.State.NOT_SET && parentObf);
        if (c instanceof net.kyori.adventure.text.TextComponent t && t.content().contains(needle) && obf) return true;
        for (var ch : c.children()) if (obfuscatedText(ch, needle, obf)) return true;
        return false;
    }

    @Test
    void claymoreKillLineNotScrambled() {
        World w = server.getWorld("world");
        PlayerMock k = server.addPlayer("KillC");
        PlayerMock v = server.addPlayer("VicC");
        server.getScheduler().performTicks(5);
        drain(k);
        k.getInventory().setItemInMainHand(Items.claymoreSd());
        v.setHealth(0.0);
        v.setKiller(k);
        server.getPluginManager().callEvent(new org.bukkit.event.entity.PlayerDeathEvent(v, org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).withCausingEntity(k).withDirectEntity(k).build(), new java.util.ArrayList<>(), 0, null));
        net.kyori.adventure.text.Component c;
        boolean found = false;
        while ((c = k.nextComponentMessage()) != null) {
            if (Msg.plain(c).contains("클레이모어")) {
                found = true;
                assertFalse(obfuscatedText(c, "클레이모어", false), "아이템 이름이 &k 로 깨지면 안 됨: " + net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().serialize(c));
                assertFalse(obfuscatedText(c, "(", false), "좌표도 깨지면 안 됨");
            }
        }
        assertTrue(found);
    }

    @Test
    void menuBlockedInCombat() {
        PlayerMock p = server.addPlayer("Fighter");
        server.getScheduler().performTicks(5);
        plugin.module(kr.wave.core.module.CombatTagModule.class).tag(p);
        p.setSneaking(true);
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerSwapHandItemsEvent(p, new ItemStack(Material.AIR), new ItemStack(Material.AIR)));
        server.getScheduler().performTicks(1);
        assertFalse(guiOpen(p), "전투 중 Shift+F 메뉴 차단");
        assertTrue(drain(p).contains("전투 중에는 메뉴를 사용할 수 없습니다"));
    }

    @Test
    void deathInAltarNoReentryAndRespawnAtLastPos() {
        World w = server.getWorld("world");
        PlayerMock admin = server.addPlayer("AdmD");
        PlayerMock p = server.addPlayer("Dier");
        admin.setOp(true);
        server.getScheduler().performTicks(5);
        admin.teleport(new org.bukkit.Location(w, 0.5, 100, 0.5));
        admin.performCommand("wave boss setcenter");
        admin.teleport(new org.bukkit.Location(w, 500, 64, 500));
        org.bukkit.Location home = new org.bukkit.Location(w, 200, 64, 200);
        p.teleport(home);
        p.getInventory().clear();
        admin.performCommand("wave boss ticket Dier 2");
        admin.performCommand("wave boss spawn");
        assertTrue(p.performCommand("천공의제단"));
        click(p, 13, ClickType.LEFT);
        assertTrue(p.getLocation().distance(home) > 50, "제단 도착");
        assertEquals(1, tickets(p));

        p.setHealth(0.0);   // 제단에서 사망
        assertTrue(p.isDead());
        p.respawn();
        server.getScheduler().performTicks(5);
        assertEquals(home, p.getLocation(), "이전 위치에서 부활 (스폰으로 보내지 않음)");

        // 이번 강림에는 재입장 불가, 이동권도 소모되지 않음
        assertTrue(p.performCommand("천공의제단"));
        assertEquals(Material.BARRIER, p.getOpenInventory().getTopInventory().getItem(13).getType());
        click(p, 13, ClickType.LEFT);
        assertEquals(home, p.getLocation());
        assertEquals(1, tickets(p));

        // 다음 강림에는 다시 가능
        findBoss(w).remove();
        server.getScheduler().performTicks(25);
        admin.performCommand("wave boss spawn");
        p.closeInventory();
        assertTrue(p.performCommand("천공의제단"));
        assertEquals(Material.ENDER_EYE, p.getOpenInventory().getTopInventory().getItem(13).getType(), "다음 강림엔 입장 가능");
    }

    @Test
    void natureBootsStopHunger() {
        PlayerMock p = server.addPlayer("Hungry");
        server.getScheduler().performTicks(5);
        p.setFoodLevel(20);
        var drop = new org.bukkit.event.entity.FoodLevelChangeEvent(p, 19);
        server.getPluginManager().callEvent(drop);
        assertFalse(drop.isCancelled(), "신발 없으면 배고픔 감소");
        p.getInventory().setBoots(Items.natureBoots());
        var drop2 = new org.bukkit.event.entity.FoodLevelChangeEvent(p, 19);
        server.getPluginManager().callEvent(drop2);
        assertTrue(drop2.isCancelled(), "대자연의 황혼: 배고픔 감소 안 함");
        p.setFoodLevel(10);
        var eat = new org.bukkit.event.entity.FoodLevelChangeEvent(p, 16);
        server.getPluginManager().callEvent(eat);
        assertFalse(eat.isCancelled(), "먹는 것은 그대로");
    }

    @Test
    void deadPlayersExcludedFromLoot() {
        World w = server.getWorld("world");
        PlayerMock admin = server.addPlayer("AdmX");
        admin.setOp(true);
        server.getScheduler().performTicks(5);
        admin.teleport(new org.bukkit.Location(w, 0.5, 100, 0.5));
        admin.performCommand("wave boss setcenter");
        admin.teleport(new org.bukkit.Location(w, 500, 64, 500));
        admin.performCommand("wave boss spawn");
        var boss = findBoss(w);
        PlayerMock alive = server.addPlayer("Alive1");
        PlayerMock dead = server.addPlayer("Dead1");
        PlayerMock late = server.addPlayer("Late1");
        server.getScheduler().performTicks(5);
        for (PlayerMock pm : new PlayerMock[]{alive, dead, late}) pm.teleport(new org.bukkit.Location(w, 3, 100, 3));
        hit(alive, boss);
        hit(dead, boss);
        hit(late, boss);
        dead.setHealth(0.0);           // 전투 중 사망 -> 분배 제외
        dead.respawn();
        server.getScheduler().performTicks(2);
        boss.setHealth(0);
        server.getScheduler().performTicks(1);
        assertTrue(guiOpen(alive));
        assertTrue(guiOpen(late));
        assertFalse(guiOpen(dead), "죽은 사람은 전리품 창 없음");
        // 분배 중 사망 -> 포기 처리, 남은 사람이 결정
        int slot = lootSlots(alive).get(0);
        Material m = alive.getOpenInventory().getTopInventory().getItem(slot).getType();
        click(late, slot, ClickType.LEFT);
        late.setHealth(0.0);
        server.getScheduler().performTicks(1);
        click(alive, slot, ClickType.LEFT);   // 이제 혼자 -> 바로 획득
        assertTrue(countInInv(alive, m) > 0, "남은 사람이 가져감");
    }

    @Test
    void lootEditorGui() {
        World w = server.getWorld("world");
        PlayerMock admin = server.addPlayer("AdmE");
        admin.setOp(true);
        server.getScheduler().performTicks(5);
        assertTrue(admin.performCommand("전리품설정"));
        assertEquals("전리품 설정 (13/45)", title(admin));
        var inv = admin.getOpenInventory().getTopInventory();
        assertEquals(54, inv.getSize());

        // 내 인벤토리 아이템 클릭 -> 추가 (이름 그대로, 수량 = 들고 있던 개수)
        admin.getInventory().clear();
        ItemStack custom = It.of(Material.DIAMOND_SWORD).name("&b보스 전용 검").build();
        custom.setAmount(1);
        admin.getInventory().setItem(0, custom);
        var view = admin.getOpenInventory();
        int rawBottom = view.getTopInventory().getSize();   // 아래 인벤토리 0번 칸(핫바 0은 raw 54+27)
        var ev = new org.bukkit.event.inventory.InventoryClickEvent(view, org.bukkit.event.inventory.InventoryType.SlotType.QUICKBAR, rawBottom + 27, ClickType.LEFT, org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(ev);
        assertTrue(ev.isCancelled(), "아이템을 실제로 옮기지는 않음");
        server.getScheduler().performTicks(2);
        assertEquals("전리품 설정 (14/45)", title(admin));
        assertTrue(Msg.name(admin.getOpenInventory().getTopInventory().getItem(13)).contains("보스 전용 검"));
        assertEquals(1, countInInv(admin, Material.DIAMOND_SWORD), "내 아이템은 그대로");

        // 항목 편집: 비중 +10, 최대 수량 +5
        click(admin, 13, ClickType.LEFT);
        server.getScheduler().performTicks(2);
        assertEquals("전리품 편집", title(admin));
        click(admin, 34, ClickType.LEFT);   // 비중 +10
        server.getScheduler().performTicks(2);
        click(admin, 25, ClickType.LEFT);   // 최대 +5
        server.getScheduler().performTicks(2);
        var pool = plugin.getConfig().getMapList("boss.loot.pool");
        var m = pool.get(13);
        assertEquals(20, ((Number) m.get("weight")).intValue());
        assertEquals(1, ((Number) m.get("min")).intValue());
        assertEquals(6, ((Number) m.get("max")).intValue());
        assertTrue(m.get("item") instanceof ItemStack);
        plugin.reloadConfig();   // 파일에 저장된 뒤 다시 읽어도 아이템 유지
        var reloaded = plugin.getConfig().getMapList("boss.loot.pool").get(13).get("item");
        assertTrue(reloaded instanceof ItemStack, "reload 후: " + reloaded);
        assertTrue(Msg.name((ItemStack) reloaded).contains("보스 전용 검"));

        // 전리품 개수 / 클레이모어 확률
        click(admin, 49 + 3, ClickType.LEFT);   // 뒤로 -> 목록 (nav back = last+3 = 48)
        server.getScheduler().performTicks(2);
        admin.performCommand("전리품설정");
        click(admin, 46, ClickType.LEFT);
        server.getScheduler().performTicks(2);
        assertEquals(6, plugin.getConfig().getInt("boss.loot.items"));
        click(admin, 47, ClickType.SHIFT_LEFT);
        server.getScheduler().performTicks(2);
        assertEquals(1.5, plugin.getConfig().getDouble("boss.loot.claymore-chance"), 0.001);

        // 실제 전리품에 커스텀 아이템이 나올 수 있음 (다른 항목 삭제 후 확인)
        admin.performCommand("전리품설정");
        for (int i = 0; i < 13; i++) {
            click(admin, 0, ClickType.LEFT);
            server.getScheduler().performTicks(2);
            click(admin, 53, ClickType.SHIFT_RIGHT);
            server.getScheduler().performTicks(2);
        }
        assertEquals("전리품 설정 (1/45)", title(admin));
        admin.teleport(new org.bukkit.Location(w, 0.5, 100, 0.5));
        admin.performCommand("wave boss setcenter");
        admin.performCommand("wave boss testloot");
        boolean found = false;
        for (int sl : lootSlots(admin)) if (Msg.name(admin.getOpenInventory().getTopInventory().getItem(sl)).contains("보스 전용 검")) found = true;
        assertTrue(found, "GUI 로 넣은 아이템이 전리품으로 나옴");

        // 일반 유저는 사용 불가
        PlayerMock u = server.addPlayer("UserE");
        server.getScheduler().performTicks(2);
        u.performCommand("전리품설정");
        assertFalse(guiOpen(u));
    }

    @Test
    void claymoreWalkSpeedSurvivesWorldChange() {
        World other = server.addSimpleWorld("other_w");
        PlayerMock p = server.addPlayer("Speedy");
        PlayerMock q = server.addPlayer("Normal");
        server.getScheduler().performTicks(5);
        p.getInventory().addItem(Items.claymoreSd());
        server.getScheduler().performTicks(25);
        assertEquals(0.25f, p.getWalkSpeed(), 0.001f);
        // 월드 이동 후 다른 플러그인/서버가 속도를 되돌린 경우
        p.teleport(new org.bukkit.Location(other, 0, 64, 0));
        p.setWalkSpeed(0.2f);
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerChangedWorldEvent(p, server.getWorld("world")));
        server.getScheduler().performTicks(3);
        assertEquals(0.25f, p.getWalkSpeed(), 0.001f, "월드 이동 직후 바로 복구");
        // 이벤트 없이 되돌려져도 1초 안에 복구
        p.setWalkSpeed(0.2f);
        server.getScheduler().performTicks(21);
        assertEquals(0.25f, p.getWalkSpeed(), 0.001f, "매초 확인");
        // 버리면 기본 속도
        p.getInventory().clear();
        server.getScheduler().performTicks(21);
        assertEquals(0.2f, p.getWalkSpeed(), 0.001f);
        // 클레이모어 없는 사람의 다른 속도 설정은 건드리지 않음
        q.setWalkSpeed(0.4f);
        server.getScheduler().performTicks(41);
        assertEquals(0.4f, q.getWalkSpeed(), 0.001f);
    }

    @Test
    void announcementsRotateWithButtons() {
        PlayerMock p = server.addPlayer("Listener");
        PlayerMock op = server.addPlayer("AnnOp");
        op.setOp(true);
        server.getScheduler().performTicks(5);
        drain(p);
        // 15분마다 하나씩, 돌아가며
        server.getScheduler().performTicks(20 * 60 * 15 + 20);
        String first = drain(p);
        assertTrue(first.contains("메뉴를 빠르게 여는 방법"), first);
        assertTrue(first.contains("[메뉴 열기]"));
        op.performCommand("공지테스트");
        String second = drain(p);
        assertTrue(second.contains("천공의 심판자"), second);
        op.performCommand("공지테스트");
        assertTrue(drain(p).contains("프리미엄 패스"));
        // 버튼은 클릭 가능
        op.performCommand("공지테스트");   // dailyshop 은 미설치라 건너뜀 -> rules
        net.kyori.adventure.text.Component c;
        boolean click = false;
        while ((c = p.nextComponentMessage()) != null) if (c.clickEvent() != null && c.clickEvent().value().startsWith("/report")) click = true;
        assertTrue(click, "신고하기 버튼");
    }

    private kr.wave.core.module.CrateModule crates() {
        return plugin.module(kr.wave.core.module.CrateModule.class);
    }

    @Test
    void crateSystemFromCrazyCratesConfigs() {
        var cm = crates();
        for (String id : new String[]{"combat", "enderpearl", "Sealedp", "TheRuiner", "chronos", "TheEchoesOfNatural", "CatastropheOfChaos"}) assertNotNull(cm.crate(id), id);
        PlayerMock p = server.addPlayer("Opener");
        PlayerMock admin = server.addPlayer("CrateAdm");
        admin.setOp(true);
        server.getScheduler().performTicks(5);
        p.getInventory().clear();
        assertTrue(p.performCommand("상자"));
        assertEquals("아이템 뽑기", title(p));
        var inv = p.getOpenInventory().getTopInventory();
        assertEquals(Material.AMETHYST_SHARD, inv.getItem(10).getType(), "Sealed (Slot 10)");
        assertEquals(Material.REDSTONE, inv.getItem(11).getType(), "Ruiner (Slot 11)");
        assertEquals(Material.NETHER_STAR, inv.getItem(12).getType(), "Chronos (Slot 12)");
        assertEquals(Material.BEACON, inv.getItem(13).getType(), "스페셜 테마 뽑기 버튼 (Slot 13)");
        assertTrue(Msg.name(inv.getItem(13)).contains("스페셜 테마 뽑기"));
        for (int i = 0; i < inv.getSize(); i++) if (inv.getItem(i) != null) {
            assertNotEquals(Material.MOSS_BLOCK, inv.getItem(i).getType(), "Echoes 는 메인에 없음");
            assertNotEquals(Material.CRYING_OBSIDIAN, inv.getItem(i).getType(), "Chaos 는 메인에 없음");
        }
        click(p, 13, ClickType.LEFT);
        server.getScheduler().performTicks(2);
        assertEquals("스페셜 테마 뽑기", title(p));
        var th = p.getOpenInventory().getTopInventory();
        assertEquals(Material.MOSS_BLOCK, th.getItem(12).getType(), "Echoes");
        assertEquals(Material.CRYING_OBSIDIAN, th.getItem(14).getType(), "Chaos");
        assertTrue(Msg.name(th.getItem(14)).contains("Catastrophe of Chaos"));
        click(p, 14, ClickType.RIGHT);
        server.getScheduler().performTicks(2);
        assertTrue(title(p).contains("Catastrophe of Chaos"), title(p));
        boolean apo = false, wave = false;
        for (ItemStack it : p.getOpenInventory().getTopInventory().getContents()) if (it != null) {
            if (Msg.name(it).contains("혼돈의 아포칼립스")) apo = true;
            if (Msg.name(it).contains("다크니스 웨이브")) wave = true;
        }
        assertTrue(apo && wave, "구성품에 혼돈 세트");
        GuiSnapshot.save(p, "chaos_preview.json");
        p.performCommand("상자");
        click(p, 13, ClickType.LEFT);
        server.getScheduler().performTicks(2);
        GuiSnapshot.save(p, "theme_menu.json");
        p.performCommand("상자");
        GuiSnapshot.save(p, "crate_menu.json");
        inv = p.getOpenInventory().getTopInventory();
        assertEquals(Material.IRON_SWORD, inv.getItem(28).getType(), "combat (Slot 28)");
        assertEquals(Material.ENDER_PEARL, inv.getItem(29).getType(), "enderpearl (Slot 29)");
        assertTrue(allText(inv).contains("오픈 가능한 상자 : 0"));

        // 우클릭 = 구성품
        click(p, 28, ClickType.RIGHT);
        server.getScheduler().performTicks(2);
        assertTrue(title(p).contains("상자 구성품"), title(p));
        assertEquals(Material.DIAMOND_HELMET, p.getOpenInventory().getTopInventory().getItem(10).getType());
        // 키 없이 좌클릭 = 거부
        p.performCommand("상자");
        click(p, 28, ClickType.LEFT);
        assertTrue(drain(p).contains("키가 부족합니다"));

        // 가상 키 지급 -> 열기 -> 보상
        assertTrue(admin.performCommand("상자 give Opener combat 2"));
        assertEquals(2, cm.virtualKeys(p, cm.crate("combat")));
        p.performCommand("상자");
        assertTrue(allText(p.getOpenInventory().getTopInventory()).contains("오픈 가능한 상자 : 2"));
        int before = p.getInventory().all(Material.DIAMOND_HELMET).size() + 0;
        click(p, 28, ClickType.LEFT);
        assertEquals(1, cm.virtualKeys(p, cm.crate("combat")), "키 1개 사용");
        assertEquals("다양한 무기 룰렛", title(p));
        server.getScheduler().performTicks(20 * 15);
        int items = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) if (it != null) items++;
        assertTrue(items >= 1, "보상 지급");
        assertTrue(drain(p).contains("획득했습니다"));

        // 실물 키 (교환권): 우클릭 -> 메뉴, 열면 소모
        p.getInventory().clear();
        p.getInventory().setItem(0, cm.keyItem(cm.crate("TheEchoesOfNatural"), 2));
        p.getInventory().setHeldItemSlot(0);
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerInteractEvent(p, org.bukkit.event.block.Action.RIGHT_CLICK_AIR, p.getInventory().getItemInMainHand(), null, org.bukkit.block.BlockFace.UP, org.bukkit.inventory.EquipmentSlot.HAND));
        assertEquals("스페셜 테마 뽑기", title(p), "테마 상자 교환권은 테마 메뉴로");
        assertEquals(0, cm.physicalKeys(p, cm.crate("TheEchoesOfNatural")), "실물 키는 계정으로 옮겨짐");
        assertEquals(2, cm.virtualKeys(p, cm.crate("TheEchoesOfNatural")));
        click(p, 12, ClickType.LEFT);
        assertEquals(1, cm.virtualKeys(p, cm.crate("TheEchoesOfNatural")));
        server.getScheduler().performTicks(20 * 15);

        // 천국과 지옥: 야생에서만
        admin.performCommand("상자 give Opener enderpearl 1");
        p.teleport(new org.bukkit.Location(server.getWorld("spawn-world"), 0, 64, 0));
        p.performCommand("상자");
        click(p, 29, ClickType.LEFT);
        assertTrue(drain(p).contains("야생에서만"));
        assertEquals(1, cm.virtualKeys(p, cm.crate("enderpearl")), "거부 시 키 유지");
    }

    @Test
    void specialShopAndShopRedirects() {
        PlayerMock p = server.addPlayer("Buyer");
        server.getScheduler().performTicks(5);
        PlayerData d = plugin.store().get(p);
        d.exMoney = 100_000;
        d.money = 100_000;
        p.getInventory().clear();
        assertTrue(p.performCommand("상점"));
        var inv = p.getOpenInventory().getTopInventory();
        for (int sl : new int[]{11, 13, 15, 21, 23, 29, 31, 33}) {
            assertTrue(allText(inv).contains("클릭 시 이동됩니다."));
            var lore = inv.getItem(sl).getItemMeta().lore();
            assertEquals(1, lore.size(), "설명은 이동 안내 한 줄: " + sl);
        }
        // 아이템 뽑기 -> 새 상자 메뉴
        click(p, 13, ClickType.LEFT);
        server.getScheduler().performTicks(2);
        assertEquals("아이템 뽑기", title(p));
        // 스페셜 상점
        p.performCommand("상점");
        click(p, 15, ClickType.LEFT);
        server.getScheduler().performTicks(2);
        assertEquals("스페셜 상점", title(p));
        GuiSnapshot.save(p, "specialshop.json");
        click(p, 14, ClickType.LEFT);   // Echoes 교환권 10개 45,000
        assertEquals(55_000, d.exMoney);
        var sp = p.getOpenInventory().getTopInventory();
        assertTrue(Msg.plain(sp.getItem(15).getItemMeta().displayName()).contains("Catastrophe of Chaos"), "혼돈 교환권 1개");
        assertTrue(Msg.plain(sp.getItem(16).getItemMeta().displayName()).contains("Catastrophe of Chaos"), "혼돈 교환권 10개");
        assertEquals(10, crates().virtualKeys(p, crates().crate("TheEchoesOfNatural")), "교환권은 계정에 지급");
        assertEquals(0, crates().physicalKeys(p, crates().crate("TheEchoesOfNatural")));
        click(p, 10, ClickType.LEFT);   // 이동권 1개 5,000
        assertEquals(50_000, d.exMoney);
        int tickets = 0;
        for (ItemStack it : p.getInventory().getContents()) if (Msg.nameIs(it, Items.N_ALTAR_TICKET)) tickets += it.getAmount();
        assertEquals(1, tickets);
        // 룰렛 키 상점 -> 새 시스템 가상 키
        assertTrue(p.performCommand("roulette"));
        click(p, 11, ClickType.LEFT);
        assertEquals(1, crates().virtualKeys(p, crates().crate("combat")));
        // 서버 메뉴의 아이템 뽑기 -> 새 상자 메뉴
        p.performCommand("메뉴");
        click(p, 14, ClickType.LEFT);
        server.getScheduler().performTicks(2);
        assertEquals("아이템 뽑기", title(p));
    }

    @Test
    void crateTenOpenSkipAndBorder() {
        var cm = crates();
        var combat = cm.crate("combat");
        PlayerMock p = server.addPlayer("Mass");
        server.getScheduler().performTicks(5);
        p.getInventory().clear();
        cm.giveKeys(p, combat, 12, false);
        assertTrue(p.performCommand("상자"));
        var inv = p.getOpenInventory().getTopInventory();
        // 테두리만 검은 유리판, 안쪽 빈칸은 비어 있음
        assertEquals(Material.BLACK_STAINED_GLASS_PANE, inv.getItem(0).getType());
        assertEquals(Material.BLACK_STAINED_GLASS_PANE, inv.getItem(17).getType());
        assertNull(inv.getItem(20), "안쪽 빈칸은 유리판 없음");
        for (ItemStack it : inv.getContents()) if (it != null && it.getType().name().endsWith("GLASS_PANE")) assertEquals(Material.BLACK_STAINED_GLASS_PANE, it.getType());
        assertTrue(allText(inv).contains("Shift + 좌클릭 시 10회 오픈됩니다."));

        // Shift + 좌클릭 = 10회
        click(p, 28, ClickType.SHIFT_LEFT);
        assertEquals(2, cm.virtualKeys(p, combat), "10개 사용");
        assertEquals("다양한 무기 룰렛 x10", title(p));
        var g = p.getOpenInventory().getTopInventory();
        assertEquals(Material.FEATHER, g.getItem(31).getType(), "스킵 버튼");
        click(p, 31, ClickType.LEFT);   // 스킵 -> 바로 지급
        int got = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) if (it != null) got++;
        assertTrue(got >= 1, "10회 보상 지급");
        for (int sl : new int[]{11, 12, 13, 14, 15, 20, 21, 22, 23, 24}) assertNotEquals(Material.BLACK_STAINED_GLASS_PANE, g.getItem(sl).getType(), "결과 공개: " + sl);
        assertTrue(drain(p).contains("10회 결과"));
        assertEquals(Material.LIME_DYE, g.getItem(31).getType(), "완료 버튼");
        // 스킵 후 남은 연출이 다시 지급하지 않음
        int before = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) if (it != null) before += it.getAmount();
        server.getScheduler().performTicks(20 * 10);
        int after = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) if (it != null) after += it.getAmount();
        assertEquals(before, after, "중복 지급 없음");

        // 키가 2개뿐이면 Shift+좌클릭은 2회만
        p.performCommand("상자");
        click(p, 28, ClickType.SHIFT_LEFT);
        assertEquals(0, cm.virtualKeys(p, combat));
        assertEquals("다양한 무기 룰렛 x2", title(p));
        server.getScheduler().performTicks(20 * 5);

        // 1회 룰렛 스킵
        cm.giveKeys(p, combat, 1, false);
        p.performCommand("상자");
        click(p, 28, ClickType.LEFT);
        var w = p.getOpenInventory().getTopInventory();
        assertEquals(Material.FEATHER, w.getItem(22).getType());
        assertNull(w.getItem(4) == null ? null : (w.getItem(4).getType() == Material.LIME_STAINED_GLASS_PANE ? w.getItem(4) : null), "초록 유리판 없음");
        click(p, 22, ClickType.LEFT);
        assertTrue(drain(p).contains("획득했습니다"));
        assertEquals(Material.LIME_DYE, w.getItem(22).getType());
    }

    @Test
    void crateNoBroadcastAndPaidShopKeyNames() {
        var cm = crates();
        PlayerMock p = server.addPlayer("Quiet");
        PlayerMock other = server.addPlayer("Other1");
        server.getScheduler().performTicks(5);
        PlayerData d = plugin.store().get(p);
        d.exMoney = 100_000;
        p.getInventory().clear();
        drain(other);
        cm.giveKeys(p, cm.crate("combat"), 1, false);
        p.performCommand("상자");
        click(p, 28, ClickType.LEFT);
        click(p, 22, ClickType.LEFT);   // 스킵
        assertFalse(drain(other).contains("[상자]"), "전체 메시지 없음");
        // 유료 캐시 상점: 상자 이름으로 된 키, 계정에 지급, 인벤토리 공간 필요 없음
        assertTrue(p.performCommand("yurocash"));
        var inv = p.getOpenInventory().getTopInventory();
        assertTrue(Msg.name(inv.getItem(21)).contains("Sealed Master Power 키"), Msg.name(inv.getItem(21)));
        assertTrue(Msg.name(inv.getItem(22)).contains("The Ruiner's Grudge 키"), Msg.name(inv.getItem(22)));
        assertTrue(Msg.name(inv.getItem(19)).contains("다양한 무기 룰렛 키"));
        assertTrue(Msg.name(inv.getItem(20)).contains("천국과 지옥 룰렛 키"));
        for (int i = 0; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Material.DIRT, 64));
        click(p, 21, ClickType.LEFT);
        assertEquals(1, cm.virtualKeys(p, cm.crate("Sealedp")), "인벤토리가 가득 차도 계정에 지급");
        assertEquals(95_000, d.exMoney);
    }

    @Test
    void crownAndKillEffectToggles() {
        PlayerMock p = server.addPlayer("Crowner");
        server.getScheduler().performTicks(5);
        PlayerData d = plugin.store().get(p);
        d.ranks.add("supreme");
        assertTrue(p.performCommand("메뉴"));
        click(p, 19, ClickType.LEFT);   // 프로필
        server.getScheduler().performTicks(2);
        assertEquals("프로필", title(p));
        assertFalse(allText(p.getOpenInventory().getTopInventory()).contains("크라운 처치 알림 표식 ["), "프로필에는 크라운 버튼 없음");
        click(p, 10, ClickType.LEFT);   // 킬 특수 효과
        server.getScheduler().performTicks(2);
        assertEquals("킬 특수 효과 설정", title(p));
        var inv = p.getOpenInventory().getTopInventory();
        assertTrue(Msg.name(inv.getItem(16)).contains("크라운 처치 알림 표식"));
        assertTrue(allText(inv).contains("미보유"));

        // 킬 효과: 적용 -> 다시 클릭하면 해제
        click(p, 10, ClickType.LEFT);
        assertEquals(1, d.killEffect);
        server.getScheduler().performTicks(2);
        assertTrue(allText(p.getOpenInventory().getTopInventory()).contains("적용 중"));
        click(p, 10, ClickType.LEFT);
        assertEquals(0, d.killEffect, "해제");
        server.getScheduler().performTicks(2);
        click(p, 12, ClickType.LEFT);   // Exceed 미보유
        assertEquals(0, d.killEffect);

        // 크라운: 미보유 -> 아이템 사용 -> 해제/적용
        click(p, 16, ClickType.LEFT);
        assertFalse(d.crownKill);
        p.closeInventory();
        p.getInventory().setItemInMainHand(Items.crownMark());
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerInteractEvent(p, org.bukkit.event.block.Action.RIGHT_CLICK_AIR, p.getInventory().getItemInMainHand(), null, org.bukkit.block.BlockFace.UP, org.bukkit.inventory.EquipmentSlot.HAND));
        assertTrue(d.crownOwned && d.crownKill);
        p.performCommand("메뉴");
        click(p, 19, ClickType.LEFT);
        server.getScheduler().performTicks(2);
        click(p, 10, ClickType.LEFT);
        server.getScheduler().performTicks(2);
        click(p, 16, ClickType.LEFT);
        assertFalse(d.crownKill, "해제");
        server.getScheduler().performTicks(2);
        click(p, 16, ClickType.LEFT);
        assertTrue(d.crownKill, "다시 적용");
    }

    @Test
    void crownKillGrandAndNickToggle() {
        PlayerMock k = server.addPlayer("King");
        PlayerMock v = server.addPlayer("Peon");
        PlayerMock w = server.addPlayer("Watcher");
        server.getScheduler().performTicks(5);
        PlayerData kd = plugin.store().get(k);
        kd.crownOwned = true;
        kd.crownKill = true;
        drain(w);
        k.getInventory().setItemInMainHand(Items.quantumSword());
        v.setKiller(k);
        server.getPluginManager().callEvent(new org.bukkit.event.entity.PlayerDeathEvent(v, org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).withCausingEntity(k).withDirectEntity(k).build(), new java.util.ArrayList<>(), 0, null));
        net.kyori.adventure.text.Component c;
        StringBuilder sb = new StringBuilder();
        boolean hover = false, scrambled = false;
        while ((c = w.nextComponentMessage()) != null) {
            sb.append(Msg.plain(c)).append('\n');
            if (hasHover(c)) hover = true;
            if (obfuscatedText(c, "King", false) || obfuscatedText(c, "앱솔루트", false)) scrambled = true;
        }
        String all = sb.toString();
        assertFalse(all.contains("CROWN KILL"), all);
        long lines = all.lines().filter(l -> l.contains("👑")).count();
        assertEquals(1, lines, "크라운 알림은 한 줄: " + all);
        assertTrue(all.lines().anyMatch(l -> l.contains("👑") && l.contains("King") && l.contains("Peon") && l.contains("(")), all);
        assertTrue(all.contains("King") && all.contains("Peon"), all);
        assertTrue(hover, "아이템 마우스 정보");
        assertFalse(scrambled, "이름이 깨지지 않음");
        w.assertSoundHeard("block.bell.use");

        // 클레이모어 · 프로스트 그레이브 킬로그에는 왕관 없음
        for (ItemStack wpn : new ItemStack[]{Items.claymoreSd(), Items.frostGrave()}) {
            drain(w);
            k.getInventory().setItemInMainHand(wpn);
            server.getPluginManager().callEvent(new org.bukkit.event.entity.PlayerDeathEvent(v, org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).withCausingEntity(k).withDirectEntity(k).build(), new java.util.ArrayList<>(), 0, null));
            StringBuilder fb = new StringBuilder();
            boolean goldArrow = false, goldPos2 = false;
            net.kyori.adventure.text.Component cc;
            while ((cc = w.nextComponentMessage()) != null) {
                fb.append(Msg.plain(cc)).append('\n');
                String mm = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().serialize(cc).toLowerCase();
                if (Msg.plain(cc).contains("»") && mm.contains("#ffb732")) goldArrow = true;
                if (Msg.plain(cc).contains("(") && (mm.contains("#fff176") || mm.contains("#ffb732"))) goldPos2 = true;
            }
            String fl = fb.toString();
            assertTrue(fl.contains("King") && fl.contains("»") && !fl.contains("👑"), fl);
            assertTrue(goldArrow && goldPos2, "크라운: 화살표 · 좌표 금빛 " + fl);
        }

        // 닉네임 색상: 적용 -> 다시 클릭하면 해제
        kd.nickPapers.add("red");
        k.performCommand("메뉴");
        click(k, 19, ClickType.LEFT);
        server.getScheduler().performTicks(2);
        click(k, 12, ClickType.LEFT);   // 닉네임 색상 설정
        server.getScheduler().performTicks(2);
        assertEquals("닉네임 색상", title(k));
        var inv = k.getOpenInventory().getTopInventory();
        assertTrue(allText(inv).contains("해제됨"));
        assertTrue(allText(inv).contains("미보유"));
        click(k, 10, ClickType.LEFT);
        assertEquals(1, kd.nickColor);
        server.getScheduler().performTicks(2);
        assertTrue(allText(k.getOpenInventory().getTopInventory()).contains("적용 중"));
        click(k, 10, ClickType.LEFT);
        assertEquals(0, kd.nickColor, "해제");
        server.getScheduler().performTicks(2);
        click(k, 12, ClickType.LEFT);   // Gold 미보유
        assertEquals(0, kd.nickColor);
    }

    @Test
    void titleToggle() {
        PlayerMock p = server.addPlayer("Titler");
        server.getScheduler().performTicks(5);
        PlayerData d = plugin.store().get(p);
        d.titles.add("ace");
        p.performCommand("메뉴");
        click(p, 19, ClickType.LEFT);
        server.getScheduler().performTicks(2);
        click(p, 16, ClickType.LEFT);   // 칭호 설정
        server.getScheduler().performTicks(2);
        assertEquals("칭호 설정", title(p));
        var inv = p.getOpenInventory().getTopInventory();
        assertTrue(allText(inv).contains("해제됨"));
        assertTrue(allText(inv).contains("미보유"));
        assertFalse(allText(inv).contains("칭호 제거하기"), "제거 버튼 없음 (다시 클릭하면 해제)");
        click(p, 10, ClickType.LEFT);
        assertEquals("ace", d.activeTitle);
        server.getScheduler().performTicks(2);
        assertTrue(allText(p.getOpenInventory().getTopInventory()).contains("적용 중"));
        click(p, 10, ClickType.LEFT);
        assertNull(d.activeTitle, "해제");
        server.getScheduler().performTicks(2);
        click(p, 11, ClickType.LEFT);   // supreme 미보유
        assertNull(d.activeTitle);
    }

    @Test
    void gradientNickColorsAndCrownPos() {
        PlayerMock k = server.addPlayer("Glow");
        PlayerMock v = server.addPlayer("Vict");
        PlayerMock w = server.addPlayer("Seer");
        server.getScheduler().performTicks(5);
        PlayerData kd = plugin.store().get(k);
        // 변경권 사용 -> 보유 + 적용
        k.getInventory().setItemInMainHand(Items.nickPaper("gradient", "test"));
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerInteractEvent(k, org.bukkit.event.block.Action.RIGHT_CLICK_AIR, k.getInventory().getItemInMainHand(), null, org.bukkit.block.BlockFace.UP, org.bukkit.inventory.EquipmentSlot.HAND));
        assertEquals(5, kd.nickColor);
        assertTrue(kd.nickPapers.contains("gradient"));
        k.getInventory().setItemInMainHand(Items.nickPaper("glacier", "test"));
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerInteractEvent(k, org.bukkit.event.block.Action.RIGHT_CLICK_AIR, k.getInventory().getItemInMainHand(), null, org.bukkit.block.BlockFace.UP, org.bukkit.inventory.EquipmentSlot.HAND));
        assertEquals(6, kd.nickColor);
        // 메뉴: 6개 색상, 그라데이션 미리보기
        k.performCommand("메뉴");
        click(k, 19, ClickType.LEFT);
        server.getScheduler().performTicks(2);
        click(k, 12, ClickType.LEFT);
        server.getScheduler().performTicks(2);
        var inv = k.getOpenInventory().getTopInventory();
        assertTrue(Msg.name(inv.getItem(20)).contains("Gradient Gold"));
        assertTrue(Msg.name(inv.getItem(24)).contains("Glacier"));
        assertTrue(allText(inv).contains("적용 중"));
        click(k, 20, ClickType.LEFT);
        assertEquals(5, kd.nickColor, "Gradient Gold 적용");
        // 채팅 이름이 그라데이션으로 (색이 섞여 있어야 함)
        drain(w);
        k.chat("hello");
        server.getScheduler().performTicks(2);
        net.kyori.adventure.text.Component c;
        boolean grad = false;
        while ((c = w.nextComponentMessage()) != null) {
            String mm = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().serialize(c);
            if (mm.contains("Glow") || mm.contains("G</")) grad = grad || mm.toLowerCase().contains("#fff") || mm.toLowerCase().contains("#ff");
        }
        assertTrue(grad, "그라데이션 닉네임");

        // 크라운 + 클레이모어: 클레이모어 형식 그대로, 좌표만 금빛
        kd.crownOwned = true;
        kd.crownKill = true;
        drain(w);
        k.getInventory().setItemInMainHand(Items.claymoreSd());
        v.setKiller(k);
        server.getPluginManager().callEvent(new org.bukkit.event.entity.PlayerDeathEvent(v, org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).withCausingEntity(k).withDirectEntity(k).build(), new java.util.ArrayList<>(), 0, null));
        StringBuilder sb = new StringBuilder();
        boolean goldPos = false;
        while ((c = w.nextComponentMessage()) != null) {
            sb.append(Msg.plain(c)).append('\n');
            String mm = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().serialize(c).toLowerCase();
            if (Msg.plain(c).contains("클레이모어") && (mm.contains("#fff176") || mm.contains("#ffb732") || mm.contains("#ffd"))) goldPos = true;
        }
        String all = sb.toString();
        assertFalse(all.contains("👑"), "클레이모어 형식에는 왕관 없음: " + all);
        assertTrue(all.contains("클레이모어"), all);
        assertTrue(goldPos, "좌표 금빛");
    }

    private static net.kyori.adventure.text.Component findWithText(net.kyori.adventure.text.Component c, String text) {
        if (c instanceof net.kyori.adventure.text.TextComponent t && t.content().contains(text)) return c;
        for (var ch : c.children()) {
            var f = findWithText(ch, text);
            if (f != null) return f;
        }
        return null;
    }

    /** text 를 감싸는 가장 가까운 조상 중 hover 가 있는 것 */
    private static net.kyori.adventure.text.Component hoverOwner(net.kyori.adventure.text.Component c, String text) {
        if (c.hoverEvent() != null && Msg.plain(c).contains(text)) return c;
        for (var ch : c.children()) {
            var f = hoverOwner(ch, text);
            if (f != null) return f;
        }
        return null;
    }

    @Test
    void gradientNameNotScrambledAndNameHover() {
        PlayerMock k = server.addPlayer("Mint");
        PlayerMock v = server.addPlayer("Solar");
        PlayerMock w = server.addPlayer("Eye");
        server.getScheduler().performTicks(5);
        PlayerData kd = plugin.store().get(k);
        kd.nickColor = 6;   // Glacier (그라데이션) - 예전에는 &k 가 이어져 이름이 깨졌음
        kd.level = 7;
        for (ItemStack tool : new ItemStack[]{Items.claymoreSd(), Items.frostGrave(), Items.quantumSword()}) {
            drain(w);
            k.getInventory().setItemInMainHand(tool);
            v.setKiller(k);
            server.getPluginManager().callEvent(new org.bukkit.event.entity.PlayerDeathEvent(v, org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).withCausingEntity(k).withDirectEntity(k).build(), new java.util.ArrayList<>(), 0, null));
            net.kyori.adventure.text.Component c;
            boolean checked = false;
            while ((c = w.nextComponentMessage()) != null) {
                if (!Msg.plain(c).contains("Mint") || !Msg.plain(c).contains("Solar")) continue;
                assertFalse(obfuscatedText(c, "M", false) || obfuscatedText(c, "Solar", false), "이름이 깨지면 안 됨 (" + Msg.name(tool) + ")");
                var owner = hoverOwner(c, "Mint");
                assertNotNull(owner, "처치자 이름 마우스 정보");
                String info = Msg.plain((net.kyori.adventure.text.Component) owner.hoverEvent().value());
                assertTrue(info.contains("레벨 : 7") && info.contains("칭호") && info.contains("소속 팀") && info.contains("좌클릭하여 귓속말"), info);
                assertEquals("/귓 Mint ", owner.clickEvent().value());
                assertNotNull(hoverOwner(c, "Solar"), "상대 이름 마우스 정보");
                checked = true;
            }
            assertTrue(checked, Msg.name(tool));
        }
        // 채팅 이름도
        drain(w);
        k.chat("hi");
        for (int i = 0; i < 20; i++) {   // 채팅은 비동기 -> 다음 틱에 처리
            try {
                server.getScheduler().waitAsyncEventsFinished();
            } catch (Throwable ignored) {
            }
            server.getScheduler().performTicks(1);
        }
        net.kyori.adventure.text.Component c;
        boolean ok = false;
        while ((c = w.nextComponentMessage()) != null) {
            var owner = hoverOwner(c, "Mint");
            if (owner != null && owner.clickEvent() != null && owner.clickEvent().value().equals("/귓 Mint ")) ok = true;
        }
        assertTrue(ok, "채팅 이름 마우스 정보 + 좌클릭 귓속말");
    }

    @Test
    void overlordRewards() {
        PlayerMock p = server.addPlayer("Lord");
        server.getScheduler().performTicks(5);
        PlayerData d = plugin.store().get(p);
        p.getInventory().clear();
        var ol = plugin.module(kr.wave.core.module.OverlordModule.class);
        // 잠김
        d.exMoneyTotal = 900_000;
        assertEquals(0, ol.insignias(d), "Peerless 전에는 잠김");
        assertTrue(p.performCommand("오버로드"));
        assertEquals("Over Lord", title(p));
        click(p, 11, ClickType.LEFT);
        assertTrue(d.overlordClaimed.isEmpty());
        // Peerless + 200,000 + 75,000 x 3 = 휘장 3개
        d.ranks.add("peerless");
        d.exMoneyTotal = 200_000 + 75_000 * 3 + 10;
        assertEquals(3, ol.insignias(d));
        long money = d.money;
        p.performCommand("오버로드");
        click(p, 11, ClickType.LEFT);   // ★1
        assertEquals(money + 500_000, d.money);
        click(p, 11, ClickType.LEFT);   // 중복 수령 불가
        assertEquals(money + 500_000, d.money);
        server.getScheduler().performTicks(2);
        click(p, 12, ClickType.LEFT);   // ★2 이동권 10
        int tickets = 0;
        for (ItemStack it : p.getInventory().getContents()) if (Msg.nameIs(it, Items.N_ALTAR_TICKET)) tickets += it.getAmount();
        assertEquals(10, tickets);
        server.getScheduler().performTicks(2);
        click(p, 13, ClickType.LEFT);   // ★3 키 30
        assertEquals(30, crates().virtualKeys(p, crates().crate("TheEchoesOfNatural")));
        server.getScheduler().performTicks(2);
        click(p, 14, ClickType.LEFT);   // ★4 휘장 부족
        assertFalse(d.overlordClaimed.contains(4));
        // 휘장 10개
        d.exMoneyTotal = 200_000 + 75_000 * 10;
        for (int sl : new int[]{14, 15, 20, 21, 22, 23, 24}) {
            p.performCommand("오버로드");
            click(p, sl, ClickType.LEFT);
            server.getScheduler().performTicks(2);
        }
        assertEquals(10, d.overlordClaimed.size());
        assertEquals(3, d.backpackRows, "★4 배낭 3줄");
        assertTrue(d.crownOwned && d.crownKill, "★8 크라운");
        assertTrue(d.nickPapers.contains("gradient"), "★9 Gradient Gold 영구");
        assertEquals(5, d.nickColor);
        // 배낭 3줄
        p.closeInventory();
        assertTrue(p.performCommand("배낭"));
        assertEquals(27, p.getOpenInventory().getTopInventory().getSize());
        p.closeInventory();
        // ★10 전설의 무기 교환권 -> 클레이모어 선택
        int slotV = -1;
        for (int i = 0; i < 36; i++) if (Msg.nameIs(p.getInventory().getItem(i), Items.N_MYTHIC_VOUCHER)) slotV = i;
        assertTrue(slotV >= 0 && slotV < 9, "교환권이 핫바에 있음: " + slotV);
        p.getInventory().setHeldItemSlot(slotV);
        ItemStack v = p.getInventory().getItemInMainHand();
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerInteractEvent(p, org.bukkit.event.block.Action.RIGHT_CLICK_AIR, v, null, org.bukkit.block.BlockFace.UP, org.bukkit.inventory.EquipmentSlot.HAND));
        assertEquals("전설의 무기 교환", title(p));
        var mi = p.getOpenInventory().getTopInventory();
        assertTrue(Msg.name(mi.getItem(11)).contains("초승달의 서약"), "스킬 -> 천공 -> 프로스트");
        assertTrue(Msg.name(mi.getItem(13)).contains("천공의 심판자 클레이모어"));
        assertTrue(Msg.name(mi.getItem(15)).contains("프로스트 그레이브"));
        PlayerMock watcher = server.addPlayer("Watch2");
        server.getScheduler().performTicks(2);
        drain(watcher);
        click(p, 13, ClickType.LEFT);
        String seen = drain(watcher);
        assertTrue(seen.contains("Arbiter of Heaven") && seen.contains("전설의 무기 교환권"), "제단과 같은 획득 연출: " + seen);
        boolean clay = false, still = false;
        for (ItemStack it : p.getInventory().getContents()) {
            if (Msg.nameIs(it, Items.N_CLAYMORE_SD)) clay = true;
            if (Msg.nameIs(it, Items.N_MYTHIC_VOUCHER)) still = true;
        }
        assertTrue(clay);
        assertFalse(still, "교환권 소모");
        // ★5 Legendary 교환권: 얼어붙은 제왕 -> 파괴 -> 공허 -> 철학자
        int slotL = -1;
        for (int i = 0; i < 36; i++) if (Msg.nameIs(p.getInventory().getItem(i), Items.N_LEGEND_VOUCHER)) slotL = i;
        assertTrue(slotL >= 0 && slotL < 9, "[👑] Legendary 교환권: " + slotL);
        p.getInventory().setHeldItemSlot(slotL);
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerInteractEvent(p, org.bukkit.event.block.Action.RIGHT_CLICK_AIR, p.getInventory().getItemInMainHand(), null, org.bukkit.block.BlockFace.UP, org.bukkit.inventory.EquipmentSlot.HAND));
        var li = p.getOpenInventory().getTopInventory();
        assertEquals(36, li.getSize());
        assertTrue(Msg.name(li.getItem(10)).contains("얼어붙은 제왕의 검"));
        assertTrue(Msg.name(li.getItem(19)).contains("얼어붙은 제왕의 날개"));
        assertTrue(Msg.name(li.getItem(12)).contains("파괴의 낫"));
        assertTrue(Msg.name(li.getItem(21)).contains("재앙의 서막 바지"));
        assertTrue(Msg.name(li.getItem(14)).contains("공허의 속삭임"));
        assertTrue(Msg.name(li.getItem(23)).contains("뒤틀린 차원의 갑옷"));
        assertTrue(Msg.name(li.getItem(16)).contains("아리스토텔레스"));
        assertTrue(Msg.name(li.getItem(25)).contains("러셀의 패러독스"));
        click(p, 21, ClickType.LEFT);   // 방어구 선택
        boolean pants = false;
        for (ItemStack it : p.getInventory().getContents()) if (Msg.nameIs(it, Items.N_RUINER_PANTS)) pants = true;
        assertTrue(pants, "세트 방어구로 교환");
        // ★7 전용 입장 메시지 (재접속)
        // (테스트 서버는 모든 접속을 첫 접속으로 처리해 입장 메시지를 지우므로 오버 로드 처리만 직접 호출)
        var je = new org.bukkit.event.player.PlayerJoinEvent(p, net.kyori.adventure.text.Component.text("x"));
        ol.onJoin(je);
        String joinSeen = Msg.plain(je.joinMessage());
        assertTrue(joinSeen.contains("OVER LORD") && joinSeen.contains("Lord") && joinSeen.contains("님이 강림하셨습니다"), joinSeen);
        assertFalse(obfuscatedText(je.joinMessage(), "Lord", false), "이름이 깨지지 않음");
        // 랭크 메뉴에 오버 로드 버튼
        p.performCommand("메뉴");
        click(p, 19, ClickType.LEFT);
        server.getScheduler().performTicks(2);
        click(p, 14, ClickType.LEFT);   // 랭크 확인
        server.getScheduler().performTicks(2);
        assertTrue(Msg.name(p.getOpenInventory().getTopInventory().getItem(22)).contains("OVER LORD"));
    }

    @Test
    void newbiePassLimitAndWhisperReply() {
        PlayerMock a = server.addPlayer("Wa");
        PlayerMock b = server.addPlayer("Wb");
        server.getScheduler().performTicks(5);
        PlayerData d = plugin.store().get(a);
        d.exMoney = 10_000;
        a.getInventory().clear();
        a.performCommand("yurocash");
        click(a, 28, ClickType.LEFT);
        assertEquals(7_000, d.exMoney);
        server.getScheduler().performTicks(2);
        click(a, 28, ClickType.LEFT);
        assertEquals(7_000, d.exMoney, "두 번째 구매 불가");
        assertTrue(allText(a.getOpenInventory().getTopInventory()).contains("이미 구매했습니다"));
        // 귓속말: 받은 사람은 이름 클릭으로 답장
        drain(b);
        assertTrue(a.performCommand("귓 Wb 안녕"));
        net.kyori.adventure.text.Component c;
        boolean reply = false;
        while ((c = b.nextComponentMessage()) != null) {
            var owner = hoverOwner(c, "Wa");
            if (owner != null && owner.clickEvent() != null && owner.clickEvent().value().equals("/귓 Wa ")) reply = true;
        }
        assertTrue(reply, "보낸 사람 이름 클릭 = 답장");
        drain(a);
        assertTrue(b.performCommand("답장 반가워"));
        assertTrue(drain(a).contains("반가워"));
    }

    private org.bukkit.event.entity.EntityDamageByEntityEvent hitEv(PlayerMock a, org.bukkit.entity.LivingEntity v) {
        var ev = new org.bukkit.event.entity.EntityDamageByEntityEvent(a, v, org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).withCausingEntity(a).withDirectEntity(a).build(), 4.0);
        server.getPluginManager().callEvent(ev);
        return ev;
    }

    @Test
    void claymoreTrueDamage() {
        PlayerMock a = server.addPlayer("DmgA");
        PlayerMock v = server.addPlayer("DmgV");
        server.getScheduler().performTicks(5);
        a.getInventory().setItemInMainHand(Items.claymoreSd());
        plugin.getConfig().set("weapons.claymore.judgment-damage", 0.0);
        // 체력 40 (하트 20칸): 방어구 무시 20 제거 -> 20
        var hp = v.getAttribute(org.bukkit.Registry.ATTRIBUTE.get(org.bukkit.NamespacedKey.minecraft("max_health")));
        hp.setBaseValue(40);
        v.setHealth(40.0);
        v.getInventory().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
        var ev = hitEv(a, v);
        assertEquals(20.0, v.getHealth(), 0.001, "하트 10칸 제거 (방어구 무시)");
        assertTrue(ev.getDamage() < 0.01, "바닐라 피해는 없음");
        // 남은 체력 10칸 이하 -> 1칸 남김
        v.setHealth(15.0);
        hitEv(a, v);
        assertEquals(2.0, v.getHealth(), 0.001, "1칸 남기기");
        // 이미 1칸 이하 -> 일반 공격으로 마무리 가능
        v.setHealth(2.0);
        var ev3 = hitEv(a, v);
        assertEquals(2.0, v.getHealth(), 0.001);
        assertEquals(4.0, ev3.getDamage(), 0.001, "일반 데미지 그대로");
        // 몬스터는 hit-damage 설정
        plugin.getConfig().set("weapons.claymore.hit-damage", 9.0);
        var z = server.getWorld("world").spawn(new org.bukkit.Location(server.getWorld("world"), 0, 64, 0), org.bukkit.entity.Zombie.class);
        assertEquals(9.0, hitEv(a, z).getDamage(), 0.001);
        plugin.getConfig().set("weapons.claymore.hit-damage", 0);
        plugin.getConfig().set("weapons.claymore.judgment-damage", 6.0);
    }

    private void killWith(PlayerMock k, PlayerMock v, ItemStack tool) {
        k.getInventory().setItemInMainHand(tool);
        v.setKiller(k);
        server.getPluginManager().callEvent(new org.bukkit.event.entity.PlayerDeathEvent(v, org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).withCausingEntity(k).withDirectEntity(k).build(), new java.util.ArrayList<>(), 0, null));
    }

    @Test
    void crownOverridesKillEffectAndWorksWithSpecialWeapons() {
        PlayerMock k = server.addPlayer("Kfx");
        PlayerMock v = server.addPlayer("Vfx");
        server.getScheduler().performTicks(5);
        PlayerData kd = plugin.store().get(k);
        kd.killEffect = 3;   // Explosion (처치자에게 위더 소리)
        // 크라운 꺼짐: 기존 킬 효과
        killWith(k, v, Items.quantumSword());
        k.assertSoundHeard("entity.wither.spawn");
        k.clearSounds();
        // 크라운 켜짐: 기존 킬 효과 없음, 크라운 연출
        kd.crownOwned = true;
        kd.crownKill = true;
        killWith(k, v, Items.quantumSword());
        k.assertSoundHeard("ui.toast.challenge_complete");
        assertFalse(k.getHeardSounds().stream().anyMatch(sd -> sd.getSound().toString().contains("wither.spawn")), "크라운이면 기존 킬 효과 없음");
        // 클레이모어 / 프로스트로 죽여도 크라운 연출
        for (ItemStack tool : new ItemStack[]{Items.claymoreSd(), Items.frostGrave()}) {
            k.clearSounds();
            killWith(k, v, tool);
            k.assertSoundHeard("ui.toast.challenge_complete");
        }
        // 크라운 끄면 클레이모어로도 기존 킬 효과
        kd.crownKill = false;
        k.clearSounds();
        killWith(k, v, Items.claymoreSd());
        k.assertSoundHeard("entity.wither.spawn");
    }

    @Test
    void hiddenCommandsForPlayers() {
        PlayerMock p = server.addPlayer("Plain");
        PlayerMock op = server.addPlayer("OpGuy");
        op.setOp(true);
        server.getScheduler().performTicks(5);
        java.util.Collection<String> cmds = new java.util.ArrayList<>(java.util.List.of("wave", "cashgive", "상자", "메뉴", "wavecore:메뉴", "전리품설정", "귓"));
        var e = new org.bukkit.event.player.PlayerCommandSendEvent(p, cmds);
        server.getPluginManager().callEvent(e);
        assertFalse(cmds.contains("wave"), "관리자 명령어 숨김");
        assertFalse(cmds.contains("cashgive"));
        assertFalse(cmds.contains("전리품설정"));
        assertFalse(cmds.contains("wavecore:메뉴"), "플러그인:명령어 숨김");
        assertTrue(cmds.contains("메뉴") && cmds.contains("상자") && cmds.contains("귓"), "일반 명령어는 보임");
        java.util.Collection<String> opCmds = new java.util.ArrayList<>(java.util.List.of("wave", "cashgive"));
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerCommandSendEvent(op, opCmds));
        assertEquals(2, opCmds.size(), "관리자는 전부 보임");
    }

    @Test
    void collectionRenewal() {
        PlayerMock p = server.addPlayer("Collector");
        server.getScheduler().performTicks(5);
        PlayerData d = plugin.store().get(p);
        p.getInventory().clear();
        assertTrue(p.performCommand("컬렉션"));
        var inv = p.getOpenInventory().getTopInventory();
        for (int sl : new int[]{10, 12, 14, 16, 20, 22, 24}) assertNotNull(inv.getItem(sl), "컬렉션 칸 " + sl);
        assertTrue(Msg.name(inv.getItem(14)).contains("Whisper of the Void"));
        assertTrue(Msg.name(inv.getItem(24)).contains("Glacier Storm"));
        // 아이템을 가지면 수집 기록 (버려도 유지)
        p.closeInventory();
        p.getInventory().addItem(Items.frostGrave());
        server.getScheduler().performTicks(25);
        p.getInventory().clear();
        server.getScheduler().performTicks(25);
        p.performCommand("컬렉션");
        assertTrue(allText(p.getOpenInventory().getTopInventory()).contains("보상 수령 가능"));
        click(p, 24, ClickType.LEFT);   // Glacier Storm
        server.getScheduler().performTicks(2);
        assertEquals("Glacier Storm", title(p));
        {   // 상태 문구 앞 빈 줄은 한 줄만
            var lore = p.getOpenInventory().getTopInventory().getItem(13).lore();
            for (int i = 1; i < lore.size(); i++)
                assertFalse(Msg.plain(lore.get(i)).isBlank() && Msg.plain(lore.get(i - 1)).isBlank(), "빈 줄 두 개 연속");
            assertTrue(Msg.plain(lore.get(lore.size() - 1)).contains("상태"));
        }
        GuiSnapshot.save(p, "collection_sub.json");
        click(p, 31, ClickType.LEFT);   // 보상
        boolean book = false, paper = false;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it == null) continue;
            if (Msg.name(it).contains("[칭호]") && Msg.name(it).contains("빙하의 군주")) book = true;
            if (Msg.name(it).contains("닉네임 색상 변경권") && Msg.name(it).contains("Glacier")) paper = true;
        }
        assertTrue(book, "칭호북");
        assertTrue(paper, "Glacier 닉네임 색상권");
        assertTrue(d.collectionClaimed.contains("glacier"));
        // 두 번 받기 불가
        int before = 0;
        for (ItemStack it : p.getInventory().getContents()) if (it != null) before++;
        server.getScheduler().performTicks(2);
        click(p, 31, ClickType.LEFT);
        int after = 0;
        for (ItemStack it : p.getInventory().getContents()) if (it != null) after++;
        assertEquals(before, after);
        // 미완성 컬렉션은 보상 불가 (Sealed: 검만)
        p.getInventory().clear();
        p.getInventory().addItem(Items.sealedSword());
        server.getScheduler().performTicks(25);
        p.performCommand("컬렉션");
        click(p, 10, ClickType.LEFT);
        server.getScheduler().performTicks(2);
        click(p, 31, ClickType.LEFT);
        assertFalse(d.collectionClaimed.contains("sealed"));
        // 칭호북 사용 -> 칭호 보유 + 적용
        p.closeInventory();
        p.getInventory().clear();
        p.getInventory().setItemInMainHand(Items.collectionTitleBook("void", "Whisper of the Void"));
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerInteractEvent(p, org.bukkit.event.block.Action.RIGHT_CLICK_AIR, p.getInventory().getItemInMainHand(), null, org.bukkit.block.BlockFace.UP, org.bukkit.inventory.EquipmentSlot.HAND));
        assertTrue(d.titles.contains("void"));
        assertEquals("void", d.activeTitle);
    }

    @Test
    void tutorialFlowAndReward() {
        PlayerMock p = server.addPlayer("Newbie1");
        server.getScheduler().performTicks(30);
        PlayerData d = plugin.store().get(p);
        assertTrue(d.tutorialStarted, "첫 접속 시 자동 시작");
        long money = d.money;
        p.setSneaking(true);
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerSwapHandItemsEvent(p, new ItemStack(Material.AIR), new ItemStack(Material.AIR)));
        click(p, 11, ClickType.LEFT);   // 이동 메뉴
        server.getScheduler().performTicks(2);
        p.performCommand("상점");
        p.performCommand("배낭");
        p.closeInventory();
        p.performCommand("상자");
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerCommandPreprocessEvent(p, "/pass"));
        p.performCommand("컬렉션");
        assertEquals(7, d.tutorialSteps.size());
        assertFalse(d.tutorialFinished);
        p.closeInventory();
        p.teleport(new org.bukkit.Location(server.getWorld("world"), 0, 64, 0));
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerChangedWorldEvent(p, server.getWorld("spawn-world")));
        assertTrue(d.tutorialFinished, "모든 단계 완료");
        assertEquals(money + 30_000, d.money, "보상 캐시");
        boolean pack = false;
        for (ItemStack it : p.getInventory().getContents()) if (Msg.nameIs(it, Items.N_NEWBIE_PACKAGE)) pack = true;
        assertTrue(pack, "뉴비 지원 패키지");
        assertTrue(p.performCommand("튜토리얼"));
        assertTrue(allText(p.getOpenInventory().getTopInventory()).contains("튜토리얼을 완료했습니다"));
    }

    @Test
    void noColoredGlassPanes() {
        PlayerMock p = server.addPlayer("Panes");
        server.getScheduler().performTicks(5);
        for (String c : new String[]{"메뉴", "levelstats", "상점", "컬렉션", "상자", "워프", "오버로드", "튜토리얼"}) {
            p.closeInventory();
            p.performCommand(c);
            server.getScheduler().performTicks(2);
            for (ItemStack it : p.getOpenInventory().getTopInventory().getContents()) {
                if (it != null && it.getType().name().endsWith("GLASS_PANE")) assertEquals(Material.BLACK_STAINED_GLASS_PANE, it.getType(), "/" + c + " 에 색 유리판");
            }
        }
    }

    @Test
    void premiumPassVoucherAndLevelPurchase() {
        MockBukkit.load(com.premiumpass.PremiumPass.class);   // 진짜 PremiumPass
        PlayerMock p = server.addPlayer("PassBuyer");
        server.getScheduler().performTicks(5);
        PlayerData d = plugin.store().get(p);
        d.exMoney = 100_000;
        p.getInventory().clear();
        com.premiumpass.api.PremiumPassAPI.addLevels(p, 95 - com.premiumpass.api.PremiumPassAPI.getLevel(p));
        assertEquals(95, com.premiumpass.api.PremiumPassAPI.getLevel(p));
        assertTrue(p.performCommand("yurocash"));
        GuiSnapshot.save(p, "paidshop.json");
        var inv = p.getOpenInventory().getTopInventory();
        assertTrue(Msg.plain(inv.getItem(29).getItemMeta().displayName()).contains("프리미엄 패스권") || inv.getItem(29).getItemMeta().getDisplayName().contains("프리미엄 패스권"));
        assertTrue(allText(inv).contains("25,000"));
        assertFalse(allText(inv).contains("패스 레벨 구매"), "유료 캐시 상점에는 레벨 구매 없음");
        for (int i = 10; i <= 13; i++) assertNotNull(inv.getItem(i), "왼쪽부터 채움 " + i);
        click(p, 29, ClickType.LEFT);   // 패스권 25,000
        assertEquals(75_000, d.exMoney);
        assertTrue(java.util.Arrays.stream(p.getInventory().getContents()).anyMatch(it -> it != null && it.getType() == Material.PAPER));
        // 레벨 구매: 1레벨 1,750
        server.getScheduler().performTicks(2);
        assertTrue(p.performCommand("패스레벨구매"));   // /pass 메뉴의 레벨 구매 버튼이 실행하는 명령어
        server.getScheduler().performTicks(2);
        assertEquals("패스 레벨 구매", title(p));
        GuiSnapshot.save(p, "passlevel.json");
        click(p, 11, ClickType.LEFT);   // +1
        assertEquals(96, com.premiumpass.api.PremiumPassAPI.getLevel(p));
        assertEquals(75_000 - 1_750, d.exMoney);
        server.getScheduler().performTicks(2);
        click(p, 15, ClickType.LEFT);   // +10 -> 최대 100 까지 4레벨만, 4 x 1,750
        assertEquals(100, com.premiumpass.api.PremiumPassAPI.getLevel(p));
        assertEquals(75_000 - 1_750 * 5, d.exMoney, "최대 레벨까지만 결제");
        server.getScheduler().performTicks(2);
        click(p, 11, ClickType.LEFT);   // 최대 -> 결제 안 됨
        assertEquals(75_000 - 1_750 * 5, d.exMoney);
        // 명령어로도 열림 (PremiumPass 메뉴 버튼이 이 명령어를 실행)
        p.closeInventory();
        assertTrue(p.performCommand("패스레벨구매"));
        assertEquals("패스 레벨 구매", title(p));
    }

    @Test
    void arbiterTitleAndChronosScreen() {
        assertTrue(Msg.plainOf(Items.COLLECTION_TITLES[5][1]).contains("천공의 심판자"));
        PlayerMock p = server.addPlayer("Chrono");
        server.getScheduler().performTicks(5);
        PlayerData d = plugin.store().get(p);
        p.getInventory().clear();
        p.getInventory().addItem(Items.claymoreSd());
        server.getScheduler().performTicks(25);
        p.performCommand("컬렉션");
        GuiSnapshot.save(p, "collection.json");
        assertTrue(allText(p.getOpenInventory().getTopInventory()).contains("1 / 1"), "Arbiter: 클레이모어만으로 완성");
        // 크로노스 강화 화면: 빈 상태, 칼날 올린 상태
        p.getInventory().clear();
        p.getInventory().setItem(0, Items.chronos(1));
        for (int i = 0; i < 4; i++) p.getInventory().addItem(Items.timeShard());
        p.getInventory().addItem(new ItemStack(Material.NETHERITE_SCRAP, 3));
        assertTrue(p.performCommand("chronosupgr"));
        GuiSnapshot.save(p, "chronos_empty.json");
        clickBottom(p, 0);
        GuiSnapshot.save(p, "chronos.json");
        var inv = p.getOpenInventory().getTopInventory();
        assertEquals(1, Items.chronosStage(inv.getItem(11)));
        assertTrue(Msg.name(inv.getItem(13)).contains("재료가 부족"), Msg.name(inv.getItem(13)));
        assertTrue(Msg.name(inv.getItem(21)).contains("✘") && Msg.name(inv.getItem(21)).contains("x10"), Msg.name(inv.getItem(21)));
        assertTrue(Msg.name(inv.getItem(23)).contains("✔") && Msg.name(inv.getItem(23)).contains("네더라이트 파편"), Msg.name(inv.getItem(23)));
        assertEquals(2, Items.chronosStage(inv.getItem(15)), "결과 미리보기 +2");
        assertEquals(Material.ARROW, inv.getItem(12).getType());
    }

    @Test
    void shopScreens() {
        PlayerMock p = server.addPlayer("Shopper");
        server.getScheduler().performTicks(5);
        PlayerData d = plugin.store().get(p);
        d.money = 52_300;
        d.exMoney = 31_500;
        for (String[] c : new String[][]{{"상점", "mainshop"}, {"combatshop", "combatshop"}, {"ore", "oreshop"}, {"enchantbook", "enchantshop"}, {"eat", "foodshop"}}) {
            assertTrue(p.performCommand(c[0]));
            GuiSnapshot.save(p, c[1] + ".json");
            String t = allText(p.getOpenInventory().getTopInventory());
            assertFalse(t.contains("구매 가격:") || t.contains(" Cash"), c[0] + " 옛 가격 표시 남음: " + t);
        }
        assertTrue(p.performCommand("yurocash"));
        assertFalse(allText(p.getOpenInventory().getTopInventory()).contains(" Cash"));
    }

    private void chatAndWait(PlayerMock p, String msg) {
        p.chat(msg);
        for (int i = 0; i < 20; i++) {
            try {
                server.getScheduler().waitAsyncEventsFinished();
            } catch (Throwable ignored) {
            }
            server.getScheduler().performTicks(1);
        }
    }

    @Test
    void teamGui() {
        PlayerMock a = server.addPlayer("Master");
        PlayerMock b = server.addPlayer("Joiner");
        server.getScheduler().performTicks(5);
        PlayerData da = plugin.store().get(a);
        da.money = 50_000;
        assertTrue(a.performCommand("팀"));
        assertEquals("팀", title(a));
        click(a, 11, ClickType.LEFT);                 // 팀 만들기 -> 채팅으로 이름
        chatAndWait(a, "x");                          // 너무 짧음 -> 거부
        assertNull(plugin.store().global().teamOf(a.getUniqueId()));
        a.performCommand("팀");
        click(a, 11, ClickType.LEFT);
        chatAndWait(a, "레드팀");
        GlobalData.Team t = plugin.store().global().teamOf(a.getUniqueId());
        assertNotNull(t, "채팅으로 팀 생성");
        assertEquals("레드팀", t.id);
        assertEquals(10_000, da.money);
        server.getScheduler().performTicks(2);
        assertEquals("팀 · 레드팀", title(a));
        // 초대: 플레이어 고르기
        click(a, 12, ClickType.LEFT);
        server.getScheduler().performTicks(2);
        assertEquals("팀원 초대", title(a));
        GuiSnapshot.save(a, "team_invite.json");
        assertTrue(Msg.name(a.getOpenInventory().getTopInventory().getItem(0)).contains("Joiner"));
        click(a, 0, ClickType.LEFT);
        // 초대받은 사람: 팀 없음 화면에서 수락
        assertTrue(b.performCommand("팀"));
        GuiSnapshot.save(b, "team_none.json");
        assertEquals(Material.PAPER, b.getOpenInventory().getTopInventory().getItem(15).getType());
        click(b, 15, ClickType.LEFT);
        assertEquals(t, plugin.store().global().teamOf(b.getUniqueId()));
        // 팀 메뉴: 팀원 목록 (마스터 먼저)
        a.performCommand("팀");
        var inv = a.getOpenInventory().getTopInventory();
        assertTrue(Msg.name(inv.getItem(19)).contains("Master") && Msg.name(inv.getItem(19)).contains("★"));
        assertTrue(Msg.name(inv.getItem(20)).contains("Joiner"));
        assertTrue(allText(inv).contains("팀원 관리"));
        // 팀 채팅 켜기
        click(a, 13, ClickType.LEFT);
        assertEquals("team", da.channel);
        server.getScheduler().performTicks(2);
        // 역할: 명령어로 승급 (팀원 -> 엘리트 -> 부마스터), 강등
        assertTrue(a.performCommand("팀 승급 Joiner"));
        assertEquals("엘리트", t.members.get(b.getUniqueId()));
        a.performCommand("팀 역할 Joiner 부마스터");
        assertEquals("부마스터", t.members.get(b.getUniqueId()));
        b.performCommand("팀 추방 Master");                       // 부마스터는 마스터 추방 불가
        assertTrue(t.members.containsKey(a.getUniqueId()));
        a.performCommand("팀 강등 Joiner");
        assertEquals("엘리트", t.members.get(b.getUniqueId()));
        // 엘리트는 초대 가능, 추방 불가
        PlayerMock c = server.addPlayer("Third");
        server.getScheduler().performTicks(2);
        b.performCommand("팀 초대 Third");
        c.performCommand("팀 수락");
        assertEquals("팀원", t.members.get(c.getUniqueId()));
        b.performCommand("팀 추방 Third");
        assertTrue(t.members.containsKey(c.getUniqueId()), "엘리트는 추방 불가");
        // 접속 중인 팀원 화면
        assertTrue(a.performCommand("팀 접속"));
        assertEquals("접속 중인 팀원 (3/3)", title(a));
        assertTrue(allText(a.getOpenInventory().getTopInventory()).contains("위치 :"));
        GuiSnapshot.save(a, "team_online.json");
        a.performCommand("팀");
        GuiSnapshot.save(a, "team_menu.json");
        assertTrue(Msg.name(a.getOpenInventory().getTopInventory().getItem(20)).contains("◆ Joiner"), "역할 순으로 표시");
        // 팀원 관리 화면 (GUI): 승급 -> 부마스터
        click(a, 20, ClickType.LEFT);
        server.getScheduler().performTicks(2);
        assertEquals("팀원 관리 · Joiner", title(a));
        GuiSnapshot.save(a, "team_member.json");
        click(a, 10, ClickType.LEFT);
        assertEquals("부마스터", t.members.get(b.getUniqueId()));
        server.getScheduler().performTicks(2);
        // 부마스터는 팀원을 추방할 수 있음
        b.performCommand("팀 추방 Third");
        assertFalse(t.members.containsKey(c.getUniqueId()));
        // 마스터 넘기기 (Shift + 좌클릭) -> 나는 부마스터
        a.performCommand("팀");
        click(a, 20, ClickType.LEFT);
        server.getScheduler().performTicks(2);
        click(a, 14, ClickType.LEFT);                 // 그냥 클릭은 안 됨
        assertEquals("부마스터", t.members.get(b.getUniqueId()));
        click(a, 14, ClickType.SHIFT_LEFT);
        assertEquals("마스터", t.members.get(b.getUniqueId()));
        assertEquals("부마스터", t.members.get(a.getUniqueId()));
        assertTrue(a.performCommand("팀 도움말"));
        // 마스터가 탈퇴하면 남은 팀원이 마스터
        server.getScheduler().performTicks(2);
        b.performCommand("팀");
        click(b, 16, ClickType.LEFT);                 // 그냥 클릭은 탈퇴 안 됨
        assertEquals(t, plugin.store().global().teamOf(b.getUniqueId()));
        click(b, 16, ClickType.SHIFT_RIGHT);
        assertNull(plugin.store().global().teamOf(b.getUniqueId()));
        assertEquals("마스터", t.members.get(a.getUniqueId()));
    }

    @Test
    void echoesKeyLimitsSoulSplitterAndWeaponShop() {
        PlayerMock p = server.addPlayer("Buyer2");
        server.getScheduler().performTicks(5);
        PlayerData d = plugin.store().get(p);
        d.exMoney = 20_000;
        d.money = 100_000;
        kr.wave.core.module.CrateModule cm = plugin.module(kr.wave.core.module.CrateModule.class);
        assertTrue(p.performCommand("yurocash"));
        var inv = p.getOpenInventory().getTopInventory();
        assertTrue(Msg.name(inv.getItem(23)).contains("The Echoes of Natural"), Msg.name(inv.getItem(23)));
        assertTrue(allText(inv).contains("영구 구매 제한 : 0/1"), allText(inv));
        click(p, 23, ClickType.LEFT);
        assertEquals(1, cm.virtualKeys(p, cm.crate("TheEchoesOfNatural")));
        assertEquals(15_000, d.exMoney);
        // Soul Splitter: 새 이름/설명, 예전 이름도 인식
        ItemStack ss = Items.soulSplitter(plugin, 120);
        assertTrue(Msg.name(ss).contains("[Harvest] [Lv.♱] 망령 수확자"), Msg.name(ss));
        String lore = String.join("\n", ss.getItemMeta().lore().stream().map(Msg::plain).toList());
        assertTrue(lore.contains("깃든 망령 : 120 / 250") && lore.contains("✦ 두 번째 각성") && lore.contains("✧ 마지막 각성") && lore.contains("날카로움 VI · 약탈 V"), lore);
        assertEquals(5, ss.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.SHARPNESS));
        ItemStack old = It.of(Material.NETHERITE_SWORD).name("<gradient:#FF0000:#FFA9B4:#6B5760>[✦] [Counter] Soul Splitter</gradient>").build();
        assertTrue(Items.isSoulSplitter(old), "예전 Soul Splitter 도 같은 무기로 인식");
        assertTrue(Items.isSoulSplitter(ss));
        assertTrue(Msg.plain(Msg.lore(Items.soulSplitterLore(300)).get(Msg.lore(Items.soulSplitterLore(300)).size() - 1)).contains("완전 각성"));
        // 무기 상점
        p.getInventory().clear();
        assertTrue(p.performCommand("combatshop"));
        inv = p.getOpenInventory().getTopInventory();
        assertFalse(allText(inv).contains("철퇴") || allText(inv).contains("삼지창"));
        for (int i = 0; i < inv.getSize(); i++) if (inv.getItem(i) != null) assertNotEquals(Material.MACE, inv.getItem(i).getType());
        assertTrue(Msg.name(inv.getItem(10)).contains("망령 수확자"));
        assertTrue(Msg.name(inv.getItem(12)).contains("잿불 낙인검"));
        for (int i = 19; i <= 23; i++) assertTrue(inv.getItem(i).getEnchantments().isEmpty(), "인챈트된 장비 없음 " + i);
        for (int i = 19; i <= 25; i++) if (inv.getItem(i) != null) {   // 전투 장비 줄
            assertNotEquals(Material.DIAMOND_SWORD, inv.getItem(i).getType(), "다이아몬드 검 없음");
            assertNotEquals(Material.NETHERITE_SWORD, inv.getItem(i).getType(), "네더라이트 검 없음");
        }
        assertEquals(Material.IRON_AXE, inv.getItem(19).getType());
        assertEquals(Material.FISHING_ROD, inv.getItem(20).getType());
        click(p, 19, ClickType.LEFT);
        assertEquals(97_500, d.money);
        assertTrue(p.getInventory().getItem(p.getInventory().first(Material.IRON_AXE)).getEnchantments().isEmpty());
    }

    @Test
    void emberBrand() {
        PlayerMock p = server.addPlayer("Ember");
        server.getScheduler().performTicks(5);
        PlayerData d = plugin.store().get(p);
        d.money = 100_000;
        p.getInventory().clear();
        assertTrue(p.performCommand("combatshop"));
        click(p, 12, ClickType.LEFT);
        assertEquals(30_000, d.money);
        ItemStack sword = p.getInventory().getItem(p.getInventory().first(Material.DIAMOND_SWORD));
        assertTrue(Msg.nameHas(sword, Items.N_EMBER));
        assertTrue(sword.getEnchantments().isEmpty() && !sword.getItemMeta().isUnbreakable());
        String el = String.join("\n", sword.getItemMeta().lore().stream().map(Msg::plain).toList());
        assertFalse(el.contains("부서지지 않습니다") || el.contains("회복") || el.contains("타오른다"), el);
        assertTrue(el.contains("등급 : Epic") && el.contains("4~7개"), el);
        plugin.getConfig().set("weapons.ember.min-stacks", 5);
        plugin.getConfig().set("weapons.ember.max-stacks", 5);
        p.getInventory().setItemInMainHand(sword);
        p.closeInventory();
        var z = (org.bukkit.entity.LivingEntity) server.getWorld("world").spawnEntity(new org.bukkit.Location(server.getWorld("world"), 3, 70, 3), org.bukkit.entity.EntityType.ZOMBIE);
        double[] dmg = new double[5];
        for (int i = 0; i < 5; i++) {
            var ev = new org.bukkit.event.entity.EntityDamageByEntityEvent(p, z, org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK, org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).withDirectEntity(p).withCausingEntity(p).build(), 5.0);
            server.getPluginManager().callEvent(ev);
            dmg[i] = ev.getDamage();
            if (i == 0) {   // 바로 연타 -> 낙인 안 쌓임
                var spam = new org.bukkit.event.entity.EntityDamageByEntityEvent(p, z, org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK, org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).withDirectEntity(p).withCausingEntity(p).build(), 5.0);
                server.getPluginManager().callEvent(spam);
                assertEquals(5.0, spam.getDamage(), 1e-9);
            }
            try { Thread.sleep(480); } catch (InterruptedException ignored) { }
        }
        for (int i = 0; i < 4; i++) assertEquals(5.0, dmg[i], 1e-9, "적중 " + (i + 1));
        assertEquals(9.0, dmg[4], 1e-9, "설정한 개수(5)에서 낙인 폭발 +4");
        // 기본 4~7: 3번째까지는 절대 폭발하지 않음
        plugin.getConfig().set("weapons.ember.min-stacks", 4);
        plugin.getConfig().set("weapons.ember.max-stacks", 7);
        assertTrue(z.getFireTicks() > 0, "화염");
    }

    @Test
    void umbralChain() {
        plugin.getConfig().set("weapons.shade.chance", 1.0);
        PlayerMock p = server.addPlayer("Chainer");
        server.getScheduler().performTicks(5);
        PlayerData d = plugin.store().get(p);
        d.money = 60_000;
        p.getInventory().clear();
        assertTrue(p.performCommand("combatshop"));
        var inv = p.getOpenInventory().getTopInventory();
        assertEquals(Items.chainMaterial(), inv.getItem(13).getType());
        assertTrue(Msg.name(inv.getItem(13)).contains("[✦] 그림자 사슬"));
        String lore = String.join("\n", inv.getItem(13).getItemMeta().lore().stream().map(Msg::plain).toList());
        assertTrue(lore.contains("등급 : Epic") && lore.contains("끌어당기고 묶어버립니다"), lore);
        GuiSnapshot.save(p, "combatshop.json");
        click(p, 13, ClickType.LEFT);
        assertEquals(5_000, d.money);
        p.closeInventory();
        ItemStack chain = p.getInventory().getItem(p.getInventory().first(Items.chainMaterial()));
        p.getInventory().clear();
        p.getInventory().setItemInMainHand(chain);
        p.teleport(new org.bukkit.Location(server.getWorld("world"), 0, 70, 0));
        PlayerMock v = server.addPlayer("Chained");
        v.teleport(new org.bukkit.Location(server.getWorld("world"), 5, 70, 0));
        var ev = new org.bukkit.event.entity.EntityDamageByEntityEvent(p, v, org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK, org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).withDirectEntity(p).withCausingEntity(p).build(), 1.0);
        server.getPluginManager().callEvent(ev);
        assertTrue(v.hasPotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS), "묶임");
        server.getScheduler().performTicks(5);
        assertTrue(v.getVelocity().getX() < 0, "공격자 쪽으로 끌려옴: " + v.getVelocity());
    }
    @Test
    void echoBell() {
        plugin.getConfig().set("weapons.bell.chance", 1.0);
        PlayerMock p = server.addPlayer("Ringer");
        server.getScheduler().performTicks(5);
        PlayerData d = plugin.store().get(p);
        d.money = 60_000;
        p.getInventory().clear();
        assertTrue(p.performCommand("combatshop"));
        var inv = p.getOpenInventory().getTopInventory();
        assertEquals(Material.BELL, inv.getItem(14).getType());
        assertTrue(Msg.name(inv.getItem(14)).contains("[✦] 메아리 종"));
        GuiSnapshot.save(p, "combatshop.json");
        click(p, 14, ClickType.LEFT);
        assertEquals(10_000, d.money);
        p.closeInventory();
        p.getInventory().setItemInMainHand(p.getInventory().getItem(p.getInventory().first(Material.BELL)));
        p.teleport(new org.bukkit.Location(server.getWorld("world"), 0, 70, 0));
        PlayerMock v = server.addPlayer("Rung");
        v.teleport(new org.bukkit.Location(server.getWorld("world"), 2, 70, 0));
        var ev = new org.bukkit.event.entity.EntityDamageByEntityEvent(p, v, org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK, org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).withDirectEntity(p).withCausingEntity(p).build(), 1.0);
        server.getPluginManager().callEvent(ev);
        assertTrue(v.hasPotionEffect(org.bukkit.potion.PotionEffectType.DARKNESS) && v.hasPotionEffect(org.bukkit.potion.PotionEffectType.NAUSEA));
        assertTrue(v.getVelocity().getX() > 0, "공격자 반대쪽으로 밀려남: " + v.getVelocity());
        server.getScheduler().performTicks(12);
    }
    @Test
    void soulHarvesterMonstersAndLegacyNames() {
        PlayerMock p = server.addPlayer("Harvester");
        server.getScheduler().performTicks(5);
        // 고유 번호가 없는 칼 (상자 보상 등): 처음 처치 때 번호가 붙고 셈
        ItemStack bare = Items.soulSplitterDisplay();
        String lore0 = String.join("\n", bare.getItemMeta().lore().stream().map(Msg::plain).toList());
        assertTrue(lore0.contains("등급 : Epic") && lore0.contains("플레이어나 몬스터") && !lore0.contains("다음 사냥을 부른다"), lore0);
        p.getInventory().clear();
        p.getInventory().setItemInMainHand(bare);
        var w = server.getWorld("world");
        for (var type : new org.bukkit.entity.EntityType[]{org.bukkit.entity.EntityType.ZOMBIE, org.bukkit.entity.EntityType.SLIME, org.bukkit.entity.EntityType.PIG}) {
            var mob = (org.bukkit.entity.LivingEntity) w.spawnEntity(new org.bukkit.Location(w, 1, 70, 1), type);
            ((org.mockbukkit.mockbukkit.entity.LivingEntityMock) mob).setKiller(p);
            server.getPluginManager().callEvent(new org.bukkit.event.entity.EntityDeathEvent(mob, org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).withDirectEntity(p).withCausingEntity(p).build(), new java.util.ArrayList<>()));
        }
        String lore = String.join("\n", p.getInventory().getItemInMainHand().getItemMeta().lore().stream().map(Msg::plain).toList());
        assertTrue(lore.contains("깃든 망령 : 2 / 50"), "좀비 + 슬라임은 셈, 돼지는 안 셈: " + lore);
        // 예전 영어 이름의 보조 무기: 그대로 작동하고, 쓰는 순간 한국어 이름으로
        plugin.getConfig().set("weapons.bell.chance", 1.0);
        ItemStack old = It.of(Material.BELL).name("<gradient:#FFD86B:#8A6BFF>[✦] Echo Bell</gradient>").build();
        p.getInventory().setItemInMainHand(old);
        PlayerMock v = server.addPlayer("OldVictim");
        var ev = new org.bukkit.event.entity.EntityDamageByEntityEvent(p, v, org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK, org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).withDirectEntity(p).withCausingEntity(p).build(), 1.0);
        server.getPluginManager().callEvent(ev);
        assertTrue(v.hasPotionEffect(org.bukkit.potion.PotionEffectType.DARKNESS), "예전 이름도 작동");
        assertTrue(Msg.name(p.getInventory().getItemInMainHand()).contains("[✦] 메아리 종"), Msg.name(p.getInventory().getItemInMainHand()));
        assertTrue(Msg.name(Items.timePiecerWind()).contains("[✦] 시간을 가르는 바람"));
    }
    @Test
    void weaponLoreHighlights() {
        PlayerMock p = server.addPlayer("LoreView");
        server.getScheduler().performTicks(5);
        ItemStack[] all = {Items.claymoreSd(), Items.frostGrave(), Items.newCharacteristic(), Items.sealedSword(), Items.ruinerScythe(), Items.chaosApocalypse(),
                Items.voidBow(), Items.quantumSword(), Items.thunderAxe(), Items.voidChest(), Items.russellHelmet(), Items.chaosHelmet(), Items.natureBoots(), Items.ruinerPants(), Items.auroraBoots(), Items.judgeHelmet(), Items.chronos(5),
                Items.timePiecerWind(), Items.umbralChain(), Items.echoBell(), Items.emberBrand(), Items.soulSplitterDisplay()};
        kr.wave.core.util.Gui g = new kr.wave.core.util.Gui(3, "&8&l무기 · 방어구");
        for (int i = 0; i < all.length; i++) g.set(i, all[i]);
        g.open(p);
        GuiSnapshot.save(p, "weapons.json");
        String chaos = String.join("\n", Items.chaosApocalypse().getItemMeta().lore().stream().map(Msg::plain).toList());
        assertTrue(chaos.contains("적의 체력을 절반") && chaos.contains("우클릭 시 주변 5칸의 적에게") && !chaos.contains("혼돈의 파열") && !chaos.contains("화염 저항") && !chaos.contains("세트 :"), chaos);
    }
    @Test
    void newCharacteristicKoreanName() {
        assertTrue(Msg.name(Items.newCharacteristic()).contains("[🌙] [Lv.DD] 초승달의 서약"), Msg.name(Items.newCharacteristic()));
        PlayerMock p = server.addPlayer("Inscriber");
        server.getScheduler().performTicks(5);
        p.teleport(new org.bukkit.Location(server.getWorld("world"), 0, 64, 0));
        ItemStack old = It.of(Material.MUSIC_DISC_5).name("<gradient:#1E2586:#C8B0B0><b>[🌙] [Lv.DD] The New Characteristic</b></gradient>").build();
        p.getInventory().clear();
        p.getInventory().setItemInMainHand(old);
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerInteractEvent(p, org.bukkit.event.block.Action.RIGHT_CLICK_AIR, old, null, org.bukkit.block.BlockFace.SELF, org.bukkit.inventory.EquipmentSlot.HAND));
        assertTrue(Msg.name(p.getInventory().getItemInMainHand()).contains("초승달의 서약"), "우클릭하면 새 이름: " + Msg.name(p.getInventory().getItemInMainHand()));
        assertTrue(p.hasPotionEffect(org.bukkit.potion.PotionEffectType.STRENGTH), "예전 이름도 능력 발동");
        assertTrue(kr.wave.core.module.CoreModule.protectedName("[🌙] [Lv.DD] The New Characteristic") && kr.wave.core.module.CoreModule.protectedName("새 시대의 각인") && kr.wave.core.module.CoreModule.protectedName("신월의 기록") && kr.wave.core.module.CoreModule.protectedName("초승달의 서약"));
    }
    @Test
    void chaosSet() {
        PlayerMock p = server.addPlayer("ChaosUser");
        server.getScheduler().performTicks(5);
        var w = server.getWorld("world");
        p.teleport(new org.bukkit.Location(w, 0, 70, 0));
        p.getInventory().clear();
        p.getInventory().setItemInMainHand(Items.chaosApocalypse());
        // 고정 데미지 6.5
        var z = (org.bukkit.entity.LivingEntity) w.spawnEntity(new org.bukkit.Location(w, 2, 70, 0), org.bukkit.entity.EntityType.ZOMBIE);
        var ev = new org.bukkit.event.entity.EntityDamageByEntityEvent(p, z, org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK, org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).withDirectEntity(p).withCausingEntity(p).build(), 1.0);
        server.getPluginManager().callEvent(ev);
        assertTrue(ev.getDamage() == 6.5 || ev.getDamage() < 6.5 * 1.01, "고정 데미지: " + ev.getDamage());
        assertEquals(6.5, ev.getDamage(), 1e-9);
        // 우클릭: 혼돈의 파열 -> 주변 적 몹에 위더 + 피해, 팀원은 제외
        PlayerMock mate = server.addPlayer("ChaosMate");
        mate.teleport(new org.bukkit.Location(w, 1, 70, 1));
        plugin.store().get(p).money = 50_000;
        p.performCommand("팀 생성 혼돈팀");
        p.performCommand("팀 초대 ChaosMate");
        mate.performCommand("팀 수락");
        p.closeInventory();
        double zh = z.getHealth();
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerInteractEvent(p, org.bukkit.event.block.Action.RIGHT_CLICK_AIR, p.getInventory().getItemInMainHand(), null, org.bukkit.block.BlockFace.SELF, org.bukkit.inventory.EquipmentSlot.HAND));
        assertTrue(z.hasPotionEffect(org.bukkit.potion.PotionEffectType.WITHER) && z.getHealth() < zh, "몹 피해 + 위더");
        assertFalse(mate.hasPotionEffect(org.bukkit.potion.PotionEffectType.WITHER), "팀원 제외");
        // 다크니스 웨이브: 1번 어둠+구속, 2번 투명화+신속
        p.getInventory().setItemInMainHand(Items.darknessWave());
        var z2 = (org.bukkit.entity.LivingEntity) w.spawnEntity(new org.bukkit.Location(w, -3, 70, 0), org.bukkit.entity.EntityType.SKELETON);
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerInteractEvent(p, org.bukkit.event.block.Action.RIGHT_CLICK_AIR, p.getInventory().getItemInMainHand(), null, org.bukkit.block.BlockFace.SELF, org.bukkit.inventory.EquipmentSlot.HAND));
        assertTrue(z2.hasPotionEffect(org.bukkit.potion.PotionEffectType.DARKNESS) && z2.getPotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS).getAmplifier() == 2, "구속 III");
        assertNull(Items.special("abyss_helmet"), "심연의 공포 삭제");
        p.setSneaking(true);
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerInteractEvent(p, org.bukkit.event.block.Action.RIGHT_CLICK_AIR, p.getInventory().getItemInMainHand(), null, org.bukkit.block.BlockFace.SELF, org.bukkit.inventory.EquipmentSlot.HAND));
        server.getScheduler().performTicks(6);
        assertTrue(z2.hasPotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS), "어둠의 장막: 주변 적 실명");
        assertFalse(p.hasPotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY), "투명화 없음");
        assertEquals(Items.N_DARK_WAVE, Msg.name(Items.special("darkness_wave")));
        server.getScheduler().performTicks(15);
        // 화면용
        kr.wave.core.util.Gui g = new kr.wave.core.util.Gui(3, "&8&l혼돈의 아포칼립스 세트");
        g.set(11, Items.chaosApocalypse()); g.set(15, Items.darknessWave());
        g.set(19, Items.aristotlePickaxe()); g.set(21, Items.melon(1)); g.set(23, Items.newbiePass());
        g.open(p);
        GuiSnapshot.save(p, "chaosset.json");
    }
    @Test
    void joinRefreshesItems() {
        PlayerMock p = server.addPlayer("Returner");
        server.getScheduler().performTicks(30);
        // 예전 모습의 아이템들: 옛 설명의 공허의 속삭임(인챈트 유지 확인), 영어 이름 보조 무기, 옛 새 시대의 각인
        ItemStack bow = Items.voidBow();
        var bm = bow.getItemMeta();
        bm.lore(Msg.lore("&7예전 설명"));
        bow.setItemMeta(bm);
        bow.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.PUNCH, 2);   // 플레이어가 붙인 인챈트
        ItemStack wind = It.of(Material.BREEZE_ROD).name("<gradient:#00FF7C:#FFAFCA>[✦] Time piecer Wind</gradient>").lore("&7옛 설명").build();
        ItemStack nc = It.of(Material.MUSIC_DISC_5).name("&5[🌙] [Lv.DD] The New Characteristic").build();
        ItemStack dirt = It.of(Material.DIRT).name("&7그냥 흙").lore("&7건드리면 안 됨").build();
        p.getInventory().clear();
        p.getInventory().addItem(bow, wind, nc, dirt);
        PlayerData d = plugin.store().get(p);
        d.backpack[0] = It.of(Material.DIAMOND_SWORD).name("<gradient:#FF7A18:#FFD36E>[Ember] [Lv.✹] 잿불 낙인검</gradient>").lore("&7옛 설명").build();
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerJoinEvent(p, net.kyori.adventure.text.Component.empty()));
        server.getScheduler().performTicks(25);
        var inv = p.getInventory();
        ItemStack nb = inv.getItem(inv.first(Material.BOW));
        assertEquals(Items.voidBow().getItemMeta().lore(), nb.getItemMeta().lore(), "설명 최신화");
        assertEquals(2, nb.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.PUNCH), "인챈트 유지");
        assertTrue(Msg.name(inv.getItem(inv.first(Material.BREEZE_ROD))).contains("시간을 가르는 바람"));
        assertEquals(Items.N_NEW_CHAR, Msg.name(inv.getItem(inv.first(Material.MUSIC_DISC_5))));
        assertEquals(Items.emberBrand().getItemMeta().lore(), d.backpack[0].getItemMeta().lore(), "배낭 안 아이템도");
        assertEquals("그냥 흙", Msg.name(inv.getItem(inv.first(Material.DIRT))));
        assertTrue(Msg.plain(inv.getItem(inv.first(Material.DIRT)).getItemMeta().lore().get(0)).contains("건드리면 안 됨"));
        assertEquals("[🌙] [Lv.DD] 초승달의 서약", Msg.name(Items.newCharacteristic()));
        assertEquals(Items.N_NEW_CHAR, Msg.name(Items.template("[🌙] [Lv.DD] 신월의 기록").get()));
        // 직전 이름(새 시대의 각인)도 접속 시 새 이름으로
        assertEquals(Items.N_NEW_CHAR, Msg.name(Items.template("[🌙] [Lv.DD] 새 시대의 각인").get()));
        assertTrue(Items.isNewChar("[🌙] [Lv.DD] 새 시대의 각인"));
    }
    @Test
    void subWeaponNamesBold() {
        for (ItemStack it : new ItemStack[]{Items.timePiecerWind(), Items.umbralChain(), Items.echoBell()}) {
            var n = it.getItemMeta().displayName();
            boolean bold = n.decoration(net.kyori.adventure.text.format.TextDecoration.BOLD) == net.kyori.adventure.text.format.TextDecoration.State.TRUE
                    || n.children().stream().anyMatch(c -> c.decoration(net.kyori.adventure.text.format.TextDecoration.BOLD) == net.kyori.adventure.text.format.TextDecoration.State.TRUE);
            assertTrue(bold, Msg.name(it));
        }
        assertEquals(Items.N_TIME_WIND, Msg.name(Items.timePiecerWind()));
    }
    @Test
    void weaponActionBars() {
        PlayerMock p = server.addPlayer("BarUser");
        server.getScheduler().performTicks(5);
        var wm = plugin.module(kr.wave.core.module.WeaponModule.class);
        java.util.function.Supplier<String> bar = () -> {
            while (p.nextActionBar() != null) { }
            wm.secondTick();
            String last = null;
            net.kyori.adventure.text.Component c;
            while ((c = p.nextActionBar()) != null) last = Msg.plain(c);
            return last;
        };
        p.getInventory().clear();
        p.teleport(new org.bukkit.Location(server.getWorld("world"), 0, 64, 0));
        p.getInventory().setItemInMainHand(Items.sealedSword());
        String ready = bar.get();
        assertTrue(ready.contains("❄ 혹한의 연쇄") && ready.contains("» 사용 가능") && !ready.contains("✔") && ready.contains("우클릭"), ready);
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerInteractEvent(p, org.bukkit.event.block.Action.RIGHT_CLICK_AIR, p.getInventory().getItemInMainHand(), null, org.bukkit.block.BlockFace.SELF, org.bukkit.inventory.EquipmentSlot.HAND));
        String cool = bar.get();
        assertTrue(cool.contains("발동 중") && cool.contains("6초"), cool);
        p.getInventory().setItemInMainHand(Items.newCharacteristic());
        String two = bar.get();
        assertTrue(two.contains("🌙 월광의 힘") && two.contains("달빛 치유") && two.contains("│"), two);
        p.getInventory().setItemInMainHand(Items.claymoreSd());
        assertTrue(bar.get().contains("⚡ 심판의 빛 » 사용 가능 (타격 시 발동)"));
        // 화면용: 여러 상태의 액션바 (색 포함)
        java.util.function.Supplier<String> raw = () -> {
            while (p.nextActionBar() != null) { }
            wm.secondTick();
            String last = null;
            net.kyori.adventure.text.Component c;
            while ((c = p.nextActionBar()) != null) last = GuiSnapshot.legacy(c);
            return last;
        };
        StringBuilder out = new StringBuilder();
        p.getInventory().setItemInMainHand(Items.sealedSword()); out.append(raw.get()).append("\n");
        try {
            var f = wm.getClass().getDeclaredField("sealedCd"); f.setAccessible(true);
            ((java.util.Map<java.util.UUID, Long>) f.get(wm)).put(p.getUniqueId(), System.currentTimeMillis() + 18_000);
            out.append(raw.get()).append("\n");
            p.getInventory().setItemInMainHand(Items.chronos(6)); out.append(raw.get()).append("\n");
            var fs = wm.getClass().getDeclaredField("sandStormUntil"); fs.setAccessible(true);
            ((java.util.Map<java.util.UUID, Long>) fs.get(wm)).put(p.getUniqueId(), System.currentTimeMillis() + 9_000);
            out.append(raw.get()).append("\n");
            p.getInventory().setItemInMainHand(Items.newCharacteristic());
            var f1 = wm.getClass().getDeclaredField("newCharCd1"); f1.setAccessible(true);
            ((java.util.Map<java.util.UUID, Long>) f1.get(wm)).put(p.getUniqueId(), System.currentTimeMillis() + 40_000);
            out.append(raw.get()).append("\n");
            p.getInventory().setItemInMainHand(Items.claymoreSd());
            var fc = wm.getClass().getDeclaredField("claymoreCd"); fc.setAccessible(true);
            ((java.util.Map<java.util.UUID, Long>) fc.get(wm)).put(p.getUniqueId(), System.currentTimeMillis() + 8_000);
            out.append(raw.get()).append("\n");
            p.getInventory().setItemInMainHand(Items.chaosApocalypse()); out.append(raw.get()).append("\n");
            p.getInventory().setItemInMainHand(Items.darknessWave());
            var fw = wm.getClass().getDeclaredField("waveCd2"); fw.setAccessible(true);
            ((java.util.Map<java.util.UUID, Long>) fw.get(wm)).put(p.getUniqueId(), System.currentTimeMillis() + 70_000);
            out.append(raw.get()).append("\n");
            GuiSnapshot.text("actionbars.txt", out.toString());
        } catch (Exception ex) { throw new RuntimeException(ex); }
    }
    @Test
    void sealedSwordRebuild() {
        plugin.getConfig().set("weapons.sealed.chain-chance", 1.0);
        plugin.getConfig().set("weapons.sealed.crit-chance", 0.0);
        PlayerMock p = server.addPlayer("Emperor");
        server.getScheduler().performTicks(5);
        var w = server.getWorld("world");
        p.teleport(new org.bukkit.Location(w, 0, 70, 0));
        p.getInventory().clear();
        p.getInventory().setItemInMainHand(Items.sealedSword());
        String lore = String.join("\n", Items.sealedSword().getItemMeta().lore().stream().map(Msg::plain).toList());
        assertTrue(lore.contains("우클릭 시 6초간 타격이 주변 적에게 튀어 피해 1칸") && !lore.contains("냉기") && lore.contains("피해 1칸 (쿨타임 40초)") && !lore.contains("최대 5명") && !lore.contains("최대 3명") && !lore.contains("서리 균열"), lore);
        plugin.module(kr.wave.core.module.WeaponModule.class).secondTick();
        assertTrue(p.hasPotionEffect(org.bukkit.potion.PotionEffectType.RESISTANCE), "저항 I 유지");
        org.bukkit.entity.LivingEntity[] mobs = new org.bukkit.entity.LivingEntity[6];
        for (int i = 0; i < 6; i++) mobs[i] = (org.bukkit.entity.LivingEntity) w.spawnEntity(new org.bukkit.Location(w, 2 + i * 2, 70, 0), org.bukkit.entity.EntityType.ZOMBIE);
        double[] hp = new double[6];
        for (int i = 0; i < 6; i++) hp[i] = mobs[i].getHealth();
        java.util.function.Consumer<org.bukkit.entity.LivingEntity> hit = t -> server.getPluginManager().callEvent(new org.bukkit.event.entity.EntityDamageByEntityEvent(p, t, org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK, org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).withDirectEntity(p).withCausingEntity(p).build(), 5.0));
        hit.accept(mobs[0]);
        server.getScheduler().performTicks(20);
        int chained = 0;
        for (int i = 1; i < 6; i++) if (mobs[i].getHealth() < hp[i]) chained++;
        assertEquals(0, chained, "평소 타격에는 연쇄 없음");
        // 우클릭: 혹한의 연쇄 -> 확률 없이 최대 5명
        plugin.getConfig().set("weapons.sealed.chain-chance", 0.0);
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerInteractEvent(p, org.bukkit.event.block.Action.RIGHT_CLICK_AIR, p.getInventory().getItemInMainHand(), null, org.bukkit.block.BlockFace.SELF, org.bukkit.inventory.EquipmentSlot.HAND));
        for (int i = 0; i < 6; i++) hp[i] = mobs[i].getHealth();
        hit.accept(mobs[0]);
        server.getScheduler().performTicks(20);
        chained = 0;
        for (int i = 1; i < 6; i++) if (mobs[i].getHealth() < hp[i]) chained++;
        assertEquals(5, chained, "혹한의 연쇄 중 5명");
        assertEquals(hp[1] - 2.0, mobs[1].getHealth(), 1e-9, "피해 1칸");
        // 데미지 1.3배
        plugin.getConfig().set("weapons.sealed.crit-chance", 1.0);
        var crit = new org.bukkit.event.entity.EntityDamageByEntityEvent(p, mobs[0], org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK, org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).withDirectEntity(p).withCausingEntity(p).build(), 10.0);
        server.getPluginManager().callEvent(crit);
        assertEquals(13.0, crit.getDamage(), 1e-9);
        assertTrue(lore.contains("데미지의 1.3배"), lore);
    }

    @Test
    void chaosHelmet() {
        plugin.getConfig().set("weapons.chaos-helmet.wither-chance", 1.0);
        PlayerMock p = server.addPlayer("Doom");
        server.getScheduler().performTicks(5);
        p.getInventory().setHelmet(Items.chaosHelmet());
        assertEquals(Items.chaosHelmet(), Items.template(Msg.name(Items.chaosHelmet())).get(), "접속 갱신 대상");
        assertNotNull(Items.special("chaos_helmet"));
        String lore = String.join("\n", Items.chaosHelmet().getItemMeta().lore().stream().map(Msg::plain).toList());
        assertTrue(lore.contains("방어구가 부서지지 않습니다") && lore.contains("위더 I") && lore.contains("나약함과 구속에 걸리지 않습니다")
                && lore.contains("30% 미만일 때 받는 데미지가 20% 감소") && !lore.contains("어둠"), lore);
        assertTrue(Items.chaosHelmet().getItemMeta().isUnbreakable());
        // 나약함 · 구속 면역
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.WEAKNESS, 100, 0));
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, 100, 1));
        plugin.module(kr.wave.core.module.WeaponModule.class).secondTick();
        assertFalse(p.hasPotionEffect(org.bukkit.potion.PotionEffectType.WEAKNESS));
        assertFalse(p.hasPotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS));
        // 공격자 위더
        var z = p.getWorld().spawn(p.getLocation().add(2, 0, 0), org.bukkit.entity.Zombie.class);
        p.setHealth(20);
        var ev = new org.bukkit.event.entity.EntityDamageByEntityEvent(z, p, org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK, org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.MOB_ATTACK).withDirectEntity(z).withCausingEntity(z).build(), 4);
        server.getPluginManager().callEvent(ev);
        assertTrue(z.hasPotionEffect(org.bukkit.potion.PotionEffectType.WITHER), "공격자 위더");
        assertEquals(4.0, ev.getDamage(), 1e-9, "체력 많을 때는 감소 없음");
        // 체력 30% 미만: 25% 감소
        p.setHealth(5);
        var ev2 = new org.bukkit.event.entity.EntityDamageByEntityEvent(z, p, org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK, org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.MOB_ATTACK).withDirectEntity(z).withCausingEntity(z).build(), 4);
        server.getPluginManager().callEvent(ev2);
        assertEquals(3.2, ev2.getDamage(), 1e-9, "20% 감소");
        // 상자 구성품
        var cm = plugin.module(kr.wave.core.module.CrateModule.class);
        boolean inCrate = false;
        p.performCommand("상자");
        click(p, 13, ClickType.LEFT);
        server.getScheduler().performTicks(2);
        click(p, 14, ClickType.RIGHT);
        server.getScheduler().performTicks(2);
        for (ItemStack it : p.getOpenInventory().getTopInventory().getContents()) if (it != null && Msg.name(it).contains("종말의 투구")) inCrate = true;
        assertTrue(inCrate, "혼돈 상자 구성품");
        GuiSnapshot.save(p, "chaos_preview.json");
    }

    @Test
    void reviewFixes() throws Exception {
        PlayerMock admin = server.addPlayer("GiveAdm");
        admin.setOp(true);
        PlayerMock p = server.addPlayer("Getter");
        server.getScheduler().performTicks(5);
        var wm = plugin.module(kr.wave.core.module.WeaponModule.class);
        // /아이템: 이름 목록 + 지급 + 자동완성
        var ids = wm.getClass().getDeclaredMethod("giveIds");
        ids.setAccessible(true);
        @SuppressWarnings("unchecked") var map = (java.util.Map<String, ?>) ids.invoke(wm);
        GuiSnapshot.text("giveids.txt", String.join("\n", map.keySet()));
        for (String k : new String[]{"혼돈의아포칼립스", "종말의투구", "다크니스웨이브", "잿불낙인검", "초승달의서약", "망령수확자", "프로스트그레이브"}) assertTrue(map.containsKey(k), k + " in " + map.keySet());
        p.getInventory().clear();
        assertTrue(admin.performCommand("아이템 종말의투구 Getter"));
        assertTrue(Msg.name(p.getInventory().getItem(0)).contains("종말의 투구"));
        assertTrue(admin.performCommand("아이템 시간의파편 Getter 10"));
        int shards = 0;
        for (ItemStack it : p.getInventory().getContents()) if (it != null && Msg.name(it).contains("시간의 파편")) shards += it.getAmount();
        assertEquals(10, shards);
        var tab = server.getCommandMap().getCommand("아이템").tabComplete(admin, "아이템", new String[]{"혼돈"});
        assertTrue(tab.contains("혼돈의아포칼립스"), tab.toString());
        assertTrue(server.getCommandMap().getCommand("아이템").tabComplete(p, "아이템", new String[]{""}).isEmpty(), "권한 없으면 자동완성 없음");
        drain(p);
        assertTrue(p.performCommand("아이템 종말의투구"));
        assertTrue(drain(p).contains("권한"));
        // 파괴의 낫 · 천둥의 심판 설명은 원래대로
        String th = String.join("\n", Items.thunderAxe().getItemMeta().lore().stream().map(Msg::plain).toList());
        assertTrue(th.contains("고정 피해 2칸"), th);
        String sc = String.join("\n", Items.ruinerScythe().getItemMeta().lore().stream().map(Msg::plain).toList());
        assertTrue(sc.contains("기본 데미지가 6으로 고정"), sc);
        {   // 실제 기본 데미지도 6
            PlayerMock atk = server.addPlayer("ScytheUser");
            PlayerMock vic = server.addPlayer("ScytheVictim");
            server.getScheduler().performTicks(5);
            atk.teleport(new org.bukkit.Location(server.getWorld("world"), 0, 64, 0));
            vic.teleport(new org.bukkit.Location(server.getWorld("world"), 1, 64, 0));
            atk.getInventory().setItemInMainHand(Items.ruinerScythe());
            var ev = new org.bukkit.event.entity.EntityDamageByEntityEvent(atk, vic, org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK, org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).withDirectEntity(atk).withCausingEntity(atk).build(), 1.0);
            server.getPluginManager().callEvent(ev);
            assertEquals(6.0, ev.getDamage(), 1e-9, "파괴의 낫 기본 데미지 6");
        }
        String nc = String.join("\n", Items.newCharacteristic().getItemMeta().lore().stream().map(Msg::plain).toList());
        assertTrue(nc.contains("[초승달의 각인]") && !nc.contains("새로운 시대"), nc);
        assertTrue(Msg.plainOf(Items.COLLECTION_TITLES[4][1]).contains("초승달의 서약자"));
        assertNotNull(Items.renamedTitle("inscribed"));
        assertNull(Items.renamedTitle("sealed"));
        // 상자 구성품 확률
        p.performCommand("상자");
        click(p, 13, ClickType.LEFT);
        server.getScheduler().performTicks(2);
        click(p, 14, ClickType.RIGHT);
        server.getScheduler().performTicks(2);
        String prev = allText(p.getOpenInventory().getTopInventory());
        assertTrue(prev.contains("확률 : 1.5%") && prev.contains("확률 : 16.5%") && prev.contains("확률 : 13%"), prev);
        GuiSnapshot.save(p, "chaos_preview.json");
        // 팀 창고 갱신
        var g = plugin.store().global();
        p.closeInventory();
        var team = new kr.wave.core.data.GlobalData.Team();
        team.id = "storeteam";
        team.display = "storeteam";
        team.members.put(p.getUniqueId(), "마스터");
        g.teams.put(team.id, team);
        team.storage[0] = It.of(Material.NETHERITE_HELMET).name("<gray>[Chaos] [Lv.✦] 종말의 투구").lore("old").build();
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerJoinEvent(p, net.kyori.adventure.text.Component.empty()));
        server.getScheduler().performTicks(25);
        assertTrue(String.join("\n", team.storage[0].getItemMeta().lore().stream().map(Msg::plain).toList()).contains("받는 데미지"), "팀 창고 갱신");
    }

    @Test
    void skillBlockedInSpawnAndPaidLog() throws Exception {
        PlayerMock p = server.addPlayer("SpawnCaster");
        server.getScheduler().performTicks(5);
        var wm = plugin.module(kr.wave.core.module.WeaponModule.class);
        p.teleport(new org.bukkit.Location(server.getWorld("spawn-world"), 0, 64, 0));
        assertTrue(wm.skillBlocked(p), "스폰 월드");
        p.getInventory().setItemInMainHand(Items.newCharacteristic());
        drain(p);
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerInteractEvent(p, org.bukkit.event.block.Action.RIGHT_CLICK_AIR, p.getInventory().getItemInMainHand(), null, org.bukkit.block.BlockFace.SELF, org.bukkit.inventory.EquipmentSlot.HAND));
        assertFalse(p.hasPotionEffect(org.bukkit.potion.PotionEffectType.STRENGTH), "스폰에서는 스킬 안 나감");
        String msg = drain(p);
        assertTrue(msg.contains("스킬을 사용할 수 없습니다"), msg);
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerInteractEvent(p, org.bukkit.event.block.Action.RIGHT_CLICK_AIR, p.getInventory().getItemInMainHand(), null, org.bukkit.block.BlockFace.SELF, org.bukkit.inventory.EquipmentSlot.HAND));
        assertFalse(drain(p).contains("스킬을 사용할 수 없습니다"), "연타 시 안내는 한 번");
        p.teleport(new org.bukkit.Location(server.getWorld("world"), 0, 64, 0));
        assertFalse(wm.skillBlocked(p), "야생은 가능");
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerInteractEvent(p, org.bukkit.event.block.Action.RIGHT_CLICK_AIR, p.getInventory().getItemInMainHand(), null, org.bukkit.block.BlockFace.SELF, org.bukkit.inventory.EquipmentSlot.HAND));
        assertTrue(p.hasPotionEffect(org.bukkit.potion.PotionEffectType.STRENGTH), "야생에서는 발동");

        // 유료 캐시 기록
        PlayerMock admin = server.addPlayer("LogAdmin");
        admin.setOp(true);
        assertTrue(admin.performCommand("excashgive SpawnCaster 20000"));
        var data = plugin.store().get(p);
        assertEquals(20000, data.exMoney);
        assertTrue(plugin.module(kr.wave.core.module.EconomyModule.class).take(p, 5000, true, "테스트 구매"));
        assertTrue(kr.wave.core.api.WaveAPI.takeCash(p, 1000, true));
        kr.wave.core.util.TxLog.shutdown();   // 기록이 다 써질 때까지 기다림
        java.io.File f = new java.io.File(plugin.getDataFolder(), "logs/paid-cash-" + java.time.LocalDate.now() + ".log");
        assertTrue(f.exists(), f.toString());
        String log = java.nio.file.Files.readString(f.toPath());
        assertTrue(log.contains("관리자 지급") && log.contains("SpawnCaster") && log.contains("+20000") && log.contains("잔액 20000") && log.contains("지급자 LogAdmin"), log);
        assertTrue(log.contains("사용") && log.contains("-5000") && log.contains("잔액 15000") && log.contains("테스트 구매"), log);
        assertTrue(log.contains("-1000") && log.contains("WaveAPI"), log);
        // 상자 오픈 기록
        var cm = plugin.module(kr.wave.core.module.CrateModule.class);
        cm.giveKeys(p, cm.crate("combat"), 1, false);
        p.getInventory().clear();
        p.performCommand("상자");
        click(p, 28, ClickType.LEFT);
        server.getScheduler().performTicks(20 * 15);
        kr.wave.core.util.TxLog.shutdown();
        java.io.File cf = new java.io.File(plugin.getDataFolder(), "logs/crate-" + java.time.LocalDate.now() + ".log");
        String clog = java.nio.file.Files.readString(cf.toPath());
        assertTrue(clog.contains("상자 오픈") && clog.contains("SpawnCaster") && clog.contains("combat x1"), clog);
        GuiSnapshot.text("sample-logs.txt", log + "\n" + clog);
    }

    @Test
    void gambleGuiAndGiveAll() {
        PlayerMock p = server.addPlayer("Gambler");
        PlayerMock q = server.addPlayer("Bystander");
        PlayerMock admin = server.addPlayer("GiveAllAdm");
        admin.setOp(true);
        server.getScheduler().performTicks(5);
        var d = plugin.store().get(p);
        d.money = 100_000;
        assertTrue(p.performCommand("도박"));
        assertEquals("홀짝 도박", title(p));
        var inv = p.getOpenInventory().getTopInventory();
        assertTrue(Msg.name(inv.getItem(13)).contains("1,000원"), Msg.name(inv.getItem(13)));
        click(p, 15, ClickType.LEFT);   // +1만
        assertTrue(Msg.name(p.getOpenInventory().getTopInventory().getItem(13)).contains("11,000원"));
        click(p, 10, ClickType.LEFT);   // -10만 -> 최소
        assertTrue(Msg.name(p.getOpenInventory().getTopInventory().getItem(13)).contains("1,000원"));
        click(p, 14, ClickType.LEFT);   // +1천 -> 2,000
        GuiSnapshot.save(p, "gamble.json");
        click(p, 29, ClickType.LEFT);   // 홀
        assertEquals(98_000, d.money, "베팅금 먼저 차감");
        click(p, 33, ClickType.LEFT);   // 결과 대기 중에는 또 못 검
        assertEquals(98_000, d.money);
        server.getScheduler().performTicks(70);
        assertTrue(d.money == 102_000 || d.money == 98_000, "승리 +2,000 / 패배 -2,000 : " + d.money);
        assertTrue(allText(p.getOpenInventory().getTopInventory()).contains("최근 결과"));
        GuiSnapshot.save(p, "gamble_result.json");
        // 잔액 부족
        d.money = 500;
        drain(p);
        click(p, 33, ClickType.LEFT);
        assertTrue(drain(p).contains("소지금이 부족"));
        assertEquals(500, d.money);

        // 전체 지급: 접속자
        p.closeInventory();
        p.getInventory().clear();
        q.getInventory().clear();
        assertTrue(admin.performCommand("전체지급 시간의파편 3"));
        int a1 = 0, a2 = 0;
        for (ItemStack it : p.getInventory().getContents()) if (it != null && Msg.name(it).contains("시간의 파편")) a1 += it.getAmount();
        for (ItemStack it : q.getInventory().getContents()) if (it != null && Msg.name(it).contains("시간의 파편")) a2 += it.getAmount();
        assertEquals(3, a1);
        assertEquals(3, a2);
        // 손에 든 아이템
        admin.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND, 5));
        assertTrue(admin.performCommand("전체지급 손"));
        assertEquals(5, p.getInventory().all(Material.DIAMOND).values().stream().mapToInt(ItemStack::getAmount).sum());
        // 가득 차면 우편함
        q.getInventory().clear();
        for (int i = 0; i < q.getInventory().getSize(); i++) q.getInventory().setItem(i, new ItemStack(Material.STONE, 64));
        var qd = plugin.store().get(q);
        int mailBefore = qd.mailbox.size();
        assertTrue(admin.performCommand("전체지급 종말의투구"));
        assertEquals(mailBefore + 1, qd.mailbox.size(), "인벤토리 가득 -> 우편함");
        // 우편 전체 지급: 확인 한 번 더
        int pm = d.mailbox.size();
        drain(admin);
        assertTrue(admin.performCommand("전체지급 종말의투구 1 우편"));
        assertTrue(drain(admin).contains("한 번 더"));
        assertEquals(pm, d.mailbox.size(), "확인 전에는 지급 안 됨");
        assertTrue(admin.performCommand("전체지급 종말의투구 1 우편"));
        assertEquals(pm + 1, d.mailbox.size(), "우편함 지급");
        assertTrue(p.performCommand("전체지급 손") && plugin.store().get(p).mailbox.size() == pm + 1, "권한 없음");
    }

    @Test
    void baccarat() throws Exception {
        // 세 번째 카드 규칙 (카드 c: 숫자 = c % 13 + 1)
        var deal = kr.wave.core.module.EconomyModule.class.getDeclaredMethod("baccaratDeal", java.util.function.IntSupplier.class);
        deal.setAccessible(true);
        java.util.function.Function<int[], java.util.List<java.util.List<Integer>>> run = seq -> {
            int[] i = {0};
            try {
                @SuppressWarnings("unchecked") var r = (java.util.List<java.util.List<Integer>>) deal.invoke(null, (java.util.function.IntSupplier) () -> seq[i[0]++]);
                return r;
            } catch (Exception ex) { throw new RuntimeException(ex); }
        };
        var nat = run.apply(new int[]{3, 12, 4, 12});            // P 4+5=9 내추럴, B 0
        assertEquals(2, nat.get(0).size());
        assertEquals(2, nat.get(1).size());
        var b3 = run.apply(new int[]{1, 0, 2, 1, 7});             // P 2+3=5 -> 8 받음, B 1+2=3 + 상대 세 번째가 8 -> 멈춤
        assertEquals(3, b3.get(0).size());
        assertEquals(2, b3.get(1).size(), "뱅커 3점, 플레이어 세 번째 8 -> 안 받음");
        var b5 = run.apply(new int[]{5, 1, 12, 2, 9});            // P 6 멈춤, B 2+3=5 -> 받음
        assertEquals(2, b5.get(0).size());
        assertEquals(3, b5.get(1).size(), "플레이어가 멈추면 뱅커 5 이하 받음");
        var b6 = run.apply(new int[]{0, 5, 3, 12, 6, 9});            // P 1+4=5 -> 7 받음, B 6 + 상대 세 번째 7 -> 받음
        assertEquals(3, b6.get(1).size());

        // GUI 한 판
        PlayerMock p = server.addPlayer("Baccara");
        server.getScheduler().performTicks(5);
        var d = plugin.store().get(p);
        d.money = 100_000;
        assertTrue(p.performCommand("바카라 10000"));
        assertEquals("바카라", title(p));
        assertTrue(Msg.name(p.getOpenInventory().getTopInventory().getItem(31)).contains("10,000원"));
        GuiSnapshot.save(p, "baccarat.json");
        click(p, 38, ClickType.LEFT);   // 플레이어
        assertEquals(90_000, d.money, "베팅금 먼저 차감");
        click(p, 42, ClickType.LEFT);
        assertEquals(90_000, d.money, "진행 중에는 또 못 검");
        server.getScheduler().performTicks(120);
        assertTrue(d.money == 110_000 || d.money == 90_000 || d.money == 100_000, "승 +1만 / 패 -1만 / 타이 반환 : " + d.money);
        var inv = p.getOpenInventory().getTopInventory();
        assertEquals(Material.PAPER, inv.getItem(12).getType(), "카드 공개");
        assertTrue(allText(inv).contains("최근 결과"));
        GuiSnapshot.save(p, "baccarat_result.json");
        // 홀짝 <-> 바카라 이동
        click(p, 53, ClickType.LEFT);
        server.getScheduler().performTicks(2);
        assertEquals("홀짝 도박", title(p));
        click(p, 44, ClickType.LEFT);
        server.getScheduler().performTicks(2);
        assertEquals("바카라", title(p));
        // 뱅커 수수료 / 타이 배당 계산: 여러 판 돌려 돈이 가능한 값으로만 바뀌는지
        for (int k = 0; k < 20; k++) {
            long before = d.money;
            click(p, k % 2 == 0 ? 42 : 40, ClickType.LEFT);
            server.getScheduler().performTicks(120);
            long diff = d.money - before;
            if (k % 2 == 0) assertTrue(diff == 9_500 || diff == -10_000 || diff == 0, "뱅커: " + diff);
            else assertTrue(diff == 80_000 || diff == -10_000, "타이: " + diff);
            if (d.money < 20_000) d.money = 100_000;
        }
    }

    @Test
    void dailyBetLimitSupplyDropQuiz() {
        PlayerMock p = server.addPlayer("Limited");
        PlayerMock admin = server.addPlayer("EventAdm");
        admin.setOp(true);
        server.getScheduler().performTicks(5);
        var d = plugin.store().get(p);
        d.money = 5_000_000;
        d.exMoney = 5_000_000;
        // 하루 한도 100만: 90만 쓴 상태에서 20만 베팅은 거부
        d.betDay = java.time.LocalDate.now().toString();
        d.betToday = 900_000;
        assertTrue(p.performCommand("바카라 200000"));
        drain(p);
        click(p, 38, ClickType.LEFT);
        assertTrue(drain(p).contains("하루 베팅 한도"));
        assertEquals(5_000_000, d.money, "거부되면 차감 없음");
        assertEquals(5_000_000, d.exMoney, "유료 캐시는 건드리지 않음");
        assertTrue(allText(p.getOpenInventory().getTopInventory()).contains("900,000 / 1,000,000"));
        GuiSnapshot.save(p, "baccarat_limit.json");
        // 10만은 가능 -> 누적 100만
        p.performCommand("도박 100000");
        click(p, 29, ClickType.LEFT);
        assertEquals(4_900_000, d.money);
        assertEquals(1_000_000, d.betToday);
        server.getScheduler().performTicks(70);
        // 날짜가 바뀌면 초기화
        d.betDay = "2000-01-01";
        p.performCommand("도박 1000");
        click(p, 29, ClickType.LEFT);
        assertEquals(1000, d.betToday, "다음 날은 0부터");
        server.getScheduler().performTicks(70);
        assertEquals(5_000_000, d.exMoney, "도박은 유료 캐시를 안 씀");

        // 보급 상자
        var em = plugin.module(kr.wave.core.module.EventModule.class);
        drain(p);
        assertTrue(admin.performCommand("보급 지금"));
        var b = em.dropBlock();
        assertNotNull(b, "보급 상자 생성");
        assertEquals(Material.CHEST, b.getType());
        assertTrue(drain(p).contains("보급 상자가 떨어졌습니다"));
        var brk = new org.bukkit.event.block.BlockBreakEvent(b, p);
        server.getPluginManager().callEvent(brk);
        assertTrue(brk.isCancelled(), "부술 수 없음");
        var open = new org.bukkit.event.player.PlayerInteractEvent(p, org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK, null, b, org.bukkit.block.BlockFace.UP);
        server.getPluginManager().callEvent(open);
        assertTrue(open.useInteractedBlock() == org.bukkit.event.Event.Result.DENY || open.isCancelled(), "진짜 상자 창은 안 열림");
        assertTrue(drain(admin).contains("Limited님이 보급 상자를 열었습니다"));
        assertEquals("보급 상자", title(p));
        var sup = p.getOpenInventory().getTopInventory();
        assertEquals(36, sup.getSize(), "4줄");
        for (int sl : new int[]{0, 8, 9, 17, 18, 26, 27, 35}) assertEquals(Material.ORANGE_STAINED_GLASS_PANE, sup.getItem(sl).getType(), "테두리 " + sl);
        int filled = 0;
        boolean gap = false;
        for (int sl : new int[]{10, 11, 12, 13, 14, 15, 16, 19}) {
            if (sup.getItem(sl) != null) {
                assertFalse(gap, "빈칸 없이 순서대로");
                filled++;
            } else gap = true;
        }
        assertTrue(filled >= 1 && filled <= 6, "rolls 6 (같은 아이템은 합침): " + filled);
        GuiSnapshot.save(p, "supply.json");
        p.getInventory().clear();
        click(p, 10, ClickType.LEFT);
        assertNull(sup.getItem(10), "클릭하면 가져감");
        assertTrue(java.util.Arrays.stream(p.getInventory().getContents()).anyMatch(java.util.Objects::nonNull));
        click(p, 0, ClickType.LEFT);
        assertEquals(Material.ORANGE_STAINED_GLASS_PANE, sup.getItem(0).getType(), "테두리는 못 가져감");
        for (int sl : new int[]{11, 12, 13, 14, 15, 16}) click(p, sl, ClickType.LEFT);
        server.getScheduler().performTicks(2);
        assertNull(em.dropBlock(), "다 가져가면 끝");
        assertEquals(Material.AIR, b.getType());
        assertTrue(drain(admin).contains("모두 가져갔습니다"));
        assertTrue(admin.performCommand("보급 지금"));
        b = em.dropBlock();
        assertTrue(admin.performCommand("보급 취소"));
        assertNull(em.dropBlock());
        assertEquals(Material.AIR, b.getType(), "회수되면 상자 사라짐");

        // 채팅 퀴즈
        assertTrue(admin.performCommand("퀴즈 지금"));
        String ans = em.quizAnswer();
        assertNotNull(ans);
        long before = d.money;
        var msg = net.kyori.adventure.text.Component.text(ans);
        server.getPluginManager().callEvent(new io.papermc.paper.event.player.AsyncChatEvent(false, p, new java.util.HashSet<>(),
                io.papermc.paper.chat.ChatRenderer.defaultRenderer(), msg, msg, null));
        server.getScheduler().performTicks(3);
        assertNull(em.quizAnswer(), "맞히면 끝");
        assertTrue(d.money - before >= 1000 && d.money - before <= 5000, "보상 " + (d.money - before));
        assertTrue(drain(admin).contains("Limited님 정답"));
    }

    @Test
    void preloadedButNeverJoinedIsDropped() {
        var id = java.util.UUID.randomUUID();
        preLogin(new org.bukkit.event.player.AsyncPlayerPreLoginEvent("Ghost", java.net.InetAddress.getLoopbackAddress(), id));
        assertEquals(1, plugin.store().preloadedCount());
        server.getScheduler().performTicks(20 * 95);
        boolean cached = false;
        for (PlayerData x : plugin.store().cached()) if (x.uuid.equals(id)) cached = true;
        assertFalse(cached, "접속 안 하면 캐시에서 내림");
        assertEquals(0, plugin.store().preloadedCount());
    }

    @Test
    void ruinerPantsRework() {
        plugin.getConfig().set("weapons.ruiner-pants.deny-death-chance", 1.0);
        PlayerMock p = server.addPlayer("Ruined");
        server.getScheduler().performTicks(5);
        p.getInventory().setLeggings(Items.ruinerPants());
        String lore = String.join("\n", Items.ruinerPants().getItemMeta().lore().stream().map(Msg::plain).toList());
        assertTrue(lore.contains("낙하 피해와 화염 피해가 50% 감소") && !lore.contains("넉백") && lore.contains("힘 II") && lore.contains("죽음을 거부") && !lore.contains("저항 I이") && !lore.contains("드롭되지 않습니다"), lore);
        var wm = plugin.module(kr.wave.core.module.WeaponModule.class);
        wm.secondTick();
        var kb = org.bukkit.Registry.ATTRIBUTE.get(org.bukkit.NamespacedKey.minecraft("knockback_resistance"));
        for (var cause : new org.bukkit.event.entity.EntityDamageEvent.DamageCause[]{org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL, org.bukkit.event.entity.EntityDamageEvent.DamageCause.LAVA, org.bukkit.event.entity.EntityDamageEvent.DamageCause.FIRE_TICK}) {
            var ev = new org.bukkit.event.entity.EntityDamageEvent(p, cause, org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(), 4);
            server.getPluginManager().callEvent(ev);
            assertEquals(2.0, ev.getDamage(), 1e-9, cause + " 50% 감소");
        }
        var melee = new org.bukkit.event.entity.EntityDamageEvent(p, org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK, org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(), 4);
        server.getPluginManager().callEvent(melee);
        assertEquals(4.0, melee.getDamage(), 1e-9, "다른 피해는 그대로");
        assertFalse(p.hasPotionEffect(org.bukkit.potion.PotionEffectType.STRENGTH), "체력 많을 때는 없음");
        p.setHealth(4);
        wm.secondTick();
        assertTrue(p.hasPotionEffect(org.bukkit.potion.PotionEffectType.STRENGTH) && p.getPotionEffect(org.bukkit.potion.PotionEffectType.STRENGTH).getAmplifier() == 1, "힘 II");
        assertFalse(p.hasPotionEffect(org.bukkit.potion.PotionEffectType.SPEED), "신속 없음");
        // 치명상 -> 죽음 거부, 체력 절반
        var lethal = new org.bukkit.event.entity.EntityDamageEvent(p, org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK, org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(), 100);
        server.getPluginManager().callEvent(lethal);
        assertTrue(lethal.isCancelled());
        assertEquals(WeaponModuleAccess.max(p) / 2, p.getHealth(), 1e-9);
        p.getInventory().setLeggings(null);
        wm.secondTick();
    }

    static final class WeaponModuleAccess {
        static double max(PlayerMock p) { return kr.wave.core.module.WeaponModule.maxHealth(p); }
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
