package net.tfminecraft.woodworking.project;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;

import me.Plugins.TLibs.Objects.API.SubAPI.StringFormatter;

public class WoodCategory {

    private final String id;
    private final String name;
    private final String item;
    private final List<WoodProject> projects = new ArrayList<>();

    public WoodCategory(String key, ConfigurationSection config) {
        this.id = key;
        this.name = StringFormatter.formatHex(config.getString("name", key));
        this.item = config.getString("item");
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /** TLibs item path of the menu icon. */
    public String getItem() {
        return item;
    }

    public List<WoodProject> getProjects() {
        return projects;
    }

    public void addProject(WoodProject project) {
        projects.add(project);
    }

    public void clearProjects() {
        projects.clear();
    }
}
