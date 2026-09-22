package net.tfminecraft.woodworking.project;

import org.bukkit.configuration.ConfigurationSection;

import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

/** Outcome tier picked from the hit accuracy percentage of a finished project. */
public class Quality {

    private final String id;
    private final double amount;
    private final int value;
    private final String name;

    public Quality(String key, ConfigurationSection config) {
        this.id = key;
        this.amount = config.getDouble("amount");
        this.value = config.getInt("value");
        this.name = StringFormatter.formatHex(config.getString("name", key));
    }

    public String getId() {
        return id;
    }

    /** Minimum accuracy percentage required for this tier. */
    public double getAmount() {
        return amount;
    }

    public int getValue() {
        return value;
    }

    public String getName() {
        return name;
    }

    public boolean isValid(double d) {
        return d >= amount;
    }
}
