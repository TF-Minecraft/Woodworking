package net.tfminecraft.woodworking.loader;

import java.io.File;

import org.bukkit.configuration.file.FileConfiguration;

import net.tfminecraft.tlibs.interfaces.LoaderInterface;
import net.tfminecraft.woodworking.cache.Cache;

public class ConfigLoader implements LoaderInterface {

    @Override
    public void load(File configFile) {
        FileConfiguration config = Yaml.read(configFile);
        if (config == null) return;

        Cache.station = config.getString("station");
        Cache.brandingTool = config.getString("branding-tool");
        Cache.permission = config.getString("permission");
        if (Cache.permission != null && Cache.permission.isBlank()) {
            Cache.permission = null;
        }
    }
}
