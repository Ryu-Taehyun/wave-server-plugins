package kr.wave.core.module;

import kr.wave.core.Items;
import kr.wave.core.WaveCore;
import kr.wave.core.data.PlayerData;
import kr.wave.core.util.Cmd;
import kr.wave.core.util.Fx;
import kr.wave.core.util.Gui;
import kr.wave.core.util.It;
import kr.wave.core.util.Log;
import kr.wave.core.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 상자(뽑기) 시스템. CrazyCrates 상자 설정 파일(crates/*.yml)을 그대로 읽어 동작한다.
 * - /상자 : 상자 메뉴 (좌클릭 오픈, 우클릭 구성품)
 * - 키: 가상 키(플레이어 데이터) 또는 실물 키(아이템). 기존 CrazyCrates 실물 키도 인식
 * - 보상: Items(Paper 아이템 데이터), Commands, Special(서버 아이템), 없으면 Display 아이템
 */
public class CrateModule extends Module {

    /** 보상 하나 */
    static final class Prize {
        final String id;
        final ItemStack display;
        final List<ItemStack> items = new ArrayList<>();
        final List<String> commands = new ArrayList<>();
        final double weight;

        Prize(String id, ItemStack display, double weight) {
            this.id = id;
            this.display = display;
            this.weight = weight;
        }
    }

    /** 상자 하나 */
    public static final class Crate {
        final String id;
        String name = "";
        List<String> lore = new ArrayList<>();
        Material icon = Material.CHEST;
        boolean glow;
        int slot = -1;
        boolean inGui = true;
        int required = 1;
        int startingKeys;
        boolean broadcast;
        String animationName = "";
        String previewName = "상자 구성품";
        boolean preview = true;
        /** 야생 월드에서만 열 수 있음 (WaveCore 추가 항목 Wild-Only) */
        boolean wildOnly;
        String cycleSound = "block.note_block.xylophone", stopSound = "entity.player.levelup";
        String keyName = "", keyMaterial = "tripwire_hook";
        List<String> keyLore = new ArrayList<>();
        boolean keyGlow = true;
        /** 상점 표시용 "키" 대신 부를 이름 (예: 상자 교환권) */
        String keyLabel;
        final List<Prize> prizes = new ArrayList<>();

        Crate(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public String name() {
            return name;
        }
    }

    private final Map<String, Crate> crates = new LinkedHashMap<>();
    private final Set<UUID> opening = new HashSet<>();
    private NamespacedKey keyTag;

    /** 이미 운영 중인 서버에도 한 번 만들어 줄 새 기본 상자 */
    private static final java.util.Set<String> NEW_DEFAULTS = java.util.Set.of("CatastropheOfChaos");

    public CrateModule(WaveCore plugin) {
        super(plugin);
    }

    @Override
    public void enable() {
        keyTag = new NamespacedKey(plugin, "crate_key");
        load();
        cmd("상자", null, this::command, "crate", "뽑기상자");
    }

    /* ======================= 설정 읽기 ======================= */

    private File folder() {
        return new File(plugin.getDataFolder(), "crates");
    }

    public void load() {
        crates.clear();
        File dir = folder();
        boolean fresh = !dir.exists();
        if (fresh) dir.mkdirs();
        for (String n : new String[]{"Sealedp", "TheRuiner", "chronos", "TheEchoesOfNatural", "combat", "enderpearl", "CatastropheOfChaos"}) {
            // 처음 설치 때는 전부, 이후에는 새로 추가된 상자(혼돈 상자 등)만 만들어 줌. 지운 상자를 되살리지 않도록 새 상자만
            if (!fresh && !NEW_DEFAULTS.contains(n)) continue;
            if (new File(dir, n + ".yml").exists()) continue;
            try {
                plugin.saveResource("crates/" + n + ".yml", false);
            } catch (IllegalArgumentException e) {
                Log.warn("기본 상자 파일이 jar 안에 없습니다: crates/" + n + ".yml");
            }
        }
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return;
        java.util.Arrays.sort(files);
        for (File f : files) {
            try {
                Crate c = parse(f.getName().substring(0, f.getName().length() - 4), YamlConfiguration.loadConfiguration(f));
                if (c != null) crates.put(c.id.toLowerCase(), c);
            } catch (Exception e) {
                plugin.getLogger().warning("상자 설정을 읽지 못했습니다: " + f.getName() + " (" + e.getMessage() + ")");
            }
        }
        plugin.getLogger().info("상자 " + crates.size() + "개를 불러왔습니다.");
    }

    private Crate parse(String id, YamlConfiguration y) {
        ConfigurationSection s = y.getConfigurationSection("Crate");
        if (s == null) return null;
        Crate c = new Crate(id);
        c.name = s.getString("Name", id);
        c.lore = s.getStringList("Lore");
        c.icon = mat(s.getString("Item"), Material.CHEST);
        c.glow = s.getBoolean("Glowing");
        c.slot = s.getInt("Slot", -1);
        c.inGui = s.getBoolean("InGUI", true);
        c.required = Math.max(1, s.getInt("RequiredKeys", 1));
        c.startingKeys = s.getInt("StartingKeys", 0);
        c.broadcast = s.getBoolean("OpeningBroadCast", false);
        c.animationName = s.getString("Animation.Name", "");
        c.previewName = s.getString("Preview.Name", "상자 구성품");
        c.preview = s.getBoolean("Preview.Toggle", true);
        c.wildOnly = s.getBoolean("Wild-Only", false);
        if (s.getBoolean("sound.cycle-sound.toggle", true)) c.cycleSound = s.getString("sound.cycle-sound.value", c.cycleSound);
        else c.cycleSound = null;
        if (s.getBoolean("sound.stop-sound.toggle", true)) c.stopSound = s.getString("sound.stop-sound.value", c.stopSound);
        else c.stopSound = null;
        c.keyName = s.getString("PhysicalKey.Name", "<yellow>" + id + " 키");
        c.keyLore = s.getStringList("PhysicalKey.Lore");
        c.keyMaterial = s.getString("PhysicalKey.Item", "tripwire_hook");
        c.keyGlow = s.getBoolean("PhysicalKey.Glowing", true);
        c.keyLabel = s.getString("Key-Label");
        ConfigurationSection prizes = s.getConfigurationSection("Prizes");
        if (prizes != null) for (String pid : prizes.getKeys(false)) {
            ConfigurationSection p = prizes.getConfigurationSection(pid);
            if (p == null) continue;
            Prize prize = new Prize(pid, displayOf(p), Math.max(0, p.getDouble("Weight", 1)));
            for (String raw : p.getStringList("Items")) {
                ItemStack it = decode(raw);
                if (it != null) prize.items.add(it);
                else plugin.getLogger().warning("상자 '" + id + "' 보상 " + pid + " 의 아이템 데이터를 읽지 못해 미리보기 아이템으로 지급합니다.");
            }
            ItemStack special = Items.special(p.getString("Special"));
            if (special != null) {
                special.setAmount(Math.max(1, p.getInt("DisplayAmount", 1)));
                prize.items.add(special);
            }
            prize.commands.addAll(p.getStringList("Commands"));
            if (prize.items.isEmpty() && prize.commands.isEmpty()) prize.items.add(prize.display.clone());
            c.prizes.add(prize);
        }
        return c;
    }

    private static Material mat(String name, Material def) {
        if (name == null) return def;
        Material m = Material.matchMaterial(name);
        return m == null || !m.isItem() ? def : m;
    }

    /** CrazyCrates 의 "Data:..." (Paper 아이템 데이터, gzip + base64) */
    private ItemStack decode(String raw) {
        if (raw == null) return null;
        String b64 = raw.startsWith("Data:") ? raw.substring(5) : raw;
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(b64.trim()));
        } catch (Throwable t) {
            return null;   // 서버 버전이 너무 낮거나 손상된 데이터 -> Display 아이템으로 대체
        }
    }

    /** 미리보기/룰렛에 보이는 아이템 */
    private ItemStack displayOf(ConfigurationSection p) {
        Material m = mat(p.getString("DisplayItem"), Material.STONE);
        It b = It.of(m, Math.max(1, Math.min(64, p.getInt("DisplayAmount", 1))));
        if (p.getString("DisplayName") != null) b.name(p.getString("DisplayName"));
        else {
            ItemStack special = Items.special(p.getString("Special"));
            if (special != null) {
                ItemStack s = special.clone();
                s.setAmount(Math.max(1, Math.min(64, p.getInt("DisplayAmount", 1))));
                return s;
            }
        }
        for (String l : p.getStringList("DisplayLore")) b.lore(l);
        for (String en : p.getStringList("DisplayEnchantments")) {
            String[] kv = en.split(":");
            Enchantment e = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(kv[0].toLowerCase()));
            if (e != null) b.ench(e, kv.length > 1 ? parseInt(kv[1], 1) : 1);
        }
        return b.build();
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public Crate crate(String id) {
        return id == null ? null : crates.get(id.toLowerCase());
    }

    public java.util.Collection<Crate> crates() {
        return crates.values();
    }

    /* ======================= 키 ======================= */

    /** 실물 키 아이템 */
    public ItemStack keyItem(Crate c, int amount) {
        It b = It.of(mat(c.keyMaterial, Material.TRIPWIRE_HOOK), Math.max(1, amount)).name(c.keyName);
        for (String l : c.keyLore) b.lore(l);
        if (c.keyGlow) b.ench(Enchantment.UNBREAKING, 1);
        ItemStack it = b.build();
        ItemMeta meta = it.getItemMeta();
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(keyTag, PersistentDataType.STRING, c.id);
        it.setItemMeta(meta);
        return it;
    }

    /** 이 아이템이 어떤 상자의 실물 키인지 (WaveCore 키 또는 기존 CrazyCrates 키). 아니면 null */
    public Crate keyOf(ItemStack it) {
        if (It.isAir(it) || !it.hasItemMeta()) return null;
        PersistentDataContainer pdc = it.getItemMeta().getPersistentDataContainer();
        String id = pdc.get(keyTag, PersistentDataType.STRING);
        if (id != null) return crate(id);
        for (NamespacedKey k : pdc.getKeys()) {
            if (!k.getNamespace().equalsIgnoreCase("crazycrates")) continue;
            try {
                String v = pdc.get(k, PersistentDataType.STRING);
                if (v != null && crate(v) != null) return crate(v);
            } catch (IllegalArgumentException ignored) {   // 글자가 아닌 다른 형식의 CrazyCrates 태그: 키가 아님
            }
        }
        return null;
    }

    /** "상자 이름 + 키" (교환권으로 부르는 상자는 설정 Key-Label 로 바꿀 수 있음) */
    public String keyLabel(Crate c) {
        String label = c.keyLabel == null ? "키" : c.keyLabel;
        if (Msg.plainOf(c.name).trim().endsWith(label)) return c.name;   // 이름이 이미 '... 키' 로 끝나면 한 번만
        return c.name + " &7" + label;
    }

    /** 상점 판매 아이콘: 실물 키 아이템이 아니라 계정에 쌓이는 키임을 표시 */
    public ItemStack shopIcon(Crate c, int amount, String priceLine) {
        It b = It.of(mat(c.keyMaterial, Material.TRIPWIRE_HOOK), Math.max(1, Math.min(64, amount))).name(keyLabel(c))
                .lore("", "&7구매하면 &e계정에 키 " + amount + "개&7가 지급됩니다.", "&7아이템 뽑기(&f/상자&7)에서 사용할 수 있습니다.", "", "&8━━━━━━━━━━━━━━",
                        "&f수량 : &e" + amount + "개", priceLine);
        if (c.keyGlow) b.ench(Enchantment.UNBREAKING, 1);
        ItemStack it = b.build();
        ItemMeta meta = it.getItemMeta();
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        it.setItemMeta(meta);
        return it;
    }

    /** 인벤토리에 있는 실물 키(예전 키 포함)를 모두 계정 키로 옮김. 옮긴 개수 */
    public int absorbPhysical(Player p) {
        int moved = 0;
        PlayerData d = data(p);
        ItemStack[] inv = p.getInventory().getStorageContents();
        for (int i = 0; i < inv.length; i++) {
            Crate c = keyOf(inv[i]);
            if (c == null) continue;
            d.crateKeys.merge(c.id, inv[i].getAmount(), Integer::sum);
            moved += inv[i].getAmount();
            p.getInventory().setItem(i, null);
        }
        ItemStack off = p.getInventory().getItemInOffHand();
        Crate oc = keyOf(off);
        if (oc != null) {
            d.crateKeys.merge(oc.id, off.getAmount(), Integer::sum);
            moved += off.getAmount();
            p.getInventory().setItemInOffHand(null);
        }
        if (moved > 0) {
            plugin.store().save(d);
            Msg.send(p, "&a[상자] &f실물 키 &e" + moved + "개&f를 계정에 등록했습니다. &7(이제 키는 계정에 쌓입니다)");
            Msg.sound(p, "entity.experience_orb.pickup", 0.6f, 1.4f);
        }
        return moved;
    }

    public int virtualKeys(OfflinePlayer p, Crate c) {
        PlayerData d = plugin.store().get(p);
        return d.crateKeys.getOrDefault(c.id, 0);
    }

    public int physicalKeys(Player p, Crate c) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) if (keyOf(it) == c) n += it.getAmount();
        return n;
    }

    public int keys(Player p, Crate c) {
        return virtualKeys(p, c) + physicalKeys(p, c);
    }

    /** 키 지급. physical: 아이템으로, 아니면 가상 키 */
    public void giveKeys(OfflinePlayer t, Crate c, int amount, boolean physical) {
        if (physical && t.getPlayer() != null) {
            It.give(t.getPlayer(), keyItem(c, 1), amount);
            return;
        }
        PlayerData d = plugin.store().get(t);
        d.crateKeys.merge(c.id, amount, Integer::sum);
        plugin.store().saveAuto(d);
    }

    /** 키 n개 사용 (가상 키 먼저). 모자라면 아무것도 쓰지 않고 false */
    private boolean takeKeys(Player p, Crate c, int n) {
        if (keys(p, c) < n) return false;
        PlayerData d = data(p);
        int v = d.crateKeys.getOrDefault(c.id, 0);
        int useV = Math.min(v, n);
        if (useV > 0) d.crateKeys.put(c.id, v - useV);
        int left = n - useV;
        ItemStack[] inv = p.getInventory().getStorageContents();
        for (int i = 0; i < inv.length && left > 0; i++) {
            if (keyOf(inv[i]) != c) continue;
            int take = Math.min(left, inv[i].getAmount());
            left -= take;
            if (inv[i].getAmount() <= take) p.getInventory().setItem(i, null);
            else inv[i].setAmount(inv[i].getAmount() - take);
        }
        plugin.store().save(d);
        return true;
    }

    /** 키를 손에 들고 우클릭 -> 상자 메뉴 */
    @EventHandler(priority = EventPriority.HIGH)
    public void onKeyUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Crate c = keyOf(e.getPlayer().getInventory().getItemInMainHand());
        if (c == null) return;
        e.setCancelled(true);
        back(e.getPlayer(), c);   // 스페셜 테마 상자 교환권은 스페셜 테마 뽑기로 바로
    }

    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
        if (e.getPlayer().hasPlayedBefore()) return;
        for (Crate c : crates.values()) if (c.startingKeys > 0) giveKeys(e.getPlayer(), c, c.startingKeys, false);
    }

    /* ======================= 메뉴 ======================= */

    /** 바깥 테두리에만 검은 유리판 (안쪽 빈칸은 비워 둠) */
    private static void border(Gui g) {
        org.bukkit.inventory.Inventory inv = g.getInventory();
        int rows = inv.getSize() / 9;
        ItemStack pane = It.pane(Gui.FILLER, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            int row = i / 9, col = i % 9;
            if ((row == 0 || row == rows - 1 || col == 0 || col == 8) && It.isAir(inv.getItem(i))) inv.setItem(i, pane);
        }
    }

    public void openMenu(Player p) {
        absorbPhysical(p);
        tutorial(p, "crate");
        Gui g = new Gui(5, "&8&l아이템 뽑기");
        g.set(4, It.of(Material.PLAYER_HEAD).skull(p).name("&e" + p.getName()).lore("", "&7좌클릭 &f상자 1회 열기", "&7Shift + 좌클릭 &f상자 10회 열기",
                "&7우클릭 &f구성품 보기", "", "&8키는 상점의 룰렛 키 상점 / 유료 캐시 상점에서", "&8구매할 수 있습니다.").build());
        int auto = 10;
        java.util.List<Crate> theme = specialTheme();
        if (!theme.isEmpty()) {
            int keysAll = 0;
            for (Crate c : theme) keysAll += keys(p, c);
            It t = It.of(Material.BEACON).name(SPECIAL_THEME_NAME)
                    .lore("", "&7기간 한정 테마 상자를 모아 둔 곳입니다.", "");
            for (Crate c : theme) t.lore("&8- " + c.name + " &7(" + keys(p, c) + "개)");
            t.lore("", "&8&l↳ &7&l클릭 시 이동됩니다.");
            if (keysAll > 0) t.ench(Enchantment.UNBREAKING, 1);
            g.set(plugin.getConfig().getInt("crate-menu.special-theme-slot", 13), t.hide().build(), e -> later(1, () -> openTheme(p)));
        }
        for (Crate c : crates.values()) {
            if (!c.inGui || theme.contains(c)) continue;
            int slot = c.slot >= 0 && c.slot < 36 ? c.slot : auto++;
            crateButton(g, p, c, slot);
        }
        g.nav(p, () -> plugin.module(ShopModule.class).openMain(p));
        border(g);
        g.open(p);
    }

    static final String SPECIAL_THEME_NAME = "<b><gradient:#F5E400:#95FFBE:#BCBCBC><italic:false>스페셜 테마 뽑기</gradient></b>";

    /** config.yml crate-menu.special-theme 에 적힌 상자 (적힌 순서대로) */
    private java.util.List<Crate> specialTheme() {
        java.util.List<Crate> out = new java.util.ArrayList<>();
        java.util.List<String> ids = plugin.getConfig().isList("crate-menu.special-theme")
                ? plugin.getConfig().getStringList("crate-menu.special-theme") : java.util.List.of("TheEchoesOfNatural", "CatastropheOfChaos");
        for (String id : ids) {
            Crate c = crate(id);
            if (c != null && c.inGui && !out.contains(c)) out.add(c);
        }
        return out;
    }

    public boolean isSpecialTheme(Crate c) {
        return specialTheme().contains(c);
    }

    /** 스페셜 테마 뽑기: 테마 상자만 가운데 줄에 모아서 */
    public void openTheme(Player p) {
        absorbPhysical(p);
        java.util.List<Crate> theme = specialTheme();
        Gui g = new Gui(4, "&8&l스페셜 테마 뽑기");
        g.set(4, It.of(Material.PLAYER_HEAD).skull(p).name("&e" + p.getName()).lore("", "&7좌클릭 &f상자 1회 열기", "&7Shift + 좌클릭 &f상자 10회 열기",
                "&7우클릭 &f구성품 보기", "", "&8상자 교환권은 상점의 스페셜 상점 / 유료 캐시 상점에서", "&8구매할 수 있습니다.").build());
        int n = Math.min(7, theme.size());
        int start = 13 - (n - 1);   // 1개: 13, 2개: 12 14, 3개: 11 13 15 ...
        for (int i = 0; i < n; i++) crateButton(g, p, theme.get(i), n <= 4 ? start + i * 2 : 10 + i);
        g.nav(p, () -> openMenu(p));
        border(g);
        g.open(p);
    }

    private void crateButton(Gui g, Player p, Crate c, int slot) {
        {
            int keys = keys(p, c);
            It b = It.of(c.icon).name(c.name);
            for (String l : c.lore) b.lore(l.replace("%keys%", String.valueOf(keys)).replace("%Keys%", String.valueOf(keys)));
            b.lore("<gray>Shift + 좌클릭 시 10회 오픈됩니다.");
            if (locked(c)) b.lore("", "&c&l🔒 점검 중 &7- 지금은 열 수 없습니다." + lockReasonText(c), "&8구성품 보기(우클릭)는 됩니다.");
            if (c.glow || keys > 0) b.ench(Enchantment.UNBREAKING, 1);
            ItemStack icon = b.build();
            ItemMeta meta = icon.getItemMeta();
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS, org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
            icon.setItemMeta(meta);
            g.set(slot, icon, e -> {
                switch (e.getClick()) {
                    case RIGHT, SHIFT_RIGHT -> later(1, () -> preview(p, c));
                    case SHIFT_LEFT -> open(p, c, 10);
                    default -> open(p, c, 1);
                }
            });
        }
    }

    /** 상자를 열었던 메뉴로 돌아가기 */
    private void back(Player p, Crate c) {
        if (c != null && isSpecialTheme(c)) openTheme(p);
        else openMenu(p);
    }

    private void preview(Player p, Crate c) {
        if (!c.preview) return;
        int rows = Math.min(6, Math.max(1, (c.prizes.size() + 6) / 7) + 2);
        Gui g = new Gui(rows, "&8&l" + Msg.plainOf(c.previewName.isEmpty() ? "상자 구성품" : c.previewName) + " &7- " + Msg.plainOf(c.name));
        g.set(4, It.of(c.icon).name(c.name).lore("", "&7등장할 수 있는 아이템 목록입니다.").build());
        double total = 0;
        for (Prize pz : c.prizes) total += pz.weight;
        int idx = 0;
        for (int row = 1; row < rows - 1; row++) {
            for (int col = 1; col <= 7 && idx < c.prizes.size(); col++) g.set(row * 9 + col, withChance(c.prizes.get(idx++), total));
        }
        g.nav(p, () -> back(p, c));
        border(g);
        g.open(p);
    }

    /** 구성품 미리보기: 설명 끝에 확률 한 줄 */
    private static ItemStack withChance(Prize pz, double total) {
        ItemStack it = pz.display.clone();
        if (total <= 0) return it;
        String txt = java.math.BigDecimal.valueOf(pz.weight / total * 100).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        ItemMeta meta = it.getItemMeta();
        meta.lore(Msg.append(meta.lore(), "&7확률 : &e" + txt + "%"));
        it.setItemMeta(meta);
        return it;
    }

    /* ======================= 열기 ======================= */

    /** 한 번 여는 과정 (1회 룰렛 또는 10회 공개) */
    private final class Session {
        final Player p;
        final Crate c;
        final List<Prize> wins;
        Gui gui;
        boolean done;

        Session(Player p, Crate c, List<Prize> wins) {
            this.p = p;
            this.c = c;
            this.wins = wins;
        }
    }

    public void open(Player p, Crate c) {
        open(p, c, 1);
    }

    /** times 회 열기 (키가 모자라면 가진 만큼, 최대 times) */
    public void open(Player p, Crate c, int times) {
        if (locked(c)) {
            Msg.send(p, "&c&l[!] &c" + Msg.plainOf(c.name) + " &c상자는 지금 점검 중이라 열 수 없습니다." + lockReasonText(c));
            Msg.send(p, "&7가지고 있는 키는 그대로 남아 있습니다.");
            Msg.sound(p, "block.chest.locked", 0.8f, 1f);
            return;
        }
        if (c.prizes.isEmpty()) {
            Msg.send(p, "&c이 상자에는 보상이 설정되어 있지 않습니다.");
            return;
        }
        if (opening.contains(p.getUniqueId())) {
            Msg.send(p, "&c이미 상자를 여는 중입니다.");
            return;
        }
        if (c.wildOnly && !inWorld(p, "wild")) {
            Msg.send(p, "&c" + Msg.plainOf(c.name) + " &c은(는) 야생에서만 열 수 있습니다.");
            Gui.denySound(p);
            return;
        }
        int can = keys(p, c) / c.required;
        if (can < 1) {
            Msg.send(p, "&c" + Msg.plainOf(c.name) + " &c키가 부족합니다. &7(필요 " + c.required + "개, 보유 " + keys(p, c) + "개)");
            Gui.denySound(p);
            return;
        }
        int n = Math.max(1, Math.min(times, can));
        if (!takeKeys(p, c, n * c.required)) return;
        if (n < times) Msg.send(p, "&e키가 " + n + "회분만 있어 " + n + "회 엽니다.");
        opening.add(p.getUniqueId());
        List<Prize> wins = new ArrayList<>();
        for (int i = 0; i < n; i++) wins.add(pick(c));
        Session s = new Session(p, c, wins);
        if (n == 1) startWheel(s);
        else startMulti(s);
    }

    private String title(Crate c, int n) {
        return "&8&l" + Msg.plainOf(c.animationName.isEmpty() ? c.name : c.animationName) + (n > 1 ? " &7x" + n : "");
    }

    private ItemStack skipButton() {
        return It.of(Material.FEATHER).name("&e&l스킵").lore("&7클릭하면 결과를 바로 확인합니다.").build();
    }

    /* ---- 1회: 가로 룰렛 (가운데 13번 칸에 멈춤) ---- */

    private void startWheel(Session s) {
        Gui g = new Gui(3, title(s.c, 1));
        s.gui = g;
        g.set(4, It.pane(Gui.FILLER, "&f▼ 당첨 ▼"));
        g.set(22, skipButton(), e -> skip(s));
        border(g);
        Prize[] line = new Prize[7];
        for (int i = 0; i < 7; i++) line[i] = pick(s.c);
        for (int i = 0; i < 7; i++) g.set(10 + i, line[i].display.clone());
        g.onClose(e -> {
            if (!s.done) Msg.actionBar(s.p, "&7창을 닫아도 보상은 지급됩니다.");
        });
        g.open(s.p);
        spin(s, line, 30, 1);
    }

    private void spin(Session s, Prize[] line, int left, int delay) {
        if (s.done) return;
        System.arraycopy(line, 1, line, 0, 6);
        line[6] = left == 4 ? s.wins.get(0) : pick(s.c);   // 3칸 더 밀려 가운데(3번)에 멈춤
        for (int i = 0; i < 7; i++) s.gui.set(10 + i, line[i].display.clone());
        if (viewing(s) && s.c.cycleSound != null) Msg.sound(s.p, s.c.cycleSound, 0.6f, 1.0f + (30 - left) / 40f);
        if (left <= 1) {
            later(20, () -> complete(s));
            return;
        }
        int next = delay + (left <= 12 ? 1 : 0) + (left <= 6 ? 1 : 0);
        later(delay, () -> spin(s, line, left - 1, next));
    }

    /* ---- 10회: 하나씩 공개 ---- */

    private static final int[] MULTI_SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24};

    private void startMulti(Session s) {
        Gui g = new Gui(4, title(s.c, s.wins.size()));
        s.gui = g;
        for (int i = 0; i < s.wins.size() && i < MULTI_SLOTS.length; i++) g.set(MULTI_SLOTS[i], It.pane(Gui.FILLER, "&7?"));
        g.set(31, skipButton(), e -> skip(s));
        border(g);
        g.onClose(e -> {
            if (!s.done) Msg.actionBar(s.p, "&7창을 닫아도 보상은 지급됩니다.");
        });
        g.open(s.p);
        reveal(s, 0);
    }

    private void reveal(Session s, int idx) {
        if (s.done) return;
        if (idx >= s.wins.size()) {
            later(15, () -> complete(s));
            return;
        }
        s.gui.set(MULTI_SLOTS[idx], s.wins.get(idx).display.clone());
        if (viewing(s) && s.c.cycleSound != null) Msg.sound(s.p, s.c.cycleSound, 0.7f, 0.8f + idx * 0.12f);
        later(6, () -> reveal(s, idx + 1));
    }

    private boolean viewing(Session s) {
        return s.p.isOnline() && s.p.getOpenInventory().getTopInventory() != null && s.p.getOpenInventory().getTopInventory().getHolder() == s.gui;
    }

    /** 스킵: 결과를 바로 보여주고 지급 */
    private void skip(Session s) {
        if (s.done) return;
        if (s.wins.size() == 1) s.gui.set(13, s.wins.get(0).display.clone());
        else for (int i = 0; i < s.wins.size() && i < MULTI_SLOTS.length; i++) s.gui.set(MULTI_SLOTS[i], s.wins.get(i).display.clone());
        complete(s);
    }

    private Prize pick(Crate c) {
        double total = 0;
        for (Prize pr : c.prizes) total += pr.weight;
        if (total <= 0) return c.prizes.get(ThreadLocalRandom.current().nextInt(c.prizes.size()));
        double r = ThreadLocalRandom.current().nextDouble(total);
        for (Prize pr : c.prizes) {
            r -= pr.weight;
            if (r < 0) return pr;
        }
        return c.prizes.get(c.prizes.size() - 1);
    }

    /** 보상 지급 (한 번만) */
    private void complete(Session s) {
        if (s.done) return;
        s.done = true;
        opening.remove(s.p.getUniqueId());
        Player p = s.p;
        Crate c = s.c;
        for (Prize win : s.wins) {
            if (p.isOnline()) for (ItemStack it : win.items) It.give(p, it.clone());
            else for (ItemStack it : win.items) plugin.module(MailModule.class).send(p.getUniqueId(), it.clone());
            for (String cmd : win.commands) {
                String line = cmd.trim().replace("%player%", p.getName()).replace("%Player%", p.getName());
                if (line.startsWith("/")) line = line.substring(1);
                console(line);
            }
        }
        StringBuilder log = new StringBuilder();
        for (Prize win : s.wins) {
            if (log.length() > 0) log.append(", ");
            log.append(Msg.plain(win.display.getItemMeta() != null && win.display.getItemMeta().hasDisplayName() ? win.display.getItemMeta().displayName() : net.kyori.adventure.text.Component.text(win.display.getType().name())))
                    .append(" x").append(win.display.getAmount()).append(" [").append(win.id).append("]");
        }
        kr.wave.core.util.TxLog.crate(p.getName(), p.getUniqueId(), c.id, s.wins.size(), log + (p.isOnline() ? "" : " (오프라인: 우편함으로)"));
        // 결과 창: 스킵 버튼 -> 상자 메뉴로
        int btn = s.wins.size() == 1 ? 22 : 31;
        s.gui.set(btn, It.of(Material.LIME_DYE).name("&a&l완료").lore("&7클릭하면 상자 메뉴로 돌아갑니다.").build(), e -> later(1, () -> back(p, c)));
        if (!p.isOnline()) return;
        if (c.stopSound != null) Msg.sound(p, c.stopSound, 1, 1);
        Fx.burst(p.getLocation().add(0, 1.2, 0), Particle.TOTEM_OF_UNDYING, s.wins.size() > 1 ? 80 : 40, 0.4);
        if (s.wins.size() == 1) {
            Prize win = s.wins.get(0);
            String prizeName = Items.describeShort(win.display);
            Msg.send(p, "&a[상자] " + c.name + " &f에서 " + prizeName + " &f을(를) 획득했습니다!");
        } else {
            Msg.send(p, "&a[상자] " + c.name + " &f" + s.wins.size() + "회 결과:");
            Map<String, Integer> count = new LinkedHashMap<>();
            for (Prize w : s.wins) count.merge(Items.describeShort(w.display), 1, Integer::sum);
            for (Map.Entry<String, Integer> en : count.entrySet()) Msg.send(p, " &8- &f" + en.getKey() + (en.getValue() > 1 ? " &7(" + en.getValue() + "회)" : ""));
        }
    }

    /* ======================= 상자 잠금 (점검) ======================= */

    /** 잠긴 상자 id(소문자) -> 사유. config.yml crate-locks 에 저장되어 재시작해도 유지 */
    private Map<String, String> locks() {
        Map<String, String> m = new LinkedHashMap<>();
        var sec = plugin.getConfig().getConfigurationSection("crate-locks");
        if (sec != null) for (String k : sec.getKeys(false)) m.put(k.toLowerCase(), sec.getString(k, ""));
        return m;
    }

    public boolean locked(Crate c) {
        return c != null && plugin.getConfig().isSet("crate-locks." + c.id.toLowerCase());
    }

    private String lockReasonText(Crate c) {
        String r = plugin.getConfig().getString("crate-locks." + c.id.toLowerCase(), "");
        return r == null || r.isBlank() ? "" : " &7(사유: " + r + ")";
    }

    private void setLock(Crate c, String reason) {
        plugin.getConfig().set("crate-locks." + c.id.toLowerCase(), reason);
        plugin.saveConfig();
    }

    /* ======================= 명령어 ======================= */

    private void command(CommandSender s, String l, String[] a) {
        String sub = a.length == 0 ? "" : a[0].toLowerCase();
        if (sub.isEmpty() || !s.hasPermission(ADMIN)) {
            if (s instanceof Player p) openMenu(p);
            return;
        }
        switch (sub) {
            case "give" -> {
                // /상자 give <플레이어> <상자> [개수] [physical|virtual]
                if (a.length < 3) {
                    Msg.send(s, "&c/상자 give <플레이어> <상자> [개수] [physical|virtual]");
                    return;
                }
                OfflinePlayer t = Cmd.offline(a[1]);
                Crate c = crate(a[2]);
                if (t == null || c == null) {
                    Msg.send(s, "&c플레이어 또는 상자를 찾을 수 없습니다. &7(상자: " + String.join(", ", ids()) + ")");
                    return;
                }
                int n = a.length >= 4 ? Math.max(1, parseInt(a[3], 1)) : 1;
                boolean physical = a.length >= 5 && a[4].toLowerCase().startsWith("p");
                giveKeys(t, c, n, physical);
                Msg.send(s, "&a" + t.getName() + " 님에게 " + Msg.plainOf(c.name) + " 키 " + n + "개를 지급했습니다. &7(" + (physical ? "실물" : "가상") + ")");
            }
            case "keys" -> {
                OfflinePlayer t = a.length >= 2 ? Cmd.offline(a[1]) : (s instanceof Player p ? p : null);
                if (t == null) return;
                Msg.send(s, "&6" + t.getName() + " 님의 가상 키");
                for (Crate c : crates.values()) Msg.send(s, " &7- &f" + Msg.plainOf(c.name) + " &7(" + c.id + ") : &e" + virtualKeys(t, c));
            }
            case "reload" -> {
                load();
                Msg.send(s, "&a상자 설정을 다시 불러왔습니다. &7(" + crates.size() + "개: " + String.join(", ", ids()) + ")");
            }
            case "migrate" -> migrate(s);
            case "잠금", "lock" -> {
                // /상자 잠금 <상자> [사유]
                Crate c = a.length >= 2 ? crate(a[1]) : null;
                if (c == null) {
                    Msg.send(s, "&c/상자 잠금 <상자> [사유] &7(상자: " + String.join(", ", ids()) + ")");
                    return;
                }
                String reason = a.length >= 3 ? String.join(" ", java.util.Arrays.copyOfRange(a, 2, a.length)) : "";
                setLock(c, reason);
                Msg.send(s, "&a[!] " + Msg.plainOf(c.name) + " &a상자를 잠갔습니다." + (reason.isEmpty() ? "" : " &7(사유: " + reason + ")"));
                Msg.send(s, "&7열기 · 교환권 판매가 멈추고, 플레이어의 키는 그대로 남습니다. 풀려면 /상자 해제 " + c.id);
                plugin.getLogger().info("[상자 잠금] " + s.getName() + " -> " + c.id + (reason.isEmpty() ? "" : " (" + reason + ")"));
            }
            case "해제", "unlock" -> {
                Crate c = a.length >= 2 ? crate(a[1]) : null;
                if (c == null) {
                    Msg.send(s, "&c/상자 해제 <상자> &7(잠긴 상자: " + (locks().isEmpty() ? "없음" : String.join(", ", locks().keySet())) + ")");
                    return;
                }
                if (!locked(c)) {
                    Msg.send(s, "&7" + Msg.plainOf(c.name) + " &7상자는 잠겨 있지 않습니다.");
                    return;
                }
                plugin.getConfig().set("crate-locks." + c.id.toLowerCase(), null);
                plugin.saveConfig();
                Msg.send(s, "&a[!] " + Msg.plainOf(c.name) + " &a상자 잠금을 풀었습니다.");
                plugin.getLogger().info("[상자 잠금 해제] " + s.getName() + " -> " + c.id);
            }
            case "잠금목록", "locks" -> {
                Map<String, String> m = locks();
                if (m.isEmpty()) {
                    Msg.send(s, "&7잠긴 상자가 없습니다.");
                    return;
                }
                Msg.send(s, "&6잠긴 상자 " + m.size() + "개");
                for (var en : m.entrySet()) Msg.send(s, " &7- &f" + en.getKey() + (en.getValue().isBlank() ? "" : " &7(" + en.getValue() + ")"));
            }
            case "open" -> {
                if (!(s instanceof Player p) || a.length < 2 || crate(a[1]) == null) return;
                giveKeys(p, crate(a[1]), crate(a[1]).required, false);
                open(p, crate(a[1]));
            }
            default -> {
                Msg.send(s, "&e/상자 &7- 상자 메뉴");
                Msg.send(s, "&e/상자 give <플레이어> <상자> [개수] [physical|virtual] &7- 키 지급");
                Msg.send(s, "&e/상자 keys [플레이어] &7- 가상 키 확인");
                Msg.send(s, "&e/상자 reload &7- crates 폴더 다시 불러오기");
                Msg.send(s, "&e/상자 migrate &7- CrazyCrates 가상 키 가져오기");
                Msg.send(s, "&e/상자 잠금 <상자> [사유] &7- 점검: 열기 · 교환권 판매 멈춤 (키는 유지)");
                Msg.send(s, "&e/상자 해제 <상자> &7- 잠금 풀기   &e/상자 잠금목록");
                Msg.send(s, "&7상자: " + String.join(", ", ids()));
            }
        }
    }

    private List<String> ids() {
        List<String> out = new ArrayList<>();
        for (Crate c : crates.values()) out.add(c.id);
        return out;
    }

    /** CrazyCrates 가상 키 가져오기 (plugins/CrazyCrates/data.yml 또는 users.yml 의 Players.<uuid>.<상자>: 개수) */
    private void migrate(CommandSender s) {
        File base = new File(plugin.getDataFolder().getParentFile(), "CrazyCrates");
        int players = 0, total = 0;
        for (String fn : new String[]{"data.yml", "users.yml"}) {
            File f = new File(base, fn);
            if (!f.exists()) continue;
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            ConfigurationSection ps = y.getConfigurationSection("Players");
            if (ps == null) continue;
            for (String uid : ps.getKeys(false)) {
                UUID id;
                try {
                    id = UUID.fromString(uid);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                ConfigurationSection sec = ps.getConfigurationSection(uid);
                if (sec == null) continue;
                PlayerData d = plugin.store().get(id);
                boolean any = false;
                for (String k : sec.getKeys(false)) {
                    Crate c = crate(k);
                    if (c == null || !sec.isInt(k)) continue;
                    int n = sec.getInt(k);
                    if (n <= 0) continue;
                    d.crateKeys.merge(c.id, n, Integer::sum);
                    total += n;
                    any = true;
                }
                if (any) {
                    players++;
                    plugin.store().saveAuto(d);
                }
            }
        }
        Msg.send(s, players == 0 ? "&cCrazyCrates 가상 키 데이터를 찾지 못했습니다. &7(plugins/CrazyCrates/data.yml)"
                : "&aCrazyCrates 가상 키를 가져왔습니다. &7(" + players + "명, 키 " + total + "개) &c한 번만 실행하세요.");
    }
}
