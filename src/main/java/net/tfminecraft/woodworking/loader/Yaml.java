package net.tfminecraft.woodworking.loader;

import java.io.File;
import java.io.IOException;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.tfminecraft.woodworking.utils.Log;

/** Shared YAML read used by every loader, so a missing or broken file warns instead of throwing. */
final class Yaml {

    private Yaml() {
    }

    static FileConfiguration read(File configFile) {
        if (configFile == null || !configFile.exists()) {
            Log.warn("Config file " + (configFile == null ? "null" : configFile.getName()) + " is missing.");
            return null;
        }
        FileConfiguration config = new YamlConfiguration();
        try {
            config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            Log.warn("Could not read " + configFile.getName() + ": " + e.getMessage());
            return null;
        }
        return config;
    }
}
