package com.premiumpass.reward;

import com.premiumpass.PremiumPass;
import com.premiumpass.data.PlayerData;
import kr.wave.common.Items;
import kr.wave.common.Text;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;

/** 레벨 보상 관리 (rewards.yml) - 무료 / 프리미엄 트랙 */
public class RewardManager {

    private final PremiumPass plugin;
    private final File file;
    private final Map<Track, Map<Integer, LevelReward>> rewards = new EnumMap<>(Track.class);

    public RewardManager(PremiumPass plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "rewards.yml");
        for (Track t : Track.values()) rewards.put(t, new TreeMap<>());
    }

    private Map<Integer, LevelReward> map(Track t) {
        return rewards.get(t);
    }

    public void load() {
        for (Track t : Track.values()) map(t).clear();
        if (!file.exists()) {
            plugin.getLogger().info("rewards.yml 이 없어 야생 서버용 기본 보상(무료/프리미엄)을 생성합니다.");
            int max = plugin.pass().maxLevel();
            map(Track.FREE).putAll(DefaultRewards.create(Track.FREE, max));
            map(Track.PREMIUM).putAll(DefaultRewards.create(Track.PREMIUM, max));
            save();
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection levels = y.getConfigurationSection("levels");
        if (levels == null) return;
        boolean migrated = false;
        for (String key : levels.getKeys(false)) {
            int level;
            try {
                level = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("rewards.yml: 잘못된 레벨 키 '" + key + "'");
                continue;
            }
            ConfigurationSection s = levels.getConfigurationSection(key);
            if (s == null) continue;
            if (s.isConfigurationSection("free") || s.isConfigurationSection("premium")) {
                for (Track t : Track.values()) {
                    LevelReward r = read(s.getConfigurationSection(t.key));
                    if (r != null) map(t).put(level, r);
                }
            } else {
                // 이전 버전(단일 트랙) 형식 -> 프리미엄 트랙으로 옮긴다
                LevelReward r = read(s);
                if (r != null) map(Track.PREMIUM).put(level, r);
                migrated = true;
            }
        }
        if (migrated) {
            plugin.getLogger().info("이전 형식의 rewards.yml 을 프리미엄 트랙으로 변환했습니다. 무료 트랙 보상은 비어 있습니다.");
            save();
        }
    }

    private static LevelReward read(ConfigurationSection s) {
        if (s == null) return null;
        LevelReward r = new LevelReward();
        List<?> list = s.getList("items");
        if (list != null) {
            for (Object o : list) if (o instanceof ItemStack it) r.items.add(it);
        }
        r.commands.addAll(s.getStringList("commands"));
        r.commandLore.addAll(s.getStringList("command-lore"));
        return r;
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of(
                "프리미엄 패스 레벨 보상 (free = 무료 트랙, premium = 프리미엄 트랙)",
                "게임 안에서 /passadmin reward <레벨> <free|premium> 으로 GUI 편집을 권장합니다.",
                "commands: 콘솔 명령어 보상 (%player% = 플레이어 이름)",
                "command-lore: 명령어 보상을 GUI 에 표시할 설명"));
        TreeSet<Integer> levels = new TreeSet<>();
        for (Track t : Track.values()) levels.addAll(map(t).keySet());
        for (int lv : levels) {
            for (Track t : Track.values()) {
                LevelReward r = map(t).get(lv);
                if (r == null) continue;
                String base = "levels." + lv + "." + t.key;
                y.set(base + ".items", new ArrayList<>(r.items));
                if (!r.commands.isEmpty()) y.set(base + ".commands", r.commands);
                if (!r.commandLore.isEmpty()) y.set(base + ".command-lore", r.commandLore);
            }
        }
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "rewards.yml 저장 실패", ex);
        }
    }

    public LevelReward get(Track t, int level) {
        return map(t).get(level);
    }

    public LevelReward getOrCreate(Track t, int level) {
        return map(t).computeIfAbsent(level, k -> new LevelReward());
    }

    public void setItems(Track t, int level, List<ItemStack> items) {
        LevelReward r = getOrCreate(t, level);
        r.items.clear();
        r.items.addAll(items);
        save();
    }

    public void clear(Track t, int level) {
        map(t).remove(level);
        save();
    }

    public static boolean isEmpty(LevelReward r) {
        return r == null || r.isEmpty();
    }

    public List<String> describe(LevelReward r) {
        List<String> out = new ArrayList<>();
        if (isEmpty(r)) {
            out.add("&8- 보상 없음");
            return out;
        }
        for (ItemStack it : r.items) out.add("&f- " + Items.describe(it));
        if (!r.commandLore.isEmpty()) {
            for (String s : r.commandLore) out.add("&f- " + s);
        } else if (!r.commands.isEmpty()) {
            out.add("&f- 특별 보상 " + r.commands.size() + "개");
        }
        return out;
    }

    /** 레벨, 수령 여부, 프리미엄 보유까지 모두 확인 */
    public boolean canClaim(Player p, PlayerData d, Track t, int level) {
        if (isEmpty(get(t, level)) || d.level < level || d.claimed(t).contains(level)) return false;
        return t == Track.FREE || plugin.pass().isPremium(p, d);
    }

    public int claimableCount(Player p, PlayerData d) {
        int n = 0;
        for (Track t : Track.values()) {
            for (int lv = 1; lv <= d.level; lv++) if (canClaim(p, d, t, lv)) n++;
        }
        return n;
    }

    /** @return 수령 성공 여부 */
    public boolean claim(Player p, PlayerData d, Track t, int level, boolean silent) {
        if (!canClaim(p, d, t, level)) return false;
        LevelReward r = get(t, level);
        d.claimed(t).add(level);
        boolean dropped = Items.give(p, r.items);
        for (String cmd : r.commands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", p.getName()));
        }
        if (!silent) {
            Text.send(p, "&a레벨 &e" + level + " " + (t == Track.PREMIUM ? "&6프리미엄" : "&f무료") + " &a보상을 수령했습니다!");
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.8f);
        }
        if (dropped) Text.send(p, "&c인벤토리가 가득 차서 일부 보상이 발밑에 떨어졌습니다!");
        return true;
    }

    public int claimAll(Player p, PlayerData d) {
        int count = 0;
        for (Track t : Track.values()) {
            for (int lv = 1; lv <= d.level; lv++) {
                if (claim(p, d, t, lv, true)) count++;
            }
        }
        if (count > 0) {
            Text.send(p, "&a보상 &e" + count + "개&a를 한 번에 수령했습니다!");
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.8f);
        } else {
            Text.send(p, "&7수령할 수 있는 보상이 없습니다.");
        }
        if (!plugin.pass().isPremium(p, d)) {
            int locked = 0;
            for (int lv = 1; lv <= d.level; lv++) {
                if (!isEmpty(get(Track.PREMIUM, lv)) && !d.claimedPremium.contains(lv)) locked++;
            }
            if (locked > 0) Text.send(p, "&6프리미엄 패스&7를 활성화하면 보상 &e" + locked + "개&7를 더 받을 수 있습니다.");
        }
        return count;
    }
}
