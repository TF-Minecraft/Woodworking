package net.tfminecraft.woodworking.hit;

import org.bukkit.configuration.ConfigurationSection;

import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

/** A bucket of related hits, used to group requirements in the project menu. */
public class HitType {

    private final String id;
    private final String name;

    public HitType(String key, ConfigurationSection config) {
        this.id = key;
        this.name = StringFormatter.formatHex(config.getString("name", key));
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
