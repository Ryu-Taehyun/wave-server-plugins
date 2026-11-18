package com.premiumpass.reward;

/** 보상 트랙 */
public enum Track {
    FREE("무료", "free"),
    PREMIUM("프리미엄", "premium");

    public final String label;
    public final String key;

    Track(String label, String key) {
        this.label = label;
        this.key = key;
    }

    public static Track parse(String s) {
        if (s == null) return null;
        return switch (s.toLowerCase()) {
            case "free", "무료", "f" -> FREE;
            case "premium", "프리미엄", "유료", "p" -> PREMIUM;
            default -> null;
        };
    }
}
