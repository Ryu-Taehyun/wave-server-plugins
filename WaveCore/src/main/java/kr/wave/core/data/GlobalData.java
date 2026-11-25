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
        /** 풀리는 시각 (밀리초). 0 이면 영구 */
        public long until;
        /** 처리한 관리자 */
        public String by = "";
        public long at;

        public boolean permanent() {
            return until <= 0;
        }
    }

    public boolean maintenance;
    /** /저장상자1~4 : box, box1, final, final1 */
    public final Map<String, ItemStack> saved = new HashMap<>();
    /** 워프 위치. 월드는 이름으로 따로 기억 (월드를 다시 불러와도 · 늦게 불러와도 찾을 수 있게). 꺼낼 때는 warp() */
    public final Map<String, Location> warps = new HashMap<>();
    private final Map<String, String> warpWorlds = new HashMap<>();

    public void setWarp(String key, Location l) {
        warps.put(key, l.clone());
        warpWorlds.put(key, l.getWorld().getName());
    }

    public void removeWarp(String key) {
        warps.remove(key);
        warpWorlds.remove(key);
    }

    /** 지금 쓸 수 있는 워프 위치 (월드가 없으면 null) */
    public Location warp(String key) {
        Location l = warps.get(key);
        if (l == null) return null;
        String wn = warpWorlds.get(key);
        org.bukkit.World w = wn == null ? null : org.bukkit.Bukkit.getWorld(wn);
        if (w == null) {
            try {
                w = l.getWorld();
            } catch (IllegalArgumentException e) {   // 월드가 내려갔다 다시 올라온 경우 예전 월드 참조
                w = null;
            }
        }
        if (w == null) return null;
        Location c = new Location(w, l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
        return c;
    }
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
            ConfigurationSection ws = s.getConfigurationSection(k);
            if (ws != null && ws.contains("x")) {   // 새 형식: world · x · y · z · yaw · pitch
                warps.put(k, new Location(null, ws.getDouble("x"), ws.getDouble("y"), ws.getDouble("z"), (float) ws.getDouble("yaw"), (float) ws.getDouble("pitch")));
                warpWorlds.put(k, ws.getString("world", ""));
                continue;
            }
            Location l = s.getLocation(k);   // 예전 형식 (Location)
            if (l != null) {
                warps.put(k, l);
                try {
                    if (l.getWorld() != null) warpWorlds.put(k, l.getWorld().getName());
                } catch (IllegalArgumentException ignored) {
                }
            }
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
            b.until = s.getLong(u + ".until");
            b.by = s.getString(u + ".by", "");
            b.at = s.getLong(u + ".at");
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
        warps.forEach((k, v) -> {
            String wn = warpWorlds.get(k);
            if (wn == null) return;
            y.set("warps." + k + ".world", wn);
            y.set("warps." + k + ".x", v.getX());
            y.set("warps." + k + ".y", v.getY());
            y.set("warps." + k + ".z", v.getZ());
            y.set("warps." + k + ".yaw", (double) v.getYaw());
            y.set("warps." + k + ".pitch", (double) v.getPitch());
        });
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
            if (b.until > 0) y.set("bans." + u + ".until", b.until);
            if (!b.by.isEmpty()) y.set("bans." + u + ".by", b.by);
            if (b.at > 0) y.set("bans." + u + ".at", b.at);
        });
        return y.saveToString();
    }

}
