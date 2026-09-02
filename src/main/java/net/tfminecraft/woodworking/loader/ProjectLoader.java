package net.tfminecraft.woodworking.loader;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;

import org.bukkit.configuration.file.FileConfiguration;

import net.tfminecraft.woodworking.utils.Log;
import net.tfminecraft.woodworking.project.WoodCategory;
import net.tfminecraft.woodworking.project.WoodProject;

public class ProjectLoader {

    private static final LinkedHashMap<String, WoodProject> map = new LinkedHashMap<>();

    public static LinkedHashMap<String, WoodProject> get() {
        return map;
    }

    public static WoodProject getByString(String id) {
        if (id == null) return null;
        return map.get(id);
    }

    /** Reads every yml in the projects folder and attaches each project to its category. */
    public void loadFolder(File folder) {
        map.clear();
        for (WoodCategory category : CategoryLoader.get().values()) {
            category.clearProjects();
        }

        if (folder == null || !folder.isDirectory()) {
            Log.warn("Projects folder is missing, no projects loaded.");
            return;
        }

        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
        if (files == null || files.length == 0) {
            Log.warn("Projects folder contains no yml files.");
            return;
        }

        // Sorted so the project menu order does not depend on filesystem order.
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File file : files) {
            load(file);
        }
    }

    private void load(File configFile) {
        FileConfiguration config = Yaml.read(configFile);
        if (config == null) return;

        for (String key : config.getKeys(false)) {
            if (map.containsKey(key)) {
                Log.warn(
                        "Duplicate project '" + key + "' in " + configFile.getName() + ", skipping the duplicate.");
                continue;
            }

            String categoryId = config.getString(key + ".category");
            WoodCategory category = CategoryLoader.getByString(categoryId);
            if (category == null) {
                Log.warn("Project '" + key + "' in " + configFile.getName()
                        + " has unknown category '" + categoryId + "', skipping. Check categories.yml.");
                continue;
            }

            WoodProject project = new WoodProject(key, categoryId, config.getConfigurationSection(key));
            if (project.getItem() == null) {
                Log.warn("Project '" + key + "' in " + configFile.getName() + " has no item set, skipping.");
                continue;
            }
            if (project.getHits().isEmpty()) {
                Log.warn("Project '" + key + "' has no valid hit requirements.");
            }
            if (project.getRecipe().isEmpty()) {
                Log.warn("Project '" + key + "' has no valid recipe materials.");
            }

            map.put(key, project);
            category.addProject(project);
        }
    }
}
