package net.tfminecraft.woodworking.project;

import org.bukkit.configuration.ConfigurationSection;

import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

public class WoodMaterial {

    private final String id;
    private final String name;
    private final String path;
    private final String type;

    public WoodMaterial(String key, ConfigurationSection config) {
        this.id = key;
        this.name = StringFormatter.formatHex(config.getString("name", key));
        this.path = config.getString("path");
        this.type = config.getString("type", "other");
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /** TLibs item path, for example m.materials.valewood. */
    public String getPath() {
        return path;
    }

    /** Ingredient bucket this material fills on a station. */
    public String getType() {
        return type;
    }
}
