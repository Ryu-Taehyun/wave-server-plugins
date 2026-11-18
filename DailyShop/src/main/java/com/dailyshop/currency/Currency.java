package com.dailyshop.currency;

import org.bukkit.entity.Player;

/** 결제 수단 */
public interface Currency {

    /** 로그용 이름 */
    String id();

    long balance(Player p);

    /** 잔액이 충분하면 차감하고 true */
    boolean withdraw(Player p, long amount);
}
