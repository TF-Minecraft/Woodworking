package net.tfminecraft.woodworking.loader;

import java.io.File;
import java.util.LinkedHashMap;

import org.bukkit.configuration.file.FileConfiguration;

import me.Plugins.TLibs.Interface.LoaderInterface;
import net.tfminecraft.woodworking.project.MaterialType;

public class MaterialTypeLoader implements LoaderInterface {

	private static final LinkedHashMap<String, MaterialType> map = new LinkedHashMap<>();

	public static LinkedHashMap<String, MaterialType> get() {
		return map;
	}

	public static MaterialType getByString(String id) {
		if (id == null) return null;
		return map.get(id);
	}

	public static String display(String typeId) {
		MaterialType type = getByString(typeId);
		if (type != null) return type.getName();
		if (typeId == null || typeId.isBlank()) return "Materials";
		return Character.toUpperCase(typeId.charAt(0)) + typeId.substring(1) + " Materials";
	}

	@Override
	public void load(File configFile) {
		map.clear();
		FileConfiguration config = Yaml.read(configFile);
		if (config == null) return;
		for (String key : config.getKeys(false)) {
			map.put(key, new MaterialType(key, config.getConfigurationSection(key)));
		}
	}
}
