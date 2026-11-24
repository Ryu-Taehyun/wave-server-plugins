package kr.wave.core.module;

import kr.wave.core.WaveCore;
import kr.wave.core.data.GlobalData;
import kr.wave.core.data.PlayerData;
import kr.wave.core.util.Cmd;
import kr.wave.core.util.Discord;
import kr.wave.core.util.Dur;
import kr.wave.core.util.Gui;
import kr.wave.core.util.It;
import kr.wave.core.util.Msg;
import kr.wave.core.util.TxLog;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 플레이어 정보
 * - /정보 [플레이어]  누구나: 레벨 · 랭크 · 팀 · 컬렉션 진행 · 접속 상태 (채팅에서 이름을 클릭하면 열림)
 * - /조회 <플레이어>  관리자: 위 정보 + 캐시 · 유료 캐시 · 제재 기록 · 최근 유료 캐시 기록 · 우편함 · 배낭
 * - /환불 <플레이어> <금액> <사유>  유료 캐시를 돌려줌 (누적 충전액은 그대로)
 */
public class ProfileModule extends Module {

    public ProfileModule(WaveCore plugin) {
        super(plugin);
    }

    @Override
    public void enable() {
        cmd("정보", null, this::infoCmd, (s, a) -> a.length <= 1 ? null : List.of(), "profile", "프로필");
        cmd("조회", ADMIN, this::lookupCmd, (s, a) -> a.length <= 1 ? null : List.of(), "lookup");
        cmd("환불", ADMIN, this::refundCmd, (s, a) -> a.length <= 1 ? null : a.length == 2 ? List.of("<금액>") : List.of("<사유>"), "refund");
    }

    /** 가장 높은 랭크 */
    public static String rankLabel(PlayerData d) {
        if (d.ranks.contains("peerless")) return "&4[💎] &cPeerless";
        if (d.ranks.contains("exceed")) return "&a[💎] &6Exceed";
        if (d.ranks.contains("supreme")) return "&a[💎] &bSupreme";
        if (d.ranks.contains("ace")) return "&eAce";
        return "&7유저";
    }

    private String teamLine(OfflinePlayer t) {
        GlobalData.Team team = global().teamOf(t.getUniqueId());
        return team == null ? "&8없음" : "&f" + Msg.esc(team.display) + " &8(" + Msg.plainOf(TeamModule.roleTag(team.members.get(t.getUniqueId()))) + ")";
    }

    private String titleLine(OfflinePlayer t, PlayerData d) {
        if (t.getPlayer() != null) {
            String pre = plugin.hooks().prefix(t.getPlayer());
            if (pre != null && !pre.isBlank()) return pre;
        }
        if (d.activeTitle == null) return "&8없음";
        String rank = RankingModule.titleText(d.activeTitle);
        return rank != null ? rank : "&f" + d.activeTitle;
    }

    private String seenLine(OfflinePlayer t, PlayerData d) {
        if (t.isOnline()) return "&a● 접속 중";
        return d.lastSeen > 0 ? "&7● " + Dur.when(d.lastSeen) + " 접속" : "&8● 기록 없음";
    }

    /* ======================= /정보 ======================= */

    private void infoCmd(CommandSender s, String l, String[] a) {
        if (!Cmd.isPlayer(s)) return;
        Player p = (Player) s;
        OfflinePlayer t = a.length >= 1 ? Cmd.offline(a[0]) : p;
        if (t == null || (!t.isOnline() && !t.hasPlayedBefore())) {
            Msg.send(p, "&c플레이어를 찾을 수 없습니다.");
            return;
        }
        open(p, t);
    }

    public void open(Player viewer, OfflinePlayer t) {
        PlayerData d = plugin.store().get(t);
        String name = t.getName() == null ? d.name : t.getName();
        Gui g = new Gui(4, "&8플레이어 정보 : " + name);
        g.set(4, It.of(Material.PLAYER_HEAD).skull(t).name(nickName(d.nickColor, name, "&e"))
                .lore(seenLine(t, d), "&7랭크 : " + rankLabel(d)).build());
        g.set(20, It.of(Material.EXPERIENCE_BOTTLE).name("&a&l레벨").lore("&7레벨 : &e" + d.level, "&7경험치 : &f" + Msg.num(d.exp)).build());
        g.set(21, It.of(Material.DIAMOND).name("&b&l랭크").lore("&7현재 랭크 : " + rankLabel(d)).build());
        g.set(22, It.of(Material.WHITE_BANNER).name("&c&l팀").lore("&7소속 : " + teamLine(t)).build());
        CollectionModule cm = plugin.module(CollectionModule.class);
        boolean canSee = d.collectionPublic || viewer.getUniqueId().equals(t.getUniqueId()) || viewer.hasPermission(ADMIN);
        if (canSee) {
            g.set(23, It.of(Material.NETHER_STAR).name("&d&l컬렉션").lore("&7컬렉션 진행 현황을 봅니다.", "", "&e클릭하여 자세히 보기").build(),
                    e -> cm.view(viewer, d, name, () -> open(viewer, t)));
        } else {
            g.set(23, It.of(Material.GRAY_DYE).name("&7&l컬렉션 &8(비공개)").lore("&7이 플레이어는 컬렉션 진행을", "&7공개하지 않았습니다.").build());
        }
        g.set(24, It.of(Material.DIAMOND_CHESTPLATE).hide().name("&f&l장비 보기").lore("&7착용 중인 장비를 봅니다.", "", "&e클릭하여 보기").build(), e -> {
            viewer.closeInventory();
            if (t.isOnline()) viewer.performCommand("stats " + name);
            else Msg.send(viewer, "&c접속 중인 플레이어만 장비를 볼 수 있습니다.");
        });
        if (!viewer.getUniqueId().equals(t.getUniqueId())) {
            g.set(31, It.of(Material.WRITABLE_BOOK).name("&e&l귓속말 보내기").lore("&7채팅창에 /귓 " + name + " 을 넣어 줍니다.", "", "&e클릭").build(), e -> {
                viewer.closeInventory();
                viewer.sendMessage(Msg.c("&6[📬] &e여기를 클릭하면 " + name + "님에게 귓속말을 씁니다.")
                        .clickEvent(ClickEvent.suggestCommand("/귓 " + name + " "))
                        .hoverEvent(HoverEvent.showText(Msg.c("&a클릭하여 /귓 " + name + " 입력"))));
            });
        }
        if (viewer.hasPermission(ADMIN)) g.set(35, It.of(Material.COMMAND_BLOCK).name("&c&l관리자 조회").lore("&7/조회 " + name, "", "&e클릭").build(), e -> lookup(viewer, t));
        g.fill().open(viewer);
    }

    /* ======================= /조회 ======================= */

    private void lookupCmd(CommandSender s, String l, String[] a) {
        OfflinePlayer t = a.length >= 1 ? Cmd.offline(a[0]) : null;
        if (t == null) {
            Msg.send(s, "&c/" + l + " <플레이어>");
            return;
        }
        if (s instanceof Player p) {
            lookup(p, t);
            return;
        }
        for (String line : summary(t)) Msg.send(s, line);
    }

    /** 콘솔 · 로그용 한눈 요약 */
    public List<String> summary(OfflinePlayer t) {
        PlayerData d = plugin.store().get(t);
        GlobalData.Ban ban = global().bans.get(t.getUniqueId());
        List<String> out = new ArrayList<>();
        out.add("&6===== " + t.getName() + " (" + t.getUniqueId() + ") =====");
        out.add("&7상태 : " + seenLine(t, d) + " &7· 랭크 " + rankLabel(d) + " &7· 레벨 &f" + d.level);
        out.add("&7캐시 : &f" + Msg.num(d.money) + "원 &7· 유료 캐시 : &6" + Msg.num(d.exMoney) + "원 &8(누적 " + Msg.num(d.exMoneyTotal) + ")");
        out.add("&7팀 : " + teamLine(t) + " &7· 출석 &f" + d.attendStreak + "일 연속 / 누적 " + d.attendTotal + "일");
        AltModule alt = plugin.module(AltModule.class);
        if (alt != null) out.add("&7같은 곳 계정 : " + alt.summary(t.getUniqueId()));
        out.add("&7경고 &c" + d.warnings + "회 &7· 채팅 금지 " + (d.muteUntil > System.currentTimeMillis() ? "&c" + Dur.left(d.muteUntil) + " 남음" : "&a없음")
                + " &7· 차단 " + (ban == null ? "&a없음" : "&c" + (ban.permanent() ? "영구" : Dur.left(ban.until) + " 남음")));
        return out;
    }

    public void lookup(Player viewer, OfflinePlayer t) {
        PlayerData d = plugin.store().get(t);
        String name = t.getName() == null ? d.name : t.getName();
        GlobalData.Ban ban = global().bans.get(t.getUniqueId());
        Gui g = new Gui(6, "&8관리자 조회 : " + name);
        g.set(4, It.of(Material.PLAYER_HEAD).skull(t).name(nickName(d.nickColor, name, "&e"))
                .lore(seenLine(t, d), "&8" + t.getUniqueId(), "&7랭크 : " + rankLabel(d), "&7레벨 : &e" + d.level).build());
        g.set(10, It.of(Material.GOLD_INGOT).name("&e&l캐시").lore("&7보유 : &f" + Msg.num(d.money) + "원", "&7누적 획득 : &f" + Msg.num(d.moneyTotal) + "원",
                "&7오늘 베팅 : &f" + Msg.num(d.betToday) + "원").build());
        g.set(11, It.of(Material.EMERALD).name("&6&l유료 캐시").lore("&7보유 : &6" + Msg.num(d.exMoney) + "원", "&7누적 충전 : &6" + Msg.num(d.exMoneyTotal) + "원").build());
        g.set(12, It.of(Material.NAME_TAG).name("&e&l칭호 · 팀").lore("&7칭호 : " + titleLine(t, d), "&7팀 : " + teamLine(t)).build());
        g.set(13, It.of(Material.CLOCK).name("&6&l출석").lore("&7연속 : &f" + d.attendStreak + "일", "&7누적 : &f" + d.attendTotal + "일",
                "&7마지막 출석 : &f" + (d.attendDay.isEmpty() ? "-" : d.attendDay)).build());
        g.set(14, It.of(Material.CHEST).name("&f&l우편함 · 배낭").lore("&7우편함 : &f" + d.mailbox.size() + "개",
                "&7배낭 : &f" + java.util.Arrays.stream(d.backpack).filter(x -> !It.isAir(x)).count() + "칸 사용 (" + d.backpackRows + "줄)",
                "&7시즌 반환 대기 : &f" + d.seasonReturn.size() + "개").build());
        g.set(15, It.of(ban != null ? Material.BARRIER : Material.LIME_DYE).name(ban != null ? "&c&l차단됨" : "&a&l차단 없음")
                .lore(ban == null ? new String[]{"&7정상"} : new String[]{"&7사유 : &f" + Msg.esc(ban.reason), "&7기간 : &f" + (ban.permanent() ? "영구" : Dur.when(ban.until) + "까지"),
                        "&7코드 : &f#" + ban.code, "&7처리자 : &f" + (ban.by.isEmpty() ? "-" : ban.by)}).build());
        AltModule alt = plugin.module(AltModule.class);
        if (alt != null) g.set(32, It.of(Material.SPYGLASS).name("&6&l같은 곳에서 접속한 계정").lore("&7" + alt.summary(t.getUniqueId()), "",
                "&8빨간 이름 = 차단 중인 계정", "&8가족 · 같은 PC방일 수도 있으니 참고만", "&7/부계정 " + name).build());
        g.set(16, It.of(d.muteUntil > System.currentTimeMillis() ? Material.RED_DYE : Material.GRAY_DYE).name("&c&l경고 · 채팅 금지")
                .lore("&7누적 경고 : &c" + d.warnings + "회", "&7채팅 금지 : " + (d.muteUntil > System.currentTimeMillis()
                        ? "&c" + Dur.left(d.muteUntil) + " 남음 &8(" + Msg.esc(d.muteReason) + ")" : "&a없음")).build());

        List<String> sanc = new ArrayList<>();
        for (int i = d.sanctions.size() - 1; i >= 0 && sanc.size() < 10; i--) sanc.add("&7" + Msg.esc(d.sanctions.get(i)));
        if (sanc.isEmpty()) sanc.add("&8기록 없음");
        g.set(28, It.of(Material.IRON_AXE).hide().name("&c&l제재 기록 &7(최근 10건)").lore(sanc.toArray(String[]::new)).build());
        List<String> paid = recentPaid(t, 7, 10);
        if (paid.isEmpty()) paid.add("&8최근 7일 기록 없음");
        g.set(30, It.of(Material.PAPER).name("&6&l유료 캐시 기록 &7(최근 7일)").lore(paid.toArray(String[]::new)).build());

        String[][] acts = {
                {"YELLOW_DYE", "&e&l경고 주기", "/경고 " + name + " "},
                {"ORANGE_DYE", "&6&l채팅 금지", "/뮤트 " + name + " 1h "},
                {"RED_DYE", "&c&l임시 차단", "/임시밴 " + name + " 1d "},
                {"EMERALD", "&a&l환불", "/환불 " + name + " "},
                {"LIME_DYE", "&a&l제재 풀기", "/뮤트해제 " + name},
        };
        int[] slots = {37, 38, 39, 41, 42};
        for (int i = 0; i < acts.length; i++) {
            String[] x = acts[i];
            g.set(slots[i], It.of(Material.valueOf(x[0])).name(x[1]).lore("&7" + x[2].trim(), "", "&e클릭하면 채팅창에 명령어를 넣어 줍니다.").build(), e -> {
                viewer.closeInventory();
                viewer.sendMessage(Msg.c("&c[조회] &e여기를 클릭하여 입력 : &f" + x[2]).clickEvent(ClickEvent.suggestCommand(x[2]))
                        .hoverEvent(HoverEvent.showText(Msg.c("&a클릭하여 입력"))));
            });
        }
        g.set(43, It.of(Material.PLAYER_HEAD).skull(t).name("&f&l플레이어 정보 창").lore("&7/정보 " + name, "", "&e클릭").build(), e -> open(viewer, t));
        g.fill().nav(viewer, null).open(viewer);
    }

    /** logs/paid-cash-날짜.log 에서 이 플레이어 줄만 (최근 days 일, 최신순) */
    public List<String> recentPaid(OfflinePlayer t, int days, int max) {
        List<String> out = new ArrayList<>();
        File dir = new File(plugin.getDataFolder(), "logs");
        String id = t.getUniqueId().toString();
        for (int i = 0; i < days && out.size() < max; i++) {
            File f = new File(dir, "paid-cash-" + LocalDate.now().minusDays(i) + ".log");
            if (!f.isFile()) continue;
            try {
                List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
                for (int j = lines.size() - 1; j >= 0 && out.size() < max; j--) {
                    String line = lines.get(j);
                    if (!line.contains(id)) continue;
                    String[] p = line.split(" \\| ");
                    // 시각 | 종류 | 이름(uuid) | 변화 | 잔액 | 내용
                    out.add(p.length >= 6 ? "&7" + p[0].substring(5, 16) + " &f" + p[1] + " &e" + p[3] + " &8→ " + p[4] : "&7" + Msg.esc(line));
                }
            } catch (IOException e) {
                out.add("&c" + f.getName() + " 을 읽지 못했습니다.");
            }
        }
        return out;
    }

    /* ======================= /환불 ======================= */

    private void refundCmd(CommandSender s, String l, String[] a) {
        if (a.length < 3) {
            Msg.send(s, "&c/" + l + " <플레이어> <금액> <사유> &7(유료 캐시를 돌려줍니다. 누적 충전액은 오르지 않습니다)");
            return;
        }
        OfflinePlayer t = Cmd.offline(a[0]);
        Long amount = Cmd.num(a[1]);
        if (t == null || amount == null || amount <= 0) {
            Msg.send(s, "&c플레이어 또는 금액이 올바르지 않습니다.");
            return;
        }
        String reason = Cmd.rest(a, 2);
        PlayerData d = plugin.store().get(t);
        d.exMoney += amount;
        plugin.store().saveAuto(d);
        TxLog.paid("환불", String.valueOf(t.getName()), t.getUniqueId(), amount, d.exMoney, "처리자 " + s.getName() + " · " + reason);
        Discord.send("refund", "💸 유료 캐시 환불", "**" + t.getName() + "**", Discord.BLUE,
                "금액", Msg.num(amount) + "원", "잔액", Msg.num(d.exMoney) + "원", "사유", reason, "처리자", s.getName());
        Msg.send(s, "§a[💰] &6" + t.getName() + "님에게 유료 캐시 " + Msg.num(amount) + "원을 환불했습니다. &7(잔액 " + Msg.num(d.exMoney) + "원)");
        if (t.getPlayer() != null) {
            Msg.send(t.getPlayer(), "§a[💰] &6유료 캐시 " + Msg.num(amount) + "원이 환불되었습니다. &7(사유: " + Msg.esc(reason) + " · 잔액 " + Msg.num(d.exMoney) + "원)");
            Msg.sound(t.getPlayer(), "entity.experience_orb.pickup", 1, 1.2f);
        }
    }
}
