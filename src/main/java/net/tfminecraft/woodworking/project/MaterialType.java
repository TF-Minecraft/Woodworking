package net.tfminecraft.woodworking.project;

import org.bukkit.configuration.ConfigurationSection;

import net.tfminecraft.tlibs.objects.api.subapi.StringFormatter;

public class MaterialType {

	private final String id;
	private final String name;

	public MaterialType(String key, ConfigurationSection config) {
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
