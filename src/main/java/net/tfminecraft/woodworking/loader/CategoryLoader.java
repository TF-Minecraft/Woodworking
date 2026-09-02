package net.tfminecraft.woodworking.loader;

import java.io.File;
import java.util.LinkedHashMap;

import org.bukkit.configuration.file.FileConfiguration;

import me.Plugins.TLibs.Interface.LoaderInterface;
import net.tfminecraft.woodworking.project.WoodCategory;

public class CategoryLoader implements LoaderInterface {

    /** Insertion ordered so the category menu keeps a stable layout between restarts. */
    private static final LinkedHashMap<String, WoodCategory> map = new LinkedHashMap<>();

    public static LinkedHashMap<String, WoodCategory> get() {
        return map;
    }

    public static WoodCategory getByString(String id) {
        if (id == null) return null;
        return map.get(id);
    }

    @Override
    public void load(File configFile) {
        map.clear();
        FileConfiguration config = Yaml.read(configFile);
        if (config == null) return;

        for (String key : config.getKeys(false)) {
            map.put(key, new WoodCategory(key, config.getConfigurationSection(key)));
        }
    }
}
