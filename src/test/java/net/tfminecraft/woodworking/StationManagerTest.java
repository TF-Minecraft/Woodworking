package net.tfminecraft.woodworking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.lone.itemsadder.api.Events.FurnitureBreakEvent;
import io.lumine.mythic.lib.api.item.NBTItem;
import java.util.*;
import net.tfminecraft.tlibs.TLibs;
import net.tfminecraft.tlibs.objects.api.BlockAPI;
import net.tfminecraft.woodworking.command.Permissions;
import net.tfminecraft.woodworking.cache.Cache;
import net.tfminecraft.woodworking.database.*;
import net.tfminecraft.woodworking.gui.InventoryManager;
import net.tfminecraft.woodworking.hit.*;
import net.tfminecraft.woodworking.loader.*;
import net.tfminecraft.woodworking.project.*;
import net.tfminecraft.woodworking.station.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockito.*;

class StationManagerTest extends TestSupport {
  StationManager manager;
  Player p;
  PlayerInventory inv;
  World world;
  Location loc;
  Block block;
  WoodProject project;
  WoodMaterial oak;
  CraftingHit cut;
  MockedStatic<NBTItem> nbtApi;
  NBTItem nbt;

  @BeforeEach
  void stationSetup() throws Exception {
    Cache.station = "iaf(test:bench)";
    Cache.brandingTool = "v.stick";
    manager = new StationManager();
    p = mock(Player.class);
    inv = mock(PlayerInventory.class);
    world = mock(World.class);
    loc = new Location(world, 1, 2, 3);
    block = mock(Block.class);
    when(world.getName()).thenReturn("world");
    when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
    when(world.getBlockAt(any(Location.class))).thenReturn(block);
    when(block.getLocation()).thenReturn(loc);
    when(p.getUniqueId()).thenReturn(UUID.randomUUID());
    when(p.getInventory()).thenReturn(inv);
    when(p.getWorld()).thenReturn(world);
    when(p.getLocation()).thenReturn(loc);
    when(inv.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
    when(inv.getItemInMainHand()).thenReturn(item(Material.AIR));
    var blockApi = mock(BlockAPI.class, RETURNS_DEEP_STUBS);
    tlibs.when(TLibs::getBlockAPI).thenReturn(blockApi);
    when(blockApi.getChecker().checkBlock(block, Cache.station)).thenReturn(true);
    Cache.permission = null;
    var type = new HitType("cut", config(""));
    HitTypeLoader.get().clear();
    HitTypeLoader.get().put("cut", type);
    cut = new CraftingHit("cut", config("tool: TOOL.KNIFE\ntype: cut"));
    HitLoader.get().clear();
    HitLoader.get().put("cut", cut);
    oak = new WoodMaterial("oak", config("path: v.oak_log\ntype: wood"));
    MaterialLoader.get().clear();
    MaterialLoader.get().put("oak", oak);
    project =
        new WoodProject(
            "chair", "furniture", config("item: v.chest\nhits: [cut.1]\nrecipe: [oak.1]"));
    ProjectLoader.get().put("chair", project);
    nbt = mock(NBTItem.class);
    nbtApi = mockStatic(NBTItem.class);
    nbtApi.when(() -> NBTItem.get(any(ItemStack.class))).thenReturn(nbt);
  }

  @AfterEach
  void closeNbt() {
    nbtApi.close();
  }

  void cooldownReset() throws Exception {
    var f = StationManager.class.getDeclaredField("clickCooldown");
    f.setAccessible(true);
    ((Map<?, ?>) f.get(manager)).clear();
  }

  PlayerInteractEvent event(Action a) {
    var e = mock(PlayerInteractEvent.class);
    when(e.getClickedBlock()).thenReturn(block);
    when(e.getPlayer()).thenReturn(p);
    when(e.getAction()).thenReturn(a);
    when(e.getHand()).thenReturn(EquipmentSlot.HAND);
    return e;
  }

  void click(Action a) throws Exception {
    cooldownReset();
    manager.onInteract(event(a));
  }

  WoodStation station() {
    var s = manager.getOrCreate(loc);
    s.setProject(project);
    return s;
  }

  void hand(ItemStack s, boolean branding) {
    when(inv.getItemInMainHand()).thenReturn(s);
    when(api.getChecker().checkItemWithPath(s, Cache.brandingTool)).thenReturn(branding);
  }

  @Test
  void registryPersistenceKeysAndFurniturePaths() throws Exception {
    assertNull(manager.get(null));
    assertNull(manager.getOrCreate(null));
    assertNull(manager.remove(null));
    assertNull(StationManager.key(null));
    assertEquals(
        new Location(null, 1, 2, 3), StationManager.key(new Location(null, 1.5, 2.3, 3.9)));
    manager.put(null);
    manager.put(new WoodStation(null));
    var s = manager.getOrCreate(loc);
    assertSame(s, manager.getOrCreate(loc));
    assertSame(s, manager.get(loc));
    assertEquals(1, manager.getStations().size());
    assertSame(s, manager.getMap().get(loc));
    try (var store = mockStatic(StationStore.class)) {
      manager.flush(false);
      store.verifyNoInteractions();
      manager.markDirty();
      manager.flush(false);
      manager.flush(true);
      store.verify(() -> StationStore.saveAll(any()), times(2));
      store.when(StationStore::loadAll).thenReturn(List.of(s));
      manager.clear();
      manager.loadPersisted();
      assertSame(s, manager.get(loc));
      assertSame(s, manager.remove(loc));
      assertNull(manager.remove(loc));
      store.verify(() -> StationStore.delete(loc));
    }
    assertFalse(manager.isWoodworkingStation(null));
    String old = Cache.station;
    Cache.station = null;
    assertFalse(manager.isWoodworkingStation(block));
    assertEquals("", StationManager.furnitureId());
    for (String path : List.of("iaf(test:bench)", "test:bench", "raw", "iaf(broken")) {
      Cache.station = path;
      assertEquals(
          path.equals("iaf(test:bench)") ? "test:bench" : path, StationManager.furnitureId());
    }
    Cache.station = old;
  }

  @Test
  void interactionGuardsPermissionsCooldownAndMenuOpening() throws Exception {
    var e = event(Action.PHYSICAL);
    manager.onInteract(e);
    when(e.getClickedBlock()).thenReturn(null);
    manager.onInteract(e);
    when(e.getClickedBlock()).thenReturn(mock(Block.class));
    when(e.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
    manager.onInteract(e);
    Cache.permission = "use";
    e = event(Action.RIGHT_CLICK_BLOCK);
    manager.onInteract(e);
    manager.onInteract(e);
    verify(p).sendMessage(Permissions.NOT_SKILLED);
    Cache.permission = null;
    manager.onInteract(e);
    verify(p, never()).openInventory(any(Inventory.class));
    cooldownReset();
    manager.onInteract(e);
    verify(p).openInventory(any(Inventory.class));
    assertNotNull(manager.get(loc));
    click(Action.RIGHT_CLICK_BLOCK);
    verify(p, times(2)).openInventory(any(Inventory.class));
  }

  @Test
  void materialsStatusAndToolHitsFollowProgress() throws Exception {
    var s = station();
    var wood = new ItemStack(Material.OAK_LOG, 3);
    hand(wood, false);
    when(api.getChecker().checkItemWithPath(wood, "v.oak_log")).thenReturn(true);
    click(Action.RIGHT_CLICK_BLOCK);
    assertEquals(2, wood.getAmount());
    assertEquals(1, s.getDeposited().size());
    var other = new WoodMaterial("metal", config("path: v.iron_ingot\ntype: metal"));
    MaterialLoader.get().put("metal", other);
    var iron = item(Material.IRON_INGOT);
    hand(iron, false);
    when(api.getChecker().checkItemWithPath(iron, "v.iron_ingot")).thenReturn(true);
    click(Action.RIGHT_CLICK_BLOCK);
    assertEquals(2, s.getDeposited().size());
    hand(item(Material.STICK), true);
    click(Action.RIGHT_CLICK_BLOCK);
    verify(p).sendMessage("§7Project: chair");
    var knife = item(Material.IRON_AXE);
    hand(knife, false);
    click(Action.LEFT_CLICK_BLOCK);
    when(nbt.hasType()).thenReturn(true);
    when(nbt.getType()).thenReturn("TOOL");
    when(nbt.getString("MMOITEMS_ITEM_ID")).thenReturn("UNKNOWN");
    click(Action.LEFT_CLICK_BLOCK);
    when(nbt.getString("MMOITEMS_ITEM_ID")).thenReturn("KNIFE");
    click(Action.LEFT_CLICK_BLOCK);
    assertTrue(s.hitsDone());
    var untyped = new CraftingHit("none", config("tool: TOOL.BAD"));
    HitLoader.get().put("none", untyped);
    when(nbt.getString("MMOITEMS_ITEM_ID")).thenReturn("BAD");
    click(Action.LEFT_CLICK_BLOCK);
    verify(p).sendMessage(contains("cannot work the piece"));
    var empty = new WoodStation(loc, project);
    manager.put(empty);
    when(nbt.getString("MMOITEMS_ITEM_ID")).thenReturn("KNIFE");
    click(Action.LEFT_CLICK_BLOCK);
    verify(p).sendMessage("§cYou have to add all the items before working");
  }

  @Test
  void brandingFinishesCancelsOrFailsWorkAndValidatesOutput() throws Exception {
    hand(item(Material.STICK), true);
    click(Action.LEFT_CLICK_BLOCK);
    manager.getOrCreate(loc);
    click(Action.LEFT_CLICK_BLOCK);
    var s = station();
    click(Action.LEFT_CLICK_BLOCK);
    verify(p).sendMessage(contains("all the items before finishing"));
    s.addMaterial(oak, item(Material.OAK_LOG));
    click(Action.LEFT_CLICK_BLOCK);
    verify(p).sendMessage(contains("all the hits before finishing"));
    s.hit(cut);
    click(Action.LEFT_CLICK_BLOCK);
    assertSame(s, manager.get(loc));
    when(api.getCreator().getItemFromPath("v.chest")).thenReturn(item(Material.CHEST));
    click(Action.LEFT_CLICK_BLOCK);
    assertNull(manager.get(loc));
    verify(world)
        .dropItemNaturally(any(Location.class), argThat(x -> x.getType() == Material.CHEST));
    s = station();
    s.addMaterial(oak, item(Material.OAK_LOG));
    s.hit(cut);
    s.hit(cut);
    click(Action.LEFT_CLICK_BLOCK);
    assertNull(manager.get(loc));
    s = station();
    s.addMaterial(oak, item(Material.OAK_LOG));
    when(p.isSneaking()).thenReturn(true);
    when(inv.addItem(any(ItemStack.class)))
        .thenAnswer(i -> new HashMap<>(Map.of(0, (ItemStack) i.getArgument(0))));
    click(Action.LEFT_CLICK_BLOCK);
    assertNull(manager.get(loc));
    verify(world, atLeastOnce())
        .dropItemNaturally(any(Location.class), argThat(x -> x.getType() == Material.OAK_LOG));
  }

  @Test
  void furnitureBreakProtectsToolsAndRefundsValidDestruction() throws Exception {
    var e = mock(FurnitureBreakEvent.class);
    manager.onToolBreak(e);
    manager.onFurnitureBreak(e);
    when(e.getNamespacedID()).thenReturn("wrong");
    manager.onToolBreak(e);
    manager.onFurnitureBreak(e);
    when(e.getNamespacedID()).thenReturn(StationManager.furnitureId());
    var entity = mock(Entity.class);
    when(entity.getLocation()).thenReturn(loc);
    when(e.getBukkitEntity()).thenReturn(entity);
    manager.onToolBreak(e);
    manager.onFurnitureBreak(e);
    when(e.getPlayer()).thenReturn(p);
    hand(null, false);
    manager.onToolBreak(e);
    hand(item(Material.AIR), false);
    manager.onToolBreak(e);
    hand(item(Material.STICK), true);
    manager.onToolBreak(e);
    verify(e).setCancelled(true);
    hand(item(Material.IRON_AXE), false);
    manager.onToolBreak(e);
    when(nbt.hasType()).thenReturn(true);
    when(nbt.getType()).thenReturn("TOOL");
    when(nbt.getString("MMOITEMS_ITEM_ID")).thenReturn("KNIFE");
    manager.onToolBreak(e);
    verify(e, times(2)).setCancelled(true);
    var s = station();
    s.addMaterial(oak, item(Material.OAK_LOG));
    manager.onFurnitureBreak(e);
    assertNull(manager.get(loc));
    verify(inv).addItem(any(ItemStack.class));
    manager.getOrCreate(loc);
    manager.onFurnitureBreak(e);
    when(e.getPlayer()).thenReturn(null);
    s = station();
    s.addMaterial(oak, item(Material.OAK_LOG));
    manager.onFurnitureBreak(e);
    verify(world).dropItemNaturally(any(Location.class), any(ItemStack.class));
  }

  @Test
  void menusRouteCategoriesPagesAndSelectedProjects() throws Exception {
    var view = mock(InventoryView.class);
    var e = mock(InventoryClickEvent.class);
    when(e.getView()).thenReturn(view);
    when(view.getTitle()).thenReturn("other");
    manager.onMenuClick(e);
    when(view.getTitle()).thenReturn(InventoryManager.CATEGORY_TITLE);
    when(e.getWhoClicked()).thenReturn(mock(HumanEntity.class));
    manager.onMenuClick(e);
    when(e.getWhoClicked()).thenReturn(p);
    Cache.permission = "use";
    manager.onMenuClick(e);
    Cache.permission = null;
    manager.onMenuClick(e);
    var plain = mock(ItemStack.class);
    when(e.getCurrentItem()).thenReturn(plain);
    manager.onMenuClick(e);
    var icon = item(Material.CHEST);
    when(e.getCurrentItem()).thenReturn(icon);
    manager.onMenuClick(e);
    var meta = icon.getItemMeta();
    meta.getPersistentDataContainer()
        .set(InventoryManager.pageKey(), PersistentDataType.INTEGER, 1);
    icon.setItemMeta(meta);
    manager.onMenuClick(e);
    meta.getPersistentDataContainer()
        .set(InventoryManager.categoryKey(), PersistentDataType.STRING, "furniture");
    icon.setItemMeta(meta);
    manager.onMenuClick(e);
    CategoryLoader.get().put("furniture", new WoodCategory("furniture", config("")));
    manager.onMenuClick(e);
    meta.getPersistentDataContainer().remove(InventoryManager.pageKey());
    icon.setItemMeta(meta);
    manager.onMenuClick(e);
    CategoryLoader.get().clear();
    manager.onMenuClick(e);
    meta.getPersistentDataContainer().remove(InventoryManager.categoryKey());
    meta.getPersistentDataContainer()
        .set(InventoryManager.projectKey(), PersistentDataType.STRING, "missing");
    icon.setItemMeta(meta);
    when(view.getTitle()).thenReturn(InventoryManager.PROJECT_TITLE);
    manager.onMenuClick(e);
    meta.getPersistentDataContainer()
        .set(InventoryManager.projectKey(), PersistentDataType.STRING, "chair");
    icon.setItemMeta(meta);
    manager.onMenuClick(e);
    verify(p).sendMessage(contains("no longer available"));
    click(Action.RIGHT_CLICK_BLOCK);
    manager.onMenuClick(e);
    assertSame(project, manager.get(loc).getProject());
    var f = StationManager.class.getDeclaredField("openMenu");
    f.setAccessible(true);
    @SuppressWarnings("unchecked")
    var menus = (Map<UUID, WoodStation>) f.get(manager);
    menus.put(p.getUniqueId(), manager.get(loc));
    manager.onMenuClick(e);
    verify(p).sendMessage(contains("already has a project"));
    menus.put(UUID.randomUUID(), new WoodStation(new Location(world, 9, 2, 3)));
    manager.remove(loc);
    assertEquals(1, menus.size());
  }

  @Test
  void offhandInteractionDoesNotDoubleDepositOrOpenMenus() throws Exception {
    var e = event(Action.RIGHT_CLICK_BLOCK);
    when(e.getHand()).thenReturn(EquipmentSlot.OFF_HAND);
    manager.onInteract(e);
    verify(p, never()).openInventory(any(Inventory.class));
  }

  @Test
  void airOutputDoesNotConsumeFinishedWork() throws Exception {
    var s = station();
    s.addMaterial(oak, item(Material.OAK_LOG));
    s.hit(cut);
    hand(item(Material.STICK), true);
    when(api.getCreator().getItemFromPath("v.chest")).thenReturn(item(Material.AIR));
    click(Action.LEFT_CLICK_BLOCK);
    assertSame(s, manager.get(loc));
    assertTrue(s.hasProject());
  }

  @Test
  void heldItemAbsenceAndCooldownExpiryDoNotInventActions() throws Exception {
    station();
    hand(null, false);
    click(Action.LEFT_CLICK_BLOCK);
    click(Action.RIGHT_CLICK_BLOCK);
    hand(item(Material.AIR), false);
    click(Action.LEFT_CLICK_BLOCK);
    click(Action.RIGHT_CLICK_BLOCK);
    hand(item(Material.DIRT), false);
    click(Action.RIGHT_CLICK_BLOCK);
    assertTrue(manager.get(loc).getDeposited().isEmpty());
    var field = StationManager.class.getDeclaredField("clickCooldown");
    field.setAccessible(true);
    @SuppressWarnings("unchecked")
    var times = (Map<UUID, Long>) field.get(manager);
    times.put(p.getUniqueId(), 0L);
    manager.onInteract(event(Action.RIGHT_CLICK_BLOCK));
  }

  @Test
  void outputFallbacksQualityAndDetachedWorldEffects() throws Exception {
    QualityLoader.get().clear();
    QualityLoader.get().put("good", new Quality("good", config("name: Good\nvalue: 2\namount: 0")));
    var method = new Class[] {Player.class, WoodStation.class};
    for (String path : Arrays.asList("ia.bad", "ia.good", "v.dirt", null)) {
      var configuration = config("");
      configuration.set("item", path);
      var proj = new WoodProject("x", "furniture", configuration);
      var station = new WoodStation(new Location(null, 1, 2, 3), proj);
      var output = item(path != null && path.equals("ia.bad") ? Material.DIRT : Material.CHEST);
      when(api.getCreator().getItemFromPath(path)).thenReturn(output);
      invoke(manager, "completeCraft", method, p, station);
      if ("ia.bad".equals(path)) assertTrue(station.hasProject());
      else assertFalse(station.hasProject());
    }
    var detached = new WoodStation(new Location(null, 1, 2, 3), project);
    invoke(manager, "failCraft", method, p, detached);
    assertFalse(detached.hasProject());
    invoke(
        manager,
        "dropAt",
        new Class[] {Location.class, List.class},
        new Location(null, 0, 0, 0),
        List.of(item(Material.DIAMOND)));
    invoke(
        manager,
        "playWorkFx",
        new Class[] {Location.class, Material.class},
        new Location(null, 0, 0, 0),
        Material.OAK_LOG);
  }
}
