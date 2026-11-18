package com.premiumpass.reward;

import com.premiumpass.util.Items;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 야생 서버용 임시 기본 보상표.
 * rewards.yml 이 없을 때 한 번만 생성되며, 이후에는 관리자가 자유롭게 수정한다.
 *
 * 무료 트랙: 홀수 레벨 + 5/10레벨 단위에 기본 자원 (짝수 레벨은 비어 있음)
 * 프리미엄 트랙: 모든 레벨에 보상, 10레벨마다 핵심 아이템
 */
public final class DefaultRewards {

    private DefaultRewards() {
    }

    public static Map<Integer, LevelReward> create(Track track, int maxLevel) {
        Map<Integer, LevelReward> map = new TreeMap<>();
        for (int lv = 1; lv <= maxLevel; lv++) {
            List<ItemStack> items = track == Track.FREE ? free(lv, maxLevel) : premium(lv, maxLevel);
            LevelReward r = new LevelReward();
            r.items.addAll(items);
            serverExtras(track, lv, maxLevel, r);
            if (r.isEmpty()) continue;
            map.put(lv, r);
        }
        return map;
    }

    /* ======================= 서버 전용 보상 (WaveCore 명령어) ======================= */

    private static void cash(LevelReward r, long amount) {
        r.commands.add("cashgive %player% " + amount);
        r.commandLore.add("&e" + String.format("%,d", amount) + " 캐시");
    }

    private static void key(LevelReward r, String crate, String label, int n) {
        r.commands.add("상자 give %player% " + crate + " " + n);
        r.commandLore.add(label + " &f키 " + n + "개 &7(계정 지급)");
    }

    private static void altar(LevelReward r, int n) {
        r.commands.add("wave boss ticket %player% " + n);
        r.commandLore.add("&e천공의 제단 이동권 &f" + n + "개");
    }

    /**
     * 서버 상점 물가 기준 (다양한 무기 룰렛 키 22,500 캐시, 천국과 지옥 키 12,000 캐시,
     * 심해 왕궁·파멸의 지배자 키 / 천공의 제단 이동권 / Echoes 교환권 5,000 유료 캐시)
     */
    private static void serverExtras(Track track, int lv, int max, LevelReward r) {
        if (track == Track.FREE) {
            if (lv == max) {
                cash(r, 50_000);
                key(r, "TheEchoesOfNatural", "<gradient:#F5E400:#95FFBE>The Echoes of Natural</gradient>", 1);
            } else if (lv == 25) key(r, "enderpearl", "&c천국과 지옥 룰렛", 1);
            else if (lv == 50) key(r, "combat", "&b다양한 무기 룰렛", 1);
            else if (lv == 75) key(r, "enderpearl", "&c천국과 지옥 룰렛", 2);
            else if (lv % 20 == 10) cash(r, 2_500L * (lv / 10 + 1));   // 10, 30, 50(위), 70, 90 레벨: 5,000 ~ 25,000
            return;
        }
        // 프리미엄 트랙
        if (lv == max) {
            cash(r, 100_000);
            key(r, "TheEchoesOfNatural", "<gradient:#F5E400:#95FFBE>The Echoes of Natural</gradient>", 3);
            altar(r, 3);
            return;
        }
        if (lv % 10 == 0) {
            switch (lv / 10) {
                case 1 -> key(r, "combat", "&b다양한 무기 룰렛", 1);
                case 2 -> key(r, "enderpearl", "&c천국과 지옥 룰렛", 2);
                case 3 -> altar(r, 1);
                case 4 -> key(r, "Sealedp", "<gradient:#3DE8FA:#FFFFFF>Sealed Master Power</gradient>", 1);
                case 5 -> {
                    key(r, "TheEchoesOfNatural", "<gradient:#F5E400:#95FFBE>The Echoes of Natural</gradient>", 1);
                    cash(r, 30_000);
                }
                case 6 -> key(r, "TheRuiner", "<gradient:#FF0000:#530427>The Ruiner's Grudge</gradient>", 1);
                case 7 -> altar(r, 2);
                case 8 -> key(r, "TheEchoesOfNatural", "<gradient:#F5E400:#95FFBE>The Echoes of Natural</gradient>", 2);
                case 9 -> {
                    key(r, "Sealedp", "<gradient:#3DE8FA:#FFFFFF>Sealed Master Power</gradient>", 1);
                    key(r, "TheRuiner", "<gradient:#FF0000:#530427>The Ruiner's Grudge</gradient>", 1);
                }
                default -> {
                }
            }
        } else if (lv % 5 == 0) {
            cash(r, lv < 50 ? 10_000 : 15_000);
        }
    }

    /* ======================= 무료 트랙 ======================= */

    private static List<ItemStack> free(int lv, int max) {
        if (lv == max) {
            return List.of(
                    Items.of(Material.DIAMOND, 8),
                    Items.of(Material.TOTEM_OF_UNDYING, 1),
                    Items.of(Material.GOLDEN_APPLE, 4));
        }
        if (lv == 1) {
            return List.of(Items.of(Material.BREAD, 16), Items.of(Material.TORCH, 16));
        }
        if (lv % 10 == 0) {
            return switch (lv / 10) {
                case 1 -> List.of(Items.of(Material.DIAMOND, 2), Items.of(Material.EXPERIENCE_BOTTLE, 8));
                case 2 -> List.of(Items.of(Material.IRON_BLOCK, 2), Items.of(Material.EXPERIENCE_BOTTLE, 8));
                case 3 -> List.of(Items.of(Material.DIAMOND, 3));
                case 4 -> List.of(Items.of(Material.GOLDEN_APPLE, 3), Items.of(Material.EXPERIENCE_BOTTLE, 16));
                case 5 -> List.of(Items.of(Material.DIAMOND, 4), Items.of(Material.ENDER_PEARL, 8));
                case 6 -> List.of(Items.book(Enchantment.UNBREAKING, 3));
                case 7 -> List.of(Items.of(Material.DIAMOND, 5));
                case 8 -> List.of(Items.of(Material.GOLDEN_APPLE, 5), Items.of(Material.EXPERIENCE_BOTTLE, 16));
                default -> List.of(Items.of(Material.DIAMOND, 6), Items.of(Material.EXPERIENCE_BOTTLE, 24));
            };
        }
        int tier = lv < 34 ? 1 : lv < 67 ? 2 : 3;
        if (lv % 5 == 0) {
            return List.of(Items.of(Material.EXPERIENCE_BOTTLE, 8 * tier));
        }
        if (lv % 2 == 0) return List.of(); // 짝수 레벨은 무료 보상 없음
        return switch ((lv / 2) % 5) {
            case 0 -> List.of(Items.of(Material.BREAD, 8 * tier));
            case 1 -> List.of(Items.of(Material.COAL, 8 * tier));
            case 2 -> List.of(Items.of(Material.TORCH, 16 * tier));
            case 3 -> List.of(Items.of(Material.IRON_INGOT, 4 * tier));
            default -> List.of(Items.of(Material.ARROW, 16 * tier));
        };
    }

    /* ======================= 프리미엄 트랙 ======================= */

    private static List<ItemStack> premium(int lv, int max) {
        if (lv == max) {
            return List.of(
                    Items.of(Material.ELYTRA, 1),
                    Items.of(Material.NETHERITE_INGOT, 2),
                    Items.of(Material.TOTEM_OF_UNDYING, 2),
                    Items.of(Material.ENCHANTED_GOLDEN_APPLE, 2));
        }
        if (lv == 1) {
            return List.of(
                    Items.of(Material.IRON_INGOT, 16),
                    Items.of(Material.GOLDEN_APPLE, 2),
                    Items.of(Material.EXPERIENCE_BOTTLE, 8));
        }
        if (lv % 10 == 0) {
            return switch (lv / 10) {
                case 1 -> List.of(Items.of(Material.DIAMOND, 5), Items.of(Material.EXPERIENCE_BOTTLE, 16));
                case 2 -> List.of(Items.of(Material.SHULKER_BOX, 1), Items.of(Material.DIAMOND, 3));
                case 3 -> List.of(Items.book(Enchantment.MENDING, 1), Items.of(Material.EXPERIENCE_BOTTLE, 32));
                case 4 -> List.of(Items.of(Material.TOTEM_OF_UNDYING, 1), Items.of(Material.GOLDEN_APPLE, 8));
                case 5 -> List.of(Items.of(Material.NETHERITE_SCRAP, 2), Items.of(Material.DIAMOND, 10));
                case 6 -> List.of(Items.book(Enchantment.FORTUNE, 3), Items.of(Material.SHULKER_BOX, 1));
                case 7 -> List.of(Items.of(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1), Items.of(Material.DIAMOND, 8));
                case 8 -> List.of(Items.of(Material.ENCHANTED_GOLDEN_APPLE, 1), Items.of(Material.NETHERITE_SCRAP, 4));
                case 9 -> List.of(Items.of(Material.BEACON, 1), Items.of(Material.EXPERIENCE_BOTTLE, 64));
                default -> List.of(Items.of(Material.DIAMOND, 10), Items.of(Material.NETHERITE_SCRAP, 2));
            };
        }
        if (lv % 5 == 0) {
            return switch ((lv / 5) % 4) {
                case 1 -> List.of(Items.of(Material.DIAMOND, lv < 50 ? 2 : 4), Items.of(Material.GOLDEN_APPLE, lv < 50 ? 2 : 4));
                case 3 -> List.of(Items.book(Enchantment.EFFICIENCY, lv < 50 ? 4 : 5), Items.of(Material.EXPERIENCE_BOTTLE, 16));
                default -> List.of(Items.book(Enchantment.UNBREAKING, 3), Items.of(Material.DIAMOND, lv < 50 ? 2 : 4));
            };
        }

        int idx = lv % 7;
        if (lv < 25) {
            return switch (idx) {
                case 0 -> List.of(Items.of(Material.IRON_INGOT, 16));
                case 1 -> List.of(Items.of(Material.COOKED_BEEF, 16));
                case 2 -> List.of(Items.of(Material.TORCH, 64));
                case 3 -> List.of(Items.of(Material.COAL, 32));
                case 4 -> List.of(Items.of(Material.OAK_LOG, 64));
                case 5 -> List.of(Items.of(Material.ARROW, 32), Items.of(Material.STRING, 8));
                default -> List.of(Items.of(Material.BREAD, 32));
            };
        }
        if (lv < 50) {
            return switch (idx) {
                case 0 -> List.of(Items.of(Material.IRON_INGOT, 24));
                case 1 -> List.of(Items.of(Material.GOLD_INGOT, 12));
                case 2 -> List.of(Items.of(Material.LAPIS_LAZULI, 32));
                case 3 -> List.of(Items.of(Material.REDSTONE, 32));
                case 4 -> List.of(Items.of(Material.EXPERIENCE_BOTTLE, 8));
                case 5 -> List.of(Items.of(Material.GOLDEN_CARROT, 16));
                default -> List.of(Items.of(Material.OBSIDIAN, 10));
            };
        }
        if (lv < 75) {
            return switch (idx) {
                case 0 -> List.of(Items.of(Material.DIAMOND, 1), Items.of(Material.IRON_INGOT, 16));
                case 1 -> List.of(Items.of(Material.EMERALD, 8));
                case 2 -> List.of(Items.of(Material.EXPERIENCE_BOTTLE, 16));
                case 3 -> List.of(Items.of(Material.IRON_BLOCK, 4));
                case 4 -> List.of(Items.of(Material.GOLD_BLOCK, 3));
                case 5 -> List.of(Items.of(Material.GOLDEN_APPLE, 2));
                default -> List.of(Items.of(Material.ENDER_PEARL, 8));
            };
        }
        return switch (idx) {
            case 0 -> List.of(Items.of(Material.DIAMOND, 2));
            case 1 -> List.of(Items.of(Material.IRON_BLOCK, 8));
            case 2 -> List.of(Items.of(Material.EXPERIENCE_BOTTLE, 24));
            case 3 -> List.of(Items.of(Material.EMERALD_BLOCK, 2));
            case 4 -> List.of(Items.of(Material.BLAZE_ROD, 8), Items.of(Material.ENDER_PEARL, 8));
            case 5 -> List.of(Items.of(Material.GOLDEN_APPLE, 3));
            default -> List.of(Items.of(Material.FIREWORK_ROCKET, 32), Items.of(Material.GOLD_BLOCK, 4));
        };
    }
}
