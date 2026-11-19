package com.dailyshop;

import com.dailyshop.command.DailyShopCommand;
import com.dailyshop.gui.ShopMenu;
import kr.wave.common.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class DailyShop extends JavaPlugin {

    private ShopManager shop;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        shop = new ShopManager(this);
        Text.setPrefix(getConfig().getString("prefix", "&e[일일 상점] &f"));
        shop.load();

        Bukkit.getPluginManager().registerEvents(new ShopMenu.Guard(), this);
        PluginCommand cmd = getCommand("dailyshop");
        if (cmd != null) {
            DailyShopCommand exec = new DailyShopCommand(this);
            cmd.setExecutor(exec);
            cmd.setTabCompleter(exec);
        }
        // 1분마다 날짜가 바뀌었는지 확인
        Bukkit.getScheduler().runTaskTimer(this, shop::tick, 1200L, 1200L);
        getLogger().info("일일 상점 활성화 완료");
    }

    @Override
    public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            var top = p.getOpenInventory().getTopInventory();
            if (top != null && top.getHolder() instanceof ShopMenu) p.closeInventory();
        }
        if (shop != null) shop.shutdown();
    }

    private final kr.wave.common.ConfigWarnings warnings = new kr.wave.common.ConfigWarnings(getLogger());

    /** 설정 · 데이터 파일 경고 (기준은 ConfigWarnings 설명 참고) */
    public kr.wave.common.ConfigWarnings warnings() {
        return warnings;
    }

    public void reloadAll() {
        reloadConfig();
        Text.setPrefix(getConfig().getString("prefix", "&e[일일 상점] &f"));
        shop.load();
    }

    public ShopManager shop() {
        return shop;
    }
}
