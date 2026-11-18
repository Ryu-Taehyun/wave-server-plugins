package kr.wave.core.module;

import kr.wave.core.Items;
import kr.wave.core.WaveCore;
import kr.wave.core.data.GlobalData;
import kr.wave.core.data.PlayerData;
import kr.wave.core.util.Cmd;
import kr.wave.core.util.Gui;
import kr.wave.core.util.It;
import kr.wave.core.util.Msg;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** team.sk : 팀 시스템 */
public class TeamModule extends Module {

    private static final long CREATE_COST = 40000;
    private final Map<UUID, String> invites = new HashMap<>();
    private final Map<String, Gui> openStorage = new HashMap<>();

    public TeamModule(WaveCore plugin) {
        super(plugin);
    }

    @Override
    public void enable() {
        cmd("팀", null, this::teamCmd);
        cmd("팀창고", null, (s, l, a) -> {
            if (Cmd.isPlayer(s)) storage((Player) s);
        }, "teamchest");
        cmd("팀관리", ADMIN, this::adminCmd);
        cmd("팀닉변경권", ADMIN, (s, l, a) -> {
            if (Cmd.isPlayer(s)) It.give((Player) s, Items.teamColorBook("red"), Items.teamColorBook("gold"));
        });
        cmd("추첨", ADMIN, (s, l, a) -> {
            List<Player> all = new ArrayList<>(Bukkit.getOnlinePlayers());
            if (all.isEmpty()) {
                Msg.send(s, "&c[시스템] 추첨할 플레이어가 없습니다.");
                return;
            }
            Player w = all.get(ThreadLocalRandom.current().nextInt(all.size()));
            Msg.broadcast("");
            Msg.broadcast("<b><gradient:#A1FF52:#45FFF6><italic:false>[ 추첨 결과 ]</gradient></b>");
            Msg.broadcast("&a&l추첨을 통해 당첨된 행운의 주인공은...");
            Msg.broadcast("");
            Msg.broadcast("&e&l>>> &b" + w.getName() + " &e&l<<<");
            Msg.broadcast("");
            Msg.broadcast("&7우편함으로 보상이 지급되었습니다!");
            Msg.broadcast("");
            Msg.soundAt(w.getLocation(), "entity.player.levelup", 1, 1);
            Msg.send(w, "&e당신이 랜덤 추첨에 당첨되었습니다!");
        });
    }

    private void header(Player p) {
        Msg.send(p, "");
        Msg.send(p, "");
        Msg.send(p, "             <b><gradient:#C0FEC4:#79AB96>TEAM SYSTEM</gradient></b>");
    }

    /** 명령어: /팀 = GUI, 예전 하위 명령어(/팀 생성 이름 등)도 그대로 동작 */
    /* ======================= 역할 ======================= */

    /** 높은 순서: 마스터 > 부마스터 > 엘리트 > 팀원 */
    public static final List<String> ROLES = List.of("마스터", "부마스터", "엘리트", "팀원");

    public static int rank(String role) {
        int i = ROLES.indexOf(role);
        return i < 0 ? ROLES.size() - 1 : i;
    }

    /** 색이 들어간 역할 이름 */
    public static String roleTag(String role) {
        return switch (rank(role)) {
            case 0 -> "&6★ 마스터";
            case 1 -> "&e☆ 부마스터";
            case 2 -> "&b◆ 엘리트";
            default -> "&f팀원";
        };
    }

    private static String role(GlobalData.Team t, UUID id) {
        return t.members.getOrDefault(id, "팀원");
    }

    /** 초대: 엘리트 이상 */
    private static boolean canInvite(GlobalData.Team t, UUID id) {
        return rank(role(t, id)) <= 2;
    }

    /** 추방: 부마스터 이상, 자기보다 낮은 역할만 */
    private static boolean canKick(GlobalData.Team t, UUID actor, UUID target) {
        return !actor.equals(target) && rank(role(t, actor)) <= 1 && rank(role(t, actor)) < rank(role(t, target));
    }

    /** 역할 변경: 마스터는 부마스터까지, 부마스터는 엘리트까지 (자기보다 낮은 팀원만) */
    private static boolean canSetRole(GlobalData.Team t, UUID actor, UUID target, String to) {
        if (actor.equals(target) || !t.members.containsKey(target)) return false;
        int a = rank(role(t, actor)), cur = rank(role(t, target)), nw = rank(to);
        if (nw == 0 || nw == cur) return false;
        return a < cur && a < nw && a <= 1;
    }

    /* ======================= 명령어 ======================= */

    /** /팀 = GUI, /팀 <하위 명령어> 로도 모든 기능 사용 */
    private void teamCmd(CommandSender s, String l, String[] a) {
        if (!Cmd.isPlayer(s)) return;
        Player p = (Player) s;
        if (a.length == 0) {
            open(p);
            return;
        }
        GlobalData.Team mine = global().teamOf(p.getUniqueId());
        switch (a[0]) {
            case "도움말", "help", "?" -> help(p);
            case "생성" -> {
                if (a.length < 2) prompt(p);
                else create(p, a[1]);
            }
            case "정보" -> info(p, a.length >= 2 ? findTeam(a[1]) : mine);
            case "접속", "목록" -> {
                if (mine == null) Msg.send(p, "&c소속된 팀이 없습니다.");
                else onlineGui(p, mine);
            }
            case "창고" -> storage(p);
            case "채팅" -> toggleChat(p);
            case "탈퇴" -> leaveSelf(p);
            case "초대" -> {
                if (a.length < 2) {
                    invitePicker(p, 1);
                    return;
                }
                Player t = Cmd.online(a[1]);
                if (t == null) Msg.send(p, "&c접속 중인 플레이어가 아닙니다.");
                else invite(p, t);
            }
            case "수락" -> accept(p);
            case "거절" -> decline(p);
            case "추방" -> kick(p, member(p, mine, Cmd.arg(a, 1)));
            case "승급" -> step(p, mine, member(p, mine, Cmd.arg(a, 1)), -1);
            case "강등" -> step(p, mine, member(p, mine, Cmd.arg(a, 1)), +1);
            case "역할" -> {
                UUID t = member(p, mine, Cmd.arg(a, 1));
                String r = Cmd.arg(a, 2);
                if (t == null) return;
                if (!ROLES.contains(r) || r.equals("마스터")) {
                    Msg.send(p, "&c역할: 부마스터 / 엘리트 / 팀원 &7(마스터는 /팀 위임)");
                    return;
                }
                setRole(p, mine, t, r);
            }
            case "위임" -> {
                UUID t = member(p, mine, Cmd.arg(a, 1));
                if (t != null) transfer(p, mine, t);
            }
            default -> help(p);
        }
    }

    private void help(Player p) {
        Msg.send(p, "");
        Msg.send(p, "            " + TITLE + " &7명령어");
        Msg.send(p, "&f/팀 &7- 팀 메뉴 열기");
        Msg.send(p, "&f/팀 생성 [이름] &7- 팀 만들기 &c(" + Msg.num(CREATE_COST) + " 캐시)");
        Msg.send(p, "&f/팀 정보 [팀 이름] &7- 팀 정보 보기");
        Msg.send(p, "&f/팀 접속 &7- 접속 중인 팀원 보기");
        Msg.send(p, "&f/팀 창고 &7· &f/팀 채팅 &7- 팀 창고 열기 · 팀 채팅 켜기/끄기");
        Msg.send(p, "&f/팀 초대 [이름] &7- 초대 &8(엘리트 이상)");
        Msg.send(p, "&f/팀 수락 &7· &f/팀 거절 &7- 받은 초대 수락 · 거절");
        Msg.send(p, "&f/팀 승급 [이름] &7· &f/팀 강등 [이름] &7- 역할 올리기 · 내리기 &8(부마스터 이상)");
        Msg.send(p, "&f/팀 역할 [이름] [부마스터|엘리트|팀원] &7- 역할 지정");
        Msg.send(p, "&f/팀 추방 [이름] &7- 추방 &8(부마스터 이상, 낮은 역할만)");
        Msg.send(p, "&f/팀 위임 [이름] &7- 마스터 넘기기 &8(마스터)");
        Msg.send(p, "&f/팀 탈퇴 &7- 팀 나가기");
        Msg.send(p, "&8역할: 마스터 > 부마스터 > 엘리트 > 팀원");
        Msg.send(p, "");
    }

    /** 이름으로 같은 팀원 찾기 (없으면 안내 후 null) */
    private UUID member(Player p, GlobalData.Team t, String name) {
        if (t == null) {
            Msg.send(p, "&c소속된 팀이 없습니다.");
            return null;
        }
        if (name == null || name.isEmpty()) {
            Msg.send(p, "&c플레이어 이름을 입력하세요.");
            return null;
        }
        for (UUID id : t.members.keySet()) {
            String n = Bukkit.getOfflinePlayer(id).getName();
            if (n != null && n.equalsIgnoreCase(name)) return id;
        }
        Msg.send(p, "&c같은 팀원이 아닙니다.");
        return null;
    }

    private GlobalData.Team findTeam(String name) {
        GlobalData.Team t = global().teams.get(name);
        if (t != null) return t;
        for (GlobalData.Team x : global().teams.values()) if (Msg.plainOf(x.display).equalsIgnoreCase(name)) return x;
        return null;
    }

    private void info(Player p, GlobalData.Team t) {
        if (t == null) {
            Msg.send(p, "&c팀을 찾을 수 없습니다.");
            return;
        }
        Msg.send(p, "");
        Msg.send(p, "            " + TITLE);
        Msg.send(p, "&f팀 : " + Msg.esc(t.display) + " &7(" + t.members.size() + "명, 접속 중 &a" + onlineCount(t) + "&7)");
        for (String r : ROLES) {
            List<String> names = new ArrayList<>();
            for (Map.Entry<UUID, String> en : t.members.entrySet()) {
                if (rank(en.getValue()) != rank(r)) continue;
                boolean on = Bukkit.getPlayer(en.getKey()) != null;
                names.add((on ? "&a" : "&7") + Bukkit.getOfflinePlayer(en.getKey()).getName());
            }
            if (!names.isEmpty()) Msg.send(p, roleTag(r) + " &8: " + String.join("&8, ", names));
        }
        Msg.send(p, "");
    }

    private static int onlineCount(GlobalData.Team t) {
        int n = 0;
        for (UUID id : t.members.keySet()) if (Bukkit.getPlayer(id) != null) n++;
        return n;
    }

    /* ======================= GUI ======================= */

    private static final String TITLE = "<b><gradient:#C0FEC4:#79AB96><italic:false>TEAM SYSTEM</gradient></b>";
    private static final int[] MEMBER_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    public void open(Player p) {
        GlobalData.Team t = global().teamOf(p.getUniqueId());
        if (t == null) noTeam(p);
        else teamMenu(p, t);
    }

    private void back(Player p) {
        MenuModule m = plugin.module(MenuModule.class);
        if (m != null) m.menu(p);
        else p.closeInventory();
    }

    /** 팀이 없을 때: 만들기 / 받은 초대 */
    private void noTeam(Player p) {
        Gui g = new Gui(3, "&8&l팀");
        g.set(4, It.of(Material.IRON_HELMET).name(TITLE).lore("", "&7팀원끼리는 서로 공격할 수 없고,", "&7팀 창고와 팀 채팅을 함께 씁니다.", "",
                "&8명령어: /팀 도움말", "&8부적절한 팀 이름은 삭제됩니다.").hide().build());
        long money = data(p).money;
        g.set(11, It.of(Material.WRITABLE_BOOK).name("&a&l팀 만들기")
                .lore("", "&7새 팀을 만들고 마스터가 됩니다.", "&7이름은 채팅으로 입력합니다.", "&8━━━━━━━━━━━━━━",
                        "&f가격 : &a" + Msg.num(CREATE_COST) + " 캐시",
                        money >= CREATE_COST ? "&e» 좌클릭 &f이름 정하기" : "&8» 좌클릭 캐시 부족 &7(보유 " + Msg.num(money) + ")").build(), e -> {
            if (data(p).money < CREATE_COST) {
                Gui.denySound(p);
                Msg.send(p, "&c팀을 만들려면 " + Msg.num(CREATE_COST) + " 캐시가 필요합니다.");
                return;
            }
            p.closeInventory();
            prompt(p);
        });
        String inv = invites.get(p.getUniqueId());
        GlobalData.Team it = inv == null ? null : global().teams.get(inv);
        if (it != null) {
            g.set(15, It.of(Material.PAPER).name("&e&l받은 초대").lore("", "&f팀 : " + Msg.esc(it.display), "&f팀원 : &e" + it.members.size() + "명",
                    "&8━━━━━━━━━━━━━━", "&e» 좌클릭 &f수락", "&e» 우클릭 &f거절").ench(org.bukkit.enchantments.Enchantment.UNBREAKING, 1).hide().build(), e -> {
                if (e.getClick() == ClickType.LEFT) accept(p);
                else if (e.getClick() == ClickType.RIGHT) decline(p);
                else return;
                later(1, () -> open(p));
            });
        } else {
            g.set(15, It.of(Material.GRAY_DYE).name("&7받은 초대").lore("", "&8받은 초대가 없습니다.", "&8팀원에게 초대를 부탁해 보세요.").build());
        }
        g.nav(p, () -> back(p)).fill().open(p);
    }

    /** 팀이 있을 때: 정보, 접속 중인 팀원, 창고, 초대, 채팅, 탈퇴, 팀원 목록 */
    private void teamMenu(Player p, GlobalData.Team t) {
        String myRole = role(t, p.getUniqueId());
        Gui g = new Gui(5, "&8&l팀 · " + Msg.plainOf(t.display));
        int online = onlineCount(t);
        String masterName = "?";
        int[] counts = new int[ROLES.size()];
        for (Map.Entry<UUID, String> en : t.members.entrySet()) {
            counts[rank(en.getValue())]++;
            if (rank(en.getValue()) == 0) masterName = String.valueOf(Bukkit.getOfflinePlayer(en.getKey()).getName());
        }
        g.set(4, It.of(Material.IRON_HELMET).name(Msg.esc(t.display)).lore("", "&f마스터 : &6" + masterName,
                "&f팀원 : &e" + t.members.size() + "명 &7(접속 중 &a" + online + "&7)",
                "&8 부마스터 " + counts[1] + " · 엘리트 " + counts[2] + " · 팀원 " + counts[3],
                "&f내 역할 : " + roleTag(myRole), "", "&8팀원끼리는 서로 공격할 수 없습니다.", "&8명령어: /팀 도움말").hide().build());
        g.set(10, It.of(Material.SPYGLASS).name("&a&l접속 중인 팀원 &7(" + online + "명)")
                .lore("", "&7지금 접속한 팀원의 위치와 상태를 봅니다.", "&8━━━━━━━━━━━━━━", "&e» 좌클릭 &f열기").build(), e -> later(1, () -> onlineGui(p, t)));
        g.set(11, It.of(Material.CHEST).name("&6&l팀 창고").lore("", "&7팀원이 함께 쓰는 6줄 창고입니다.", "&8━━━━━━━━━━━━━━", "&e» 좌클릭 &f열기").build(), e -> later(1, () -> storage(p)));
        boolean inv = canInvite(t, p.getUniqueId());
        g.set(12, It.of(inv ? Material.PLAYER_HEAD : Material.GRAY_DYE).name((inv ? "&a&l" : "&7") + "팀원 초대")
                .lore("", "&7접속 중인 플레이어를 골라 초대합니다.", "&8━━━━━━━━━━━━━━", inv ? "&e» 좌클릭 &f플레이어 고르기" : "&8엘리트 이상만 초대할 수 있습니다.").build(), e -> {
            if (!canInvite(t, p.getUniqueId())) {
                Gui.denySound(p);
                return;
            }
            later(1, () -> invitePicker(p, 1));
        });
        boolean teamChat = "team".equals(data(p).channel);
        g.set(13, It.of(teamChat ? Material.LIME_DYE : Material.GRAY_DYE).name("&c&l팀 채팅")
                .lore("", "&f현재 채널 : " + (teamChat ? "&cTEAM" : "&b" + ("local".equals(data(p).channel) ? "Local" : "Global")),
                        "&7켜면 채팅이 팀원에게만 보입니다.", "&8━━━━━━━━━━━━━━", teamChat ? "&e» 좌클릭 &f끄기 (Global)" : "&e» 좌클릭 &f켜기").build(), e -> {
            toggleChat(p);
            later(1, () -> open(p));
        });
        boolean master = rank(myRole) == 0;
        g.set(16, It.of(Material.IRON_DOOR).name("&c&l팀 탈퇴").lore("", master && t.members.size() > 1 ? "&7마스터는 가장 높은 역할의 팀원에게 넘어갑니다." : t.members.size() <= 1 ? "&7마지막 팀원이 나가면 팀이 사라집니다." : "&7팀에서 나갑니다.",
                "&8━━━━━━━━━━━━━━", "&c» Shift + 우클릭 &f탈퇴").build(), e -> {
            if (e.getClick() != ClickType.SHIFT_RIGHT) {
                Msg.send(p, "&7탈퇴하려면 &cShift + 우클릭 &7하세요.");
                return;
            }
            p.closeInventory();
            leaveSelf(p);
        });
        // 팀원 목록 (역할 높은 순)
        List<Map.Entry<UUID, String>> list = sorted(t);
        for (int i = 0; i < list.size() && i < MEMBER_SLOTS.length; i++) {
            if (i == MEMBER_SLOTS.length - 1 && list.size() > MEMBER_SLOTS.length) {
                g.set(MEMBER_SLOTS[i], It.of(Material.PAPER).name("&7+" + (list.size() - i) + "명 더").lore("", "&8/팀 정보 로 모두 볼 수 있습니다.").build());
                break;
            }
            UUID id = list.get(i).getKey();
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            boolean on = op.getPlayer() != null;
            boolean manage = manageable(t, p.getUniqueId(), id);
            List<String> lore = new ArrayList<>(List.of("", "&f역할 : " + roleTag(list.get(i).getValue()), "&f상태 : " + (on ? "&a● 접속 중" : "&8○ 오프라인")));
            if (manage) {
                lore.add("&8━━━━━━━━━━━━━━");
                lore.add("&e» 좌클릭 &f팀원 관리");
            }
            ItemStack head = It.of(Material.PLAYER_HEAD).skull(op).name(rankColor(list.get(i).getValue()) + op.getName()).lore(lore.toArray(new String[0])).build();
            g.set(MEMBER_SLOTS[i], head, manage ? e -> later(1, () -> memberGui(p, t, id)) : null);
        }
        g.nav(p, () -> back(p)).fill().open(p);
    }

    private static String rankColor(String role) {
        return switch (rank(role)) {
            case 0 -> "&6★ ";
            case 1 -> "&e☆ ";
            case 2 -> "&b◆ ";
            default -> "&f";
        };
    }

    private static List<Map.Entry<UUID, String>> sorted(GlobalData.Team t) {
        List<Map.Entry<UUID, String>> list = new ArrayList<>(t.members.entrySet());
        list.sort((x, y) -> Integer.compare(rank(x.getValue()), rank(y.getValue())));
        return list;
    }

    /** 이 팀원에게 할 수 있는 관리 동작이 하나라도 있으면 true */
    private static boolean manageable(GlobalData.Team t, UUID actor, UUID target) {
        if (actor.equals(target)) return false;
        if (canKick(t, actor, target) || rank(role(t, actor)) == 0) return true;
        for (String r : ROLES) if (canSetRole(t, actor, target, r)) return true;
        return false;
    }

    /** 팀원 관리: 승급 / 강등 / 마스터 넘기기 / 추방 */
    private void memberGui(Player p, GlobalData.Team t, UUID id) {
        if (!t.members.containsKey(id) || !t.members.containsKey(p.getUniqueId())) {
            open(p);
            return;
        }
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        String cur = role(t, id);
        Gui g = new Gui(3, "&8&l팀원 관리 · " + op.getName());
        g.set(4, It.of(Material.PLAYER_HEAD).skull(op).name(rankColor(cur) + op.getName())
                .lore("", "&f역할 : " + roleTag(cur), "&f상태 : " + (op.getPlayer() != null ? "&a● 접속 중" : "&8○ 오프라인")).build());
        int r = rank(cur);
        String up = r - 1 >= 1 ? ROLES.get(r - 1) : null, down = r + 1 < ROLES.size() ? ROLES.get(r + 1) : null;
        boolean canUp = up != null && canSetRole(t, p.getUniqueId(), id, up);
        boolean canDown = down != null && canSetRole(t, p.getUniqueId(), id, down);
        g.set(10, It.of(canUp ? Material.LIME_DYE : Material.GRAY_DYE).name((canUp ? "&a&l" : "&7") + "승급")
                .lore("", up == null ? "&8더 올릴 수 없습니다. &7(마스터는 넘기기)" : "&f" + Msg.plainOf(roleTag(cur)) + " &7→ " + roleTag(up), "&8━━━━━━━━━━━━━━",
                        canUp ? "&e» 좌클릭 &f승급" : "&8권한이 없습니다.").build(), e -> {
            if (!canUp) {
                Gui.denySound(p);
                return;
            }
            setRole(p, t, id, up);
            later(1, () -> memberGui(p, t, id));
        });
        g.set(12, It.of(canDown ? Material.ORANGE_DYE : Material.GRAY_DYE).name((canDown ? "&6&l" : "&7") + "강등")
                .lore("", down == null ? "&8더 내릴 수 없습니다." : "&f" + Msg.plainOf(roleTag(cur)) + " &7→ " + roleTag(down), "&8━━━━━━━━━━━━━━",
                        canDown ? "&e» 좌클릭 &f강등" : "&8권한이 없습니다.").build(), e -> {
            if (!canDown) {
                Gui.denySound(p);
                return;
            }
            setRole(p, t, id, down);
            later(1, () -> memberGui(p, t, id));
        });
        boolean master = rank(role(t, p.getUniqueId())) == 0;
        g.set(14, It.of(master ? Material.GOLDEN_HELMET : Material.GRAY_DYE).name((master ? "&6&l" : "&7") + "마스터 넘기기")
                .lore("", "&7이 팀원이 마스터가 되고,", "&7나는 부마스터가 됩니다.", "&8━━━━━━━━━━━━━━", master ? "&e» Shift + 좌클릭 &f넘기기" : "&8마스터만 할 수 있습니다.").hide().build(), e -> {
            if (!master) {
                Gui.denySound(p);
                return;
            }
            if (e.getClick() != ClickType.SHIFT_LEFT) {
                Msg.send(p, "&7넘기려면 &eShift + 좌클릭 &7하세요.");
                return;
            }
            transfer(p, t, id);
            later(1, () -> open(p));
        });
        boolean kick = canKick(t, p.getUniqueId(), id);
        g.set(16, It.of(kick ? Material.IRON_DOOR : Material.GRAY_DYE).name((kick ? "&c&l" : "&7") + "추방")
                .lore("", "&7팀에서 내보냅니다.", "&8━━━━━━━━━━━━━━", kick ? "&c» Shift + 우클릭 &f추방" : "&8자기보다 낮은 역할만 추방할 수 있습니다.").build(), e -> {
            if (!kick) {
                Gui.denySound(p);
                return;
            }
            if (e.getClick() != ClickType.SHIFT_RIGHT) {
                Msg.send(p, "&7추방하려면 &cShift + 우클릭 &7하세요.");
                return;
            }
            kick(p, id);
            later(1, () -> open(p));
        });
        g.nav(p, () -> open(p)).fill().open(p);
    }

    /** 접속 중인 팀원 (따로 보는 화면) */
    private void onlineGui(Player p, GlobalData.Team t) {
        List<Player> on = new ArrayList<>();
        for (Map.Entry<UUID, String> en : sorted(t)) {
            Player o = Bukkit.getPlayer(en.getKey());
            if (o != null) on.add(o);
        }
        Gui g = new Gui(4, "&8&l접속 중인 팀원 &7(" + on.size() + "/" + t.members.size() + ")");
        for (int i = 0; i < on.size() && i < 27; i++) {
            Player o = on.get(i);
            PlayerData d = data(o);
            var loc = o.getLocation();
            boolean combat = plugin.module(CombatTagModule.class) != null && plugin.module(CombatTagModule.class).tagged(o);
            g.set(i, It.of(Material.PLAYER_HEAD).skull(o).name(rankColor(role(t, o.getUniqueId())) + o.getName())
                    .lore("", "&f역할 : " + roleTag(role(t, o.getUniqueId())), "&f레벨 : &e" + d.level,
                            "&f체력 : &c" + (int) Math.ceil(o.getHealth()) + " &7/ " + (int) WeaponModule.maxHealth(o),
                            "&f월드 : &7" + worldName(o.getWorld().getName()), "&f위치 : &7" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ(),
                            "&f상태 : " + (combat ? "&c⚔ 전투 중" : "&a● 접속 중")).build());
        }
        if (on.isEmpty()) g.set(13, It.of(Material.GRAY_DYE).name("&7접속 중인 팀원이 없습니다.").build());
        g.nav(p, () -> open(p)).fill().open(p);
    }

    private String worldName(String w) {
        if (w.equalsIgnoreCase(world("wild"))) return "야생";
        if (w.equalsIgnoreCase("sky")) return "천공의 제단";
        if (w.equalsIgnoreCase(world("spawn"))) return "스폰";
        if (w.equalsIgnoreCase(world("nether"))) return "네더";
        if (w.equalsIgnoreCase(world("end"))) return "엔드";
        if (w.equalsIgnoreCase(world("duel"))) return "1대1 대전";
        if (w.equalsIgnoreCase(world("newbie"))) return "뉴비 월드";
        return w;
    }

    /** 초대할 플레이어 고르기 (접속 중, 팀 없는 플레이어) */
    private void invitePicker(Player p, int page) {
        GlobalData.Team mine = global().teamOf(p.getUniqueId());
        if (mine == null) {
            Msg.send(p, "&c소속된 팀이 없습니다.");
            return;
        }
        if (!canInvite(mine, p.getUniqueId())) {
            Msg.send(p, "&c엘리트 이상만 초대할 수 있습니다.");
            return;
        }
        List<Player> cand = new ArrayList<>();
        for (Player o : Bukkit.getOnlinePlayers()) if (o != p && global().teamOf(o.getUniqueId()) == null) cand.add(o);
        cand.sort((x, y) -> x.getName().compareToIgnoreCase(y.getName()));
        int per = 45, pages = Math.max(1, (cand.size() + per - 1) / per);
        int pg = Math.max(1, Math.min(page, pages));
        Gui g = new Gui(6, "&8&l팀원 초대");
        if (cand.isEmpty()) g.set(22, It.of(Material.GRAY_DYE).name("&7초대할 수 있는 플레이어가 없습니다.").lore("", "&8팀이 없는 접속 중인 플레이어만 표시됩니다.").build());
        for (int i = 0; i < per && (pg - 1) * per + i < cand.size(); i++) {
            Player o = cand.get((pg - 1) * per + i);
            boolean sent = mine.id.equals(invites.get(o.getUniqueId()));
            g.set(i, It.of(Material.PLAYER_HEAD).skull(o).name("&f" + o.getName())
                    .lore("", sent ? "&a초대를 보냈습니다." : "&7팀이 없는 플레이어입니다.", "&8━━━━━━━━━━━━━━", "&e» 좌클릭 &f" + (sent ? "다시 초대" : "초대 보내기")).build(), e -> {
                if (!o.isOnline()) {
                    Msg.send(p, "&c이미 접속을 종료한 플레이어입니다.");
                    return;
                }
                invite(p, o);
                later(1, () -> invitePicker(p, pg));
            });
        }
        g.pages(p, pg, pages, n -> invitePicker(p, n));
        g.nav(p, () -> open(p)).fill().open(p);
    }

    /* ======================= 팀 이름 입력 (채팅) ======================= */

    private final Map<UUID, Long> naming = new HashMap<>();

    private void prompt(Player p) {
        if (global().teamOf(p.getUniqueId()) != null) {
            Msg.send(p, "&c이미 소속된 팀이 있습니다.");
            return;
        }
        naming.put(p.getUniqueId(), System.currentTimeMillis() + 60_000);
        Msg.send(p, "");
        Msg.send(p, "             " + TITLE);
        Msg.send(p, "      &f채팅에 &a팀 이름&f을 입력하세요. &7(2~10자, 한글/영어/숫자)");
        Msg.send(p, "      &7취소하려면 &c취소 &7를 입력하세요. &8(60초)");
        Msg.send(p, "");
        Msg.sound(p, "block.note_block.chime", 0.8f, 1.4f);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onNameChat(io.papermc.paper.event.player.AsyncChatEvent e) {
        Long until = naming.get(e.getPlayer().getUniqueId());
        if (until == null) return;
        e.setCancelled(true);
        String text = Msg.plain(e.message()).trim();
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (naming.remove(p.getUniqueId()) == null) return;
            if (System.currentTimeMillis() > until) {
                Msg.send(p, "&c입력 시간이 지났습니다. 다시 시도해 주세요.");
                return;
            }
            if (text.equals("취소")) {
                Msg.send(p, "&7팀 만들기를 취소했습니다.");
                return;
            }
            create(p, text);
        });
    }

    /* ======================= 동작 ======================= */

    private void notifyTeam(GlobalData.Team t, String msg) {
        for (UUID m : t.members.keySet()) {
            Player o = Bukkit.getPlayer(m);
            if (o != null) Msg.send(o, msg);
        }
    }

    private void toggleChat(Player p) {
        PlayerData d = data(p);
        if (global().teamOf(p.getUniqueId()) == null) {
            Msg.send(p, "&c&l[!] &c팀에 속해있지 않습니다.");
            return;
        }
        d.channel = "team".equals(d.channel) ? "global" : "team";
        Msg.send(p, "&e&l[!] &7채널이 " + ("team".equals(d.channel) ? "&cTEAM" : "&bGlobal") + " &7으로 변경되었습니다.");
    }

    private void create(Player p, String name) {
        GlobalData g = global();
        if (g.teamOf(p.getUniqueId()) != null) {
            Msg.send(p, "&c이미 소속된 팀이 있습니다.");
            return;
        }
        if (!name.matches("[가-힣A-Za-z0-9]{2,10}")) {
            Msg.send(p, "&c팀 이름은 2~10자의 한글, 영어, 숫자만 쓸 수 있습니다.");
            Gui.denySound(p);
            return;
        }
        if (findTeam(name) != null) {
            Msg.send(p, "&c이미 같은 이름의 팀이 있습니다.");
            Gui.denySound(p);
            return;
        }
        if (!plugin.module(EconomyModule.class).take(p, CREATE_COST, false)) {
            Msg.send(p, "&c생성에 필요한 캐시가 부족합니다. &7(" + Msg.num(CREATE_COST) + " 캐시)");
            Gui.denySound(p);
            return;
        }
        GlobalData.Team t = new GlobalData.Team();
        t.id = name;
        t.display = name;
        t.members.put(p.getUniqueId(), "마스터");
        g.teams.put(name, t);
        plugin.store().saveGlobal();
        header(p);
        Msg.send(p, "        &7팀 " + Msg.esc(t.display) + "이(가) 생성되었습니다.");
        Msg.send(p, "");
        Msg.send(p, "      &7유저들과 함께 팀을 성장시켜 보세요!");
        Msg.send(p, "");
        Msg.send(p, "");
        Msg.soundAt(p.getLocation(), "entity.player.levelup", 1, 2);
        later(1, () -> open(p));
    }

    private void invite(Player p, Player t) {
        GlobalData g = global();
        GlobalData.Team mine = g.teamOf(p.getUniqueId());
        if (mine == null) {
            Msg.send(p, "&c소속된 팀이 없습니다.");
            return;
        }
        if (!canInvite(mine, p.getUniqueId())) {
            Msg.send(p, "&c엘리트 이상만 초대할 수 있습니다.");
            return;
        }
        if (t == p || g.teamOf(t.getUniqueId()) != null) {
            Msg.send(p, "&c이미 팀에 소속된 플레이어 입니다.");
            return;
        }
        invites.put(t.getUniqueId(), mine.id);
        Msg.send(p, "&e" + t.getName() + "에게 초대장을 보냈습니다.");
        Msg.send(t, "");
        Msg.send(t, "");
        Msg.send(t, "             " + TITLE);
        Msg.send(t, "      &7팀 " + Msg.esc(mine.display) + "로부터 초대가 도착했습니다.");
        Msg.send(t, "");
        t.sendMessage(Msg.c("  &a&l[수락]").clickEvent(ClickEvent.runCommand("/팀 수락")).hoverEvent(HoverEvent.showText(Msg.c("&a클릭하여 팀 수락하기")))
                .append(Msg.c("  &c&l[거절]").clickEvent(ClickEvent.runCommand("/팀 거절")).hoverEvent(HoverEvent.showText(Msg.c("&c클릭하여 거절하기"))))
                .append(Msg.c("  &7또는 &f/팀 &7메뉴에서 확인")));
        Msg.send(t, "");
        Msg.send(t, "");
        Msg.soundAt(t.getLocation(), "entity.player.levelup", 1, 2);
    }

    private void accept(Player p) {
        GlobalData g = global();
        String id = invites.get(p.getUniqueId());
        GlobalData.Team t = id == null ? null : g.teams.get(id);
        if (t == null) {
            Msg.send(p, "&c초대장이 없습니다.");
            return;
        }
        if (g.teamOf(p.getUniqueId()) != null) {
            Msg.send(p, "&c이미 팀에 소속되어 있습니다.");
            return;
        }
        notifyTeam(t, "&a[팀] &f" + p.getName() + "&7님이 팀에 가입했습니다.");
        t.members.put(p.getUniqueId(), "팀원");
        invites.remove(p.getUniqueId());
        plugin.store().saveGlobal();
        header(p);
        Msg.send(p, "           &7팀 " + Msg.esc(t.display) + "에 가입되었습니다.");
        Msg.send(p, "");
        Msg.send(p, "      &7팀원들과 함께 팀을 성장시켜 보세요!");
        Msg.send(p, "");
        Msg.send(p, "");
        Msg.soundAt(p.getLocation(), "entity.player.levelup", 1, 2);
    }

    private void decline(Player p) {
        if (invites.remove(p.getUniqueId()) == null) Msg.send(p, "&c초대장이 없습니다.");
        else Msg.send(p, "&7팀 초대를 거절했습니다.");
    }

    private void leaveSelf(Player p) {
        GlobalData.Team mine = global().teamOf(p.getUniqueId());
        if (mine == null) {
            Msg.send(p, "&c팀에 소속되어 있지 않습니다.");
            return;
        }
        boolean wasMaster = rank(role(mine, p.getUniqueId())) == 0;
        leave(mine, p.getUniqueId());
        Msg.send(p, "&c팀에서 탈퇴했습니다.");
        if (mine.members.isEmpty()) return;
        notifyTeam(mine, "&c[팀] &f" + p.getName() + "&7님이 팀을 떠났습니다.");
        if (wasMaster) {   // 가장 높은 역할(같으면 먼저 들어온) 팀원이 마스터
            UUID next = sorted(mine).get(0).getKey();
            mine.members.put(next, "마스터");
            plugin.store().saveGlobal();
            notifyTeam(mine, "&6[팀] &f" + Bukkit.getOfflinePlayer(next).getName() + "&7님이 새 마스터가 되었습니다.");
        }
    }

    private void kick(Player p, UUID target) {
        GlobalData.Team mine = global().teamOf(p.getUniqueId());
        if (target == null) return;
        if (mine == null || !mine.members.containsKey(target)) {
            Msg.send(p, "&c같은 팀원이 아닙니다.");
            return;
        }
        if (!canKick(mine, p.getUniqueId(), target)) {
            Msg.send(p, "&c부마스터 이상만, 자기보다 낮은 역할의 팀원을 추방할 수 있습니다.");
            Gui.denySound(p);
            return;
        }
        OfflinePlayer t = Bukkit.getOfflinePlayer(target);
        leave(mine, target);
        notifyTeam(mine, "&c[팀] &f" + t.getName() + "&7님이 &f" + p.getName() + "&7님에 의해 추방되었습니다.");
        if (t.getPlayer() != null) {
            Msg.send(t.getPlayer(), "&c팀 &7" + Msg.esc(mine.display) + " &c에서 추방되었습니다.");
            Msg.soundAt(t.getPlayer().getLocation(), "block.note_block.bass", 1, 1);
        }
    }

    /** 승급(-1) / 강등(+1) 한 칸 */
    private void step(Player p, GlobalData.Team t, UUID target, int dir) {
        if (t == null || target == null) return;
        int r = rank(role(t, target)) + dir;
        if (r < 1 || r >= ROLES.size()) {
            Msg.send(p, dir < 0 ? "&c더 올릴 수 없습니다. &7(마스터는 /팀 위임)" : "&c더 내릴 수 없습니다.");
            return;
        }
        setRole(p, t, target, ROLES.get(r));
    }

    private void setRole(Player p, GlobalData.Team t, UUID target, String to) {
        if (t == null || target == null) return;
        if (!canSetRole(t, p.getUniqueId(), target, to)) {
            Msg.send(p, "&c권한이 없습니다. &7(마스터: 부마스터까지 / 부마스터: 엘리트까지, 자기보다 낮은 팀원만)");
            Gui.denySound(p);
            return;
        }
        boolean up = rank(to) < rank(role(t, target));
        t.members.put(target, to);
        plugin.store().saveGlobal();
        notifyTeam(t, (up ? "&a[팀] " : "&6[팀] ") + "&f" + Bukkit.getOfflinePlayer(target).getName() + "&7님이 " + roleTag(to) + "&7(으)로 " + (up ? "승급" : "강등") + "되었습니다.");
    }

    private void transfer(Player p, GlobalData.Team t, UUID to) {
        if (t == null || to == null) return;
        if (rank(role(t, p.getUniqueId())) != 0) {
            Msg.send(p, "&c마스터만 넘길 수 있습니다.");
            return;
        }
        if (!t.members.containsKey(to) || to.equals(p.getUniqueId())) return;
        t.members.put(p.getUniqueId(), "부마스터");
        t.members.put(to, "마스터");
        plugin.store().saveGlobal();
        notifyTeam(t, "&6[팀] &f" + Bukkit.getOfflinePlayer(to).getName() + "&7님이 새 마스터가 되었습니다. &8(" + p.getName() + " → 부마스터)");
    }

    private void leave(GlobalData.Team t, UUID member) {
        t.members.remove(member);
        if (t.members.isEmpty()) {
            global().teams.remove(t.id);
            Gui open = openStorage.remove(t.id);
            if (open != null) open.getInventory().close();
        }
        plugin.store().saveGlobal();
        Player p = Bukkit.getPlayer(member);
        if (p != null) {
            PlayerData d = data(p);
            if ("team".equals(d.channel)) d.channel = "global";
        }
    }

    /* ======================= 팀킬 방지 ======================= */

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = CoreModule.playerAttacker(e);
        if (attacker == null || attacker == victim) return;
        GlobalData.Team t = global().teamOf(victim.getUniqueId());
        if (t != null && t.members.containsKey(attacker.getUniqueId())) {
            e.setCancelled(true);
            Msg.send(attacker, "&c같은 팀원은 공격할 수 없습니다!");
        }
    }

    /* ======================= 팀 창고 (팀원이 같은 창을 공유) ======================= */

    private void storage(Player p) {
        GlobalData.Team t = global().teamOf(p.getUniqueId());
        if (t == null) {
            Msg.send(p, "&c소속된 팀이 없습니다.");
            return;
        }
        Gui g = openStorage.get(t.id);
        if (g == null) {
            g = new Gui(6, "&8&l팀 창고: " + Msg.esc(t.display)).editable(true);
            for (int i = 0; i < 54; i++) g.set(i, t.storage[i]);
            final Gui gui = g;
            g.onClose(e -> {
                ItemStack[] c = gui.getInventory().getContents();
                for (int i = 0; i < 54; i++) t.storage[i] = c[i] == null ? null : c[i].clone();
                plugin.store().saveGlobal();
                Msg.send(e.getPlayer(), "&a창고에 아이템이 저장되었습니다.");
                if (gui.getInventory().getViewers().size() <= 1) openStorage.remove(t.id);
            });
            openStorage.put(t.id, g);
        }
        g.open(p);
    }

    /* ======================= 관리자 ======================= */

    private void adminCmd(CommandSender s, String l, String[] a) {
        if (a.length == 0) {
            Msg.send(s, "");
            Msg.send(s, "     <gradient:#FF0000:#FFFFFF>TEAM ADMIN PANEL</gradient>");
            Msg.send(s, "   &f/팀관리 삭제 (팀이름) &7- 해당 팀을 강제로 삭제합니다.");
            Msg.send(s, "   &f/팀관리 퇴출 (플레이어) &7- 플레이어를 소속 팀에서 강제 퇴출합니다.");
            Msg.send(s, "");
            return;
        }
        String admin = "<gradient:#FF0000:#FFFFFF>[Admin]</gradient> ";
        if (a[0].equals("삭제")) {
            if (a.length < 2) {
                Msg.send(s, admin + "&c삭제할 팀 이름을 입력해주세요.");
                return;
            }
            GlobalData.Team t = global().teams.get(a[1]);
            if (t == null) {
                for (GlobalData.Team x : global().teams.values()) if (Msg.plainOf(x.display).equals(a[1])) t = x;
            }
            if (t == null) {
                Msg.send(s, admin + "&c존재하지 않는 팀입니다.");
                return;
            }
            global().teams.remove(t.id);
            Gui open = openStorage.remove(t.id);
            if (open != null) open.getInventory().close();
            plugin.store().saveGlobal();
            Msg.send(s, admin + "&e" + Msg.esc(a[1]) + " &c팀이 관리자에 의해 삭제되었습니다.");
        } else if (a[0].equals("퇴출")) {
            OfflinePlayer t = Cmd.offline(a.length >= 3 ? a[2] : Cmd.arg(a, 1));
            if (t == null) {
                Msg.send(s, admin + "&c퇴출할 플레이어를 선택해주세요.");
                return;
            }
            GlobalData.Team team = global().teamOf(t.getUniqueId());
            if (team == null) {
                Msg.send(s, admin + "&c해당 플레이어는 팀에 소속되어 있지 않습니다.");
                return;
            }
            leave(team, t.getUniqueId());
            Msg.send(s, admin + "&e" + t.getName() + "&7님을 &e" + Msg.esc(team.display) + " &c팀에서 강제 퇴출했습니다.");
            if (t.getPlayer() != null) Msg.send(t.getPlayer(), "<gradient:#FF0000:#FFFFFF>[Team]</gradient> &c관리자에 의해 팀에서 퇴출되었습니다.");
        }
    }

    /* ======================= 팀 색상 변경권 ======================= */

    @EventHandler(priority = EventPriority.HIGH)
    public void onBook(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        String n = Msg.name(p.getInventory().getItemInMainHand());
        String code = n.equals(Items.N_TEAM_RED) ? "&c" : n.equals(Items.N_TEAM_GOLD) ? "&6" : null;
        if (code == null) return;
        e.setCancelled(true);
        GlobalData.Team t = global().teamOf(p.getUniqueId());
        if (t == null) {
            Msg.send(p, "&c소속된 팀이 없습니다.");
            return;
        }
        if (rank(t.members.get(p.getUniqueId())) != 0) {
            Msg.send(p, "&c마스터만 사용할 수 있습니다.");
            return;
        }
        It.takeOneFromHand(p);
        t.display = code + Msg.plainOf(t.display);
        plugin.store().saveGlobal();
        Msg.send(p, "&a적용되었습니다!");
    }
}
