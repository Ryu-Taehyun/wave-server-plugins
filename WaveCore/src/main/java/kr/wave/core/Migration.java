package kr.wave.core;

import kr.wave.core.data.GlobalData;
import kr.wave.core.data.PlayerData;
import kr.wave.core.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Skript 변수(variables.csv)를 WaveCore 데이터로 옮긴다.
 * Skript 플러그인이 켜져 있어야 하며, Skript 가 직접 변수를 읽어 주므로 아이템도 그대로 옮겨진다.
 */
public class Migration {

    private final WaveCore plugin;
    private Method getVariable;
    private final Map<String, UUID> nameToUuid = new HashMap<>();
    private final Map<UUID, PlayerData> touched = new HashMap<>();
    private int values;

    public Migration(WaveCore plugin) {
        this.plugin = plugin;
    }

    public void run(CommandSender s) {
        if (!Bukkit.getPluginManager().isPluginEnabled("Skript")) {
            Msg.send(s, "&cSkript 플러그인이 켜져 있지 않습니다.");
            Msg.send(s, "&7Skript 를 그대로 둔 채, scripts 폴더의 .sk 파일 이름 앞에 '-' 를 붙여 비활성화한 뒤 서버를 켜고 다시 실행하세요.");
            return;
        }
        try {
            Class<?> vars = Class.forName("ch.njol.skript.variables.Variables", true, getClass().getClassLoader());
            getVariable = vars.getMethod("getVariable", String.class, Event.class, boolean.class);
        } catch (ReflectiveOperationException | LinkageError e) {
            Msg.send(s, "&cSkript 변수 API 를 찾지 못했습니다: " + e);
            return;
        }
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.getName() != null) nameToUuid.put(op.getName().toLowerCase(), op.getUniqueId());
        }

        // 숫자
        number("money", (d, n) -> d.money = n);
        number("moneytotal", (d, n) -> d.moneyTotal = n);
        number("exmoney", (d, n) -> d.exMoney = n);
        number("exmoneytotal", (d, n) -> d.exMoneyTotal = n);
        number("level", (d, n) -> d.level = (int) Math.max(1, Math.min(n, 18)));
        number("exp", (d, n) -> d.exp = n);
        number("nickcolor", (d, n) -> d.nickColor = (int) n);
        number("killeffect", (d, n) -> d.killEffect = (int) n);

        // 참/거짓
        flag("havepaperred", d -> d.nickPapers.add("red"));
        flag("havepapergold", d -> d.nickPapers.add("gold"));
        flag("havepaperpurple", d -> d.nickPapers.add("purple"));
        flag("havepapermagenta", d -> d.nickPapers.add("magenta"));
        String[][] titles = {{"titleace", "ace"}, {"titlesupreme", "supreme"}, {"titleexceed", "exceed"}, {"titlepeerless", "peerless"},
                {"titlesealed", "sealed"}, {"titledestory", "destroy"}, {"titlenether", "nether"}, {"title7days", "7days"}};
        for (String[] t : titles) flag(t[0], d -> d.titles.add(t[1]));
        String[][] ranks = {{"isace", "ace"}, {"issupreme", "supreme"}, {"isexceed", "exceed"}, {"ispeerless", "peerless"}};
        for (String[] r : ranks) flag(r[0], d -> d.ranks.add(r[1]));
        flag("crownkillmsg", d -> {
            d.crownKill = true;
            d.crownOwned = true;
        });
        flag("my_variable", d -> d.newbiePassUntil = System.currentTimeMillis() + 3L * 60 * 60 * 1000);
        String[] announce = {"never_broadcast", "never_broadcast1", "never_broadcast2", "never_broadcast3"};
        for (int i = 0; i < announce.length; i++) {
            int idx = i;
            flag(announce[i], d -> d.collectionAnnounced[idx] = true);
        }

        // 뉴비 보호 시작 시각
        for (Map.Entry<String, Object> e : list("newbie_start").entrySet()) {
            PlayerData d = player(e.getKey());
            Long ts = timestamp(e.getValue());
            if (d != null && ts != null) {
                d.newbieStart = ts;
                values++;
            }
        }

        // 아이템
        for (Map.Entry<String, Object> e : list("mailbox").entrySet()) {
            PlayerData d = player(e.getKey());
            if (d == null) continue;
            for (Object o : children(e.getValue())) {
                ItemStack it = item(o);
                if (it != null) {
                    d.mailbox.add(it);
                    values++;
                }
            }
        }
        for (Map.Entry<String, Object> e : list("backpack").entrySet()) {
            PlayerData d = player(e.getKey());
            if (d == null || !(e.getValue() instanceof Map<?, ?> m)) continue;
            for (Map.Entry<?, ?> se : m.entrySet()) {
                if (se.getKey() == null) continue;
                try {
                    int slot = Integer.parseInt(se.getKey().toString());
                    ItemStack it = item(se.getValue());
                    if (slot >= 0 && slot < 18 && it != null) {
                        d.backpack[slot] = it;
                        values++;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        for (Map.Entry<String, Object> e : list("saved_item").entrySet()) {
            PlayerData d = player(e.getKey());
            ItemStack it = item(e.getValue());
            if (d != null && it != null) {
                d.savedItem = it;
                values++;
            }
        }
        for (String pending : new String[]{"save.newbie", "save.items", "saves.items", "save.items4"}) {
            for (Map.Entry<String, Object> e : list(pending).entrySet()) {
                PlayerData d = player(e.getKey());
                if (d == null) continue;
                for (Object o : children(e.getValue())) {
                    ItemStack it = item(o);
                    if (it != null) {
                        d.respawnItems.add(it);
                        values++;
                    }
                }
            }
        }

        GlobalData g = plugin.store().global();

        // 팀
        Map<String, Object> teamRanks = list("team::rank");
        for (Map.Entry<String, Object> e : list("team::player").entrySet()) {
            UUID u = uuid(e.getKey());
            if (u == null || e.getValue() == null) continue;
            String display = e.getValue().toString();
            String id = Msg.plainOf(display);
            GlobalData.Team t = g.teams.computeIfAbsent(id, k -> {
                GlobalData.Team nt = new GlobalData.Team();
                nt.id = k;
                nt.display = display;
                return nt;
            });
            Object rank = teamRanks.get(e.getKey());
            t.members.put(u, rank == null ? "팀원" : rank.toString());
            values++;
        }
        for (Map.Entry<String, Object> e : list("team::storage").entrySet()) {
            GlobalData.Team t = g.teams.get(Msg.plainOf(e.getKey()));
            if (t == null) {
                for (GlobalData.Team x : g.teams.values()) if (x.display.equalsIgnoreCase(e.getKey())) t = x;
            }
            if (t == null || !(e.getValue() instanceof Map<?, ?> m)) continue;
            for (Map.Entry<?, ?> se : m.entrySet()) {
                if (se.getKey() == null) continue;
                try {
                    int slot = Integer.parseInt(se.getKey().toString());
                    ItemStack it = item(se.getValue());
                    if (slot >= 0 && slot < 54 && it != null) {
                        t.storage[slot] = it;
                        values++;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        // 밴
        Map<String, Object> reasons = list("ban::reason");
        Map<String, Object> codes = list("ban::code");
        for (Map.Entry<String, Object> e : list("ban::isbanned").entrySet()) {
            UUID u = uuid(e.getKey());
            if (u == null || !Boolean.TRUE.equals(e.getValue())) continue;
            GlobalData.Ban b = new GlobalData.Ban();
            b.reason = reasons.get(e.getKey()) == null ? "사유 미작성" : reasons.get(e.getKey()).toString();
            b.code = codes.get(e.getKey()) == null ? null : codes.get(e.getKey()).toString();
            g.bans.put(u, b);
            values++;
        }

        // 장터
        Map<String, Object> prices = list("market.price");
        Map<String, Object> sellers = list("market.seller");
        for (Map.Entry<String, Object> e : list("market.item").entrySet()) {
            ItemStack it = item(e.getValue());
            Object seller = sellers.get(e.getKey());
            Object price = prices.get(e.getKey());
            if (it == null || !(seller instanceof OfflinePlayer op) || !(price instanceof Number n)) continue;
            stripMarketLore(it);
            GlobalData.Listing li = new GlobalData.Listing();
            li.id = g.nextListingId++;
            li.seller = op.getUniqueId();
            li.sellerName = op.getName() == null ? "?" : op.getName();
            li.price = n.longValue();
            li.item = it;
            g.market.add(li);
            values++;
        }

        // Soul Splitter 킬 수
        for (Map.Entry<String, Object> e : list("sword_kills").entrySet()) {
            if (!(e.getValue() instanceof Number n)) continue;
            try {
                g.swordKills.put(Integer.parseInt(e.getKey()), n.intValue());
                values++;
            } catch (NumberFormatException ignored) {
            }
        }

        // 저장 상자, 점검 모드
        String[][] boxes = {{"saved_box_item", "box"}, {"saved_box_item1", "box1"}, {"saved_box_item_final", "final"}, {"saved_box_item_final1", "final1"}};
        for (String[] b : boxes) {
            ItemStack it = item(v(b[0]));
            if (it != null) {
                g.saved.put(b[1], it);
                values++;
            }
        }
        if (Boolean.TRUE.equals(v("server::maintenance"))) g.maintenance = true;

        for (PlayerData d : touched.values()) plugin.store().saveAuto(d);
        plugin.store().saveGlobal();
        Msg.send(s, "&a[WaveCore] Skript 데이터 가져오기 완료: 플레이어 " + touched.size() + "명, 값 " + values + "개");
        Msg.send(s, "&7확인 후 Skript 플러그인과 .sk 파일을 제거하세요. (다시 실행하면 우편함/장터 등이 중복으로 추가됩니다)");
    }

    /* ---------------- 도우미 ---------------- */

    private Object v(String name) {
        try {
            return getVariable.invoke(null, name.toLowerCase(), null, false);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> list(String prefix) {
        Object o = v(prefix + "::*");
        if (!(o instanceof Map<?, ?> m)) return Collections.emptyMap();
        Map<String, Object> out = new HashMap<>();
        for (Map.Entry<?, ?> e : ((Map<Object, Object>) m).entrySet()) {
            if (e.getKey() == null) continue;
            Object val = e.getValue();
            // 하위 목록이 있는 항목은 자기 값이 null 키에 들어 있음
            if (val instanceof Map<?, ?> sub && sub.containsKey(null) && sub.size() == 1) val = sub.get(null);
            out.put(e.getKey().toString(), val);
        }
        return out;
    }

    private static List<Object> children(Object o) {
        List<Object> out = new ArrayList<>();
        if (o instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) if (e.getKey() != null) out.add(e.getValue());
        } else if (o != null) {
            out.add(o);
        }
        return out;
    }

    private UUID uuid(String key) {
        try {
            return UUID.fromString(key);
        } catch (IllegalArgumentException e) {
            return nameToUuid.get(key.toLowerCase());
        }
    }

    private PlayerData player(String key) {
        UUID u = uuid(key);
        if (u == null) return null;
        return touched.computeIfAbsent(u, id -> plugin.store().get(id));
    }

    private interface NumSetter {
        void set(PlayerData d, long n);
    }

    private interface FlagSetter {
        void set(PlayerData d);
    }

    private void number(String prefix, NumSetter setter) {
        for (Map.Entry<String, Object> e : list(prefix).entrySet()) {
            if (!(e.getValue() instanceof Number n)) continue;
            PlayerData d = player(e.getKey());
            if (d == null) continue;
            setter.set(d, n.longValue());
            values++;
        }
    }

    private void flag(String prefix, FlagSetter setter) {
        for (Map.Entry<String, Object> e : list(prefix).entrySet()) {
            if (!Boolean.TRUE.equals(e.getValue())) continue;
            PlayerData d = player(e.getKey());
            if (d == null) continue;
            setter.set(d);
            values++;
        }
    }

    private static Long timestamp(Object o) {
        if (o == null) return null;
        try {
            Object ts = o.getClass().getMethod("getTimestamp").invoke(o);
            return ts instanceof Number n ? n.longValue() : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /** Skript 가 저장한 아이템(ItemStack 또는 ItemType)을 ItemStack 으로 */
    private static ItemStack item(Object o) {
        if (o instanceof ItemStack it) return it.getType().isAir() ? null : it.clone();
        if (o == null) return null;
        try {
            Method m = o.getClass().getMethod("getRandom");
            Object r = m.invoke(o);
            if (r instanceof ItemStack it && !it.getType().isAir()) return it;
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Method m = o.getClass().getMethod("getItem");
            Object r = m.invoke(o);
            if (r instanceof ItemStack it && !it.getType().isAir()) return it.clone();
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    /** 장터 등록 시 붙었던 "가격/판매자" 설명 줄 제거 */
    private static void stripMarketLore(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        if (meta == null || meta.lore() == null) return;
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>(meta.lore());
        lore.removeIf(c -> {
            String p = Msg.plain(c).trim();
            return p.startsWith("가격:") || p.startsWith("판매자:");
        });
        while (!lore.isEmpty() && Msg.plain(lore.get(lore.size() - 1)).isBlank()) lore.remove(lore.size() - 1);
        meta.lore(lore.isEmpty() ? null : lore);
        it.setItemMeta(meta);
    }
}
