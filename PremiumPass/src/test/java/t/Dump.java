package t;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.nio.file.*;

final class Dump {
    static final LegacyComponentSerializer L = LegacyComponentSerializer.builder().character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    static String q(String s) { return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
    static String c(Component c) { return c == null ? "" : L.serialize(c); }
    static void inv(Player p, String file) {
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
        try { Files.writeString(Path.of(System.getProperty("dumpdir", "/tmp"), file), b.toString()); } catch (Exception e) { throw new RuntimeException(e); }
    }
}
