package net.tfminecraft.woodworking;

import java.io.File;

import org.bukkit.plugin.java.JavaPlugin;

import net.tfminecraft.woodworking.command.CommandManager;
import net.tfminecraft.woodworking.loader.CategoryLoader;
import net.tfminecraft.woodworking.loader.ConfigLoader;
import net.tfminecraft.woodworking.loader.HitLoader;
import net.tfminecraft.woodworking.loader.HitTypeLoader;
import net.tfminecraft.woodworking.loader.MaterialLoader;
import net.tfminecraft.woodworking.loader.MaterialTypeLoader;
import net.tfminecraft.woodworking.loader.ProjectLoader;
import net.tfminecraft.woodworking.loader.QualityLoader;
import net.tfminecraft.woodworking.station.StationManager;

public class Woodworking extends JavaPlugin {

    public static Woodworking plugin;

    private final HitTypeLoader hitTypeLoader = new HitTypeLoader();
    private final HitLoader hitLoader = new HitLoader();
    private final MaterialLoader materialLoader = new MaterialLoader();
    private final MaterialTypeLoader materialTypeLoader = new MaterialTypeLoader();
    private final CategoryLoader categoryLoader = new CategoryLoader();
    private final ProjectLoader projectLoader = new ProjectLoader();
    private final QualityLoader qualityLoader = new QualityLoader();
    private final ConfigLoader configLoader = new ConfigLoader();

    private final CommandManager commands = new CommandManager();
    private final StationManager stations = new StationManager();

    @Override
    public void onEnable() {
        plugin = this;

        createFolders();
        createConfigs();
        loadConfigs();

        getCommand(commands.cmd1).setExecutor(commands);
        getCommand(commands.cmd1).setTabCompleter(commands);
        getServer().getPluginManager().registerEvents(stations, this);
        stations.loadPersisted();
        getServer().getScheduler().runTaskTimer(this, () -> stations.flush(false), 20L * 60, 20L * 60);

        getLogger().info(loadSummary());
    }

    @Override
    public void onDisable() {
        stations.flush(true);
    }

    // ----------------------------------------------------------------------
    //  Config Loading
    // ----------------------------------------------------------------------
    public void loadConfigs() {
        hitTypeLoader.load(new File(getDataFolder(), "hit-types.yml"));
        hitLoader.load(new File(getDataFolder(), "hits.yml"));
        materialLoader.load(new File(getDataFolder(), "materials.yml"));
        materialTypeLoader.load(new File(getDataFolder(), "material-types.yml"));
        categoryLoader.load(new File(getDataFolder(), "categories.yml"));
        projectLoader.loadFolder(new File(getDataFolder(), "projects"));
        qualityLoader.load(new File(getDataFolder(), "qualities.yml"));
        configLoader.load(new File(getDataFolder(), "config.yml"));
    }

    public void reload() {
        stations.flush(true);
        stations.clear();
        reloadConfig();
        loadConfigs();
        stations.loadPersisted();
    }

    public StationManager getStations() {
        return stations;
    }

    /** Single line describing every registry size, used on enable and after a reload. */
    public String loadSummary() {
        return "Loaded " + HitTypeLoader.get().size() + " hit types - "
                + HitLoader.get().size() + " hits - "
                + MaterialLoader.get().size() + " materials - "
                + CategoryLoader.get().size() + " categories - "
                + ProjectLoader.get().size() + " projects - "
                + QualityLoader.get().size() + " qualities";
    }

    // ----------------------------------------------------------------------
    //  Folders
    // ----------------------------------------------------------------------
    public void createFolders() {
        if (!getDataFolder().exists()) getDataFolder().mkdir();

        File subFolder = new File(getDataFolder(), "projects");
        if (!subFolder.exists()) subFolder.mkdirs();

        subFolder = new File(getDataFolder(), "data/stations");
        if (!subFolder.exists()) subFolder.mkdirs();
    }

    // ----------------------------------------------------------------------
    //  Config Generation
    // ----------------------------------------------------------------------
    public void createConfigs() {
        String[] files = {
                "config.yml",
                "hit-types.yml",
                "hits.yml",
                "materials.yml",
                "material-types.yml",
                "categories.yml",
                "qualities.yml",
                "projects/market.yml",
                "projects/royal.yml",
                "projects/marauder.yml",
                "projects/cozy.yml",
                "projects/witch.yml"
        };

        for (String s : files) {
            File newConfigFile = new File(getDataFolder(), s);
            if (!newConfigFile.exists()) {
                newConfigFile.getParentFile().mkdirs();
                saveResource(s, false);
            }
        }
    }
}
