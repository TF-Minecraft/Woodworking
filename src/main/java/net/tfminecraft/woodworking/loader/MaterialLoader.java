package net.tfminecraft.woodworking.loader;

import java.io.File;
import java.util.LinkedHashMap;

import org.bukkit.configuration.file.FileConfiguration;

import net.tfminecraft.tlibs.interfaces.LoaderInterface;
import net.tfminecraft.woodworking.utils.Log;
import net.tfminecraft.woodworking.project.WoodMaterial;

public class MaterialLoader implements LoaderInterface {

    private static final LinkedHashMap<String, WoodMaterial> map = new LinkedHashMap<>();

    public static LinkedHashMap<String, WoodMaterial> get() {
        return map;
    }

    public static WoodMaterial getByString(String id) {
        if (id == null) return null;
        return map.get(id);
    }

    @Override
    public void load(File configFile) {
        map.clear();
        FileConfiguration config = Yaml.read(configFile);
        if (config == null) return;

        for (String key : config.getKeys(false)) {
            WoodMaterial material = new WoodMaterial(key, config.getConfigurationSection(key));
            if (material.getPath() == null) {
                Log.warn("Material '" + key + "' has no path set, skipping.");
                continue;
            }
            map.put(key, material);
        }
    }
}
