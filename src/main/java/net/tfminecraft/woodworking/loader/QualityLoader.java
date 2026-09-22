package net.tfminecraft.woodworking.loader;

import java.io.File;
import java.util.LinkedHashMap;

import org.bukkit.configuration.file.FileConfiguration;

import net.tfminecraft.tlibs.interfaces.LoaderInterface;
import net.tfminecraft.woodworking.project.Quality;

public class QualityLoader implements LoaderInterface {

    private static final LinkedHashMap<String, Quality> map = new LinkedHashMap<>();

    public static LinkedHashMap<String, Quality> get() {
        return map;
    }

    public static Quality getByString(String id) {
        if (id == null) return null;
        return map.get(id);
    }

    /** Highest tier whose threshold the given accuracy percentage reaches. */
    public static Quality getByAmount(double a) {
        Quality q = null;
        int prev = -1;
        for (Quality o : map.values()) {
            if (o.isValid(a) && o.getValue() > prev) {
                q = o;
                prev = o.getValue();
            }
        }
        return q;
    }

    @Override
    public void load(File configFile) {
        map.clear();
        FileConfiguration config = Yaml.read(configFile);
        if (config == null) return;

        for (String key : config.getKeys(false)) {
            map.put(key, new Quality(key, config.getConfigurationSection(key)));
        }
    }
}
