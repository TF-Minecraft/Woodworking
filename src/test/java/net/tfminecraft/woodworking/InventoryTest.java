package net.tfminecraft.woodworking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import net.tfminecraft.woodworking.gui.*;
import net.tfminecraft.woodworking.hit.*;
import net.tfminecraft.woodworking.loader.*;
import net.tfminecraft.woodworking.project.*;
import org.bukkit.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.Test;

class InventoryTest extends TestSupport {
  @Test
  void categoriesSkipInvalidIconsAndLimitInventorySize() throws Exception {
    var m = new InventoryManager();
    var p = server.addPlayer();
    for (String key : Arrays.asList("missing", "blank", "null", "badia", "air")) {
      var c =
          new WoodCategory(
              key,
              config(
                  "item: "
                      + switch (key) {
                        case "missing" -> "null";
                        case "blank" -> "' '";
                        case "badia" -> "ia.bad";
                        default -> key;
                      }));
      CategoryLoader.get().put(key, c);
    }
    when(api.getCreator().getItemFromPath("ia.bad")).thenReturn(item(Material.DIRT));
    when(api.getCreator().getItemFromPath("air")).thenReturn(item(Material.AIR));
    CategoryLoader.get().put("goodia", new WoodCategory("goodia", config("item: ia.good")));
    when(api.getCreator().getItemFromPath("ia.good")).thenReturn(item(Material.CHEST));
    when(api.getCreator().getItemFromPath("v.chest")).thenAnswer(i -> item(Material.CHEST));
    for (int i = 0; i < 30; i++)
      CategoryLoader.get().put("c" + i, new WoodCategory("c" + i, config("item: v.chest")));
    m.openCategories(p);
    var inv = p.getOpenInventory().getTopInventory();
    assertEquals(
        27, Arrays.stream(inv.getContents()).filter(x -> x.getType() == Material.CHEST).count());
    assertEquals(
        "goodia",
        inv.getItem(0)
            .getItemMeta()
            .getPersistentDataContainer()
            .get(InventoryManager.categoryKey(), org.bukkit.persistence.PersistentDataType.STRING));
  }

  @Test
  void projectPaginationCountsOnlyValidIconsAndDecoratesRequirements() throws Exception {
    var m = new InventoryManager();
    var p = server.addPlayer();
    var cat = new WoodCategory("chairs", config(""));
    HitTypeLoader.get().put("cut", new HitType("cut", config("name: Cut")));
    HitLoader.get().put("cut", new CraftingHit("cut", config("type: cut")));
    MaterialLoader.get().put("oak", new WoodMaterial("oak", config("type: wood\npath: v.oak_log")));
    cat.addProject(new WoodProject("bad", "chairs", config("item: missing")));
    when(api.getCreator().getItemFromPath("v.chest")).thenAnswer(i -> item(Material.CHEST));
    for (int i = 0; i < 50; i++)
      cat.addProject(
          new WoodProject(
              "p" + i, "chairs", config("item: v.chest\nhits: [cut.1]\nrecipe: [oak.2]")));
    m.openProjects(p, cat, -1);
    var inv = p.getOpenInventory().getTopInventory();
    assertEquals(Material.ARROW, inv.getItem(53).getType());
    assertEquals(
        45, Arrays.stream(inv.getContents()).filter(x -> x.getType() == Material.CHEST).count());
    assertTrue(inv.getItem(0).getItemMeta().getLore().getFirst().contains("2"));
    m.openProjects(p, cat, 1);
    inv = p.getOpenInventory().getTopInventory();
    assertEquals(Material.ARROW, inv.getItem(45).getType());
    assertEquals(Material.GRAY_STAINED_GLASS_PANE, inv.getItem(53).getType());
    m.openProjects(p, new WoodCategory("empty", config("")), 0);
    assertEquals(
        Material.GRAY_STAINED_GLASS_PANE,
        p.getOpenInventory().getTopInventory().getItem(0).getType());
    var air = item(Material.AIR);
    assertSame(
        air,
        invoke(
            m,
            "decorateProject",
            new Class[] {ItemStack.class, WoodProject.class},
            air,
            cat.getProjects().getFirst()));
  }
}
