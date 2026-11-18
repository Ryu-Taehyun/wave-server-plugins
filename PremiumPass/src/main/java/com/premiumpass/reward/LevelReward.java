package com.premiumpass.reward;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** 한 레벨의 보상 (아이템 + 콘솔 명령어) */
public class LevelReward {

    public final List<ItemStack> items = new ArrayList<>();
    /** 콘솔로 실행, %player% 치환. 예: "eco give %player% 1000" */
    public final List<String> commands = new ArrayList<>();
    /** 명령어 보상을 GUI 에 보여줄 설명 */
    public final List<String> commandLore = new ArrayList<>();

    public boolean isEmpty() {
        return items.isEmpty() && commands.isEmpty();
    }
}
