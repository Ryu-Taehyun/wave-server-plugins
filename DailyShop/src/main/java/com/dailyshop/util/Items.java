package com.dailyshop.util;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Items {

    private Items() {
    }

    /* ---------------- 생성 ---------------- */

    public static ItemStack of(Material m, int amount) {
        return new ItemStack(m, amount);
    }

    public static ItemStack book(Enchantment ench, int level) {
        ItemStack it = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) it.getItemMeta();
        meta.addStoredEnchant(ench, level, true);
        it.setItemMeta(meta);
        return it;
    }

    /** GUI 용 아이콘 */
    public static ItemStack icon(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m == null || m.isAir() ? Material.PAPER : m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(Text.color(name));
        if (lore != null) meta.setLore(Text.color(lore));
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    public static ItemStack icon(Material m, String name, String... lore) {
        return icon(m, name, Arrays.asList(lore));
    }

    public static ItemStack glow(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setEnchantmentGlintOverride(true);
            it.setItemMeta(meta);
        }
        return it;
    }

    public static ItemStack filler() {
        return icon(Material.BLACK_STAINED_GLASS_PANE, " ");
    }

    /* ---------------- 지급 ---------------- */

    /** 인벤토리에 넣고, 남는 아이템은 발밑에 떨어뜨린다. @return 떨어진 아이템이 있었는지 */
    public static boolean give(Player p, Collection<ItemStack> items) {
        boolean dropped = false;
        for (ItemStack it : items) {
            if (it == null || it.getType().isAir()) continue;
            Map<Integer, ItemStack> left = p.getInventory().addItem(it.clone());
            for (ItemStack l : left.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), l);
                dropped = true;
            }
        }
        return dropped;
    }

    /* ---------------- 이름 표시 ---------------- */

    public static String describe(ItemStack it) {
        String name;
        ItemMeta meta = it.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            name = meta.getDisplayName();
        } else {
            name = koreanName(it.getType());
        }
        if (meta instanceof EnchantmentStorageMeta esm && esm.hasStoredEnchants()) {
            StringBuilder sb = new StringBuilder();
            esm.getStoredEnchants().forEach((e, lv) -> {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(enchantName(e)).append(' ').append(roman(lv));
            });
            name = name + " (" + sb + ")";
        }
        return name + " x" + it.getAmount();
    }

    public static String koreanName(Material m) {
        String k = KOREAN.get(m);
        if (k != null) return k;
        String[] parts = m.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    public static String enchantName(Enchantment e) {
        String key = e.getKey().getKey();
        return ENCHANT_KOREAN.getOrDefault(key, key);
    }

    public static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }

    private static final Map<Material, String> KOREAN = new EnumMap<>(Material.class);
    private static final Map<String, String> ENCHANT_KOREAN = new HashMap<>();

    static {
        KOREAN.put(Material.DIAMOND, "다이아몬드");
        KOREAN.put(Material.DIAMOND_BLOCK, "다이아몬드 블록");
        KOREAN.put(Material.EMERALD, "에메랄드");
        KOREAN.put(Material.EMERALD_BLOCK, "에메랄드 블록");
        KOREAN.put(Material.IRON_INGOT, "철 주괴");
        KOREAN.put(Material.IRON_BLOCK, "철 블록");
        KOREAN.put(Material.GOLD_INGOT, "금 주괴");
        KOREAN.put(Material.GOLD_BLOCK, "금 블록");
        KOREAN.put(Material.COAL, "석탄");
        KOREAN.put(Material.LAPIS_LAZULI, "청금석");
        KOREAN.put(Material.REDSTONE, "레드스톤 가루");
        KOREAN.put(Material.NETHERITE_SCRAP, "네더라이트 조각");
        KOREAN.put(Material.NETHERITE_INGOT, "네더라이트 주괴");
        KOREAN.put(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, "네더라이트 강화 대장장이 형판");
        KOREAN.put(Material.ANCIENT_DEBRIS, "고대 잔해");
        KOREAN.put(Material.EXPERIENCE_BOTTLE, "경험치 병");
        KOREAN.put(Material.GOLDEN_APPLE, "황금 사과");
        KOREAN.put(Material.ENCHANTED_GOLDEN_APPLE, "마법이 부여된 황금 사과");
        KOREAN.put(Material.GOLDEN_CARROT, "황금 당근");
        KOREAN.put(Material.COOKED_BEEF, "스테이크");
        KOREAN.put(Material.BREAD, "빵");
        KOREAN.put(Material.TORCH, "횃불");
        KOREAN.put(Material.OAK_LOG, "참나무 원목");
        KOREAN.put(Material.ARROW, "화살");
        KOREAN.put(Material.OBSIDIAN, "흑요석");
        KOREAN.put(Material.ENDER_PEARL, "엔더 진주");
        KOREAN.put(Material.BLAZE_ROD, "블레이즈 막대");
        KOREAN.put(Material.SLIME_BALL, "슬라임볼");
        KOREAN.put(Material.SHULKER_BOX, "셜커 상자");
        KOREAN.put(Material.TOTEM_OF_UNDYING, "불사의 토템");
        KOREAN.put(Material.ELYTRA, "겉날개");
        KOREAN.put(Material.BEACON, "신호기");
        KOREAN.put(Material.FIREWORK_ROCKET, "폭죽 로켓");
        KOREAN.put(Material.ENCHANTED_BOOK, "마법이 부여된 책");
        KOREAN.put(Material.SADDLE, "안장");
        KOREAN.put(Material.NAME_TAG, "이름표");
        KOREAN.put(Material.BOOKSHELF, "책장");
        KOREAN.put(Material.GUNPOWDER, "화약");
        KOREAN.put(Material.STRING, "실");
        KOREAN.put(Material.LEATHER, "가죽");
        KOREAN.put(Material.SPYGLASS, "망원경");
        KOREAN.put(Material.TRIDENT, "삼지창");
        KOREAN.put(Material.HEART_OF_THE_SEA, "바다의 심장");
        KOREAN.put(Material.SHULKER_SHELL, "셜커 껍데기");
        KOREAN.put(Material.SPONGE, "스펀지");
        KOREAN.put(Material.CHEST, "상자");

        ENCHANT_KOREAN.put("mending", "수선");
        ENCHANT_KOREAN.put("unbreaking", "내구성");
        ENCHANT_KOREAN.put("efficiency", "효율");
        ENCHANT_KOREAN.put("fortune", "행운");
        ENCHANT_KOREAN.put("silk_touch", "섬세한 손길");
        ENCHANT_KOREAN.put("protection", "보호");
        ENCHANT_KOREAN.put("sharpness", "날카로움");
        ENCHANT_KOREAN.put("looting", "약탈");
        ENCHANT_KOREAN.put("feather_falling", "가벼운 착지");
        ENCHANT_KOREAN.put("power", "힘");
        ENCHANT_KOREAN.put("infinity", "무한");
        ENCHANT_KOREAN.put("depth_strider", "물갈퀴");
        ENCHANT_KOREAN.put("respiration", "호흡");
        ENCHANT_KOREAN.put("aqua_affinity", "친수성");
        ENCHANT_KOREAN.put("sweeping_edge", "휩쓸기");
        ENCHANT_KOREAN.put("fire_aspect", "발화");
        ENCHANT_KOREAN.put("thorns", "가시");
        ENCHANT_KOREAN.put("swift_sneak", "신속한 잠행");
        ENCHANT_KOREAN.put("soul_speed", "영혼 가속");
    }
}
