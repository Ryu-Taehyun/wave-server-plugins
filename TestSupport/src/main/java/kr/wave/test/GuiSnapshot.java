package kr.wave.test;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.nio.file.*;

/**
 * 테스트 도우미: 테스트 중 열린 GUI 의 칸 내용(이름 · 설명 · 수량)을 JSON 으로 저장합니다.
 * 저장한 JSON 으로 GUI 미리보기 이미지를 만듭니다.
 * 테스트를 돌릴 때 -Dsnapshot.dir=폴더 를 줄 때만 저장하고, 평소에는 아무 일도 하지 않습니다.
 * 테스트 전용 모듈이라 서버 jar 에는 들어가지 않습니다.
 */
public final class GuiSnapshot {

    private GuiSnapshot() {
    }

    private static final LegacyComponentSerializer L = LegacyComponentSerializer.builder().character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    private static String q(String s) { return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
    private static String c(Component c) { return c == null ? "" : L.serialize(c); }
    /** 지금 열린 GUI 를 file 이름으로 저장. -Dsnapshot.dir 이 없으면 아무것도 하지 않음 */
    public static void save(Player p, String file) {
        String dir = System.getProperty("snapshot.dir");
        if (dir == null || dir.isBlank()) return;
        var view = p.getOpenInventory();
        var inv = view.getTopInventory();
        StringBuilder b = new StringBuilder("{\"title\":" + q(c(view.title())) + ",\"size\":" + inv.getSize() + ",\"slots\":[");
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (i > 0) b.append(',');
            if (it == null || it.getType().isAir()) { b.append("null"); continue; }
            var m = it.getItemMeta();
            String name = m != null && m.hasDisplayName() ? c(m.displayName()) : "";
            b.append("{\"type\":").append(q(it.getType().name())).append(",\"amount\":").append(it.getAmount())
             .append(",\"glint\":").append(m != null && m.hasEnchantmentGlintOverride() ? m.getEnchantmentGlintOverride() : (m != null && m.hasEnchants()))
             .append(",\"ench\":[").append(m == null ? "" : String.join(",", m.getEnchants().entrySet().stream().map(en -> q(en.getKey().getKey().getKey() + " " + en.getValue())).toList()))
             .append("],\"hide\":").append(m != null && m.hasItemFlag(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS))
             .append(",\"name\":").append(q(name)).append(",\"lore\":[");
            var lore = m == null ? null : m.lore();
            if (lore != null) for (int j = 0; j < lore.size(); j++) { if (j > 0) b.append(','); b.append(q(c(lore.get(j)))); }
            b.append("]}");
        }
        b.append("]}");
        try { Files.writeString(Path.of(dir, file), b.toString()); } catch (Exception e) { throw new RuntimeException(e); }
    }

    /** 채팅 · 액션바 글자를 색상 코드(§) 문자열로 (미리보기 이미지용) */
    public static String legacy(Component c) {
        return c(c);
    }

    /** 글자 파일 저장. -Dsnapshot.dir 이 없으면 아무것도 하지 않음 */
    public static void text(String file, String content) {
        String dir = System.getProperty("snapshot.dir");
        if (dir == null || dir.isBlank()) return;
        try {
            Files.writeString(Path.of(dir, file), content);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
