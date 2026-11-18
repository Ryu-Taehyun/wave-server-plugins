package com.premiumpass;

import com.premiumpass.command.AdminCommand;
import com.premiumpass.command.PassCommand;
import com.premiumpass.data.DataManager;
import com.premiumpass.gui.Menu;
import com.premiumpass.gui.MenuListener;
import com.premiumpass.pass.PassManager;
import com.premiumpass.pass.PremiumVoucher;
import com.premiumpass.quest.PlacedBlockTracker;
import com.premiumpass.quest.QuestListener;
import com.premiumpass.quest.QuestManager;
import com.premiumpass.reward.RewardManager;
import com.premiumpass.shop.ShopManager;
import com.premiumpass.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class PremiumPass extends JavaPlugin {

    private DataManager dataManager;
    private PassManager passManager;
    private RewardManager rewardManager;
    private ShopManager shopManager;
    private QuestManager questManager;
    private PlacedBlockTracker tracker;
    private PremiumVoucher voucher;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "quests.yml").exists()) saveResource("quests.yml", false);

        dataManager = new DataManager(this);
        passManager = new PassManager(this);
        rewardManager = new RewardManager(this);
        shopManager = new ShopManager(this);
        questManager = new QuestManager(this);
        tracker = new PlacedBlockTracker(this);
        voucher = new PremiumVoucher(this);

        loadSettings();
        com.premiumpass.api.PremiumPassAPI.init(this);

        Bukkit.getPluginManager().registerEvents(dataManager, this);
        Bukkit.getPluginManager().registerEvents(new QuestListener(this), this);
        Bukkit.getPluginManager().registerEvents(new MenuListener(), this);
        Bukkit.getPluginManager().registerEvents(voucher, this);

        command("pass", new PassCommand(this));
        command("passadmin", new AdminCommand(this));

        // 1분마다: 접속 시간 퀘스트 + 일일 초기화 확인
        Bukkit.getScheduler().runTaskTimer(this, questManager::minuteTick, 1200L, 1200L);
        // 자동 저장
        long autosave = Math.max(1, getConfig().getLong("autosave-minutes", 5)) * 1200L;
        Bukkit.getScheduler().runTaskTimer(this, dataManager::saveAll, autosave, autosave);

        // /reload 등으로 이미 접속 중인 플레이어 처리
        for (Player p : Bukkit.getOnlinePlayers()) passManager.ensure(p, dataManager.get(p));

        getLogger().info("프리미엄 패스 활성화 완료 - " + passManager.seasonName());
    }

    @Override
    public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            var top = p.getOpenInventory().getTopInventory();
            if (top != null && top.getHolder() instanceof Menu) p.closeInventory();
        }
        com.premiumpass.api.PremiumPassAPI.init(null);
        if (dataManager != null) dataManager.shutdown();
    }

    private void loadSettings() {
        Text.setPrefix(getConfig().getString("prefix", "&6[프리미엄 패스] &f"));
        passManager.load();
        questManager.load();
        rewardManager.load();
        shopManager.load();
        tracker.clearCache();
    }

    public void reloadAll() {
        reloadConfig();
        loadSettings();
        for (Player p : Bukkit.getOnlinePlayers()) passManager.ensure(p, dataManager.get(p));
    }

    private void command(String name, TabExecutor exec) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().severe("plugin.yml 에 명령어 '" + name + "' 가 없습니다.");
            return;
        }
        cmd.setExecutor(exec);
        cmd.setTabCompleter(exec);
    }

    public DataManager data() {
        return dataManager;
    }

    public PassManager pass() {
        return passManager;
    }

    public RewardManager rewards() {
        return rewardManager;
    }

    public ShopManager shop() {
        return shopManager;
    }

    public QuestManager quests() {
        return questManager;
    }

    public PremiumVoucher voucher() {
        return voucher;
    }

    public PlacedBlockTracker tracker() {
        return tracker;
    }
}
