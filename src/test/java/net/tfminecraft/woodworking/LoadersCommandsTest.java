package net.tfminecraft.woodworking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.tfminecraft.woodworking.cache.Cache;
import net.tfminecraft.woodworking.command.*;
import net.tfminecraft.woodworking.loader.*;
import net.tfminecraft.woodworking.project.*;
import net.tfminecraft.woodworking.station.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class LoadersCommandsTest extends TestSupport {
  @Test
  void loadersHandleMissingMalformedValidAndInvalidDefinitions() throws Exception {
    var loaders =
        List.of(
            new ConfigLoader(),
            new HitTypeLoader(),
            new HitLoader(),
            new MaterialLoader(),
            new MaterialTypeLoader(),
            new CategoryLoader(),
            new QualityLoader());
    for (var l : loaders) {
      l.load(null);
      l.load(dir.resolve("absent").toFile());
      l.load(yaml("bad.yml", "x: [").toFile());
    }
    new HitTypeLoader().load(yaml("types.yml", "cut: {name: Cutting}").toFile());
    new HitLoader()
        .load(
            yaml(
                    "hits.yml",
                    "badtype: {type: absent}\n"
                        + "missingtool: {type: cut}\n"
                        + "cut: {type: cut, tool: TOOL.KNIFE}")
                .toFile());
    assertEquals(1, HitLoader.get().size());
    assertNull(HitLoader.getByString(null));
    assertNull(HitLoader.getByTool(null));
    assertNull(HitLoader.getByTool("absent"));
    assertSame(HitLoader.getByString("cut"), HitLoader.getByTool("tool.knife"));
    assertNull(HitTypeLoader.getByString(null));
    new MaterialLoader()
        .load(yaml("mats.yml", "bad: {name: Bad}\noak: {path: v.oak_log, type: wood}").toFile());
    assertEquals(1, MaterialLoader.get().size());
    assertNull(MaterialLoader.getByString(null));
    new MaterialTypeLoader().load(yaml("mattypes.yml", "wood: {name: Lumber}").toFile());
    assertEquals(1, MaterialTypeLoader.get().size());
    new Cache();
    assertEquals("Lumber", MaterialTypeLoader.display("wood"));
    assertEquals("Unknown Materials", MaterialTypeLoader.display("unknown"));
    assertEquals("Materials", MaterialTypeLoader.display(null));
    assertEquals("Materials", MaterialTypeLoader.display(" "));
    new CategoryLoader().load(yaml("cats.yml", "furniture: {item: v.chest}").toFile());
    assertNull(CategoryLoader.getByString(null));
    new QualityLoader()
        .load(
            yaml(
                    "quality.yml",
                    "good: {amount: 90, value: 2}\n"
                        + "low: {amount: 0, value: 1}\n"
                        + "invalid: {amount: 0, value: -2}")
                .toFile());
    assertNull(QualityLoader.getByString(null));
    assertEquals("good", QualityLoader.getByAmount(100).getId());
    assertEquals("low", QualityLoader.getByAmount(50).getId());
    assertNull(QualityLoader.getByAmount(-10));
    assertNotNull(QualityLoader.getByString("good"));
    for (String text : List.of("{}", "permission: ' '", "permission: woodworking.use"))
      new ConfigLoader().load(yaml("config.yml", text).toFile());
    assertEquals("woodworking.use", Cache.permission);
    var projects = new ProjectLoader();
    projects.loadFolder(null);
    projects.loadFolder(dir.resolve("absent").toFile());
    var folder = Files.createDirectory(dir.resolve("projects"));
    projects.loadFolder(folder.toFile());
    File inaccessible = mock(File.class);
    when(inaccessible.isDirectory()).thenReturn(true);
    projects.loadFolder(inaccessible);
    Files.writeString(
        folder.resolve("a.yml"),
        "chair: {category: furniture, item: v.oak_stairs, hits: [cut.1], recipe: [oak.1]}\n"
            + "unknown: {category: missing}\n"
            + "noitem: {category: furniture}\n"
            + "empty: {category: furniture, item: v.chest}");
    Files.writeString(folder.resolve("b.yml"), "chair: {category: furniture, item: v.dirt}");
    Files.writeString(folder.resolve("c.yml"), "x: [");
    Files.writeString(folder.resolve("ignored.txt"), "");
    projects.loadFolder(folder.toFile());
    assertEquals(2, ProjectLoader.get().size());
    assertNull(ProjectLoader.getByString(null));
    assertEquals("v.oak_stairs", ProjectLoader.getByString("chair").getItem());
    var previous = Woodworking.plugin;
    Woodworking.plugin = null;
    assertNotNull(net.tfminecraft.woodworking.utils.Log.get());
    net.tfminecraft.woodworking.utils.Log.info("fallback");
    Woodworking.plugin = previous;
  }

  @Test
  void commandsPermissionsSelectionAndCompletion() throws Exception {
    var c = new CommandManager();
    var cmd = mock(Command.class);
    var sender = mock(CommandSender.class);
    when(cmd.getName()).thenReturn("other");
    assertFalse(c.onCommand(sender, cmd, "w", new String[0]));
    assertNull(c.onTabComplete(sender, cmd, "w", new String[0]));
    when(cmd.getName()).thenReturn("woodworking");
    c.onCommand(sender, cmd, "w", new String[0]);
    c.onCommand(sender, cmd, "w", new String[] {"other"});
    c.onCommand(sender, cmd, "w", new String[] {"reload"});
    c.onCommand(sender, cmd, "w", new String[] {"select"});
    when(sender.hasPermission(Permissions.ADMIN)).thenReturn(true);
    c.onCommand(sender, cmd, "w", new String[] {"reload"});
    verify(plugin).reload();
    c.onCommand(sender, cmd, "w", new String[] {"select"});
    var p = mock(Player.class);
    Cache.permission = "woodworking.use";
    assertFalse(Permissions.requireUse(p));
    when(p.hasPermission(Permissions.ADMIN)).thenReturn(true);
    assertTrue(Permissions.canUse(p));
    c.onCommand(p, cmd, "w", new String[] {"select"});
    c.onCommand(p, cmd, "w", new String[] {"select", "missing"});
    var project = new WoodProject("chair", "furniture", config("item: v.chest"));
    ProjectLoader.get().put("chair", project);
    var manager = mock(StationManager.class);
    when(plugin.getStations()).thenReturn(manager);
    c.onCommand(p, cmd, "w", new String[] {"select", "chair"});
    var block = mock(org.bukkit.block.Block.class);
    when(p.getTargetBlockExact(6)).thenReturn(block);
    c.onCommand(p, cmd, "w", new String[] {"select", "chair"});
    when(manager.isWoodworkingStation(block)).thenReturn(true);
    var station = new WoodStation(new Location(null, 0, 0, 0));
    when(manager.getOrCreate(any())).thenReturn(station);
    c.onCommand(p, cmd, "w", new String[] {"select", "chair"});
    assertTrue(station.hasProject());
    c.onCommand(p, cmd, "w", new String[] {"select", "chair"});
    verify(manager).markDirty();
    when(p.hasPermission(Permissions.ADMIN)).thenReturn(false);
    Cache.permission = null;
    assertTrue(Permissions.canUse(p));
    Cache.permission = " ";
    assertTrue(Permissions.canUse(p));
    Cache.permission = "woodworking.use";
    when(p.hasPermission(Cache.permission)).thenReturn(true);
    assertTrue(Permissions.requireUse(p));
    assertEquals(List.of("reload"), c.onTabComplete(sender, cmd, "w", new String[] {"re"}));
    assertTrue(c.onTabComplete(p, cmd, "w", new String[] {"select", ""}).isEmpty());
    assertEquals(
        List.of("chair"), c.onTabComplete(sender, cmd, "w", new String[] {"select", "ch"}));
    for (int i = 0; i < 50; i++) ProjectLoader.get().put("x" + i, project);
    assertEquals(40, c.onTabComplete(sender, cmd, "w", new String[] {"select", ""}).size());
    assertTrue(c.onTabComplete(sender, cmd, "w", new String[] {"select", "nope"}).isEmpty());
    assertTrue(c.onTabComplete(sender, cmd, "w", new String[] {"other", ""}).isEmpty());
    assertTrue(c.onTabComplete(sender, cmd, "w", new String[0]).isEmpty());
  }

  @Test
  void lifecycleLoadsReloadsAndFlushes() throws Exception {
    for (String name : List.of("TLibs", "MMOItems", "MythicLib", "ItemsAdder"))
      MockBukkit.createMockPlugin(name);
    try (var managers = mockConstruction(StationManager.class)) {
      var loaded = MockBukkit.load(Woodworking.class);
      assertSame(loaded, Woodworking.plugin);
      assertNotNull(loaded.getCommand("woodworking").getExecutor());
      assertNotNull(loaded.getCommand("woodworking").getTabCompleter());
      assertTrue(loaded.loadSummary().startsWith("Loaded"));
      loaded.createFolders();
      loaded.createConfigs();
      loaded.reload();
      verify(loaded.getStations()).clear();
      server.getScheduler().performTicks(1200);
      verify(loaded.getStations()).flush(false);
      loaded.onDisable();
      verify(loaded.getStations(), atLeastOnce()).flush(true);
      var spy = spy(loaded);
      doReturn(dir.resolve("new").toFile()).when(spy).getDataFolder();
      spy.createFolders();
      assertTrue(Files.isDirectory(dir.resolve("new/data/stations")));
    }
  }
}
