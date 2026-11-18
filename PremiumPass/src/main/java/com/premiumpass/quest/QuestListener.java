package com.premiumpass.quest;

import com.premiumpass.PremiumPass;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumSet;
import java.util.Set;

/** 게임 내 행동을 퀘스트 진행으로 연결한다 */
public class QuestListener implements Listener {

    /** 다 자랐을 때만 인정하는 농작물 */
    private static final Set<Material> CROPS = EnumSet.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
            Material.NETHER_WART, Material.COCOA, Material.SWEET_BERRY_BUSH, Material.TORCHFLOWER_CROP);

    private final PremiumPass plugin;
    private final NamespacedKey spawnerKey;

    public QuestListener(PremiumPass plugin) {
        this.plugin = plugin;
        this.spawnerKey = new NamespacedKey(plugin, "spawner");
    }

    private boolean counts(Player p) {
        if (p == null) return false;
        if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) return true;
        return plugin.getConfig().getBoolean("quests.count-creative", false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Material type = b.getType();
        boolean placed = !CROPS.contains(type) && plugin.tracker().consumePlaced(b);
        if (placed || !counts(e.getPlayer())) return;
        if (CROPS.contains(type) && b.getBlockData() instanceof Ageable age && age.getAge() < age.getMaximumAge()) return;
        plugin.quests().progress(e.getPlayer(), QuestType.BREAK_BLOCK, 1, type.name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        if (!CROPS.contains(b.getType())) plugin.tracker().markPlaced(b);
        if (!counts(e.getPlayer())) return;
        plugin.quests().progress(e.getPlayer(), QuestType.PLACE_BLOCK, 1, b.getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) {
            e.getEntity().getPersistentDataContainer().set(spawnerKey, PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        Player killer = dead.getKiller();
        if (!counts(killer) || dead instanceof Player) return;
        if (!plugin.getConfig().getBoolean("quests.count-spawner-mobs", false)
                && dead.getPersistentDataContainer().has(spawnerKey, PersistentDataType.BYTE)) return;
        String hostile = dead instanceof Enemy ? "HOSTILE" : null;
        plugin.quests().progress(killer, QuestType.KILL_MOB, 1, dead.getType().name(), hostile);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH || !counts(e.getPlayer())) return;
        Entity caught = e.getCaught();
        String key = caught instanceof Item item ? item.getItemStack().getType().name() : "ANY";
        plugin.quests().progress(e.getPlayer(), QuestType.FISH, 1, key);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !counts(p)) return;
        if (e.getAction() == InventoryAction.NOTHING) return;
        ItemStack result = e.getRecipe().getResult();
        int amount = result.getAmount();
        if (e.isShiftClick()) {
            int min = Integer.MAX_VALUE;
            for (ItemStack it : e.getInventory().getMatrix()) {
                if (it != null && !it.getType().isAir()) min = Math.min(min, it.getAmount());
            }
            if (min != Integer.MAX_VALUE) amount *= min;
        }
        plugin.quests().progress(p, QuestType.CRAFT, amount, result.getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSmelt(FurnaceExtractEvent e) {
        if (!counts(e.getPlayer())) return;
        plugin.quests().progress(e.getPlayer(), QuestType.SMELT, e.getItemAmount(), e.getItemType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent e) {
        if (!counts(e.getEnchanter())) return;
        plugin.quests().progress(e.getEnchanter(), QuestType.ENCHANT, 1, e.getItem().getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent e) {
        if (!(e.getBreeder() instanceof Player p) || !counts(p)) return;
        plugin.quests().progress(p, QuestType.BREED, 1, e.getEntity().getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        if (!counts(e.getPlayer())) return;
        plugin.quests().progress(e.getPlayer(), QuestType.CONSUME, 1, e.getItem().getType().name());
    }
}
