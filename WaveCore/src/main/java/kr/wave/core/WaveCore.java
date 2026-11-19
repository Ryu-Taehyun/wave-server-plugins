package kr.wave.core;

import kr.wave.core.data.Store;
import kr.wave.core.module.*;
import kr.wave.core.module.Module;
import kr.wave.core.util.Cmd;
import kr.wave.core.util.Gui;
import kr.wave.core.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/** Wave Online 서버 Skript 25개를 Java 로 옮긴 플러그인 */
public class WaveCore extends JavaPlugin {

    private static WaveCore instance;
    private Store store;
    private Hooks hooks;
    private final List<Module> modules = new ArrayList<>();

    public static WaveCore get() {
        return instance;
    }

    /**
     * private.yml: 공개 저장소에 올리지 않는 값 (IP 잠금, 관리자 닉네임, 문의처, 홍보 링크 등)
     * plugins/WaveCore/private.yml 이 있으면 그 값이 config.yml 보다 우선합니다.
     * 서버의 plugins/WaveCore/private.yml 에 직접 두세요. (jar 에 넣어 빌드하면 jar 를 공유할 때 값이 함께 나가므로 권장하지 않음.
     *  예전 방식대로 jar 안에 들어 있으면 처음 실행 때 꺼내 둡니다.)
     */
    private void applyPrivate() {
        java.io.File f = new java.io.File(getDataFolder(), "private.yml");
        if (!f.exists() && getResource("private.yml") != null) saveResource("private.yml", false);
        if (!f.exists()) return;
        org.bukkit.configuration.file.YamlConfiguration y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
        for (String key : y.getKeys(true)) {
            if (!y.isConfigurationSection(key)) getConfig().set(key, y.get(key));
        }
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        applyPrivate();
        store = new Store(this);
        hooks = new Hooks(this);

        Bukkit.getPluginManager().registerEvents(store, this);
        Bukkit.getPluginManager().registerEvents(new Gui.Listen(), this);

        modules.add(new CoreModule(this));          // maincore.sk, motd.sk, newbieprotect.sk, viewweapon.sk, whisper.sk
        modules.add(new EconomyModule(this));       // shop.sk(캐시), 유료캐시, 도박, /돈확인
        modules.add(new ShopModule(this));          // 상점.sk
        modules.add(new MenuModule(this));          // shop.sk(메뉴/프로필/칭호/공지/신고)
        modules.add(new MailModule(this));          // post.sk
        modules.add(new BackpackModule(this));      // backpack.sk
        modules.add(new LevelModule(this));         // levelsystm.sk
        modules.add(new RankModule(this));          // rank.sk, test.sk
        modules.add(new TeamModule(this));          // team.sk
        modules.add(new MarketModule(this));        // 장터.sk
        modules.add(new DuelModule(this));          // 1v1.sk
        modules.add(new CombatTagModule(this));     // CombatLogX.sk
        modules.add(new NewbieModule(this));        // aboulute.sk
        modules.add(new PackageModule(this));       // package.sk, shop.sk(패키지/룰렛)
        modules.add(new CollectionModule(this));    // gui.sk, sealed.sk(시즌 GUI)
        modules.add(new WeaponModule(this));        // randombox.sk, sealed.sk, 시즌무기.sk, 장터.sk(Soul Splitter)
        modules.add(new InfoModule(this));          // help.sk
        modules.add(new BossModule(this));
        modules.add(new CrateModule(this));
        modules.add(new OverlordModule(this));
        modules.add(new SeasonModule(this));         // 시즌 초기화
        modules.add(new EventModule(this));          // 보급 상자 낙하, 채팅 퀴즈
        modules.add(new TutorialModule(this));      // 튜토리얼      // 오버 로드 (Peerless 이후 휘장 보상)         // 상자(뽑기): CrazyCrates 설정 파일 사용          // 보스 '천공의 심판자' + 주사위 분배

        for (Module m : modules) {
            Bukkit.getPluginManager().registerEvents(m, this);
            try {
                m.enable();
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, m.getClass().getSimpleName() + " 활성화 실패", e);
            }
        }

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Module m : modules) {
                try {
                    m.secondTick();
                } catch (Exception e) {
                    getLogger().log(Level.WARNING, m.getClass().getSimpleName() + " 1초 작업 오류", e);
                }
            }
        }, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, store::saveAll, 6000L, 6000L);

        Cmd.register("wave", "wave.admin", this::adminCommand, "웨이브");
        for (Player p : Bukkit.getOnlinePlayers()) store.get(p);
        getLogger().info("WaveCore 활성화 완료 (모듈 " + modules.size() + "개)");
    }

    @Override
    public void onDisable() {
        for (Module m : modules) {
            try {
                m.disable();
            } catch (Exception e) {   // 한 모듈이 실패해도 나머지 모듈은 계속 종료
                getLogger().log(java.util.logging.Level.WARNING, m.getClass().getSimpleName() + " 종료 중 오류", e);
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            var top = p.getOpenInventory().getTopInventory();
            if (top != null && top.getHolder() instanceof Gui) p.closeInventory();
        }
        if (store != null) store.shutdown();
        kr.wave.core.util.TxLog.shutdown();
    }

    private void adminCommand(org.bukkit.command.CommandSender s, String label, String[] a) {
        String sub = a.length == 0 ? "" : a[0].toLowerCase();
        switch (sub) {
            case "migrate" -> new Migration(this).run(s);
            case "boss" -> module(BossModule.class).admin(s, a);
            case "setwarp" -> {
                if (!(s instanceof Player p) || a.length < 2) {
                    Msg.send(s, "&c/wave setwarp <spawn|protect> &7(게임 안에서, 서 있는 위치로 설정)");
                    return;
                }
                store.global().warps.put(a[1].toLowerCase(), p.getLocation());
                store.saveGlobal();
                Msg.send(s, "&a워프 '" + a[1] + "' 위치를 설정했습니다.");
            }
            case "delwarp" -> {
                if (a.length < 2) return;
                store.global().warps.remove(a[1].toLowerCase());
                store.saveGlobal();
                Msg.send(s, "&a워프 '" + a[1] + "' 를 삭제했습니다. (월드 스폰으로 이동합니다)");
            }
            case "reload" -> {
                reloadConfig();
                applyPrivate();
                Msg.send(s, "&aconfig.yml, private.yml 을 다시 불러왔습니다.");
            }
            case "save" -> {
                store.saveAll();
                Msg.send(s, "&a저장했습니다.");
            }
            default -> {
                Msg.send(s, "&6WaveCore 관리 명령어");
                Msg.send(s, "&e/wave migrate &7- Skript 변수(돈, 레벨, 우편함 등)를 가져옵니다");
                Msg.send(s, "&e/wave setwarp <spawn|protect> &7- /spawn, /보호구역 도착 위치 지정");
                Msg.send(s, "&e/wave delwarp <spawn|protect> &7- 지정 해제 (월드 스폰 사용)");
                Msg.send(s, "&e/wave boss &7- 보스 '천공의 심판자' 관리");
                Msg.send(s, "&e/wave reload &7- config.yml 다시 불러오기");
                Msg.send(s, "&e/wave save &7- 즉시 저장");
            }
        }
    }

    public Store store() {
        return store;
    }

    public Hooks hooks() {
        return hooks;
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T module(Class<T> type) {
        for (Module m : modules) if (type.isInstance(m)) return (T) m;
        return null;
    }
}
