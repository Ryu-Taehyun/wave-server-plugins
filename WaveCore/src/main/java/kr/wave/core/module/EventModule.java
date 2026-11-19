package kr.wave.core.module;

import io.papermc.paper.event.player.AsyncChatEvent;
import kr.wave.core.Items;
import kr.wave.core.WaveCore;
import kr.wave.core.data.PlayerData;
import kr.wave.core.util.Cmd;
import kr.wave.core.util.Fx;
import kr.wave.core.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 서버 이벤트
 * - 보급 상자 낙하: 일정 시간마다 야생 무작위 좌표에 보급 상자, 먼저 연 사람이 차지
 * - 채팅 퀴즈 · 타자 대결: 일정 시간마다 문제, 먼저 맞힌 사람에게 캐시
 */
public class EventModule extends Module {

    private static final String DROP_TAG = "<gradient:#FFB347:#FFE29A><b>[보급]</b></gradient> ";
    private static final String QUIZ_TAG = "<gradient:#7FD8FF:#C2FFF5><b>[퀴즈]</b></gradient> ";

    public EventModule(WaveCore plugin) {
        super(plugin);
    }

    @Override
    public void enable() {
        cmd("보급", ADMIN, this::dropCmd, (s, a) -> a.length <= 1 ? List.of("지금", "정보", "취소") : List.of(), "supplydrop");
        cmd("퀴즈", ADMIN, this::quizCmd, (s, a) -> a.length <= 1 ? List.of("지금", "취소") : List.of(), "chatquiz");
        dropTimer = interval("supply-drop.interval-minutes", 60);
        quizTimer = interval("chat-quiz.interval-minutes", 15);
    }

    @Override
    public void disable() {
        endDrop(null);
    }

    private int interval(String key, int def) {
        return Math.max(1, plugin.getConfig().getInt(key, def)) * 60;
    }

    @Override
    public void secondTick() {
        var cfg = plugin.getConfig();
        int online = Bukkit.getOnlinePlayers().size();
        // 보급 상자
        if (drop != null) {
            dropLeft--;
            Location c = drop.getLocation().add(0.5, 1, 0.5);
            Fx.p(c, Particle.END_ROD, 6, 0.15, 0.02);
            for (int y = 2; y < 40; y += 3) Fx.p(c.clone().add(0, y, 0), Particle.FIREWORK, 1, 0.05, 0);
            if (dropLeft == cfg.getInt("supply-drop.despawn-minutes", 15) * 30 && !dropOpened)
                Msg.broadcast(DROP_TAG + "&f보급 상자가 아직 &e" + pos(drop) + "&f에 있습니다! &7(남은 시간 " + (dropLeft / 60) + "분)");
            if (dropLeft <= 0) endDrop(DROP_TAG + "&7보급 상자가 회수되었습니다.");
        } else if (cfg.getBoolean("supply-drop.enabled", true) && --dropTimer <= 0) {
            dropTimer = interval("supply-drop.interval-minutes", 60);
            if (online >= cfg.getInt("supply-drop.min-players", 5)) startDrop(null);
        }
        // 채팅 퀴즈
        if (quiz != null) {
            if (--quizLeft <= 0) {
                Msg.broadcast(QUIZ_TAG + "&7시간 초과! 정답은 &f" + quiz.shown + "&7였습니다.");
                quiz = null;
            }
        } else if (cfg.getBoolean("chat-quiz.enabled", true) && --quizTimer <= 0) {
            quizTimer = interval("chat-quiz.interval-minutes", 15);
            if (online >= cfg.getInt("chat-quiz.min-players", 3)) startQuiz();
        }
    }

    /* ======================= 보급 상자 ======================= */

    private Block drop;
    private int dropLeft, dropTimer;
    private boolean dropOpened;

    private static String pos(Block b) {
        return "X " + b.getX() + " / Y " + b.getY() + " / Z " + b.getZ();
    }

    /** 보급 상자 떨어뜨리기. by = 관리자(없으면 자동) */
    public boolean startDrop(CommandSender by) {
        if (drop != null) {
            if (by != null) Msg.send(by, "&c이미 보급 상자가 있습니다: &f" + pos(drop));
            return false;
        }
        var cfg = plugin.getConfig();
        World w = Bukkit.getWorld(cfg.getString("supply-drop.world", world("wild")));
        if (w == null) {
            if (by != null) Msg.send(by, "&c보급 월드를 찾을 수 없습니다: &f" + cfg.getString("supply-drop.world", world("wild")));
            return false;
        }
        Block spot = pickSpot(w);
        if (spot == null) {
            if (by != null) Msg.send(by, "&c알맞은 위치를 찾지 못했습니다. 다시 시도해 주세요.");
            return false;
        }
        spot.setType(Material.CHEST);   // 겉모습만 상자. 열면 보급 상자 창(4줄)이 열림
        dropGui = buildGui();
        drop = spot;
        dropOpened = false;
        dropLeft = cfg.getInt("supply-drop.despawn-minutes", 15) * 60;
        Location c = spot.getLocation().add(0.5, 1, 0.5);
        Fx.burst(c, Particle.FIREWORK, 60, 0.4);
        Msg.soundAt(c, "entity.firework_rocket.large_blast", 2, 0.8f);
        Msg.broadcast(" ");
        Msg.broadcast(DROP_TAG + "&f보급 상자가 떨어졌습니다!");
        Msg.broadcast(DROP_TAG + "&f위치 : &e" + w.getName() + " " + pos(spot) + " &7(" + (dropLeft / 60) + "분 뒤 회수)");
        Msg.broadcast(" ");
        for (Player o : Bukkit.getOnlinePlayers()) {
            Msg.title(o, "<gradient:#FFB347:#FFE29A><b>보급 상자 낙하!</b></gradient>", "&e" + pos(spot), 5, 50, 10);
            Msg.sound(o, "entity.firework_rocket.launch", 0.8f, 0.8f);
        }
        if (by != null) Msg.send(by, "&a[!] 보급 상자를 떨어뜨렸습니다: &f" + pos(spot));
        return true;
    }

    /** 야생에서 상자 놓을 자리: 반경 안 무작위, 물·용암·보호구역은 제외 */
    private Block pickSpot(World w) {
        var cfg = plugin.getConfig();
        int min = Math.max(0, cfg.getInt("supply-drop.min-radius", 300)), max = Math.max(min + 1, cfg.getInt("supply-drop.max-radius", 3000));
        Location center = w.getSpawnLocation();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 25; i++) {
            double ang = r.nextDouble(Math.PI * 2), dist = r.nextDouble(min, max);
            int x = center.getBlockX() + (int) (Math.cos(ang) * dist), z = center.getBlockZ() + (int) (Math.sin(ang) * dist);
            if (!w.getWorldBorder().isInside(new Location(w, x, 64, z))) continue;
            Block top = w.getHighestBlockAt(x, z);
            Material m = top.getType();
            if (top.isLiquid() || m == Material.WATER || m == Material.LAVA || m.name().endsWith("LEAVES") || m == Material.ICE || m == Material.BEDROCK) continue;
            Block spot = top.getRelative(0, 1, 0);
            if (!spot.getType().isAir()) continue;
            Location l = spot.getLocation();
            if (plugin.hooks().pvpDenied(l) || plugin.hooks().inRegion(l, cfg.getString("protect-region", "protect"))) continue;
            return spot;
        }
        return null;
    }

    /** 보급품이 들어가는 칸 (4줄 창의 안쪽 2줄, 왼쪽 위부터 순서대로) */
    static final int[] LOOT_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    private kr.wave.core.util.Gui dropGui;

    /** 4줄 창: 테두리는 판유리, 보급품은 안쪽 칸에 순서대로. 여러 명이 같은 창을 봄 */
    private kr.wave.core.util.Gui buildGui() {
        kr.wave.core.util.Gui g = new kr.wave.core.util.Gui(4, "<gradient:#FFB347:#FFE29A><b>보급 상자</b></gradient>");
        g.put("supply", true);
        List<ItemStack> loot = rollLoot();
        for (int i = 0; i < LOOT_SLOTS.length; i++) {
            int slot = LOOT_SLOTS[i];
            if (i < loot.size()) g.set(slot, loot.get(i));
            g.on(slot, e -> take(e, g, slot));
            g.quiet(slot);
        }
        ItemStack border = kr.wave.core.util.It.pane(Material.ORANGE_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 36; i++) {
            boolean inner = false;
            for (int s2 : LOOT_SLOTS) if (s2 == i) inner = true;
            if (!inner) g.set(i, border);
        }
        return g;
    }

    /** 칸을 클릭하면 그 아이템을 가져감 (인벤토리가 차면 남는 만큼 그대로 둠) */
    private void take(org.bukkit.event.inventory.InventoryClickEvent e, kr.wave.core.util.Gui g, int slot) {
        if (!(e.getWhoClicked() instanceof Player p) || g != dropGui) return;
        ItemStack it = g.getInventory().getItem(slot);
        if (it == null || it.getType().isAir()) return;
        var left = p.getInventory().addItem(it.clone());
        if (left.isEmpty()) g.getInventory().setItem(slot, null);
        else {
            g.getInventory().setItem(slot, left.values().iterator().next());
            Msg.send(p, "&c인벤토리가 가득 찼습니다.");
        }
        Msg.sound(p, "entity.item.pickup", 0.6f, 1.2f);
        boolean empty = true;
        for (int s2 : LOOT_SLOTS) {
            ItemStack x = g.getInventory().getItem(s2);
            if (x != null && !x.getType().isAir()) empty = false;
        }
        if (empty) later(1, () -> endDrop(DROP_TAG + "&e" + p.getName() + "&f님이 보급 상자를 모두 가져갔습니다."));
    }

    /** config supply-drop.loot 에서 rolls 번 뽑기 (최대 14묶음) */
    private List<ItemStack> rollLoot() {
        List<ItemStack> out = new ArrayList<>();
        var cfg = plugin.getConfig();
        List<Map<?, ?>> pool = cfg.getMapList("supply-drop.loot");
        if (pool.isEmpty()) return out;
        double total = 0;
        for (Map<?, ?> m : pool) total += num(m.get("weight"), 1);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int rolls = Math.max(1, Math.min(LOOT_SLOTS.length, cfg.getInt("supply-drop.rolls", 6)));
        for (int k = 0; k < rolls; k++) {
            double pick = r.nextDouble(total);
            Map<?, ?> chosen = pool.get(pool.size() - 1);
            for (Map<?, ?> m : pool) {
                pick -= num(m.get("weight"), 1);
                if (pick < 0) {
                    chosen = m;
                    break;
                }
            }
            ItemStack it = lootItem(chosen);
            if (it == null) continue;
            boolean merged = false;   // 같은 아이템이 또 나오면 한 칸에 합침 (최대 묶음 수까지)
            for (ItemStack o : out) {
                if (o.isSimilar(it) && o.getAmount() + it.getAmount() <= o.getMaxStackSize()) {
                    o.setAmount(o.getAmount() + it.getAmount());
                    merged = true;
                    break;
                }
            }
            if (!merged) out.add(it);
        }
        return out;
    }

    private static double num(Object o, double def) {
        if (o instanceof Number n) return n.doubleValue();
        try {
            return o == null ? def : Double.parseDouble(o.toString());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private ItemStack lootItem(Map<?, ?> m) {
        int min = (int) num(m.get("min"), 1), max = Math.max(min, (int) num(m.get("max"), min));
        int amount = ThreadLocalRandom.current().nextInt(min, max + 1);
        ItemStack it = null;
        if (m.get("special") != null) it = Items.special(m.get("special").toString());
        else if (m.get("material") != null) {
            Material mat = Material.matchMaterial(m.get("material").toString());
            if (mat != null && mat.isItem()) it = new ItemStack(mat);
        }
        if (it == null) {
            plugin.getLogger().warning("supply-drop.loot 항목을 해석할 수 없습니다: " + m);
            return null;
        }
        it.setAmount(Math.max(1, Math.min(it.getMaxStackSize(), amount)));
        return it;
    }

    private boolean isDrop(Block b) {
        return drop != null && b != null && b.equals(drop);
    }

    /** 끝내기: 상자 지우기 (msg 가 있으면 공지) */
    private void endDrop(String msg) {
        if (drop == null) return;
        Block b = drop;
        drop = null;
        if (dropGui != null) {
            for (var v : new ArrayList<>(dropGui.getInventory().getViewers())) v.closeInventory();
            dropGui = null;
        }
        b.setType(Material.AIR);
        Fx.burst(b.getLocation().add(0.5, 0.5, 0.5), Particle.CLOUD, 20, 0.3);
        if (msg != null) Msg.broadcast(msg);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDropOpen(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || !isDrop(e.getClickedBlock())) return;
        e.setCancelled(true);   // 진짜 상자 창 대신 보급 상자 창
        Player p = e.getPlayer();
        if (dropGui != null) dropGui.open(p);
        if (dropOpened) return;
        dropOpened = true;
        Msg.broadcast(DROP_TAG + "&e" + p.getName() + "&f님이 보급 상자를 열었습니다!");
        Msg.soundAt(drop.getLocation(), "block.chest.open", 1, 0.8f);
        Fx.burst(drop.getLocation().add(0.5, 1, 0.5), Particle.TOTEM_OF_UNDYING, 40, 0.4);
        // 연 뒤에는 2분 안에 회수 (남은 시간이 더 짧으면 그대로)
        dropLeft = Math.min(dropLeft, plugin.getConfig().getInt("supply-drop.after-open-seconds", 120));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDropBreak(BlockBreakEvent e) {
        if (!isDrop(e.getBlock())) return;
        e.setCancelled(true);
        Msg.send(e.getPlayer(), "&c보급 상자는 부술 수 없습니다. 열어서 가져가세요.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onDropExplode(EntityExplodeEvent e) {
        if (drop != null) e.blockList().remove(drop);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDropExplode2(BlockExplodeEvent e) {
        if (drop != null) e.blockList().remove(drop);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDropHopper(InventoryMoveItemEvent e) {
        if (drop != null && e.getSource().getHolder() instanceof Chest c && c.getBlock().equals(drop)) e.setCancelled(true);
    }

    private void dropCmd(CommandSender s, String l, String[] a) {
        String sub = a.length == 0 ? "" : a[0];
        switch (sub) {
            case "지금" -> startDrop(s);
            case "취소" -> {
                if (drop == null) Msg.send(s, "&7떨어진 보급 상자가 없습니다.");
                else {
                    endDrop(DROP_TAG + "&7보급 상자가 회수되었습니다.");
                    Msg.send(s, "&a[!] 보급 상자를 회수했습니다.");
                }
            }
            case "정보" -> Msg.send(s, drop != null
                    ? "&e보급 상자 : &f" + drop.getWorld().getName() + " " + pos(drop) + " &7(남은 " + dropLeft + "초, " + (dropOpened ? "열림" : "안 열림") + ")"
                    : "&e다음 보급까지 : &f" + (dropTimer / 60) + "분 " + (dropTimer % 60) + "초");
            default -> Msg.send(s, "&e/" + l + " 지금 &7- 바로 떨어뜨리기   &e/" + l + " 정보   &e/" + l + " 취소");
        }
    }

    /* ======================= 채팅 퀴즈 · 타자 대결 ======================= */

    private record Quiz(String question, String answer, String shown, long started) {
    }

    private volatile Quiz quiz;
    private int quizLeft, quizTimer;

    public void startQuiz() {
        var cfg = plugin.getConfig();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<String> words = cfg.getStringList("chat-quiz.words");
        List<Map<?, ?>> qa = cfg.getMapList("chat-quiz.questions");
        int kind = r.nextInt(3);   // 0 타자, 1 계산, 2 상식
        if (kind == 0 && words.isEmpty()) kind = 1;
        if (kind == 2 && qa.isEmpty()) kind = 1;
        Quiz q;
        long now = System.currentTimeMillis();
        if (kind == 0) {
            String w = words.get(r.nextInt(words.size()));
            q = new Quiz("&f다음 문장을 가장 먼저 입력하세요! &e" + w, w, w, now);
        } else if (kind == 1) {
            int x = r.nextInt(12, 100), y = r.nextInt(2, 30);
            boolean mul = r.nextBoolean();
            if (mul) y = r.nextInt(2, 13);
            String ans = String.valueOf(mul ? x * y : x + y);
            q = new Quiz("&f계산 문제! &e" + x + (mul ? " × " : " + ") + y + " = ?", ans, ans, now);
        } else {
            Map<?, ?> m = qa.get(r.nextInt(qa.size()));
            String ans = String.valueOf(m.get("a"));
            q = new Quiz("&f상식 문제! &e" + m.get("q"), ans, ans, now);
        }
        quiz = q;
        quizLeft = cfg.getInt("chat-quiz.time-seconds", 30);
        Msg.broadcast(" ");
        Msg.broadcast(QUIZ_TAG + q.question);
        Msg.broadcast(QUIZ_TAG + "&7" + quizLeft + "초 안에 채팅으로 먼저 맞히면 &e" + Msg.num(cfg.getLong("chat-quiz.reward-min", 1000)) + " ~ "
                + Msg.num(cfg.getLong("chat-quiz.reward-max", 5000)) + "원&7!");
        Msg.broadcast(" ");
        for (Player o : Bukkit.getOnlinePlayers()) Msg.sound(o, "block.note_block.bell", 0.7f, 1.2f);
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().replace(" ", "").toLowerCase();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onQuizChat(AsyncChatEvent e) {
        Quiz q = quiz;
        if (q == null) return;
        String text = Msg.plain(e.message());
        if (!norm(text).equals(norm(q.answer))) return;
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> win(p, q));
    }

    private void win(Player p, Quiz q) {
        if (quiz != q || !p.isOnline()) return;   // 이미 누가 맞힘
        quiz = null;
        var cfg = plugin.getConfig();
        long min = cfg.getLong("chat-quiz.reward-min", 1000), max = Math.max(min, cfg.getLong("chat-quiz.reward-max", 5000));
        long reward = min == max ? min : ThreadLocalRandom.current().nextLong(min, max + 1);
        PlayerData d = data(p);
        d.money += reward;
        d.moneyTotal += reward;
        plugin.store().save(d);
        double sec = (System.currentTimeMillis() - q.started) / 1000.0;
        Msg.broadcast(QUIZ_TAG + "&e" + p.getName() + "&f님 정답! &7(" + String.format("%.1f", sec) + "초) &f정답 : &e" + q.shown + " &a+" + Msg.num(reward) + "원");
        Msg.sound(p, "entity.player.levelup", 0.7f, 1.6f);
    }

    private void quizCmd(CommandSender s, String l, String[] a) {
        String sub = a.length == 0 ? "" : a[0];
        switch (sub) {
            case "지금" -> {
                if (quiz != null) Msg.send(s, "&c이미 진행 중인 문제가 있습니다.");
                else startQuiz();
            }
            case "취소" -> {
                if (quiz == null) Msg.send(s, "&7진행 중인 문제가 없습니다.");
                else {
                    quiz = null;
                    Msg.broadcast(QUIZ_TAG + "&7문제가 취소되었습니다.");
                }
            }
            default -> Msg.send(s, "&e/" + l + " 지금 &7- 바로 문제 내기   &e/" + l + " 취소");
        }
    }

    /** 테스트 · 관리용 */
    public String quizAnswer() {
        Quiz q = quiz;
        return q == null ? null : q.answer;
    }

    public Block dropBlock() {
        return drop;
    }
}
