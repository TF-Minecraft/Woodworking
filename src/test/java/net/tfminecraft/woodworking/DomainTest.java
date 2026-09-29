package net.tfminecraft.woodworking;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import net.tfminecraft.woodworking.hit.*;
import net.tfminecraft.woodworking.loader.*;
import net.tfminecraft.woodworking.project.*;
import net.tfminecraft.woodworking.station.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;

class DomainTest {
  ServerMock server;
  WoodMaterial wood, extra;
  CraftingHit hit;
  HitType type;
  WoodProject project;

  YamlConfiguration yaml(String text) throws Exception {
    var y = new YamlConfiguration();
    y.loadFromString(text);
    return y;
  }

  @BeforeEach
  void setup() throws Exception {
    server = MockBukkit.mock();
    MaterialLoader.get().clear();
    HitLoader.get().clear();
    HitTypeLoader.get().clear();
    QualityLoader.get().clear();
    wood = new WoodMaterial("oak", yaml("path: v.oak_log\ntype: wood"));
    extra = new WoodMaterial("birch", yaml("path: v.birch_log\ntype: wood"));
    MaterialLoader.get().put("oak", wood);
    MaterialLoader.get().put("birch", extra);
    type = new HitType("cut", yaml(""));
    HitTypeLoader.get().put("cut", type);
    hit = new CraftingHit("whittle", yaml("tool: TOOL.KNIFE\ntype: cut"));
    HitLoader.get().put("whittle", hit);
    project =
        new WoodProject(
            "chair", "furniture", yaml("item: v.oak_stairs\nhits: [whittle.2]\nrecipe: [oak.2]"));
  }

  @AfterEach
  void teardown() {
    MockBukkit.unmock();
  }

  @Test
  void definitionsExposeConfiguredAndDefaultValues() throws Exception {
    assertEquals("oak", wood.getId());
    assertEquals("oak", wood.getName());
    assertEquals("v.oak_log", wood.getPath());
    assertEquals("wood", wood.getType());
    assertEquals("other", new WoodMaterial("x", yaml("")).getType());
    var mat = new MaterialType("wood", yaml("name: Lumber"));
    assertEquals("wood", mat.getId());
    assertEquals("Lumber", mat.getName());
    assertEquals("cut", type.getId());
    assertEquals("cut", type.getName());
    assertEquals("whittle", hit.getId());
    assertEquals("whittle", hit.getName());
    assertEquals("TOOL.KNIFE", hit.getTool());
    assertSame(type, hit.getType());
    var q = new Quality("good", yaml("amount: 90\nvalue: 2\nname: Good"));
    assertEquals("good", q.getId());
    assertEquals("Good", q.getName());
    assertEquals(90, q.getAmount());
    assertEquals(2, q.getValue());
    assertTrue(q.isValid(90));
    assertFalse(q.isValid(89));
    var cat = new WoodCategory("furniture", yaml("item: v.chest"));
    assertEquals("furniture", cat.getId());
    assertEquals("furniture", cat.getName());
    assertEquals("v.chest", cat.getItem());
    cat.addProject(project);
    assertEquals(List.of(project), cat.getProjects());
    cat.clearProjects();
    assertTrue(cat.getProjects().isEmpty());
    assertEquals("chair", project.getId());
    assertEquals("chair", project.getName());
    assertEquals("furniture", project.getCategoryId());
    assertEquals("v.oak_stairs", project.getItem());
    assertEquals(2, project.getTotalHits());
    assertEquals(2, project.getTotalMaterials());
    assertEquals(Map.of(type, 2), project.getHitsByType());
    assertEquals(Map.of("wood", 2), project.getMaterialsByType());
    assertThrows(UnsupportedOperationException.class, () -> project.getHits().clear());
    assertThrows(UnsupportedOperationException.class, () -> project.getRecipe().clear());
  }

  @Test
  void malformedRequirementsAreSkippedAndDuplicatesCombined() throws Exception {
    var untyped = new CraftingHit("untyped", yaml("tool: TOOL.X"));
    HitLoader.get().put("untyped", untyped);
    var p =
        new WoodProject(
            "test",
            "furniture",
            yaml(
                "hits: [invalid, '.1', 'whittle.', whittle.no, whittle.0, whittle.-2, missing.1,"
                    + " whittle.1, whittle.2, untyped.1]\n"
                    + "recipe: [bad, '.1', 'oak.', oak.no, oak.0, oak.-2, missing.1, oak.1, oak.2,"
                    + " birch.1]"));
    assertEquals(4, p.getTotalHits());
    assertEquals(Map.of(type, 3), p.getHitsByType());
    assertEquals(4, p.getTotalMaterials());
    assertEquals(Map.of("wood", 4), p.getMaterialsByType());
  }

  @Test
  void stationRequiresMaterialsAndExactHitsBeforeFinishing() {
    var loc = new Location(null, 1, 2, 3);
    var s = new WoodStation(loc);
    assertSame(loc, s.getLoc());
    assertFalse(s.hasProject());
    assertNull(s.getProject());
    assertEquals(StationFeedback.NO_PROJECT, s.canFinish());
    assertEquals(StationFeedback.NO_PROJECT, s.addMaterial(wood, null));
    assertEquals(StationFeedback.NO_PROJECT, s.hit(hit));
    assertFalse(s.materialsAdded());
    assertFalse(s.hitsDone());
    assertFalse(s.checkExactHits());
    assertFalse(s.checkExactRecipe());
    assertEquals(100, s.calculatePercentage());
    s.setProject(project);
    assertSame(project, s.getProject());
    assertTrue(s.hasProject());
    assertEquals(StationFeedback.LACKING_ITEMS, s.canFinish());
    assertEquals(StationFeedback.LACKING_ITEMS, s.hit(hit));
    assertEquals(StationFeedback.WRONG_TYPE, s.addMaterial(null, null));
    assertFalse(s.checkExactHits());
    var stack = new ItemStack(Material.OAK_LOG, 8);
    assertEquals(StationFeedback.SUCCESS, s.addMaterial(wood, stack));
    assertEquals(8, stack.getAmount());
    assertEquals(1, s.getDeposited().getFirst().getAmount());
    s.addMaterial(wood, stack);
    assertEquals(StationFeedback.LACKING_HITS, s.canFinish());
    assertEquals(StationFeedback.WRONG_TYPE, s.hit(null));
    assertEquals(StationFeedback.SUCCESS, s.hit(hit));
    assertEquals(50, s.calculatePercentage());
    s.hit(hit);
    assertEquals(StationFeedback.SUCCESS, s.canFinish());
    assertTrue(s.checkExactRecipe());
    assertTrue(s.checkExactHits());
    assertEquals(100, s.calculatePercentage());
    assertEquals(2, s.getTypes().get("wood").getCurrent());
    assertEquals(2, s.getHits().get(hit).getCurrent());
    assertEquals(2, s.getHitTypes().get(type).getCurrent());
    assertEquals(2, s.getDepositedByMaterial().get(wood));
    s.hit(hit);
    assertEquals(50, s.calculatePercentage());
    assertEquals(StationFeedback.RECIPE_MISMATCH, s.canFinish());
    s.hit(hit);
    assertEquals(0, s.calculatePercentage());
    assertEquals(2, s.cancel().size());
    assertFalse(s.hasProject());
    assertTrue(s.getDeposited().isEmpty());
    s.setProject(null);
    assertTrue(s.getTypes().isEmpty());
  }

  @Test
  void wrongIngredientsAndUnexpectedHitsCountButFailExactRecipe() throws Exception {
    var s = new WoodStation(new Location(null, 0, 0, 0), project);
    s.addMaterial(extra, null);
    s.addMaterial(extra, null);
    assertTrue(s.materialsAdded());
    assertFalse(s.checkExactRecipe());
    s.hit(hit);
    s.hit(hit);
    assertEquals(StationFeedback.RECIPE_MISMATCH, s.canFinish());
    s.addMaterial(wood, null);
    assertFalse(s.checkExactRecipe());
    var otherType = new HitType("hammer", yaml(""));
    HitTypeLoader.get().put("hammer", otherType);
    var otherHit = new CraftingHit("hammer", yaml("type: hammer\ntool: TOOL.HAMMER"));
    assertEquals(StationFeedback.SUCCESS, s.hit(otherHit));
    assertFalse(s.checkExactHits());
    assertEquals(StationFeedback.WRONG_TYPE, s.hit(new CraftingHit("none", yaml(""))));
  }

  @Test
  void restoredProgressIgnoresInvalidIdsAndClampsNegativeCounts() throws Exception {
    var s = new WoodStation(new Location(null, 0, 0, 0), project);
    s.applySavedProgress(null, null, null);
    var mats = new HashMap<String, Integer>();
    mats.put("oak", 2);
    mats.put("missing", 1);
    mats.put("birch", null);
    var hits = new HashMap<String, Integer>();
    hits.put("whittle", 2);
    hits.put("missing", 1);
    s.applySavedProgress(mats, hits, List.of(new ItemStack(Material.OAK_LOG, 2)));
    assertEquals(StationFeedback.SUCCESS, s.canFinish());
    assertEquals(1, s.getDeposited().size());
    hits.put("whittle", null);
    s.applySavedProgress(Map.of("oak", -10), hits, List.of());
    assertEquals(0, s.getTypes().get("wood").getCurrent());
    var exotic = new WoodMaterial("iron", yaml("path: v.iron_ingot\ntype: metal"));
    MaterialLoader.get().put("iron", exotic);
    var untyped = new CraftingHit("untyped", yaml(""));
    HitLoader.get().put("untyped", untyped);
    s.applySavedProgress(Map.of("iron", 1), Map.of("untyped", -2), null);
    assertEquals(1, s.getTypes().get("metal").getCurrent());
    assertEquals(0, s.getHits().get(untyped).getCurrent());
    var q = new Quality("ok", yaml("value: 1"));
    QualityLoader.get().put("ok", q);
    assertSame(q, s.getQuality());
  }

  @Test
  void extraHitTypesAndMaterialCountsRemainAccountedFor() throws Exception {
    var s = new WoodStation(new Location(null, 0, 0, 0), project);
    s.addMaterial(wood, null);
    s.addMaterial(wood, null);
    s.addMaterial(wood, null);
    assertFalse(s.checkExactRecipe());
    var unexpected = new CraftingHit("extra", yaml("type: cut"));
    HitLoader.get().put("extra", unexpected);
    s.applySavedProgress(Map.of("oak", 2), Map.of("whittle", 2, "extra", 1), null);
    assertFalse(s.checkExactHits());
    var otherType = new HitType("hammer", yaml(""));
    HitTypeLoader.get().put("hammer", otherType);
    var other = new CraftingHit("hammer", yaml("type: hammer"));
    HitLoader.get().put("hammer", other);
    s.applySavedProgress(Map.of(), Map.of("hammer", 1), null);
    assertEquals(1, s.getHitTypes().get(otherType).getCurrent());
  }
}
