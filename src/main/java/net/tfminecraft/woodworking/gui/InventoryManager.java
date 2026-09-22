package net.tfminecraft.woodworking.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.woodworking.Woodworking;
import net.tfminecraft.woodworking.hit.HitType;
import net.tfminecraft.woodworking.loader.CategoryLoader;
import net.tfminecraft.woodworking.loader.MaterialTypeLoader;
import net.tfminecraft.woodworking.project.WoodCategory;
import net.tfminecraft.woodworking.project.WoodProject;
import net.tfminecraft.woodworking.utils.Log;

public class InventoryManager {

    public static final String CATEGORY_TITLE = "§8Woodworking";
    public static final String PROJECT_TITLE = "§8Projects";

    public static final int PAGE_SIZE = 45;
    private static final int PREV_SLOT = 45;
    private static final int NEXT_SLOT = 53;

    public static NamespacedKey categoryKey() {
        return new NamespacedKey(Woodworking.plugin, "ww_category");
    }

    public static NamespacedKey projectKey() {
        return new NamespacedKey(Woodworking.plugin, "ww_project");
    }

    public static NamespacedKey pageKey() {
        return new NamespacedKey(Woodworking.plugin, "ww_page");
    }

    public void openCategories(Player player) {
        Inventory inv = Woodworking.plugin.getServer().createInventory(null, 27, CATEGORY_TITLE);
        int slot = 0;
        for (WoodCategory category : CategoryLoader.get().values()) {
            ItemStack icon = iconFromPath(category.getItem(), "category " + category.getId());
            if (icon == null) continue;
            ItemMeta meta = icon.getItemMeta();
            if (meta == null) continue;
            meta.setDisplayName(category.getName());
            List<String> lore = new ArrayList<>();
            lore.add(" ");
            lore.add("§7Contains §6" + category.getProjects().size() + " §7entries");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(categoryKey(), PersistentDataType.STRING, category.getId());
            icon.setItemMeta(meta);
            if (slot < inv.getSize()) {
                inv.setItem(slot, icon);
                slot++;
            }
        }
        fillEmpty(inv);
        player.openInventory(inv);
    }

    public void openProjects(Player player, WoodCategory category, int page) {
        if (page < 0) page = 0;
        Inventory inv = Woodworking.plugin.getServer().createInventory(null, 54, PROJECT_TITLE);

        int visible = 0;
        int start = page * PAGE_SIZE;
        int end = start + PAGE_SIZE;
        for (WoodProject project : category.getProjects()) {
            ItemStack icon = iconFromPath(project.getItem(), "project " + project.getId());
            if (icon == null) continue;
            if (visible >= start && visible < end) {
                inv.setItem(visible - start, decorateProject(icon, project));
            }
            visible++;
        }

        if (page > 0) {
            inv.setItem(PREV_SLOT, pageButton("§ePrevious", category.getId(), page - 1));
        }
        if (visible > end) {
            inv.setItem(NEXT_SLOT, pageButton("§eNext", category.getId(), page + 1));
        }

        fillEmpty(inv);
        player.openInventory(inv);
    }

    private ItemStack decorateProject(ItemStack icon, WoodProject project) {
        ItemMeta meta = icon.getItemMeta();
        if (meta == null) return icon;
        meta.setDisplayName(project.getName());
        List<String> lore = new ArrayList<>();
        for (Map.Entry<String, Integer> e : project.getMaterialsByType().entrySet()) {
            lore.add("§7Requires §a" + e.getValue() + " " + MaterialTypeLoader.display(e.getKey()));
        }
        lore.add(" ");
        for (Map.Entry<HitType, Integer> e : project.getHitsByType().entrySet()) {
            lore.add("§7Requires §a" + e.getValue() + " " + e.getKey().getName() + " §7hits");
        }
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(projectKey(), PersistentDataType.STRING, project.getId());
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack pageButton(String name, String categoryId, int page) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.getPersistentDataContainer().set(categoryKey(), PersistentDataType.STRING, categoryId);
        meta.getPersistentDataContainer().set(pageKey(), PersistentDataType.INTEGER, page);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * TLibs returns DIRT when an ItemsAdder path cannot be resolved. Treat that as missing
     * so a bad config cannot hang a menu loop or fill the chest with dirt.
     */
    private ItemStack iconFromPath(String path, String label) {
        if (path == null || path.isBlank()) {
            Log.warn("Missing item path for " + label + ", skipping menu slot.");
            return null;
        }
        ItemStack stack = TLibs.getItemAPI().getCreator().getItemFromPath(path);
        if (stack == null) {
            Log.warn("Could not build item for " + label + " (" + path + "), skipping menu slot.");
            return null;
        }
        if (path.toLowerCase().startsWith("ia.") && stack.getType() == Material.DIRT) {
            Log.warn("Could not resolve ItemsAdder item for " + label + " (" + path + "), skipping menu slot.");
            return null;
        }
        ItemStack copy = stack.clone();
        copy.setAmount(1);
        return copy;
    }

    private void fillEmpty(Inventory inv) {
        ItemStack fill = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = fill.getItemMeta();
        meta.setDisplayName("§8 ");
        fill.setItemMeta(meta);
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, fill.clone());
            }
        }
    }
}
