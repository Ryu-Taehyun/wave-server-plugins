package com.dailyshop.currency;

import com.premiumpass.api.PremiumPassAPI;
import org.bukkit.entity.Player;

/**
 * PremiumPass 포인트 결제.
 * 이 클래스는 PremiumPass 가 켜져 있을 때만 로드되므로, 없는 서버에서도 오류가 나지 않는다.
 */
public class PremiumPassCurrency implements Currency {

    @Override
    public String id() {
        return "PREMIUMPASS";
    }

    @Override
    public long balance(Player p) {
        return PremiumPassAPI.isAvailable() ? PremiumPassAPI.getPoints(p) : 0;
    }

    @Override
    public boolean withdraw(Player p, long amount) {
        return PremiumPassAPI.isAvailable() && PremiumPassAPI.takePoints(p, amount);
    }

    public static boolean isPremium(Player p) {
        return PremiumPassAPI.isAvailable() && PremiumPassAPI.isPremium(p);
    }
}
