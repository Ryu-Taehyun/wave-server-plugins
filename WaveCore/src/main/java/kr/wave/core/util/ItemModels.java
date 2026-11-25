package kr.wave.core.util;

import kr.wave.core.WaveCore;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 리소스팩(Wave 특별 아이템) 텍스쳐 연결.
 * 아이템 이름에 아래 글자가 들어 있으면 item_model 을 wave:<키> 로 지정합니다.
 * 리소스팩이 없는 사람에게는 바닐라 모양 그대로 보입니다. config resource-pack.item-models: false 면 끔.
 * (위에서부터 먼저 맞는 것 하나만. 더 긴 · 구체적인 이름을 위에)
 */
public final class ItemModels {

    private static final Map<String, String> BY_NAME = new LinkedHashMap<>();

    static {
        // 무기
        BY_NAME.put("[Lv.sd] 천공의 심판자 클레이모어", "claymore_sd");
        BY_NAME.put("천공의 심판자 클레이모어", "claymore_js");
        BY_NAME.put("천공의 심판자 헬멧", "judge_helmet");
        BY_NAME.put("프로스트 그레이브", "frost_grave");
        BY_NAME.put("얼어붙은 제왕의 검", "sealed_sword");
        BY_NAME.put("얼어붙은 제왕의 날개", "sealed_wings");
        BY_NAME.put("앱솔루트 퀀텀", "quantum_sword");
        BY_NAME.put("오로라 퀸트라늄", "aurora_boots");
        BY_NAME.put("잿불 낙인검", "ember_brand");
        BY_NAME.put("망령 수확자", "soul_splitter");
        BY_NAME.put("Soul Splitter", "soul_splitter");
        BY_NAME.put("크로노스의 신발", "chronos_boots");
        BY_NAME.put("크로노스의 칼날", "chronos");
        BY_NAME.put("이터널 크로노스", "chronos");
        BY_NAME.put("천둥의 심판", "thunder_axe");
        BY_NAME.put("파괴의 낫", "ruiner_scythe");
        BY_NAME.put("재앙의 서막 바지", "ruiner_pants");
        BY_NAME.put("혼돈의 아포칼립스", "chaos_apocalypse");
        BY_NAME.put("종말의 투구", "chaos_helmet");
        BY_NAME.put("다크니스 웨이브", "darkness_wave");
        BY_NAME.put("아리스토텔레스의 이데아", "aristotle_pickaxe");
        BY_NAME.put("러셀의 패러독스", "russell_helmet");
        BY_NAME.put("공허의 속삭임", "void_bow");
        BY_NAME.put("뒤틀린 차원의 갑옷", "void_chest");
        BY_NAME.put("시간을 가르는 바람", "time_piecer_wind");
        BY_NAME.put("메아리 종", "echo_bell");
        BY_NAME.put("그림자 사슬", "umbral_chain");
        BY_NAME.put("초승달의 서약", "new_characteristic");
        BY_NAME.put("신월의 기록", "new_characteristic");
        BY_NAME.put("새 시대의 각인", "new_characteristic");
        BY_NAME.put("대자연의 황혼", "nature_boots");
        // 교환권 · 이동권 · 재료 · 키 · 표식
        BY_NAME.put("Legendary 무기 교환권", "legend_voucher");
        BY_NAME.put("전설의 무기 교환권", "mythic_voucher");
        BY_NAME.put("천공의 제단 이동권", "altar_ticket");
        BY_NAME.put("시간의 파편", "time_shard");
        BY_NAME.put("양자 물질", "quantum_matter");
        BY_NAME.put("비브라늄", "vibranium");
        BY_NAME.put("다양한 무기 룰렛 키", "weapon_key");
        BY_NAME.put("천국과 지옥 룰렛 키", "heaven_key");
        BY_NAME.put("심해 왕궁 키", "sealed_key");
        BY_NAME.put("파멸의 지배자 키", "ruiner_key");
        BY_NAME.put("크라운 처치 알림 표식", "crown_mark");
    }

    private ItemModels() {
    }

    /** 이름에 맞는 모델 키 (없으면 null) */
    public static String keyFor(String plainName) {
        if (plainName == null || plainName.isEmpty()) return null;
        for (Map.Entry<String, String> e : BY_NAME.entrySet()) if (plainName.contains(e.getKey())) return e.getValue();
        return null;
    }

    public static java.util.Collection<String> keys() {
        return new java.util.LinkedHashSet<>(BY_NAME.values());
    }

    private static boolean enabled() {
        WaveCore w = WaveCore.get();
        return w == null || w.getConfig().getBoolean("resource-pack.item-models", true);
    }

    /** 특별 아이템이면 리소스팩 모델 지정. 바꿨으면 true */
    public static boolean apply(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta() || !enabled()) return false;
        ItemMeta meta = it.getItemMeta();
        String key = keyFor(Msg.name(it));
        if (key == null) return false;
        NamespacedKey want = new NamespacedKey("wave", key);
        if (meta.hasItemModel() && want.equals(meta.getItemModel())) return false;
        meta.setItemModel(want);
        it.setItemMeta(meta);
        return true;
    }

    /** 여러 칸 (셜커 상자 안까지) */
    public static void applyAll(ItemStack[] items) {
        if (items == null) return;
        for (ItemStack it : items) {
            if (it == null || it.getType().isAir()) continue;
            apply(it);
            if (it.getItemMeta() instanceof org.bukkit.inventory.meta.BlockStateMeta bsm && bsm.getBlockState() instanceof org.bukkit.block.ShulkerBox box) {
                ItemStack[] in = box.getInventory().getContents();
                boolean changed = false;
                for (ItemStack x : in) changed |= apply(x);
                if (changed) {
                    box.getInventory().setContents(in);
                    bsm.setBlockState(box);
                    it.setItemMeta(bsm);
                }
            }
        }
    }
}
