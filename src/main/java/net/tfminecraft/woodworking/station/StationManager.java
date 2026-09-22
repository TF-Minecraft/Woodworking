package net.tfminecraft.woodworking.station;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import dev.lone.itemsadder.api.Events.FurnitureBreakEvent;
import io.lumine.mythic.lib.api.item.NBTItem;
import me.Plugins.TLibs.TLibs;
import me.Plugins.TLibs.Objects.Utils.IntCounter;
import net.tfminecraft.woodworking.cache.Cache;
import net.tfminecraft.woodworking.command.Permissions;
import net.tfminecraft.woodworking.database.StationStore;
import net.tfminecraft.woodworking.gui.InventoryManager;
import net.tfminecraft.woodworking.hit.CraftingHit;
import net.tfminecraft.woodworking.hit.HitType;
import net.tfminecraft.woodworking.loader.CategoryLoader;
import net.tfminecraft.woodworking.loader.HitLoader;
import net.tfminecraft.woodworking.loader.MaterialLoader;
import net.tfminecraft.woodworking.loader.MaterialTypeLoader;
import net.tfminecraft.woodworking.loader.ProjectLoader;
import net.tfminecraft.woodworking.project.Quality;
import net.tfminecraft.woodworking.project.WoodCategory;
import net.tfminecraft.woodworking.project.WoodMaterial;
import net.tfminecraft.woodworking.project.WoodProject;
import net.tfminecraft.woodworking.utils.Log;

/**
 * In-memory stations keyed by block location, plus click and furniture-break handling.
 */
public class StationManager implements Listener {

    private static final long CLICK_COOLDOWN_MS = 200L;

    private final HashMap<Location, WoodStation> stations = new HashMap<>();
    private final HashMap<UUID, Long> clickCooldown = new HashMap<>();
    private final HashMap<UUID, WoodStation> openMenu = new HashMap<>();
    private final InventoryManager menus = new InventoryManager();
    private boolean dirty;

    public WoodStation get(Location loc) {
        Location key = key(loc);
        if (key == null) return null;
        return stations.get(key);
    }

    /** Returns the existing station at this block, or creates an empty one. */
    public WoodStation getOrCreate(Location loc) {
        Location key = key(loc);
        if (key == null) return null;
        WoodStation station = stations.get(key);
        if (station == null) {
            station = new WoodStation(key);
            stations.put(key, station);
        }
        return station;
    }

    public void put(WoodStation station) {
        if (station == null || station.getLoc() == null) return;
        stations.put(key(station.getLoc()), station);
    }

    public WoodStation remove(Location loc) {
        Location key = key(loc);
        if (key == null) return null;
        WoodStation removed = stations.remove(key);
        if (removed != null) {
            openMenu.entrySet().removeIf(e -> e.getValue() == removed);
            StationStore.delete(key);
            markDirty();
        }
        return removed;
    }

    public Collection<WoodStation> getStations() {
        return stations.values();
    }

    public Map<Location, WoodStation> getMap() {
        return stations;
    }

    public void clear() {
        stations.clear();
        clickCooldown.clear();
        openMenu.clear();
        dirty = false;
    }

    public void markDirty() {
        dirty = true;
    }

    /** Restores benches from disk into this manager. Call after configs are loaded. */
    public void loadPersisted() {
        for (WoodStation station : StationStore.loadAll()) {
            put(station);
        }
        dirty = false;
    }

    /**
     * Writes live in-progress benches and drops leftover files.
     * @param force write even when nothing changed
     */
    public void flush(boolean force) {
        if (!force && !dirty) return;
        StationStore.saveAll(stations.values());
        dirty = false;
    }

    public boolean isWoodworkingStation(Block block) {
        if (block == null || Cache.station == null) return false;
        return TLibs.getBlockAPI().getChecker().checkBlock(block, Cache.station);
    }

    /** Drop pitch/yaw so two clicks on the same block share a key. Null world keeps xyz as-is. */
    public static Location key(Location loc) {
        if (loc == null) return null;
        if (loc.getWorld() == null) {
            return new Location(null, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        }
        return loc.getBlock().getLocation();
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) return;
        if (!isWoodworkingStation(e.getClickedBlock())) return;
        Player player = e.getPlayer();
        if (!Permissions.canUse(player)) {
            e.setCancelled(true);
            if (!onCooldown(player)) {
                player.sendMessage("§cYou do not have permission to use woodworking.");
                markCooldown(player);
            }
            return;
        }
        if (onCooldown(player)) return;

        if (action == Action.RIGHT_CLICK_BLOCK) {
            handleRightClick(e);
        } else {
            handleLeftClick(e);
        }
    }

    @EventHandler
    public void onFurnitureBreak(FurnitureBreakEvent e) {
        if (e.getNamespacedID() == null) return;
        if (!e.getNamespacedID().equalsIgnoreCase(furnitureId())) return;

        Location loc = e.getBukkitEntity().getLocation().getBlock().getLocation();
        WoodStation station = get(loc);
        if (station == null) return;

        List<ItemStack> refund = station.hasProject() ? station.cancel() : List.of();
        remove(loc);
        Player breaker = e.getPlayer();
        if (breaker != null) {
            giveOrDrop(breaker, refund);
        } else {
            dropAt(loc, refund);
        }
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        String title = e.getView().getTitle();
        if (!title.equals(InventoryManager.CATEGORY_TITLE) && !title.equals(InventoryManager.PROJECT_TITLE)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!Permissions.requireUse(p)) {
            p.closeInventory();
            return;
        }
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        ItemMeta meta = clicked.getItemMeta();

        Integer page = meta.getPersistentDataContainer().get(InventoryManager.pageKey(), PersistentDataType.INTEGER);
        String categoryId = meta.getPersistentDataContainer().get(InventoryManager.categoryKey(), PersistentDataType.STRING);
        if (page != null && categoryId != null) {
            WoodCategory category = CategoryLoader.getByString(categoryId);
            if (category == null) return;
            menus.openProjects(p, category, page);
            return;
        }

        if (categoryId != null) {
            WoodCategory category = CategoryLoader.getByString(categoryId);
            if (category == null) return;
            menus.openProjects(p, category, 0);
            return;
        }

        String projectId = meta.getPersistentDataContainer().get(InventoryManager.projectKey(), PersistentDataType.STRING);
        if (projectId == null) return;
        WoodProject project = ProjectLoader.getByString(projectId);
        if (project == null) return;

        WoodStation station = openMenu.get(p.getUniqueId());
        if (station == null) {
            p.sendMessage("§cThat bench is no longer available.");
            p.closeInventory();
            return;
        }
        if (station.hasProject()) {
            p.sendMessage("§cThis bench already has a project. SHIFT + LEFT CLICK with the branding tool to cancel first.");
            p.closeInventory();
            return;
        }
        station.setProject(project);
        markDirty();
        openMenu.remove(p.getUniqueId());
        p.closeInventory();
        p.sendMessage("§aSelected " + project.getName() + " §aas the current woodworking project");
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.8f, 2f);
    }

    private void handleRightClick(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        WoodStation existing = get(e.getClickedBlock().getLocation());

        if (existing == null || !existing.hasProject()) {
            e.setCancelled(true);
            markCooldown(p);
            WoodStation station = getOrCreate(e.getClickedBlock().getLocation());
            openMenu.put(p.getUniqueId(), station);
            menus.openCategories(p);
            return;
        }

        if (isBranding(hand)) {
            e.setCancelled(true);
            markCooldown(p);
            sendStatus(p, existing);
            return;
        }

        WoodMaterial material = matchMaterial(hand);
        if (material == null) return;

        e.setCancelled(true);
        markCooldown(p);
        StationFeedback feedback = existing.addMaterial(material, hand);
        switch (feedback) {
            case SUCCESS:
                consumeOne(p);
                markDirty();
                IntCounter bucket = existing.getTypes().get(material.getType());
                String progress = bucket == null ? "" : bucket.getCurrent() + "/" + bucket.getNeeded();
                p.sendTitle("§aAdded " + material.getName(), MaterialTypeLoader.display(material.getType()) + " §e" + progress, 5, 20, 5);
                playWorkFx(existing.getLoc(), Material.OAK_LOG);
                p.getWorld().playSound(existing.getLoc(), Sound.ITEM_AXE_WAX_OFF, 0.7f, 2f);
                break;
            case CAPACITY:
                p.sendMessage("§cYou already have the needed amount of this type");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
                break;
            case WRONG_TYPE:
                p.sendMessage("§cThis item type is not needed for the project");
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                break;
            case NO_PROJECT:
                p.sendMessage("§cThis bench has no project. Right-click the table to choose one.");
                break;
            default:
                break;
        }
    }

    private void handleLeftClick(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        WoodStation station = get(e.getClickedBlock().getLocation());
        if (station == null || !station.hasProject()) return;

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) return;

        if (isBranding(hand)) {
            e.setCancelled(true);
            markCooldown(p);
            if (p.isSneaking()) {
                List<ItemStack> refund = station.cancel();
                remove(station.getLoc());
                giveOrDrop(p, refund);
                p.sendMessage("§cProject cancelled");
                p.getWorld().playSound(station.getLoc(), Sound.ITEM_SHIELD_BREAK, 0.4f, 1f);
                return;
            }
            StationFeedback finish = station.canFinish();
            if (finish == StationFeedback.LACKING_ITEMS) {
                p.sendMessage("§cYou have to add all the items before working");
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
            if (finish == StationFeedback.LACKING_HITS) {
                p.sendMessage("§cYou need to complete all the hits before finishing");
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
            if (finish == StationFeedback.RECIPE_MISMATCH) {
                p.sendMessage("§cThe materials do not match the recipe");
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
            completeCraft(p, station);
            return;
        }

        NBTItem nbt = NBTItem.get(hand);
        if (!nbt.hasType()) return;
        CraftingHit hit = HitLoader.getByTool(nbt.getType() + "." + nbt.getString("MMOITEMS_ITEM_ID"));
        if (hit == null) return;

        e.setCancelled(true);
        markCooldown(p);
        StationFeedback feedback = station.hit(hit);
        switch (feedback) {
            case SUCCESS:
                markDirty();
                IntCounter typeCounter = station.getHitTypes().get(hit.getType());
                HitType type = hit.getType();
                String typeName = type == null ? "Hits" : type.getName();
                String progress = typeCounter == null ? "" : typeCounter.getCurrent() + "/" + typeCounter.getNeeded();
                p.sendTitle("§a+1 " + hit.getName(), typeName + " §e" + progress, 5, 20, 5);
                playWorkFx(station.getLoc(), Material.OAK_PLANKS);
                p.getWorld().playSound(station.getLoc(), Sound.BLOCK_ANVIL_USE, 0.4f, 1f);
                break;
            case LACKING_ITEMS:
                p.sendMessage("§cYou have to add all the items before working");
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                break;
            case WRONG_TYPE:
                p.sendMessage("§cThis item cannot be used for woodworking hits");
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                break;
            case NONE:
                p.sendMessage("§cThis tool is not needed for this project");
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                break;
            case CAPACITY:
                p.sendMessage("§cYou dont need more hits with this tool");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
                break;
            default:
                break;
        }
    }

    private void sendStatus(Player p, WoodStation station) {
        p.sendMessage("§7Project: " + station.getProject().getName());
        for (Map.Entry<String, IntCounter> e : station.getTypes().entrySet()) {
            IntCounter c = e.getValue();
            p.sendMessage(MaterialTypeLoader.display(e.getKey()) + "§7: §e" + c.getCurrent() + "/" + c.getNeeded());
        }
        for (Map.Entry<HitType, IntCounter> e : station.getHitTypes().entrySet()) {
            IntCounter c = e.getValue();
            p.sendMessage(e.getKey().getName() + "§7: §e" + c.getCurrent() + "/" + c.getNeeded());
        }
        p.sendMessage("§cSHIFT + LEFT CLICK with the branding tool to cancel the project!");
    }

    private void completeCraft(Player p, WoodStation station) {
        WoodProject project = station.getProject();
        String path = project.getItem();
        ItemStack output = TLibs.getItemAPI().getCreator().getItemFromPath(path);
        if (output == null || (path != null && path.toLowerCase().startsWith("ia.") && output.getType() == Material.DIRT)) {
            Log.warn("Could not build output for project " + project.getId() + " (" + path + "). Station left intact.");
            p.sendMessage("§cCould not create that item. Contact an administrator.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        output.setAmount(1);
        Location drop = station.getLoc().clone().add(0.5, 1, 0.5);
        if (drop.getWorld() != null) {
            drop.getWorld().dropItemNaturally(drop, output);
            drop.getWorld().playSound(drop, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            drop.getWorld().playSound(drop, Sound.BLOCK_ANVIL_PLACE, 1f, 1f);
            drop.getWorld().spawnParticle(Particle.LAVA, drop, 20, 0.1, 0.2, 0.1);
        }
        Quality q = station.getQuality();
        String qName = q == null ? "" : q.getName();
        p.sendTitle("§aYou made a " + project.getName(), qName, 5, 40, 10);
        station.cancel();
        remove(station.getLoc());
    }

    private boolean isBranding(ItemStack item) {
        return TLibs.getItemAPI().getChecker().checkItemWithPath(item, Cache.brandingTool);
    }

    private WoodMaterial matchMaterial(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        for (WoodMaterial material : MaterialLoader.get().values()) {
            if (TLibs.getItemAPI().getChecker().checkItemWithPath(item, material.getPath())) {
                return material;
            }
        }
        return null;
    }

    private void consumeOne(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) return;
        hand.setAmount(hand.getAmount() - 1);
    }

    private void giveOrDrop(Player p, List<ItemStack> items) {
        for (ItemStack item : items) {
            HashMap<Integer, ItemStack> leftover = p.getInventory().addItem(item);
            for (ItemStack extra : leftover.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), extra);
            }
        }
    }

    private void dropAt(Location loc, List<ItemStack> items) {
        if (loc.getWorld() == null) return;
        Location drop = loc.clone().add(0.5, 1, 0.5);
        for (ItemStack item : items) {
            loc.getWorld().dropItemNaturally(drop, item);
        }
    }

    private void playWorkFx(Location loc, Material dust) {
        if (loc.getWorld() == null) return;
        loc.getWorld().spawnParticle(
                Particle.BLOCK,
                loc.clone().add(0.5, 1, 0.5),
                20, 0.1, 0.2, 0.1,
                dust.createBlockData());
    }

    private boolean onCooldown(Player p) {
        Long until = clickCooldown.get(p.getUniqueId());
        return until != null && System.currentTimeMillis() < until;
    }

    private void markCooldown(Player p) {
        clickCooldown.put(p.getUniqueId(), System.currentTimeMillis() + CLICK_COOLDOWN_MS);
    }

    /** Strips iaf(...) wrapping from Cache.station into namespace:id. */
    public static String furnitureId() {
        String path = Cache.station;
        if (path == null) return "";
        int open = path.indexOf('(');
        int close = path.indexOf(')', open + 1);
        if (open >= 0 && close > open) {
            return path.substring(open + 1, close);
        }
        if (path.contains(":")) return path;
        return path;
    }
}
