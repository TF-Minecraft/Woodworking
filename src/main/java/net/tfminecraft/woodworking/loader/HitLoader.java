package net.tfminecraft.woodworking.loader;

import java.io.File;
import java.util.LinkedHashMap;

import org.bukkit.configuration.file.FileConfiguration;

import me.Plugins.TLibs.Interface.LoaderInterface;
import net.tfminecraft.woodworking.utils.Log;
import net.tfminecraft.woodworking.hit.CraftingHit;

public class HitLoader implements LoaderInterface {

    private static final LinkedHashMap<String, CraftingHit> map = new LinkedHashMap<>();

    public static LinkedHashMap<String, CraftingHit> get() {
        return map;
    }

    public static CraftingHit getByString(String id) {
        if (id == null) return null;
        return map.get(id);
    }

    /** Resolves the MMOItems TYPE.ID of a held tool to the hit it produces. */
    public static CraftingHit getByTool(String path) {
        if (path == null) return null;
        for (CraftingHit hit : map.values()) {
            if (path.equalsIgnoreCase(hit.getTool())) return hit;
        }
        return null;
    }

    @Override
    public void load(File configFile) {
        map.clear();
        FileConfiguration config = Yaml.read(configFile);
        if (config == null) return;

        for (String key : config.getKeys(false)) {
            CraftingHit hit = new CraftingHit(key, config.getConfigurationSection(key));
            if (hit.getType() == null) {
                Log.warn("Hit '" + key + "' has an unknown type, skipping. Check hit-types.yml.");
                continue;
            }
            if (hit.getTool() == null) {
                Log.warn("Hit '" + key + "' has no tool set, skipping.");
                continue;
            }
            map.put(key, hit);
        }
    }
}
