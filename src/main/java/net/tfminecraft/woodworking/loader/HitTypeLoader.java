package net.tfminecraft.woodworking.loader;

import java.io.File;
import java.util.LinkedHashMap;

import org.bukkit.configuration.file.FileConfiguration;

import net.tfminecraft.tlibs.interfaces.LoaderInterface;
import net.tfminecraft.woodworking.hit.HitType;

public class HitTypeLoader implements LoaderInterface {

    private static final LinkedHashMap<String, HitType> map = new LinkedHashMap<>();

    public static LinkedHashMap<String, HitType> get() {
        return map;
    }

    public static HitType getByString(String id) {
        if (id == null) return null;
        return map.get(id);
    }

    @Override
    public void load(File configFile) {
        map.clear();
        FileConfiguration config = Yaml.read(configFile);
        if (config == null) return;

        for (String key : config.getKeys(false)) {
            map.put(key, new HitType(key, config.getConfigurationSection(key)));
        }
    }
}
