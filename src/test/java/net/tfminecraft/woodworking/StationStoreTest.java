package net.tfminecraft.woodworking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.tfminecraft.woodworking.database.*;
import net.tfminecraft.woodworking.hit.*;
import net.tfminecraft.woodworking.loader.*;
import net.tfminecraft.woodworking.project.*;
import net.tfminecraft.woodworking.station.*;
import org.bukkit.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.Test;

class StationStoreTest extends TestSupport {
  WoodProject project() throws Exception {
    var hit = new CraftingHit("cut", config("type: cut\ntool: TOOL.KNIFE"));
    HitTypeLoader.get().put("cut", new HitType("cut", config("")));
    hit = new CraftingHit("cut", config("type: cut\ntool: TOOL.KNIFE"));
    HitLoader.get().put("cut", hit);
    MaterialLoader.get().put("oak", new WoodMaterial("oak", config("path: v.oak_log\ntype: wood")));
    var p =
        new WoodProject(
            "chair", "furniture", config("item: v.chest\nhits: [cut.1]\nrecipe: [oak.1]"));
    ProjectLoader.get().put("chair", p);
    return p;
  }

  @Test
  void roundtripRestoresProgressAndExactDepositedItems() throws Exception {
    var world = server.addSimpleWorld("world");
    var loc = new Location(world, 1, 2, 3);
    var s = new WoodStation(loc, project());
    var stack = new ItemStack(Material.OAK_LOG, 3);
    var meta = stack.getItemMeta();
    meta.setDisplayName("Named wood");
    stack.setItemMeta(meta);
    s.addMaterial(MaterialLoader.getByString("oak"), stack);
    s.hit(HitLoader.getByString("cut"));
    assertTrue(StationStore.loadAll().isEmpty());
    StationStore.saveAll(
        Arrays.asList(null, new WoodStation(loc), new WoodStation(null, project()), s));
    assertTrue(StationStore.fileFor(loc).exists());
    var loaded = StationStore.loadAll();
    assertEquals(1, loaded.size());
    assertEquals(StationFeedback.SUCCESS, loaded.getFirst().canFinish());
    assertEquals(
        "Named wood", loaded.getFirst().getDeposited().getFirst().getItemMeta().getDisplayName());
    assertEquals("unknown_1_2_3.json", StationStore.fileName(new Location(null, 1, 2, 3)));
    var folder = StationStore.folder().toPath();
    Files.createDirectory(folder.resolve("nested"));
    Files.writeString(folder.resolve("ignore.txt"), "leave");
    StationStore.saveAll(List.of(s));
    assertTrue(Files.exists(folder.resolve("ignore.txt")));
    StationStore.delete(null);
    StationStore.delete(loc);
    StationStore.delete(loc);
    assertFalse(StationStore.fileFor(loc).exists());
    StationStore.saveAll(List.of(s));
    StationStore.saveAll(null);
    assertFalse(StationStore.fileFor(loc).exists());
  }

  @Test
  void corruptAndUnavailableFilesRemainOnDiskAcrossSave() throws Exception {
    var world = server.addSimpleWorld("world");
    project();
    StationStore.folder().mkdirs();
    for (var e :
        Map.of(
                "bad.json",
                "{broken",
                "null.json",
                "null",
                "missingworld.json",
                "{\"project\":\"chair\"}",
                "missingproject.json",
                "{\"world\":\"world\"}",
                "otherworld.json",
                "{\"world\":\"offline\",\"project\":\"chair\"}",
                "unknown.json",
                "{\"world\":\"world\",\"project\":\"missing\"}")
            .entrySet())
      Files.writeString(StationStore.folder().toPath().resolve(e.getKey()), e.getValue());
    assertDoesNotThrow(StationStore::loadAll);
    StationStore.saveAll(List.of());
    assertEquals(6, Files.list(StationStore.folder().toPath()).count());
  }

  @Test
  void missingFolderOrFileAndInvalidEncodedDataAreHandled() throws Exception {
    assertTrue(StationStore.loadAll().isEmpty());
    var world = server.addSimpleWorld("world");
    var p = project();
    var f = StationStore.folder().toPath().resolve("invaliditem.json");
    Files.writeString(
        f,
        "{\"world\":\"world\",\"project\":\"chair\",\"deposited\":[null,\""
            + " \",\"notbase64\",\"eA==\"]}");
    assertEquals(1, StationStore.loadAll().size());
    Files.writeString(
        f,
        "{\"world\":\"world\",\"project\":\"chair\",\"materials\":null,\"hits\":null,\"deposited\":null}");
    assertEquals(1, StationStore.loadAll().size());
    assertNull(
        invoke(
            StationStore.class,
            "loadFile",
            new Class[] {File.class},
            dir.resolve("absent").toFile()));
    assertNull(
        invoke(
            StationStore.class,
            "toData",
            new Class[] {WoodStation.class},
            new WoodStation(new Location(null, 0, 0, 0), p)));
    assertNull(
        invoke(
            StationStore.class,
            "toData",
            new Class[] {WoodStation.class},
            new WoodStation(new Location(world, 0, 0, 0))));
    var bytes = new ByteArrayOutputStream();
    try (var out = new org.bukkit.util.io.BukkitObjectOutputStream(bytes)) {
      out.writeObject("not an item");
    }
    assertNull(
        invoke(
            StationStore.class,
            "decodeItem",
            new Class[] {String.class},
            Base64.getEncoder().encodeToString(bytes.toByteArray())));
    assertEquals(
        List.of(),
        invoke(StationStore.class, "encodeItems", new Class[] {List.class}, (Object) null));
    assertEquals(
        List.of(),
        invoke(
            StationStore.class,
            "encodeItems",
            new Class[] {List.class},
            Arrays.asList((ItemStack) null)));
  }

  @Test
  void failedWritesAndDirectoryListingDoNotCrash() throws Exception {
    var p = project();
    var loc = new Location(server.addSimpleWorld("world"), 1, 2, 3);
    Files.createDirectories(StationStore.fileFor(loc).toPath());
    StationStore.saveAll(List.of(new WoodStation(loc, p)));
    Files.delete(StationStore.fileFor(loc).toPath());
    Files.delete(StationStore.folder().toPath());
    Files.writeString(StationStore.folder().toPath(), "not a directory");
    StationStore.saveAll(null);
    assertTrue(StationStore.loadAll().isEmpty());
  }

  @Test
  void rejectedFileCollisionIsQuarantinedBeforeSavingNewBench() throws Exception {
    var loc = new Location(server.addSimpleWorld("world"), 1, 2, 3);
    var s = new WoodStation(loc, project());
    StationStore.folder().mkdirs();
    var file = StationStore.fileFor(loc).toPath();
    Files.writeString(file, "{broken");
    assertTrue(StationStore.loadAll().isEmpty());
    StationStore.delete(loc);
    assertEquals("{broken", Files.readString(file));
    StationStore.saveAll(List.of(s));
    assertEquals(1, StationStore.loadAll().size());
    try (var files = Files.list(file.getParent())) {
      var rejected =
          files
              .filter(x -> x.getFileName().toString().contains(".rejected-"))
              .findFirst()
              .orElseThrow();
      assertEquals("{broken", Files.readString(rejected));
    }
  }

  @Test
  void administratorDeletedRejectedFileAllowsReplacementAndLaterSaves() throws Exception {
    var loc = new Location(server.addSimpleWorld("world"), 1, 2, 3);
    var station = new WoodStation(loc, project());
    StationStore.folder().mkdirs();
    var file = StationStore.fileFor(loc).toPath();
    Files.writeString(file, "{broken");
    assertTrue(StationStore.loadAll().isEmpty());

    Files.delete(file);
    StationStore.saveAll(List.of(station));
    assertTrue(Files.isRegularFile(file));
    StationStore.saveAll(List.of(station));
    try (var files = Files.list(file.getParent())) {
      assertEquals(List.of(file), files.toList());
    }
    assertEquals(1, StationStore.loadAll().size());
    StationStore.delete(loc);
    assertFalse(Files.exists(file));
  }

  @Test
  void failedQuarantineRetainsOriginalAndSuccessfulRetryWorks() throws Exception {
    var loc = new Location(server.addSimpleWorld("world"), 1, 2, 3);
    var s = new WoodStation(loc, project());
    StationStore.folder().mkdirs();
    var file = StationStore.fileFor(loc).toPath();
    Files.writeString(file, "{broken");
    StationStore.loadAll();
    try (var fs = mockStatic(Files.class, CALLS_REAL_METHODS)) {
      fs.when(() -> Files.move(eq(file), any(Path.class))).thenThrow(new IOException("blocked"));
      StationStore.saveAll(List.of(s));
      assertEquals("{broken", Files.readString(file));
    }
    StationStore.saveAll(List.of(s));
    assertEquals(1, StationStore.loadAll().size());
  }

  @Test
  void deleteFailuresWarnAndSerializationFailuresDoNotCrash() throws Exception {
    var loc = new Location(server.addSimpleWorld("world"), 1, 2, 3);
    File fail = mock(File.class);
    when(fail.exists()).thenReturn(true);
    when(fail.isFile()).thenReturn(true);
    when(fail.getName()).thenReturn("fail.json");
    when(fail.toPath()).thenReturn(dir.resolve("fail.json"));
    var folder = mock(File.class);
    when(folder.exists()).thenReturn(true);
    when(folder.listFiles()).thenReturn(new File[] {fail});
    try (var store = mockStatic(StationStore.class, CALLS_REAL_METHODS)) {
      store.when(() -> StationStore.fileFor(loc)).thenReturn(fail);
      store.when(StationStore::folder).thenReturn(folder);
      StationStore.delete(loc);
      StationStore.saveAll(null);
      verify(fail, times(2)).delete();
    }
    try (var output =
        mockConstruction(
            org.bukkit.util.io.BukkitObjectOutputStream.class,
            (stream, c) -> doThrow(new IOException("write")).when(stream).writeObject(any()))) {
      assertNull(
          invoke(
              StationStore.class,
              "encodeItem",
              new Class[] {ItemStack.class},
              item(Material.OAK_LOG)));
    }
  }

  @Test
  void saveCreatesDirectoryAndSkipsDetachedWorldData() throws Exception {
    StationStore.saveAll(List.of(new WoodStation(new Location(null, 0, 0, 0), project())));
    assertTrue(StationStore.folder().isDirectory());
    Files.createDirectory(StationStore.folder().toPath().resolve("nested"));
    Files.writeString(StationStore.folder().toPath().resolve("ignore.txt"), "text");
    assertTrue(StationStore.loadAll().isEmpty());
  }
}
