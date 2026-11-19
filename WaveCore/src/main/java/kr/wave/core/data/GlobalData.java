package kr.wave.core.data;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 서버 전체 데이터: 점검 모드, 저장 상자, 워프, 팀, 장터, 킬 카운터, 차단 목록 */
public class GlobalData {

    public static class Team {
        public String id;
        /** 표시 이름 (색상 변경권 적용 시 &c 등이 붙음) */
        public String display;
        public final Map<UUID, String> members = new LinkedHashMap<>(); // uuid -> "마스터"/"팀원"
        public ItemStack[] storage = new ItemStack[54];
    }

    public static class Listing {
        public int id;
        public UUID seller;
        public String sellerName;
        public long price;
        public ItemStack item;
    }

    public static class Ban {
        public String reason;
        public String code;
    }

    public boolean maintenance;
    /** /저장상자1~4 : box, box1, final, final1 */
    public final Map<String, ItemStack> saved = new HashMap<>();
    public final Map<String, Location> warps = new HashMap<>();
    public final Map<String, Team> teams = new LinkedHashMap<>();
    public final List<Listing> market = new ArrayList<>();
    public int nextListingId = 1;
    /** Soul Splitter 고유 번호 -> 킬 수 */
    public final Map<Integer, Integer> swordKills = new HashMap<>();
    public final Map<UUID, Ban> bans = new ConcurrentHashMap<>();

    public Team teamOf(UUID uuid) {
        for (Team t : teams.values()) if (t.members.containsKey(uuid)) return t;
        return null;
    }

    public void load(YamlConfiguration y) {
        maintenance = y.getBoolean("maintenance");
        ConfigurationSection s = y.getConfigurationSection("saved");
        if (s != null) for (String k : s.getKeys(false)) saved.put(k, s.getItemStack(k));
        s = y.getConfigurationSection("warps");
        if (s != null) for (String k : s.getKeys(false)) {
            Location l = s.getLocation(k);
            if (l != null) warps.put(k, l);
        }
        s = y.getConfigurationSection("teams");
        if (s != null) for (String id : s.getKeys(false)) {
            ConfigurationSection ts = s.getConfigurationSection(id);
            if (ts == null) continue;
            Team t = new Team();
            t.id = id;
            t.display = ts.getString("display", id);
            ConfigurationSection ms = ts.getConfigurationSection("members");
            if (ms != null) for (String u : ms.getKeys(false)) {
                try {
                    t.members.put(UUID.fromString(u), ms.getString(u, "팀원"));
                } catch (IllegalArgumentException e) {
                    kr.wave.core.util.Log.warn("global.yml: 팀 '" + id + "' 의 팀원 UUID 가 올바르지 않아 건너뜁니다: " + u);
                }
            }
            ConfigurationSection st = ts.getConfigurationSection("storage");
            if (st != null) for (String k : st.getKeys(false)) {
                try {
                    int i = Integer.parseInt(k);
                    if (i >= 0 && i < 54) t.storage[i] = st.getItemStack(k);
                } catch (NumberFormatException e) {
                    kr.wave.core.util.Log.warn("global.yml: 팀 '" + id + "' 창고의 칸 번호가 올바르지 않아 건너뜁니다: " + k);
                }
            }
            teams.put(id, t);
        }
        nextListingId = y.getInt("market-next-id", 1);
        List<?> ml = y.getList("market");
        if (ml != null) for (Object o : ml) {
            if (!(o instanceof Map<?, ?> m)) continue;
            Listing l = new Listing();
            l.id = ((Number) m.get("id")).intValue();
            l.seller = UUID.fromString(String.valueOf(m.get("seller")));
            l.sellerName = String.valueOf(m.get("seller-name"));
            l.price = ((Number) m.get("price")).longValue();
            l.item = (ItemStack) m.get("item");
            if (l.item != null) market.add(l);
        }
        s = y.getConfigurationSection("sword-kills");
        if (s != null) for (String k : s.getKeys(false)) {
            try {
                swordKills.put(Integer.parseInt(k), s.getInt(k));
            } catch (NumberFormatException e) {
                kr.wave.core.util.Log.warn("global.yml: sword-kills 의 번호가 올바르지 않아 건너뜁니다: " + k);
            }
        }
        s = y.getConfigurationSection("bans");
        if (s != null) for (String u : s.getKeys(false)) {
            Ban b = new Ban();
            b.reason = s.getString(u + ".reason", "사유 미작성");
            b.code = s.getString(u + ".code");
            try {
                bans.put(UUID.fromString(u), b);
            } catch (IllegalArgumentException e) {
                kr.wave.core.util.Log.warn("global.yml: 차단 목록의 UUID 가 올바르지 않아 건너뜁니다: " + u);
            }
        }
    }

    public String toYaml() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("maintenance", maintenance);
        saved.forEach((k, v) -> y.set("saved." + k, v));
        warps.forEach((k, v) -> y.set("warps." + k, v));
        for (Team t : teams.values()) {
            String b = "teams." + t.id;
            y.set(b + ".display", t.display);
            t.members.forEach((u, r) -> y.set(b + ".members." + u, r));
            for (int i = 0; i < t.storage.length; i++) if (t.storage[i] != null) y.set(b + ".storage." + i, t.storage[i]);
        }
        y.set("market-next-id", nextListingId);
        List<Map<String, Object>> ml = new ArrayList<>();
        for (Listing l : market) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.id);
            m.put("seller", l.seller.toString());
            m.put("seller-name", l.sellerName);
            m.put("price", l.price);
            m.put("item", l.item);
            ml.add(m);
        }
        y.set("market", ml);
        swordKills.forEach((k, v) -> y.set("sword-kills." + k, v));
        bans.forEach((u, b) -> {
            y.set("bans." + u + ".reason", b.reason);
            y.set("bans." + u + ".code", b.code);
        });
        return y.saveToString();
    }

}
