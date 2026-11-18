package com.premiumpass.quest;

public enum QuestType {
    BREAK_BLOCK("블록 부수기"),
    PLACE_BLOCK("블록 설치"),
    KILL_MOB("몹 처치"),
    FISH("낚시"),
    CRAFT("제작"),
    SMELT("제련"),
    ENCHANT("마법부여"),
    BREED("번식"),
    CONSUME("섭취"),
    PLAYTIME("접속 시간(분)"),
    COMPLETE_DAILY("일일 퀘스트 완료");

    public final String label;

    QuestType(String label) {
        this.label = label;
    }
}
