package net.tfminecraft.woodworking.project;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;

import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;
import net.tfminecraft.woodworking.utils.Log;
import net.tfminecraft.woodworking.hit.CraftingHit;
import net.tfminecraft.woodworking.hit.HitType;
import net.tfminecraft.woodworking.loader.HitLoader;
import net.tfminecraft.woodworking.loader.MaterialLoader;

public class WoodProject {

    private final String id;
    private final String name;
    private final String item;
    private final String categoryId;

    private final LinkedHashMap<CraftingHit, Integer> hits = new LinkedHashMap<>();
    private final LinkedHashMap<WoodMaterial, Integer> recipe = new LinkedHashMap<>();

    public WoodProject(String key, String categoryId, ConfigurationSection config) {
        this.id = key;
        this.categoryId = categoryId;
        this.name = StringFormatter.formatHex(config.getString("name", key));
        this.item = config.getString("item");

        for (String s : config.getStringList("hits")) {
            String[] parts = split(s);
            if (parts == null) {
                warn("hit entry '" + s + "' is not in the id.amount format");
                continue;
            }
            CraftingHit hit = HitLoader.getByString(parts[0]);
            if (hit == null) {
                warn("unknown hit '" + parts[0] + "'");
                continue;
            }
            hits.merge(hit, Integer.parseInt(parts[1]), Integer::sum);
        }

        for (String s : config.getStringList("recipe")) {
            String[] parts = split(s);
            if (parts == null) {
                warn("recipe entry '" + s + "' is not in the id.amount format");
                continue;
            }
            WoodMaterial material = MaterialLoader.getByString(parts[0]);
            if (material == null) {
                warn("unknown material '" + parts[0] + "'");
                continue;
            }
            recipe.merge(material, Integer.parseInt(parts[1]), Integer::sum);
        }
    }

    /** Splits "whittle.3" into id and amount. Returns null when malformed. */
    private static String[] split(String entry) {
        if (entry == null) return null;
        int i = entry.lastIndexOf('.');
        if (i <= 0 || i == entry.length() - 1) return null;
        String amount = entry.substring(i + 1);
        try {
            if (Integer.parseInt(amount) <= 0) return null;
        } catch (NumberFormatException e) {
            return null;
        }
        return new String[] { entry.substring(0, i), amount };
    }

    private void warn(String message) {
        Log.warn("Project '" + id + "': " + message + ", skipping entry.");
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /** TLibs item path of the finished furniture. */
    public String getItem() {
        return item;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public Map<CraftingHit, Integer> getHits() {
        return Collections.unmodifiableMap(hits);
    }

    public Map<WoodMaterial, Integer> getRecipe() {
        return Collections.unmodifiableMap(recipe);
    }

    public int getTotalHits() {
        int i = 0;
        for (int amount : hits.values()) i += amount;
        return i;
    }

    public int getTotalMaterials() {
        int i = 0;
        for (int amount : recipe.values()) i += amount;
        return i;
    }

    /** Hit requirements collapsed into their hit type buckets, for menu lore. */
    public Map<HitType, Integer> getHitsByType() {
        LinkedHashMap<HitType, Integer> map = new LinkedHashMap<>();
        for (Map.Entry<CraftingHit, Integer> e : hits.entrySet()) {
            HitType type = e.getKey().getType();
            if (type == null) continue;
            map.merge(type, e.getValue(), Integer::sum);
        }
        return map;
    }

    /** Material requirements collapsed into their ingredient buckets, for station counters. */
    public Map<String, Integer> getMaterialsByType() {
        LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
        for (Map.Entry<WoodMaterial, Integer> e : recipe.entrySet()) {
            map.merge(e.getKey().getType(), e.getValue(), Integer::sum);
        }
        return map;
    }
}
