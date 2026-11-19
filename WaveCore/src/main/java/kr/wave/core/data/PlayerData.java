package kr.wave.core.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 플레이어 한 명의 데이터 (Skript 변수 {money::uuid}, {level::...} 등을 모은 것) */
public class PlayerData {

    public final UUID uuid;
    public String name = "";

    /* 캐시 */
    public long money;
    public long moneyTotal;
    public long exMoney;
    public long exMoneyTotal;
    /** 도박 하루 베팅 누적 (betDay 날짜 기준, 날짜가 바뀌면 0부터) */
    public String betDay = "";
    public long betToday;

    /* 레벨 */
    public int level = 1;
    public long exp;

    /* 치장 */
    /** 0 흰색, 1 빨강, 2 금색, 3 보라, 4 마젠타 */
    public int nickColor;
    /** 보유한 닉네임 색상 변경권: red, gold, purple, magenta */
    public final Set<String> nickPapers = new HashSet<>();
    /** 보유 칭호: ace, supreme, exceed, peerless, sealed, destroy, nether, 7days */
    public final Set<String> titles = new HashSet<>();
    /** 지금 적용 중인 칭호 (titles 의 키, 없으면 null) */
    public String activeTitle;
    /** 상자 가상 키 (상자 이름 -> 개수) */
    public final java.util.Map<String, Integer> crateKeys = new java.util.HashMap<>();
    /** 달성 랭크: ace, supreme, exceed, peerless */
    public final Set<String> ranks = new HashSet<>();
    /** 킬 특수 효과 0 없음, 1 번개, 2 폭죽, 3 폭발 */
    public int killEffect;
    /** 크라운 처치 알림 표식: 보유 여부 / 켜짐 여부 */
    public boolean crownOwned;
    public boolean crownKill;

    /* 보관 */
    public final List<ItemStack> mailbox = new ArrayList<>();
    /** 시즌이 지나도 남는 아이템의 사본 (접속 종료 · 5분마다 갱신). 월드 플레이어 데이터가 사라졌을 때 되돌려 줌 */
    public final List<ItemStack> keepItems = new ArrayList<>();
    /** 시즌 초기화로 인벤토리를 비워야 하는데 그때 접속하지 않았던 경우 (다음 접속 때 처리). 비어 있으면 없음 */
    public String pendingWipe = "";
    /** 다음 접속 때 배낭으로 넣어 줄 시즌 유지 아이템 (시즌 초기화 · 맵 초기화) */
    public final List<ItemStack> seasonReturn = new ArrayList<>();
    /** 배낭 (최대 3줄 = 27칸 저장, 보이는 줄 수는 backpackRows) */
    public ItemStack[] backpack = new ItemStack[27];
    public int backpackRows = 2;
    /** 오버 로드: 수령한 별 단계 */
    public final Set<Integer> overlordClaimed = new HashSet<>();
    /** 컬렉션: 한 번이라도 가져 본 아이템 키 / 보상을 받은 컬렉션 키 */
    public final Set<String> collected = new HashSet<>();
    public final Set<String> collectionClaimed = new HashSet<>();
    /** 튜토리얼 */
    public boolean tutorialStarted, tutorialFinished;
    public final Set<String> tutorialSteps = new HashSet<>();
    /** 구매 제한 상품 구매 횟수 (상품 키 -> 횟수) */
    public final java.util.Map<String, Integer> purchases = new java.util.HashMap<>();
    public ItemStack savedItem;
    /** 리스폰 시 돌려줄 아이템 (뉴비 보호, 드롭 방지 아이템) */
    public final List<ItemStack> respawnItems = new ArrayList<>();

    /* 뉴비 */
    public long newbiePassUntil;
    public long newbieStart;
    /** 마지막 퇴장 시각 (환영 메시지용) */
    public long lastSeen;

    /** 컬렉션 획득 공지 여부 (클레이모어, 사신의 영혼, 헬멧, 사신의 심장) */
    public final boolean[] collectionAnnounced = new boolean[4];

    /** global, local, team */
    public String channel = "global";

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public boolean newbiePassActive() {
        return newbiePassUntil > System.currentTimeMillis();
    }

    public void load(YamlConfiguration y) {
        name = y.getString("name", "");
        money = y.getLong("money");
        moneyTotal = y.getLong("money-total");
        exMoney = y.getLong("exmoney");
        exMoneyTotal = y.getLong("exmoney-total");
        betDay = y.getString("bet-day", "");
        betToday = y.getLong("bet-today");
        level = Math.max(1, y.getInt("level", 1));
        exp = y.getLong("exp");
        nickColor = y.getInt("nick-color");
        nickPapers.addAll(y.getStringList("nick-papers"));
        titles.addAll(y.getStringList("titles"));
        activeTitle = y.getString("active-title");
        ConfigurationSection ck = y.getConfigurationSection("crate-keys");
        if (ck != null) for (String k : ck.getKeys(false)) crateKeys.put(k, ck.getInt(k));
        ranks.addAll(y.getStringList("ranks"));
        killEffect = y.getInt("kill-effect");
        crownKill = y.getBoolean("crown-kill");
        crownOwned = y.getBoolean("crown-owned", crownKill);
        readItems(y.getList("mailbox"), mailbox);
        readItems(y.getList("keep-items"), keepItems);
        pendingWipe = y.getString("pending-wipe", "");
        readItems(y.getList("season-return"), seasonReturn);
        backpackRows = Math.max(2, Math.min(3, y.getInt("backpack-rows", 2)));
        for (Object o : y.getList("overlord-claimed", new ArrayList<>())) if (o instanceof Number n) overlordClaimed.add(n.intValue());
        collected.addAll(y.getStringList("collected"));
        collectionClaimed.addAll(y.getStringList("collection-claimed"));
        tutorialStarted = y.getBoolean("tutorial.started");
        tutorialFinished = y.getBoolean("tutorial.finished");
        tutorialSteps.addAll(y.getStringList("tutorial.steps"));
        ConfigurationSection pu = y.getConfigurationSection("purchases");
        if (pu != null) for (String k : pu.getKeys(false)) purchases.put(k, pu.getInt(k));
        ConfigurationSection bp = y.getConfigurationSection("backpack");
        if (bp != null) {
            for (String k : bp.getKeys(false)) {
                try {
                    int i = Integer.parseInt(k);
                    if (i >= 0 && i < backpack.length) backpack[i] = bp.getItemStack(k);
                } catch (NumberFormatException e) {
                    kr.wave.core.util.Log.warn("플레이어 " + name + " 데이터: 배낭 칸 번호가 올바르지 않아 건너뜁니다: " + k);
                }
            }
        }
        savedItem = y.getItemStack("saved-item");
        readItems(y.getList("respawn-items"), respawnItems);
        newbiePassUntil = y.getLong("newbie-pass-until");
        newbieStart = y.getLong("newbie-start");
        lastSeen = y.getLong("last-seen");
        List<Boolean> ann = y.getBooleanList("collection-announced");
        for (int i = 0; i < Math.min(ann.size(), collectionAnnounced.length); i++) collectionAnnounced[i] = ann.get(i);
        channel = y.getString("channel", "global");
    }

    public String toYaml() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("name", name);
        y.set("money", money);
        y.set("money-total", moneyTotal);
        y.set("exmoney", exMoney);
        y.set("exmoney-total", exMoneyTotal);
        y.set("bet-day", betDay);
        y.set("bet-today", betToday);
        y.set("level", level);
        y.set("exp", exp);
        y.set("nick-color", nickColor);
        y.set("nick-papers", new ArrayList<>(nickPapers));
        y.set("titles", new ArrayList<>(titles));
        y.set("active-title", activeTitle);
        for (java.util.Map.Entry<String, Integer> en : crateKeys.entrySet()) if (en.getValue() > 0) y.set("crate-keys." + en.getKey(), en.getValue());
        y.set("ranks", new ArrayList<>(ranks));
        y.set("kill-effect", killEffect);
        y.set("crown-kill", crownKill);
        y.set("crown-owned", crownOwned);
        y.set("mailbox", new ArrayList<>(mailbox));
        y.set("keep-items", new ArrayList<>(keepItems));
        y.set("pending-wipe", pendingWipe);
        y.set("season-return", new ArrayList<>(seasonReturn));
        for (int i = 0; i < backpack.length; i++) if (backpack[i] != null) y.set("backpack." + i, backpack[i]);
        y.set("backpack-rows", backpackRows);
        y.set("overlord-claimed", new ArrayList<>(overlordClaimed));
        y.set("collected", new ArrayList<>(collected));
        y.set("collection-claimed", new ArrayList<>(collectionClaimed));
        y.set("tutorial.started", tutorialStarted);
        y.set("tutorial.finished", tutorialFinished);
        y.set("tutorial.steps", new ArrayList<>(tutorialSteps));
        for (java.util.Map.Entry<String, Integer> en : purchases.entrySet()) y.set("purchases." + en.getKey(), en.getValue());
        y.set("saved-item", savedItem);
        y.set("respawn-items", new ArrayList<>(respawnItems));
        y.set("newbie-pass-until", newbiePassUntil);
        y.set("newbie-start", newbieStart);
        y.set("last-seen", lastSeen);
        List<Boolean> ann = new ArrayList<>();
        for (boolean b : collectionAnnounced) ann.add(b);
        y.set("collection-announced", ann);
        y.set("channel", channel);
        return y.saveToString();
    }

    private static void readItems(List<?> list, List<ItemStack> into) {
        if (list == null) return;
        for (Object o : list) if (o instanceof ItemStack it) into.add(it);
    }
}
