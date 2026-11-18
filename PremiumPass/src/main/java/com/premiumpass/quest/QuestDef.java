package com.premiumpass.quest;

import org.bukkit.Material;

import java.util.List;
import java.util.Locale;

/** quests.yml 에 정의된 퀘스트 하나 */
public class QuestDef {

    public final String id;
    public final String name;
    public final String description;
    public final QuestType type;
    public final List<String> targets;
    public final long amount;
    public final long xp;
    public final long points;
    public final Material icon;

    public QuestDef(String id, String name, String description, QuestType type, List<String> targets,
                    long amount, long xp, long points, Material icon) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.targets = targets.stream().map(s -> s.toUpperCase(Locale.ROOT)).toList();
        this.amount = Math.max(1, amount);
        this.xp = Math.max(0, xp);
        this.points = Math.max(0, points);
        this.icon = icon;
    }

    /** keys 중 하나라도 targets 와 일치하면 true */
    public boolean matches(String... keys) {
        if (targets.isEmpty() || targets.contains("ANY")) return true;
        for (String key : keys) {
            if (key == null) continue;
            String k = key.toUpperCase(Locale.ROOT);
            for (String t : targets) {
                if (match(t, k)) return true;
            }
        }
        return false;
    }

    private static boolean match(String pattern, String key) {
        boolean start = pattern.startsWith("*");
        boolean end = pattern.endsWith("*");
        if (start && end && pattern.length() > 1) return key.contains(pattern.substring(1, pattern.length() - 1));
        if (start) return key.endsWith(pattern.substring(1));
        if (end) return key.startsWith(pattern.substring(0, pattern.length() - 1));
        return key.equals(pattern);
    }
}
