package net.tfminecraft.woodworking.hit;

import org.bukkit.configuration.ConfigurationSection;

import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;
import net.tfminecraft.woodworking.loader.HitTypeLoader;

/** One tool action, for example a whittle. Resolved from the tool in the player's hand. */
public class CraftingHit {

    private final String id;
    private final String tool;
    private final String name;
    private final HitType type;

    public CraftingHit(String key, ConfigurationSection config) {
        this.id = key;
        this.tool = config.getString("tool");
        this.name = StringFormatter.formatHex(config.getString("name", key));
        this.type = HitTypeLoader.getByString(config.getString("type"));
    }

    public String getId() {
        return id;
    }

    /** MMOItems TYPE.ID of the tool that produces this hit. */
    public String getTool() {
        return tool;
    }

    public String getName() {
        return name;
    }

    public HitType getType() {
        return type;
    }
}
