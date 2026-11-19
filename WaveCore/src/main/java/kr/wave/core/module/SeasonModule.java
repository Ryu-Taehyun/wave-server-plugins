package kr.wave.core.module;

import kr.wave.core.WaveCore;
import kr.wave.core.data.GlobalData;
import kr.wave.core.data.PlayerData;
import kr.wave.core.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 시즌 초기화 (/시즌)
 * - /시즌 초기화        : 무엇이 얼마나 지워지는지 미리보기 (아무것도 바꾸지 않음)
 * - /시즌 초기화 확인   : 미리보기 후 60초 안에 실행. 자동 백업 후 config.yml season-reset 에서 켠 항목만 초기화
 * - /시즌 정보          : 설정과 마지막 초기화 시각
 */
public class SeasonModule extends Module {

    private static final String TAG = "<gradient:#FFD84D:#FF9F1C><b>[시즌]</b></gradient> ";
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter SHOW_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 미리보기를 본 관리자 -> 확인 마감 시각 */
    private final Map<String, Long> confirmUntil = new HashMap<>();

    public SeasonModule(WaveCore plugin) {
        super(plugin);
    }

    @Override
    public void enable() {
        cmd("시즌", ADMIN, this::command, (s, a) -> a.length <= 1 ? List.of("초기화", "정보")
                : a.length == 2 && a[0].equals("초기화") ? List.of("확인") : List.of(), "season");
    }

    /** 초기화 항목 (config.yml season-reset.<key>) */
    private enum Part {
        MONEY("money", true, "일반 캐시 (보유 · 누적)"),
        EXMONEY("exmoney", false, "유료 캐시 (보유 · 누적 · 랭크 진행)"),
        LEVEL("level", true, "레벨 · 경험치"),
        MARKET("market", true, "장터 매물 (판매자 우편함으로 돌려줌)"),
        COLLECTIONS("collections", false, "컬렉션 수집 · 보상 수령 기록"),
        BACKPACK("backpack", false, "배낭"),
        TEAM_STORAGE("team-storage", false, "팀 창고"),
        BET_LIMIT("bet-limit", true, "오늘 도박 베팅 누적");

        final String key, label;
        final boolean def;

        Part(String key, boolean def, String label) {
            this.key = key;
            this.def = def;
            this.label = label;
        }
    }

    private boolean on(Part p) {
        return plugin.getConfig().getBoolean("season-reset." + p.key, p.def);
    }

    private void command(CommandSender s, String l, String[] a) {
        String sub = a.length == 0 ? "" : a[0];
        switch (sub) {
            case "초기화" -> {
                if (a.length >= 2 && a[1].equals("확인")) run(s);
                else preview(s);
            }
            case "정보" -> info(s);
            default -> {
                Msg.send(s, "&e/시즌 초기화 &7- 무엇이 지워지는지 미리보기");
                Msg.send(s, "&e/시즌 초기화 확인 &7- 미리보기 후 60초 안에 실행 (자동 백업)");
                Msg.send(s, "&e/시즌 정보 &7- 설정과 마지막 초기화 시각");
            }
        }
    }

    private void info(CommandSender s) {
        Msg.send(s, TAG + "&f초기화 설정 &7(config.yml season-reset)");
        for (Part p : Part.values()) Msg.send(s, " " + (on(p) ? "&a● 초기화" : "&8○ 유지") + " &f" + p.label);
        List<String> cmds = plugin.getConfig().getStringList("season-reset.commands");
        if (!cmds.isEmpty()) Msg.send(s, " &7초기화 후 실행: &f" + String.join(" &7/ &f", cmds));
        Msg.send(s, " &7마지막 초기화: &f" + plugin.getConfig().getString("season-reset.last", "없음"));
    }

    /* ---------------- 미리보기 ---------------- */

    private void preview(CommandSender s) {
        long[] money = {0}, ex = {0}, lv = {0}, col = {0}, bp = {0}, players = {0};
        int n = plugin.store().scanAll(d -> {
            players[0]++;
            money[0] += d.money;
            ex[0] += d.exMoney;
            if (d.level > 1 || d.exp > 0) lv[0]++;
            if (!d.collected.isEmpty() || !d.collectionClaimed.isEmpty()) col[0]++;
            for (ItemStack it : d.backpack) if (it != null) {
                bp[0]++;
                break;
            }
        });
        GlobalData g = global();
        int teamItems = 0;
        for (GlobalData.Team t : g.teams.values()) for (ItemStack it : t.storage) if (it != null) teamItems++;
        Msg.send(s, " ");
        Msg.send(s, TAG + "&f시즌 초기화 미리보기 &7(아직 아무것도 바뀌지 않았습니다)");
        Msg.send(s, " &7대상 플레이어 : &f" + n + "명");
        line(s, Part.MONEY, "합계 " + Msg.num(money[0]) + "원");
        line(s, Part.EXMONEY, "합계 " + Msg.num(ex[0]));
        line(s, Part.LEVEL, lv[0] + "명");
        line(s, Part.MARKET, g.market.size() + "개");
        line(s, Part.COLLECTIONS, col[0] + "명");
        line(s, Part.BACKPACK, bp[0] + "명");
        line(s, Part.TEAM_STORAGE, g.teams.size() + "팀, 아이템 " + teamItems + "칸");
        line(s, Part.BET_LIMIT, "전원");
        if (on(Part.EXMONEY)) Msg.send(s, " &c&l⚠ 유료 캐시도 초기화됩니다. 결제한 금액이 사라지므로 꼭 확인하세요.");
        Msg.send(s, " &7실행하면 먼저 &fplugins/WaveCore/backups/ &7에 전체 백업을 만듭니다.");
        Msg.send(s, " &e60초 안에 &f/시즌 초기화 확인 &e을 입력하면 실행합니다.");
        confirmUntil.put(s.getName(), System.currentTimeMillis() + 60_000);
    }

    private void line(CommandSender s, Part p, String what) {
        Msg.send(s, " " + (on(p) ? "&c● 초기화 " : "&8○ 유지   ") + "&f" + p.label + " &7: " + what);
    }

    /* ---------------- 실행 ---------------- */

    private void run(CommandSender s) {
        Long until = confirmUntil.remove(s.getName());
        if (until == null || until < System.currentTimeMillis()) {
            Msg.send(s, "&c먼저 &f/시즌 초기화 &c로 미리보기를 확인한 뒤 60초 안에 입력하세요.");
            return;
        }
        // 열린 창(장터 · 팀 창고 · 배낭 등)을 닫아 초기화 중에 아이템이 오가지 않게
        for (Player p : Bukkit.getOnlinePlayers()) {
            var top = p.getOpenInventory().getTopInventory();
            if (top != null && top.getHolder() instanceof kr.wave.core.util.Gui) p.closeInventory();
        }
        // 1. 백업
        plugin.store().saveAll();
        plugin.store().flush(15);
        File backup;
        try {
            backup = backup();
        } catch (IOException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "시즌 초기화 백업 실패", e);
            Msg.send(s, "&c백업을 만들지 못해 초기화를 멈췄습니다. 서버 콘솔을 확인하세요.");
            return;
        }
        List<String> done = new ArrayList<>();
        // 2. 장터: 판매자 우편함으로 돌려준 뒤 비움
        GlobalData g = global();
        if (on(Part.MARKET)) {
            int n = 0;
            for (GlobalData.Listing li : new ArrayList<>(g.market)) {
                if (li.item != null && li.seller != null) {
                    plugin.module(MailModule.class).send(li.seller, li.item);
                    n++;
                }
            }
            g.market.clear();
            done.add("장터 " + n + "개 반환");
        }
        if (on(Part.TEAM_STORAGE)) {
            for (GlobalData.Team t : g.teams.values()) t.storage = new ItemStack[54];
            done.add("팀 창고");
        }
        plugin.store().saveGlobal();
        // 3. 플레이어 데이터
        int players = plugin.store().forAll(d -> {
            if (on(Part.MONEY)) {
                d.money = 0;
                d.moneyTotal = 0;
            }
            if (on(Part.EXMONEY)) {
                d.exMoney = 0;
                d.exMoneyTotal = 0;
            }
            if (on(Part.LEVEL)) {
                d.level = 1;
                d.exp = 0;
            }
            if (on(Part.COLLECTIONS)) {
                d.collected.clear();
                d.collectionClaimed.clear();
            }
            if (on(Part.BACKPACK)) d.backpack = new ItemStack[27];
            if (on(Part.BET_LIMIT)) {
                d.betDay = "";
                d.betToday = 0;
            }
        });
        for (Part p : Part.values()) if (on(p) && p != Part.MARKET && p != Part.TEAM_STORAGE) done.add(p.label);
        // 4. 추가 명령어 (예: passadmin season next)
        for (String c : plugin.getConfig().getStringList("season-reset.commands")) {
            String line = c.startsWith("/") ? c.substring(1) : c;
            console(line);
            done.add("명령어: " + line);
        }
        String now = LocalDateTime.now().format(SHOW_TIME);
        plugin.getConfig().set("season-reset.last", now);
        plugin.saveConfig();
        String summary = String.join(", ", done);
        log(s.getName() + " | 플레이어 " + players + "명 | " + summary + " | 백업 " + backup.getName());
        plugin.getLogger().info("[시즌 초기화] " + s.getName() + " -> " + summary + " (백업 " + backup.getName() + ")");
        Msg.broadcast(" ");
        Msg.broadcast(TAG + "&e새 시즌이 시작되었습니다! &7(" + now + ")");
        Msg.broadcast(" ");
        for (Player p : Bukkit.getOnlinePlayers()) {
            Msg.title(p, "<gradient:#FFD84D:#FF9F1C><b>새 시즌 시작!</b></gradient>", "&7모두 같은 출발선에서 다시 시작합니다", 10, 60, 20);
            Msg.sound(p, "ui.toast.challenge_complete", 0.8f, 1f);
        }
        Msg.send(s, "&a[!] 시즌 초기화를 마쳤습니다. &7(플레이어 " + players + "명, 백업: backups/" + backup.getName() + ")");
    }

    /** playerdata 폴더 + global.yml + config.yml 을 zip 으로 */
    private File backup() throws IOException {
        File dir = new File(plugin.getDataFolder(), "backups");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("backups 폴더를 만들 수 없습니다");
        File out = new File(dir, "season-" + LocalDateTime.now().format(FILE_TIME) + ".zip");
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(out))) {
            File[] files = plugin.store().folder().listFiles((d, n) -> n.endsWith(".yml"));
            if (files != null) for (File f : files) put(z, f, "playerdata/" + f.getName());
            if (plugin.store().globalFile().exists()) put(z, plugin.store().globalFile(), "global.yml");
            File cfg = new File(plugin.getDataFolder(), "config.yml");
            if (cfg.exists()) put(z, cfg, "config.yml");
        }
        return out;
    }

    private static void put(ZipOutputStream z, File f, String name) throws IOException {
        z.putNextEntry(new ZipEntry(name));
        Files.copy(f.toPath(), z);
        z.closeEntry();
    }

    private void log(String line) {
        File dir = new File(plugin.getDataFolder(), "logs");
        dir.mkdirs();
        try {
            Files.writeString(new File(dir, "season-reset.log").toPath(), LocalDateTime.now().format(SHOW_TIME) + " | " + line + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            plugin.getLogger().warning("시즌 초기화 기록을 남기지 못했습니다: " + e.getMessage());
        }
    }
}
