package kr.wave.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skript 문자열(&코드, §코드, %nl%, <gradient> 등 MiniMessage 태그 혼용)을 그대로 쓸 수 있게 변환한다.
 */
public final class Msg {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Pattern LEGACY = Pattern.compile("[&§]([0-9a-fk-orA-FK-OR])");
    /** 태그가 아닌 '<' (예: "<<", "< ") 는 글자로 취급 */
    private static final Pattern LONE_LT = Pattern.compile("<(?![a-zA-Z#!/])");

    private Msg() {
    }

    private static String tag(char c) {
        return switch (Character.toLowerCase(c)) {
            case '0' -> "<reset><black>";
            case '1' -> "<reset><dark_blue>";
            case '2' -> "<reset><dark_green>";
            case '3' -> "<reset><dark_aqua>";
            case '4' -> "<reset><dark_red>";
            case '5' -> "<reset><dark_purple>";
            case '6' -> "<reset><gold>";
            case '7' -> "<reset><gray>";
            case '8' -> "<reset><dark_gray>";
            case '9' -> "<reset><blue>";
            case 'a' -> "<reset><green>";
            case 'b' -> "<reset><aqua>";
            case 'c' -> "<reset><red>";
            case 'd' -> "<reset><light_purple>";
            case 'e' -> "<reset><yellow>";
            case 'f' -> "<reset><white>";
            case 'k' -> "<obf>";
            case 'l' -> "<b>";
            case 'm' -> "<st>";
            case 'n' -> "<u>";
            case 'o' -> "<i>";
            default -> "<reset>";
        };
    }

    public static String toMini(String s) {
        if (s == null) return "";
        s = s.replace("%nl%", "\n");
        s = LONE_LT.matcher(s).replaceAll("\\\\<");
        Matcher m = LEGACY.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(tag(m.group(1).charAt(0))));
        m.appendTail(sb);
        return balance(sb.toString());
    }

    private static final Pattern TAG = Pattern.compile("<(/?)([a-zA-Z_#!][^<>:]*)(?::[^<>]*)?>");

    /** 열린 적 없는(또는 &코드의 <reset> 으로 이미 닫힌) 닫는 태그를 지운다. 예: "&7(0.8x)</color>" */
    static String balance(String s) {
        java.util.List<String> open = new java.util.ArrayList<>();
        Matcher m = TAG.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            // 백슬래시로 이스케이프된 태그는 건드리지 않음
            if (m.start() > 0 && s.charAt(m.start() - 1) == '\\') {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group()));
                continue;
            }
            String name = m.group(2).toLowerCase();
            String keep = m.group();
            if (m.group(1).isEmpty()) {
                if (name.equals("reset")) open.clear();
                else open.add(name);
            } else {
                int idx = open.lastIndexOf(name);
                if (idx < 0) keep = "";
                else open.subList(idx, open.size()).clear();
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(keep));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** 채팅/메시지용 */
    public static Component c(String s) {
        return MM.deserialize(toMini(s));
    }

    /** 아이템 이름/설명용 (기본 기울임 제거) */
    public static Component item(String s) {
        return c(s).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** 여러 문자열을 %nl% 기준으로 쪼개 설명 줄 목록으로 */
    public static List<Component> lore(String... parts) {
        List<Component> out = new ArrayList<>();
        for (String p : parts) {
            if (p == null) continue;
            for (String line : p.replace("%nl%", "\n").split("\n", -1)) out.add(item(line));
        }
        return out;
    }

    /** 기존 설명 뒤에 덧붙이기: 사이 빈 줄은 항상 한 줄만 */
    public static List<Component> append(List<Component> base, String... parts) {
        List<Component> out = base == null ? new ArrayList<>() : new ArrayList<>(base);
        while (!out.isEmpty() && plain(out.get(out.size() - 1)).isBlank()) out.remove(out.size() - 1);
        List<Component> add = lore(parts);
        while (!add.isEmpty() && plain(add.get(0)).isBlank()) add.remove(0);
        if (!out.isEmpty() && !add.isEmpty()) out.add(item(""));
        out.addAll(add);
        return out;
    }

    /** 사용자가 입력한 글자(팀 이름 등)를 태그로 해석하지 않도록 */
    public static String esc(String user) {
        return user == null ? "" : user.replace("<", "\\<");
    }

    public static String plain(Component c) {
        return c == null ? "" : PlainTextComponentSerializer.plainText().serialize(c);
    }

    /** 색상 코드를 뺀 순수 글자 */
    public static String plainOf(String s) {
        return plain(c(s));
    }

    /** 아이템 이름(색상 제외). 이름이 없으면 "" */
    public static String name(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return "";
        ItemMeta meta = it.getItemMeta();
        return meta.hasDisplayName() ? plain(meta.displayName()) : "";
    }

    public static boolean nameIs(ItemStack it, String plain) {
        return name(it).equals(plain);
    }

    public static boolean nameHas(ItemStack it, String plainPart) {
        return name(it).contains(plainPart);
    }

    /* ---------------- 전송 ---------------- */

    public static void send(CommandSender to, String s) {
        if (to != null) to.sendMessage(c(s));
    }

    public static void broadcast(String s) {
        Bukkit.broadcast(c(s));
    }

    /** 메시지 안의 %item% 자리에 넣을 글자 */
    public static final String ITEM = "%item%";

    /** 마우스를 올리면 아이템 정보(이름, 설명, 인챈트)가 보이는 글자 */
    public static Component hoverItem(ItemStack it, String styled) {
        Component text = c(styled);
        if (it == null || it.getType().isAir()) return text;
        try {
            return text.hoverEvent(it.asHoverEvent());
        } catch (Throwable t) {
            ItemMeta meta = it.getItemMeta();
            Component tip = meta != null && meta.hasDisplayName() ? meta.displayName() : Component.text(it.getType().name());
            if (meta != null && meta.lore() != null) for (Component l : meta.lore()) tip = tip.append(Component.newline()).append(l);
            return text.hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(tip));
        }
    }

    /** s 안의 %item% 를 item 으로 바꾼 메시지 */
    public static Component withItem(String s, Component item) {
        return fill(s, ITEM, item);
    }

    /**
     * s 안의 자리표시(%item%, %killer% 등)를 컴포넌트로 바꿈. kv = 자리표시, 컴포넌트, 자리표시, 컴포넌트...
     * 조각마다 따로 만들어 나란히 붙이므로 앞부분의 색/효과(&k 등)가 이름에 번지지 않음
     */
    public static Component fill(String s, Object... kv) {
        int best = -1;
        String tok = null;
        Component comp = null;
        for (int i = 0; i + 1 < kv.length; i += 2) {
            String t = (String) kv[i];
            int at = s.indexOf(t);
            if (at >= 0 && (best < 0 || at < best)) {
                best = at;
                tok = t;
                comp = (Component) kv[i + 1];
            }
        }
        if (best < 0) return c(s);
        return Component.text().append(c(s.substring(0, best))).append(comp).append(fill(s.substring(best + tok.length()), kv)).build();
    }

    public static void broadcastFill(String s, Object... kv) {
        Bukkit.broadcast(fill(s, kv));
    }

    public static void broadcastItem(String s, Component item) {
        Bukkit.broadcast(withItem(s, item));
    }

    public static void actionBar(Player p, String s) {
        p.sendActionBar(c(s));
    }

    /** 시간 단위는 tick */
    public static void title(Player p, String title, String sub, int fadeIn, int stay, int fadeOut) {
        p.showTitle(Title.title(c(title), c(sub), Title.Times.times(
                Duration.ofMillis(fadeIn * 50L), Duration.ofMillis(stay * 50L), Duration.ofMillis(fadeOut * 50L))));
    }

    public static void sound(Player p, String key, float volume, float pitch) {
        p.playSound(p.getLocation(), key, volume, pitch);
    }

    /** 주변 사람도 듣는 소리 */
    public static void soundAt(Location loc, String key, float volume, float pitch) {
        if (loc.getWorld() != null) loc.getWorld().playSound(loc, key, volume, pitch);
    }

    public static String num(long n) {
        return String.format("%,d", n);
    }
}
