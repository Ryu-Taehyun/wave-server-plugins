package kr.wave.core.module;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import kr.wave.core.Items;
import kr.wave.core.WaveCore;
import kr.wave.core.data.GlobalData;
import kr.wave.core.data.PlayerData;
import kr.wave.core.util.Cmd;
import kr.wave.core.util.Gui;
import kr.wave.core.util.Fx;
import kr.wave.core.util.It;
import kr.wave.core.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.OfflinePlayer;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * maincore.sk : 입장/퇴장, 첫 접속, 채팅, 탭, 킬/사망 알림, 리스폰, 스폰 이동, 칭호/닉네임 색상, 밴, 월드 규칙 등
 * motd.sk     : 점검 모드, MOTD
 * newbieprotect.sk : 첫 접속 2시간 아이템 보호
 * viewweapon.sk    : /stats, 팬텀 스폰 차단
 * whisper.sk       : /귓속말
 */
public class CoreModule extends Module {

    private static final long NEWBIE_PROTECT_MS = 2L * 60 * 60 * 1000;
    private final Map<UUID, BukkitTask> teleporting = new HashMap<>();
    private final Map<UUID, Long> randomSpawnCooldown = new HashMap<>();

    public CoreModule(WaveCore plugin) {
        super(plugin);
    }

    @Override
    public void enable() {
        cmd("serveritem", ADMIN, (s, l, a) -> {
            if (Cmd.isPlayer(s)) It.give((Player) s, Items.claymoreSd());
        });
        cmd("serveritem1", ADMIN, (s, l, a) -> {
            if (Cmd.isPlayer(s)) It.give((Player) s, It.of(Material.NETHERITE_LEGGINGS).unbreakable()
                    .ench(Enchantment.PROTECTION, 5).name("&6&l[&f&l✧&6&l] &c&l[Lv.6] &a&l시공의 은하수 바지").build());
        });
        cmd("nicknamecolor", ADMIN, this::nicknameColorCmd, "닉네임색상");
        // 원본은 권한이 없었으나, 다른 사람의 인벤토리를 보는 기능이라 관리자 전용으로 변경
        cmd("openinv", ADMIN, this::openInvCmd);
        cmd("ch", null, this::channelCmd, "채널", "channel");
        cmd("givetitlebook", ADMIN, (s, l, a) -> {
            if (Cmd.isPlayer(s)) It.give((Player) s, Items.titleBooks());
        });
        cmd("닉색상변경권", ADMIN, (s, l, a) -> {
            if (!Cmd.isPlayer(s)) return;
            Player p = (Player) s;
            It.give(p, Items.nickPaper("red", "Peerless 랭크 달성 보상입니다."),
                    Items.nickPaper("gold", "The Astral Fate 올 수집 보상입니다."),
                    Items.nickPaper("purple", "End Rusher 이벤트 달성 보상입니다."),
                    Items.newbiePackage());
        });
        cmd("spawn", null, (s, l, a) -> {
            if (Cmd.isPlayer(s)) startTeleport((Player) s, "스폰", "spawn");
        }, "스폰", "로비");
        cmd("보호구역", null, (s, l, a) -> {
            if (Cmd.isPlayer(s)) startTeleport((Player) s, "보호구역", "protect");
        });
        cmd("title", ADMIN, this::titleCmd);
        cmd("serverban", ADMIN, this::banCmd);
        cmd("unserverban", ADMIN, this::unbanCmd);
        cmd("랜덤스폰", null, this::randomSpawnCmd);
        cmd("test11", ADMIN, (s, l, a) -> {
            if (Cmd.isPlayer(s)) testKillBroadcast((Player) s, "&4&l심판자의 정령", false);
        });
        cmd("바닥청소", ADMIN, "&c권한이 없습니다!", (s, l, a) -> {
            int count = 0;
            for (var w : Bukkit.getWorlds()) for (Item it : w.getEntitiesByClass(Item.class)) {
                it.remove();
                count++;
            }
            Msg.send(s, "&a&l[!] &a바닥 청소가 완료되었습니다. &7(&e" + count + "&7개의 아이템 정리됨)");
        });
        cmd("점검모드", ADMIN, this::maintenanceCmd);
        cmd("stats", null, this::statsCmd, "장비보기", "스탯");
        cmd("귓속말", null, this::whisperCmd, "귓");
        cmd("답장", null, this::replyCmd, "reply");
    }

    /* ======================= 입장 / 퇴장 ======================= */

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        // IP 고정 (닉네임:허용IP)
        String ip = e.getAddress().getHostAddress();
        for (String entry : plugin.getConfig().getStringList("ip-lock")) {
            int i = entry.lastIndexOf(':');
            if (i <= 0) continue;
            if (entry.substring(0, i).equals(e.getName()) && !entry.substring(i + 1).equals(ip)) {
                e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.empty());
                return;
            }
        }
        GlobalData.Ban ban = global().bans.get(e.getUniqueId());
        if (ban != null) {
            if (ban.code == null) ban.code = banCode();
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, Msg.c(banMessage(ban)));
        }
    }

    private String banMessage(GlobalData.Ban ban) {
        return "§c§l[🚫 Wave Online 🚫]%nl%%nl%§c§l당신은 서버에서 영구적으로 차단되었습니다.%nl%%nl%§e사유 : §f" + Msg.esc(ban.reason)
                + "%nl%§6이의 제기용 코드 : §f#" + ban.code + "%nl%§7문의 : §f" + plugin.getConfig().getString("ban-contact", "관리자에게 문의하세요");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        PlayerData d = data(p);

        if (global().maintenance && !p.hasPermission("wave.maintenance.bypass")) {
            e.joinMessage(null);
            later(1, () -> p.kick(Msg.c("§c§l서버 점검 중입니다. 잠시 후 접속해주세요.")));
            return;
        }
        // 죽은 직후 나갔다가 다시 들어온 경우 보호 아이템 지급
        if (!d.respawnItems.isEmpty() && !p.isDead()) {
            later(20, () -> {
                if (!p.isOnline() || p.isDead() || d.respawnItems.isEmpty()) return;
                List<ItemStack> items = new ArrayList<>(d.respawnItems);
                d.respawnItems.clear();
                It.give(p, items);
                plugin.store().save(d);
            });
        }

        String color = nickColor(d.nickColor, "&f");
        GlobalData.Team team = global().teamOf(p.getUniqueId());
        String teamPart = team != null ? "&r" + Msg.esc(team.display) + " &7| " : "";
        boolean first = !p.hasPlayedBefore();
        long away = d.lastSeen > 0 ? System.currentTimeMillis() - d.lastSeen : 0;

        e.joinMessage(Msg.c("&8[&a+&8] " + teamPart + nickName(d.nickColor, p.getName(), "&f") + "&f 님이 서버에 &a접속&f했습니다."));

        if (first) firstJoin(p, d, e);

        // 탭 이름
        p.playerListName(Msg.c(plugin.hooks().prefix(p) + " &7- " + nickName(d.nickColor, p.getName(), "&f")));

        // 타이틀 애니메이션 "Wave Online" + 물결 파티클
        String text = "Wave Online";
        for (int i = 1; i <= text.length(); i++) {
            String part = text.substring(0, i);
            long delay = Math.round((i - 1) * 2.5);
            later(delay, () -> {
                if (!p.isOnline()) return;
                Msg.title(p, "<gradient:#0077B6:#90E0EF:#CAF0F8>&l" + part + "</gradient>", "", 0, 10, 0);
                Msg.sound(p, "block.lever.click", 0.6f, 2);
            });
        }
        later(Math.round(text.length() * 2.5), () -> {
            if (!p.isOnline()) return;
            String sub = first ? "&b처음 오신 것을 환영합니다!" : away >= 7L * 24 * 3600_000 ? "&e다시 만나서 반가워요!" : "&7오늘도 즐거운 모험 되세요.";
            Msg.title(p, "<gradient:#0077B6:#90E0EF><italic:false><b>Wave O</b></gradient><gradient:#90E0EF:#CAF0F8><italic:false><b>nli</b><b>ne</b></gradient>",
                    sub, 0, 30, 15);
            Msg.sound(p, "entity.player.levelup", 0.8f, 2);
            Msg.sound(p, "block.amethyst_block.resonate", 1f, 1.2f);
            Fx.helix(p, Fx.rgb(0x0077B6), Fx.rgb(0xCAF0F8), 0.8, 2.4, 24);
            Fx.ring(p.getLocation().add(0, 0.1, 0), 1.2, 28, Fx.rgb(0x90E0EF), 1.2f);
        });
        later(Math.round(text.length() * 2.5) + 20, () -> {
            if (p.isOnline()) welcomePanel(p, first, away);
        });
    }

    /** 접속 후 개인 안내: 읽지 않은 우편, 심판자 강림만 (해당될 때만) */
    private void welcomePanel(Player p, boolean first, long away) {
        PlayerData d = data(p);
        boolean any = false;
        if (!d.mailbox.isEmpty()) {
            p.sendMessage(Msg.c("&e[📬] &f읽지 않은 우편이 &e" + d.mailbox.size() + "개 &f있습니다. &8[클릭]")
                    .clickEvent(ClickEvent.runCommand("/우편함")).hoverEvent(HoverEvent.showText(Msg.c("&a클릭하여 우편함 열기"))));
            any = true;
        }
        BossModule boss = plugin.module(BossModule.class);
        if (boss != null && boss.descended()) {
            p.sendMessage(Msg.c("<gradient:#FFF200:#FFFCD4>[☀] 천공의 심판자가 강림해 있습니다!</gradient> &8[클릭]")
                    .clickEvent(ClickEvent.runCommand("/천공의제단")).hoverEvent(HoverEvent.showText(Msg.c("&a클릭하여 천공의 제단 메뉴 열기"))));
            any = true;
        }
        if (any) Msg.sound(p, "block.note_block.chime", 0.6f, 1.6f);
    }

    private void firstJoin(Player p, PlayerData d, PlayerJoinEvent e) {
        e.joinMessage(null);
        d.level = 1;
        d.nickColor = 0;
        d.newbieStart = System.currentTimeMillis();
        int nth = plugin.store().playerCount() + 1;
        later(1, () -> {
            String bar = "<gradient:#A1FF52:#45FFF6:#A1FF52><st>                                                  </st></gradient>";
            Msg.broadcast(" ");
            Msg.broadcast(bar);
            Msg.broadcast("   <gradient:#A1FF52:#45FFF6><b>✦ NEW ADVENTURER ✦</b></gradient>");
            Msg.broadcast(" ");
            Msg.broadcast("   &f새로운 모험가 &b&l" + p.getName() + " &f님이 처음으로 접속했습니다!");
            Msg.broadcast("   &7Wave Online 의 &e" + nth + "번째 &7모험가입니다. 반갑게 맞아 주세요!");
            Component welcome = Msg.c("   &a&l[👋 환영하기]").clickEvent(ClickEvent.suggestCommand("/귓 " + p.getName() + " 환영합니다! "))
                    .hoverEvent(HoverEvent.showText(Msg.c("&a클릭하여 " + p.getName() + "님에게 환영 인사 보내기")));
            for (Player o : Bukkit.getOnlinePlayers()) if (o != p) o.sendMessage(welcome);
            Msg.broadcast(bar);
            Msg.broadcast(" ");
            for (Player o : Bukkit.getOnlinePlayers()) if (o != p) Msg.sound(o, "entity.firework_rocket.twinkle", 0.6f, 1.2f);
            if (!p.isOnline()) return;
            tpWorld(p, "spawn");
            console("lp user " + p.getName() + " parent set default");
            console("nick " + p.getName() + " off");
            console("minecraft:team join default " + p.getName());
            It.give(p, new ItemStack(Material.CHAINMAIL_HELMET), new ItemStack(Material.CHAINMAIL_CHESTPLATE),
                    new ItemStack(Material.CHAINMAIL_LEGGINGS), new ItemStack(Material.CHAINMAIL_BOOTS),
                    new ItemStack(Material.IRON_SWORD), new ItemStack(Material.IRON_PICKAXE), new ItemStack(Material.IRON_AXE),
                    new ItemStack(Material.COOKED_BEEF, 32));
            // 스폰 도착 축하 연출 (폭죽 모양 파티클, 실제 폭죽은 피해가 있어 사용하지 않음)
            later(30, () -> {
                if (!p.isOnline()) return;
                Location l = p.getLocation();
                Fx.burst(l.clone().add(0, 2.5, 0), Particle.FIREWORK, 60, 0.15);
                Fx.burst(l.clone().add(0, 1.2, 0), Particle.TOTEM_OF_UNDYING, 50, 0.4);
                Fx.shockwave(l.clone().add(0, 0.1, 0), Fx.rgb(0x45FFF6), 3.0, 10);
                Msg.sound(p, "entity.firework_rocket.blast", 1, 1);
                Msg.sound(p, "entity.firework_rocket.twinkle", 1, 1.2f);
            });
        });
        plugin.store().save(d);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        PlayerData d = data(p);
        GlobalData.Team team = global().teamOf(p.getUniqueId());
        String teamPart = team != null ? "&r" + Msg.esc(team.display) + " &7| " : "";
        e.quitMessage(Msg.c("&8[&c-&8] " + teamPart + nickName(d.nickColor, p.getName(), "&f") + "&f 님이 서버에서 &c퇴장&f했습니다."));
        BukkitTask t = teleporting.remove(p.getUniqueId());
        if (t != null) t.cancel();
        randomSpawnCooldown.entrySet().removeIf(en -> System.currentTimeMillis() - en.getValue() > 20_000);
        d.lastSeen = System.currentTimeMillis();
    }

    /* ======================= / 명령어 목록 숨기기 ======================= */

    /** 플레이어가 / 를 눌렀을 때: 쓸 수 없는 명령어(관리자 등)와 "플러그인:명령어" 형태는 목록에서 뺌 */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandList(org.bukkit.event.player.PlayerCommandSendEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission(ADMIN)) return;
        var map = Bukkit.getCommandMap();
        e.getCommands().removeIf(label -> {
            if (label.contains(":")) return true;
            var c = map.getCommand(label);
            if (c == null) return false;
            if (!c.testPermissionSilent(p)) return true;
            java.util.List<String> hidden = plugin.getConfig().getStringList("hidden-commands");
            return hidden.contains(label.toLowerCase());
        });
    }

    /* ======================= 아이템 위조 방지 ======================= */

    /** 특수 아이템은 이름으로 구분하므로, 모루로 일반 아이템에 같은 이름을 붙이는 것을 막음 */
    private static final List<String> PROTECTED_NAMES = new ArrayList<>();

    static {
        for (java.lang.reflect.Field f : Items.class.getFields()) {
            if (f.getType() != String.class || !java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            if (!f.getName().startsWith("N_") && !f.getName().startsWith("C_")) continue;
            try {
                String v = (String) f.get(null);
                if (v != null && v.length() >= 3) PROTECTED_NAMES.add(v.toLowerCase());
            } catch (IllegalAccessException ignored) {
            }
        }
        for (String k : new String[]{"[칭호]", "닉네임 색상 변경권", "팀 색상 변경권", "천공의 심판자", "얼어붙은 제왕", "사신의",
                "[pre-order]", "[sealed]", "[roulette]", "[time]", "[quantum]", "[ruiner]", "[void]", "[natural]", "[chaos]",
                "soulstripe", "soul splitter", "캐시 수표", "이동권", "[pvp]", "[newbie]", "[magic]",
                "초승달의 서약", "신월의 기록", "새 시대의 각인", "new characteristic", "망령 수확자", "잿불 낙인검", "시간을 가르는 바람", "time piecer", "그림자 사슬", "umbral chain", "메아리 종", "echo bell"}) PROTECTED_NAMES.add(k);
    }

    public static boolean protectedName(String plain) {
        if (plain == null || plain.isEmpty()) return false;
        String n = plain.toLowerCase();
        for (String k : PROTECTED_NAMES) if (n.contains(k)) return true;
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvil(org.bukkit.event.inventory.PrepareAnvilEvent e) {
        ItemStack result = e.getResult();
        if (It.isAir(result)) return;
        String before = Msg.name(e.getInventory().getItem(0));
        String after = Msg.name(result);
        if (after.equals(before) || !protectedName(after)) return;
        e.setResult(null);
        if (e.getView().getPlayer() instanceof Player p) Msg.actionBar(p, "&c서버 아이템과 같은 이름은 사용할 수 없습니다.");
    }

    /* ======================= 채팅 ======================= */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        e.setCancelled(true);
        Player p = e.getPlayer();
        Component message = Component.text(Msg.plain(e.message()));
        Bukkit.getScheduler().runTask(plugin, () -> sendChat(p, message));
    }

    private void sendChat(Player p, Component message) {
        if (!p.isOnline()) return;
        PlayerData d = data(p);
        GlobalData.Team team = global().teamOf(p.getUniqueId());
        String channel;
        List<Player> targets = new ArrayList<>();
        if ("local".equals(d.channel)) {
            channel = "&a[Local] ";
            for (Player o : p.getWorld().getPlayers()) if (o.getLocation().distanceSquared(p.getLocation()) <= 100 * 100) targets.add(o);
        } else if ("team".equals(d.channel) && team != null) {
            channel = "&c[TEAM] ";
            for (Player o : Bukkit.getOnlinePlayers()) if (team.members.containsKey(o.getUniqueId())) targets.add(o);
        } else {
            channel = "";
            targets.addAll(Bukkit.getOnlinePlayers());
        }
        String prefix = plugin.hooks().prefix(p);
        String head = team != null
                ? channel + "&f" + Msg.esc(team.display) + " &7| " + prefix + " &7- %name% &f: "
                : channel + prefix + " &7- %name% &f: ";
        Component line = Msg.fill(head, "%name%", nameTag(p, nickName(d.nickColor, p.getName(), "&f"))).append(message);
        for (Player t : targets) t.sendMessage(line);
        Bukkit.getConsoleSender().sendMessage(line);
    }

    private void channelCmd(CommandSender s, String l, String[] a) {
        if (!Cmd.isPlayer(s)) return;
        Player p = (Player) s;
        PlayerData d = data(p);
        String c = a.length > 0 ? a[0].toLowerCase() : "";
        switch (c) {
            case "global", "gl", "g" -> {
                d.channel = "global";
                Msg.send(p, "&e&l[!] &7채널이 &bGlobal &7으로 변경되었습니다.");
            }
            case "local", "lo", "l" -> {
                d.channel = "local";
                Msg.send(p, "&e&l[!] &7채널이 &aLocal &7으로 변경되었습니다.");
            }
            case "team", "te", "t" -> {
                if (global().teamOf(p.getUniqueId()) == null) {
                    Msg.send(p, "&c&l[!] &c팀에 속해있지 않습니다.");
                } else {
                    d.channel = "team";
                    Msg.send(p, "&e&l[!] &7채널이 &cTEAM &7으로 변경되었습니다.");
                }
            }
            default -> Msg.send(p, "&c&l[!] &7사용법: /ch <global/local/team>");
        }
    }

    /* ======================= 닉네임 색상 / 칭호 ======================= */

    private void nicknameColorCmd(CommandSender s, String l, String[] a) {
        if (a.length < 2) {
            Msg.send(s, "&c/nicknamecolor <플레이어> <red|gold|purple|magenta|gradient|glacier|reset>");
            return;
        }
        Player t = Cmd.online(a[0]);
        if (t == null) {
            Msg.send(s, "&c접속 중인 플레이어가 아닙니다.");
            return;
        }
        PlayerData d = data(t);
        String prefix = plugin.hooks().prefix(t);
        switch (a[1].toLowerCase()) {
            case "red" -> setNick(s, t, d, prefix, 1, "&c", "&cRed");
            case "gold" -> setNick(s, t, d, prefix, 2, "&6", "&6Gold");
            case "purple" -> setNick(s, t, d, prefix, 3, "&5", "&5Purple");
            case "magenta" -> setNick(s, t, d, prefix, 4, "&d", "&dMagenta");
            case "gradient" -> setNick(s, t, d, prefix, 5, "&6", GRADIENT_GOLD + "Gradient Gold</gradient>");
            case "glacier" -> setNick(s, t, d, prefix, 6, "&b", GLACIER + "Glacier</gradient>");
            case "reset" -> {
                d.nickColor = 0;
                d.nickPapers.clear();
                t.playerListName(Msg.c(prefix + " &f" + t.getName()));
                Msg.send(s, "&a[System] &7플레이어 &f" + t.getName() + "&7 의 닉네임 색상이 초기화 되었습니다.");
            }
            default -> Msg.send(s, "&cred / gold / purple / magenta / gradient / glacier / reset 중 하나를 입력하세요.");
        }
        plugin.store().save(d);
    }

    private void setNick(CommandSender s, Player t, PlayerData d, String prefix, int c, String code, String label) {
        d.nickColor = c;
        t.playerListName(Msg.c(prefix + " " + nickName(c, t.getName(), code)));
        Msg.send(s, "&a[System] &7플레이어 &f" + t.getName() + "&7 의 닉네임 색상이 " + label + "&7 로 설정되었습니다.");
    }

    /** 닉네임 색상 적용 (TAB 플러그인 명령어 포함) */
    public void applyNickColor(Player p, int color) {
        PlayerData d = data(p);
        d.nickColor = color;
        String code = nickColor(color, "&f");
        String tabName = switch (color) {
            case 5 -> "<#FFF176>" + p.getName() + "</#FFB732>";   // TAB 그라데이션 형식
            case 6 -> "<#39C7FF>" + p.getName() + "</#C2FFF5>";
            default -> code + p.getName();
        };
        if (color == 0) console("tab player " + p.getName() + " customtabname ");
        else console("tab player " + p.getName() + " customtabname " + tabName);
        p.playerListName(Msg.c(plugin.hooks().prefix(p) + " &7- " + nickName(color, p.getName(), "&f")));
        console("tab player " + p.getName() + " tagprefix %luckperms_prefix% &7- " + (color == 5 ? "&#FFD54F" : color == 6 ? "&#7FDDFB" : code));
        plugin.store().save(d);
    }

    /** 칭호 적용 (LuckPerms 접두사) */
    public void applyTitle(Player p, String prefixArg) {
        console("lp user " + p.getName() + " meta clear");
        console("lp user " + p.getName() + " meta setprefix " + prefixArg);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClickItems(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        String n = Msg.name(p.getInventory().getItemInMainHand());
        if (n.isEmpty()) return;
        PlayerData d = data(p);

        // 칭호 책
        String[][] titles = {
                {"[칭호] [💦] 파수꾼", "\"&e&l[💦] 파수꾼\"", "7days"},
                {"[칭호] [Ace]", "&e[Ace]", "ace"},
                {"[칭호] [💎] Supreme", "\"&a[💎] &bSupreme\"", "supreme"},
                {"[칭호] [💎] Exceed", "\"&a[💎] &6Exceed\"", "exceed"},
                {"[칭호] [💎] Peerless", "\"&4[💎] &cPeerless\"", "peerless"},
        };
        for (String[] t : titles) {
            if (n.equals(t[0])) {
                e.setCancelled(true);
                It.takeOneFromHand(p);
                applyTitle(p, t[1]);
                d.titles.add(t[2]);
                d.activeTitle = t[2];
                Msg.send(p, "&a적용되었습니다!");
                plugin.store().save(d);
                titleFx(p);
                return;
            }
        }
        String[][] gradTitles = {
                {"[✦] 얼어붙은 제왕", "\"<b><gradient:#00E7FF:#FFFFFF>[✦] 얼어붙은 제왕</gradient></b>\"", "sealed"},
                {"[✦] 파멸의 지배자", "\"<b><gradient:#FF0000:#530427>[✦] 파멸의 지배자</gradient></b>\"", "destroy"},
                {"[✧] 네더 로드", "\"<b><gradient:#670B0B:#FD4141><italic:false><b>[✧] 네더 로드</b></gradient></b>\"", "nether"},
        };
        java.util.List<String[]> allTitles = new java.util.ArrayList<>(java.util.Arrays.asList(gradTitles));
        for (String[] ct : Items.COLLECTION_TITLES) allTitles.add(new String[]{Msg.plainOf(ct[1]), ct[2], ct[0]});
        for (String[] t : allTitles) {
            if (n.contains(t[0]) && n.startsWith("[칭호]")) {
                e.setCancelled(true);
                It.takeOneFromHand(p);
                applyTitle(p, t[1]);
                d.titles.add(t[2]);
                d.activeTitle = t[2];
                Msg.send(p, "&a적용되었습니다!");
                plugin.store().save(d);
                titleFx(p);
                return;
            }
        }

        // 닉네임 색상 변경권
        String[][] papers = {{"(Red)", "1", "red"}, {"(Gold)", "2", "gold"}, {"(Purple)", "3", "purple"}, {"(Magenta)", "4", "magenta"}, {"(Gradient Gold)", "5", "gradient"}, {"(Glacier)", "6", "glacier"}};
        if (n.startsWith("닉네임 색상 변경권")) {
            for (String[] pp : papers) {
                if (n.equals("닉네임 색상 변경권 " + pp[0])) {
                    e.setCancelled(true);
                    It.takeOneFromHand(p);
                    d.nickPapers.add(pp[2]);
                    applyNickColor(p, Integer.parseInt(pp[1]));
                    int hex = switch (pp[2]) {
                        case "red" -> 0xFF5555;
                        case "gold" -> 0xFFAA00;
                        case "purple" -> 0xAA00AA;
                        case "gradient" -> 0xFFB732;
                        case "glacier" -> 0x7FDDFB;
                        default -> 0xFF55FF;
                    };
                    Fx.helix(p, Fx.rgb(hex), Fx.rgb(0xFFFFFF), 0.7, 2.3, 25);
                    Fx.ring(p.getLocation().add(0, 2.2, 0), 0.5, 16, Fx.rgb(hex), 1.3f);
                    Msg.soundAt(p.getLocation(), "entity.player.levelup", 1, 1.6f);
                    return;
                }
            }
        }
    }

    /** 칭호 적용 연출: 마법 문자 + 금빛 고리 */
    private void titleFx(Player p) {
        Fx.p(p.getLocation().add(0, 1.2, 0), Particle.ENCHANT, 80, 0.8, 1.0);
        Fx.ring(p.getLocation().add(0, 2.3, 0), 0.55, 20, Fx.rgb(0xFFE066), 1.2f);
        Msg.soundAt(p.getLocation(), "block.enchantment_table.use", 1, 1.4f);
    }

    private void titleCmd(CommandSender s, String l, String[] a) {
        if (a.length < 2) {
            Msg.send(s, "&c/title <플레이어> <reset|supreme|exceed|peerless|slayer>");
            return;
        }
        String target = a[0];
        String group;
        String msg;
        switch (a[1].toLowerCase()) {
            case "reset" -> {
                group = "default";
                msg = "&c초기화 &7되었습니다.";
            }
            case "supreme" -> {
                group = "supreme";
                msg = "&b슈프림 &7으로 설정되었습니다.";
            }
            case "exceed" -> {
                group = "exceed";
                msg = "&6엑시드 &7로 설정되었습니다.";
            }
            case "peerless" -> {
                group = "peerless";
                msg = "&c레전드 &7로 설정되었습니다.";
            }
            case "slayer" -> {
                group = "slayer";
                msg = "&a천공의 심판자 &7로 설정되었습니다.";
            }
            default -> {
                return;
            }
        }
        Msg.send(s, "&a[System] &7플레이어 &f" + target + "&7 의 칭호가 " + msg);
        console("lp user " + target + " parent set " + group);
    }

    /* ======================= 킬 / 사망 ======================= */

    public static String itemDisplay(ItemStack it) {
        if (It.isAir(it)) return "맨손";
        if (it.hasItemMeta() && it.getItemMeta().hasDisplayName()) {
            return MiniMessage.miniMessage().serialize(it.getItemMeta().displayName());
        }
        return it.getType().name().toLowerCase().replace('_', ' ');
    }

    /** 좌표 표시: 크라운 표식을 적용했으면 금빛 */
    private static String posText(PlayerData kd, String pos) {
        return kd.crownKill ? "<gradient:#FFF176:#FFB732>(" + pos + ")</gradient>" : "&7(" + pos + ")";
    }

    /** 크라운 처치 알림 (한 줄) */
    private static final String CROWN = "<gradient:#FFF176:#FFB732><b>👑</b></gradient>";

    private void crownKillBroadcast(Component killer, Component victim, Component item, String pos) {
        // 한 줄: 👑 처치자 » 상대 ( 무기 ) (좌표) - 좌표까지 금빛
        Msg.broadcastFill(CROWN + " %killer% <color:#FFB732><b>»</b></color> %victim% &6( %item% &6) <gradient:#FFF176:#FFB732>(" + pos + ")</gradient>",
                "%killer%", killer, "%victim%", victim, "%item%", item);
        for (Player o : Bukkit.getOnlinePlayers()) Msg.sound(o, "block.bell.use", 0.5f, 0.7f);
    }

    /** 플레이어 이름: 마우스를 올리면 레벨 · 칭호 · 팀, 좌클릭하면 귓속말 입력 */
    public Component nameTag(Player p, String styled) {
        PlayerData d = data(p);
        GlobalData.Team team = global().teamOf(p.getUniqueId());
        String prefix = plugin.hooks().prefix(p);
        Component info = Msg.c(nickName(d.nickColor, p.getName(), "&e")
                + "%nl%&7레벨 : &e" + d.level
                + "%nl%&7칭호 : " + (prefix == null || prefix.isBlank() ? "&8없음" : prefix)
                + "%nl%&7소속 팀 : " + (team == null ? "&8없음" : "&f" + Msg.esc(team.display) + " &8(" + Msg.plainOf(TeamModule.roleTag(team.members.get(p.getUniqueId()))) + ")")
                + "%nl%%nl%&e좌클릭하여 귓속말 보내기");
        return Msg.c(styled).hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(info))
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand("/귓 " + p.getName() + " "));
    }

    /** 크라운 처치 연출: 쓰러진 자리 위에 거대한 황금 왕관 + 빛기둥 */
    private void crownFx(Player killer, Location at) {
        org.bukkit.Color gold = Fx.rgb(0xFFB732), light = Fx.rgb(0xFFF176);
        Location base = at.clone().add(0, 2.2, 0);
        Fx.pillar(at, 10, gold, Particle.END_ROD);
        Fx.ring(base, 1.0, 36, gold, 1.5f);
        Fx.ring(base.clone().add(0, 0.05, 0), 0.85, 28, light, 1.1f);
        for (int i = 0; i < 5; i++) {   // 왕관 뾰족한 다섯 갈래
            double a = Math.PI * 2 * i / 5;
            Location from = base.clone().add(Math.cos(a) * 1.0, 0, Math.sin(a) * 1.0);
            Fx.line(from, from.clone().add(0, 0.8, 0), light, 1.3f, 0.1);
            Fx.burst(from.clone().add(0, 0.85, 0), Particle.END_ROD, 4, 0.02);
        }
        Fx.shockwave(at.clone().add(0, 0.1, 0), gold, 3.5, 12);
        Fx.burst(base, Particle.TOTEM_OF_UNDYING, 60, 0.5);
        Msg.soundAt(at, "block.bell.resonate", 1.2f, 1.2f);
        Msg.sound(killer, "ui.toast.challenge_complete", 0.8f, 1.3f);
    }

    /** 원본 /testkill, /test11 */
    public void testKillBroadcast(Player p, String victimText, boolean crown) {
        Location l = p.getLocation();
        String pos = l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ();
        ItemStack tool = p.getInventory().getItemInMainHand();
        Component item = Msg.hoverItem(tool, itemDisplay(tool));
        Component k = nameTag(p, nickName(data(p).nickColor, p.getName(), "&7"));
        if (crown) {
            crownKillBroadcast(k, Msg.c(victimText), item, pos);
            crownFx(p, p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(2)));
        } else Msg.broadcastFill("%killer% &8» %victim% &7( %item% &7) &7(" + pos + ")", "%killer%", k, "%victim%", Msg.c(victimText), "%item%", item);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        PlayerData vd = data(victim);
        Player killer = victim.getKiller();

        // 드롭 방지 아이템 / 뉴비 보호
        boolean newbie = false;
        if (vd.newbieStart > 0) {
            if (System.currentTimeMillis() - vd.newbieStart < NEWBIE_PROTECT_MS) newbie = true;
            else if (!e.getDrops().isEmpty()) {
                vd.newbieStart = 0;
                Msg.send(victim, "&a[Newbie] &c뉴비 보호가 해제되었습니다.");
            }
        }
        Iterator<ItemStack> it = e.getDrops().iterator();
        while (it.hasNext()) {
            ItemStack drop = it.next();
            if (It.isAir(drop)) continue;
            if (newbie || WeaponModule.isKeepOnDeath(drop)) {
                vd.respawnItems.add(drop.clone());
                it.remove();
            }
        }

        Location l = victim.getLocation();
        String pos = l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ();
        if (killer != null && killer != victim) {
            PlayerData kd = data(killer);
            ItemStack tool = killer.getInventory().getItemInMainHand();
            String plain = Msg.name(tool);
            Component k = nameTag(killer, nickName(kd.nickColor, killer.getName(), "&7"));
            Component v = nameTag(victim, "&7" + victim.getName());
            // 크라운 표식: 특수 킬로그에도 👑 와 금빛 화살표
            String crown = kd.crownKill ? CROWN + " " : "", arrow = kd.crownKill ? "<color:#FFB732><b>»</b></color>" : "&8»";
            if (plain.equals(Items.N_CLAYMORE_SD)) {
                Msg.broadcast(" ");
                Msg.broadcastFill(crown + "<gold><obf>D</obf></gold> %killer% " + arrow + " %victim% <gold><obf>D</obf></gold>", "%killer%", k, "%victim%", v);
                Msg.broadcastItem("<gold><obf>D</obf></gold> " + Msg.ITEM + " " + posText(kd, pos) + " <gold><obf>D</obf></gold>", Msg.hoverItem(tool,
                        "&6&l[&a&l⚡&6&l] <gradient:#FFF200:#F6E47C:#FFFCD4><italic:false><b>[Lv.</b><obf><b>sd</b></obf><b>] 천</b><b>공의 </b><b>심판자 클레이모어</b></gradient>"));
                Msg.broadcast(" ");
                if (kd.crownKill) crownFx(killer, victim.getLocation());   // 크라운 표식: 무기와 관계없이 왕관 연출
            } else if (plain.equals(Items.N_FROST)) {
                Msg.broadcast(" ");
                Msg.broadcastFill(crown + "<color:#B0E4F8><obf>D</obf></color> %killer% " + arrow + " %victim% <color:#B0E4F8><obf>D</obf></color>", "%killer%", k, "%victim%", v);
                Msg.broadcastItem("<color:#B0E4F8><obf>D</obf></color> " + Msg.ITEM + " " + posText(kd, pos) + " <color:#B0E4F8><obf>D</obf></color>", Msg.hoverItem(tool,
                        "<gradient:#39C7FF:#D2FFF8:#00FFFF><italic:false><b>[❆] [Lv.</b><obf><b>D</b></obf><obf><b>D</b></obf><b>] </b><b>프로스트 그레이브</b></gradient>"));
                Msg.broadcast(" ");
                if (kd.crownKill) crownFx(killer, victim.getLocation());
            } else if (kd.crownKill) {
                crownKillBroadcast(k, v, Msg.hoverItem(tool, itemDisplay(tool)), pos);
                crownFx(killer, victim.getLocation());
            } else {
                Msg.broadcastFill("%killer% &8» %victim% &7( %item% &7) &7(" + pos + ")", "%killer%", k, "%victim%", v, "%item%", Msg.hoverItem(tool, itemDisplay(tool)));
            }
            return;
        }

        String color = nickColor(vd.nickColor, "&f");
        String head = "<gradient:#FE0000:#800000>Deat</gradient><gradient:#800000:#800000>h</gradient> &7>> " + nickName(vd.nickColor, victim.getName(), "&f");
        Entity attacker = attackerOf(victim.getLastDamageCause());
        if (attacker == null) {
            EntityDamageEvent.DamageCause cause = victim.getLastDamageCause() == null ? null : victim.getLastDamageCause().getCause();
            String what;
            if (cause == null) what = "사망했습니다.";
            else switch (cause) {
                case FALL -> what = "높은 곳에서 떨어졌습니다.";
                case FIRE, FIRE_TICK, LAVA -> what = "불에 타버렸습니다.";
                case DROWNING -> what = "익사했습니다.";
                case STARVATION -> what = "아사했습니다.";
                case SUFFOCATION -> what = "질식했습니다.";
                case VOID -> what = "공허에서 소멸했습니다.";
                default -> what = "사망했습니다.";
            }
            Msg.broadcast(head + " &f이(가) " + what);
        } else if (!(attacker instanceof Player)) {
            String type = attacker.getType().name().toLowerCase();
            String mob = type.contains("zombie") ? "좀비" : type.contains("skeleton") ? "스켈레톤" : type.contains("creeper") ? "크리퍼"
                    : type.contains("spider") ? "거미" : type.contains("enderman") ? "엔더맨" : "몬스터";
            Msg.broadcast(head + " &f(이)가 &e" + mob + "&f에게 사망했습니다.");
        }
    }

    public static Entity attackerOf(EntityDamageEvent cause) {
        if (!(cause instanceof EntityDamageByEntityEvent ee)) return null;
        Entity d = ee.getDamager();
        if (d instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) return shooter;
        return d;
    }

    /** 공격자(투사체면 쏜 사람) 중 플레이어 */
    public static Player playerAttacker(EntityDamageByEntityEvent e) {
        Entity d = e.getDamager();
        if (d instanceof Player p) return p;
        if (d instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        PlayerData d = data(p);
        if (!d.respawnItems.isEmpty()) {
            // 1틱 뒤 지급. 그 사이 나가면 데이터에 남겨 두었다가 다음 리스폰/접속 때 지급
            later(1, () -> {
                if (!p.isOnline() || d.respawnItems.isEmpty()) return;
                List<ItemStack> items = new ArrayList<>(d.respawnItems);
                d.respawnItems.clear();
                It.give(p, items);
                plugin.store().save(d);
            });
        }
        BossModule boss = plugin.module(BossModule.class);
        if (boss != null && boss.consumeRespawn(p)) return;   // 천공의 제단 사망: 보스 모듈이 위치를 정함
        if (e.isBedSpawn() || e.isAnchorSpawn()) {
            Msg.title(p, "", "", 0, 20, 0);
        } else {
            later(1, () -> {
                tpWorld(p, "spawn");
                Msg.title(p, "", "&7리스폰 지점이 설정되지 않아 스폰으로 이동되었습니다.", 10, 40, 10);
            });
        }
    }

    /* ======================= 텔레포트 ======================= */

    private void startTeleport(Player p, String label, String warp) {
        if (teleporting.containsKey(p.getUniqueId())) {
            Msg.send(p, "&a[🌍] &c이미 텔레포트 대기 중입니다!");
            return;
        }
        Msg.send(p, "&a[🌍] &73초 뒤 " + label + "으로 이동합니다... 움직이면 이동이 취소됩니다");
        Msg.title(p, "<gradient:#FE4E4E:#00FFD9>✦ " + label + " 이동 중 ✦</gradient>", "&73초 뒤 이동됩니다..", 10, 20, 10);
        Msg.sound(p, "block.note_block.chime", 0.5f, 1);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            teleporting.remove(p.getUniqueId());
            if (!p.isOnline()) return;
            Location dest = global().warps.get(warp);
            if (dest == null) dest = spawnOf(warp.equals("spawn") ? "spawn" : "wild");
            if (dest != null) p.teleport(dest);
            Msg.send(p, "&a[🌍] &a" + label + "으로 이동되었습니다!");
            Msg.sound(p, "entity.enderman.teleport", 0.5f, 1);
        }, 60L);
        teleporting.put(p.getUniqueId(), task);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!teleporting.containsKey(e.getPlayer().getUniqueId())) return;
        if (e.getFrom().distanceSquared(e.getTo()) <= 0.01) return;
        BukkitTask t = teleporting.remove(e.getPlayer().getUniqueId());
        if (t != null) t.cancel();
        Msg.send(e.getPlayer(), "&a[🌍] &c대기 중 이동하여 워프에 실패했습니다.");
        Msg.sound(e.getPlayer(), "block.note_block.bass", 1, 1);
    }

    private void randomSpawnCmd(CommandSender s, String l, String[] a) {
        if (!Cmd.isPlayer(s)) return;
        Player p = (Player) s;
        if (inWorld(p, "wild")) {
            Msg.send(p, "&c&l[!] &c이 명령어는 스폰에서만 사용할 수 있습니다.");
            return;
        }
        long now = System.currentTimeMillis();
        long last = randomSpawnCooldown.getOrDefault(p.getUniqueId(), 0L);
        long left = 20_000 - (now - last);
        if (left > 0) {
            Msg.send(p, "&a[🌍] &c아직 랜덤스폰을 사용할 수 없습니다. " + Math.max(1, left / 1000) + "초 후에 다시 시도해주세요.");
            Msg.sound(p, "block.note_block.bass", 1, 1);
            return;
        }
        randomSpawnCooldown.put(p.getUniqueId(), now);
        tpWorld(p, "wild");
        later(20, () -> {
            if (p.isOnline()) console("rtp player " + p.getName());
        });
    }

    /* ======================= 밴 ======================= */

    public static String banCode() {
        String pool = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) sb.append(pool.charAt(ThreadLocalRandom.current().nextInt(pool.length())));
        return sb.toString();
    }

    private void banCmd(CommandSender s, String l, String[] a) {
        if (a.length < 1) {
            Msg.send(s, "&c[&fSecurity&c] §c차단할 플레이어의 닉네임을 입력하세요.");
            return;
        }
        OfflinePlayer t = Cmd.offline(a[0]);
        if (t == null) {
            Msg.send(s, "&c플레이어를 찾을 수 없습니다.");
            return;
        }
        String reason = a.length >= 2 ? Cmd.rest(a, 1) : "사유 미작성";
        GlobalData.Ban ban = new GlobalData.Ban();
        ban.reason = reason;
        ban.code = banCode();
        global().bans.put(t.getUniqueId(), ban);
        plugin.store().saveGlobal();
        if (t.getPlayer() != null) t.getPlayer().kick(Msg.c(banMessage(ban)));
        Msg.broadcast("&c-----------------------------------------------------%nl%%nl%§c&l[&f&l Security Notice &c&l] &c&l이용약관 위반 유저 차단 알림%nl%&7%nl%&7&l플레이어 이름 : &e" + a[0] + "%nl%&7&l사유 : &c" + Msg.esc(reason) + "%nl%%nl%&c-----------------------------------------------------");
        Msg.send(s, "&c[&fSecurity&c] §a" + a[0] + "님을 차단했습니다. 코드: ##" + ban.code);
    }

    private void unbanCmd(CommandSender s, String l, String[] a) {
        OfflinePlayer t = Cmd.offline(Cmd.arg(a, 0));
        if (t == null) {
            Msg.send(s, "&c플레이어를 찾을 수 없습니다.");
            return;
        }
        global().bans.remove(t.getUniqueId());
        plugin.store().saveGlobal();
        Msg.send(s, "&c[&fSecurity&c] §a" + a[0] + "님의 차단을 해제했습니다.");
    }

    /* ======================= 점검 모드 / MOTD ======================= */

    private void maintenanceCmd(CommandSender s, String l, String[] a) {
        if (a.length == 0) {
            Msg.send(s, "&a[System] &7사용법: /점검모드 [켜기 / 끄기]");
            Msg.send(s, global().maintenance ? "&7현재 상태 : &c점검 중" : "&7현재 상태 : &a정상 운영 중");
            return;
        }
        if (a[0].equals("켜기")) {
            for (Player o : Bukkit.getOnlinePlayers()) if (o != s) o.kick(Msg.c("§c§l서버 점검으로 인해 추방되었습니다."));
            global().maintenance = true;
            plugin.store().saveGlobal();
            Msg.send(s, "&c[🛑] 서버 점검 모드를 활성화했습니다.");
        } else if (a[0].equals("끄기")) {
            global().maintenance = false;
            plugin.store().saveGlobal();
            Msg.send(s, "&a[🟢] 서버 점검 모드를 비활성화했습니다.");
        }
    }

    @EventHandler
    public void onPing(PaperServerListPingEvent e) {
        if (global().maintenance) {
            e.setMaxPlayers(0);
            e.motd(Msg.c("            &c&l[ 🛑 SERVER MAINTENANCE ]               <gradient:#FF1313:#DD9595><b>현재 서버 내부 데이터 점검 중입니다. 잠시 후 접속해주세요."));
        } else {
            e.motd(Msg.c("       &e&l&m&r &b&l>> <gradient:#0077B6:#90E0EF><italic:false><b>Wave O</b></gradient><gradient:#90E0EF:#CAF0F8><italic:false><b>nli</b><b>ne</b></gradient> &b&l<< &7[1.20-1.21.11]%nl%      <gradient:#48CAE4:#ADE8F4><italic:false><b>최신 반야생 약탈</b><b> 서버</b><b> | 다양한 이벤트 진행 중</b></gradient>"));
        }
    }

    /* ======================= 월드 규칙 ======================= */

    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
        if (e.getEntity() instanceof EnderCrystal) e.setCancelled(true);
    }

    @EventHandler
    public void onExplosionDamage(EntityDamageEvent e) {
        if (e instanceof EntityDamageByEntityEvent ee && ee.getDamager() instanceof EnderCrystal) {
            e.setCancelled(true);
            return;
        }
        if (e.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION && !(e instanceof EntityDamageByEntityEvent)) {
            String w = e.getEntity().getWorld().getName();
            if (w.equalsIgnoreCase(world("nether")) || w.equalsIgnoreCase(world("end"))) e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        Block b = e.getClickedBlock();
        Player p = e.getPlayer();
        if (b.getType() == Material.RESPAWN_ANCHOR && !inWorld(p, "nether")) {
            e.setCancelled(true);
            Msg.send(p, "&c&l[!] 네더 이외의 월드에선 리스폰 정박기를 사용할 수 없습니다.");
            Msg.sound(p, "block.note_block.bass", 1, 1);
        } else if (Tag.BEDS.isTagged(b.getType()) && (inWorld(p, "nether") || inWorld(p, "end"))) {
            e.setCancelled(true);
            Msg.send(p, "&c&l[!] &c해당 월드에서는 침대를 사용할 수 없습니다.");
            Msg.sound(p, "block.note_block.bass", 1, 1);
        }
    }

    @EventHandler
    public void onPhantom(CreatureSpawnEvent e) {
        if (e.getEntityType() == EntityType.PHANTOM) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRandomSpawnCommand(PlayerCommandPreprocessEvent e) {
        // test.sk: 야생에서 /랜덤스폰 금지 (명령어 안에서도 확인하지만 다른 플러그인 명령어보다 먼저 막기 위해)
        String m = e.getMessage().toLowerCase();
        if ((m.equals("/랜덤스폰") || m.startsWith("/랜덤스폰 ")) && inWorld(e.getPlayer(), "wild")) {
            e.setCancelled(true);
            Msg.send(e.getPlayer(), "&c&l[!] &c이 명령어는 스폰에서만 사용할 수 있습니다.");
        }
    }

    @Override
    public void secondTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (inWorld(p, "spawn")) p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30, 4, false, false, true));
        }
    }

    /* ======================= /openinv, /stats, /귓속말 ======================= */

    private void openInvCmd(CommandSender s, String l, String[] a) {
        if (!Cmd.isPlayer(s)) return;
        Player t = Cmd.online(Cmd.arg(a, 0));
        if (t == null) {
            Msg.send(s, "&c접속 중인 플레이어가 아닙니다.");
            return;
        }
        Gui g = new Gui(5, "&8&l" + t.getName() + "의 인벤토리");
        ItemStack[] c = t.getInventory().getStorageContents();
        for (int i = 0; i < Math.min(36, c.length); i++) g.set(i, c[i] == null ? null : c[i].clone());
        g.set(36, clone(t.getInventory().getHelmet()));
        g.set(37, clone(t.getInventory().getChestplate()));
        g.set(38, clone(t.getInventory().getLeggings()));
        g.set(39, clone(t.getInventory().getBoots()));
        g.set(40, clone(t.getInventory().getItemInOffHand()));
        g.set(44, It.close(), e -> e.getWhoClicked().closeInventory());
        g.open((Player) s);
    }

    private static ItemStack clone(ItemStack it) {
        return it == null ? null : it.clone();
    }

    private void statsCmd(CommandSender s, String l, String[] a) {
        if (!Cmd.isPlayer(s)) return;
        if (a.length == 0) {
            Msg.send(s, "&6[System] &c확인할 플레이어의 이름을 입력하세요.");
            return;
        }
        Player t = Cmd.online(a[0]);
        if (t == null) {
            Msg.send(s, "&c접속 중인 플레이어가 아닙니다.");
            return;
        }
        PlayerData d = data(t);
        GlobalData.Team team = global().teamOf(t.getUniqueId());
        Gui g = new Gui(1, "&8&l유저 정보");
        g.set(0, It.of(Material.PLAYER_HEAD).skull(t).name("&e" + t.getName())
                .lore("&7레벨 : &e" + d.level + "%nl%&7소속된 팀 : " + (team == null ? "&c없음" : Msg.esc(team.display)) + "%nl%&7플레이어 랭크 : " + plugin.hooks().prefix(t)).build());
        g.set(1, It.pane(Material.BLACK_STAINED_GLASS_PANE, " "));
        g.set(7, It.pane(Material.BLACK_STAINED_GLASS_PANE, " "));
        g.set(2, clone(t.getInventory().getHelmet()));
        g.set(3, clone(t.getInventory().getChestplate()));
        g.set(4, clone(t.getInventory().getLeggings()));
        g.set(5, clone(t.getInventory().getBoots()));
        g.set(6, clone(t.getInventory().getItemInMainHand()));
        g.set(8, It.close(), e -> e.getWhoClicked().closeInventory());
        g.fill().open((Player) s);
    }

    private void whisperCmd(CommandSender s, String l, String[] a) {
        if (!Cmd.isPlayer(s)) return;
        Player p = (Player) s;
        if (a.length < 1) {
            Msg.send(p, "&c&l[📬] &c보낼 대상의 이름을 적어주세요. &7(/귓 [닉네임] [할말])");
            return;
        }
        if (a.length < 2) {
            Msg.send(p, "&c&l[📬] &c보낼 내용을 입력해 주세요.");
            return;
        }
        Player t = Cmd.online(a[0]);
        if (t == null) {
            Msg.send(p, "&c&l[📬] &c해당 플레이어는 접속 중이 아닙니다.");
            return;
        }
        whisper(p, t, Cmd.rest(a, 1));
    }

    private final Map<UUID, UUID> lastWhisper = new HashMap<>();

    /** 귓속말 전송. 받은 사람은 보낸 사람 이름을 누르면 답장 */
    private void whisper(Player p, Player t, String msg) {
        Component text = Component.text(msg);
        Component toName = nameTag(t, "&e" + t.getName());
        Component fromName = Msg.c("&e" + p.getName())
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Msg.c("&e" + p.getName() + " &7님에게%nl%&a좌클릭하여 답장하기")))
                .clickEvent(ClickEvent.suggestCommand("/귓 " + p.getName() + " "));
        p.sendMessage(Msg.fill("&6[&e나 ➝ %to%&6] &7: &f", "%to%", toName).append(text));
        t.sendMessage(Msg.fill("&6[%from% &e➝ 나&6] &7: &f", "%from%", fromName).append(text)
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Msg.c("&a클릭하여 " + p.getName() + "님에게 답장하기")))
                .clickEvent(ClickEvent.suggestCommand("/귓 " + p.getName() + " ")));
        Msg.sound(t, "block.note_block.pling", 0.5f, 1.5f);
        lastWhisper.put(t.getUniqueId(), p.getUniqueId());
        lastWhisper.put(p.getUniqueId(), t.getUniqueId());
    }

    /** /답장 [할말]: 마지막으로 귓속말을 주고받은 사람에게 */
    private void replyCmd(CommandSender s, String l, String[] a) {
        if (!Cmd.isPlayer(s)) return;
        Player p = (Player) s;
        UUID last = lastWhisper.get(p.getUniqueId());
        Player t = last == null ? null : Bukkit.getPlayer(last);
        if (t == null) {
            Msg.send(p, "&c&l[📬] &c답장할 상대가 없거나 접속 중이 아닙니다.");
            return;
        }
        if (a.length == 0) {
            Msg.send(p, "&c&l[📬] &c보낼 내용을 입력해 주세요. &7(/답장 [할말])");
            return;
        }
        whisper(p, t, Cmd.rest(a, 0));
    }

    public static boolean isLiving(Entity e) {
        return e instanceof LivingEntity;
    }
}
