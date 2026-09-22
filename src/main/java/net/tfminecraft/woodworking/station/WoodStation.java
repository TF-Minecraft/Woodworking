package net.tfminecraft.woodworking.station;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.tlibs.objects.utils.IntCounter;
import net.tfminecraft.woodworking.hit.CraftingHit;
import net.tfminecraft.woodworking.hit.HitType;
import net.tfminecraft.woodworking.loader.HitLoader;
import net.tfminecraft.woodworking.loader.MaterialLoader;
import net.tfminecraft.woodworking.loader.QualityLoader;
import net.tfminecraft.woodworking.project.Quality;
import net.tfminecraft.woodworking.project.WoodMaterial;
import net.tfminecraft.woodworking.project.WoodProject;

public class WoodStation {

    private final Location loc;
    private WoodProject project;

    private final LinkedHashMap<String, IntCounter> types = new LinkedHashMap<>();
    private final LinkedHashMap<CraftingHit, IntCounter> hits = new LinkedHashMap<>();
    private final LinkedHashMap<HitType, IntCounter> hitTypes = new LinkedHashMap<>();
    private final List<ItemStack> deposited = new ArrayList<>();
    private final LinkedHashMap<WoodMaterial, Integer> depositedByMaterial = new LinkedHashMap<>();

    public WoodStation(Location loc) {
        this.loc = loc;
    }

    public WoodStation(Location loc, WoodProject project) {
        this.loc = loc;
        setProject(project);
    }

    public Location getLoc() {
        return loc;
    }

    public WoodProject getProject() {
        return project;
    }

    public boolean hasProject() {
        return project != null;
    }

    public Map<String, IntCounter> getTypes() {
        return Collections.unmodifiableMap(types);
    }

    public Map<CraftingHit, IntCounter> getHits() {
        return Collections.unmodifiableMap(hits);
    }

    public Map<HitType, IntCounter> getHitTypes() {
        return Collections.unmodifiableMap(hitTypes);
    }

    public List<ItemStack> getDeposited() {
        return Collections.unmodifiableList(deposited);
    }

    public Map<WoodMaterial, Integer> getDepositedByMaterial() {
        return Collections.unmodifiableMap(depositedByMaterial);
    }

    /**
     * Assigns a project and resets all counters. Hit requirements come from the project,
     * not from deposited materials.
     */
    public void setProject(WoodProject project) {
        this.project = project;
        types.clear();
        hits.clear();
        hitTypes.clear();
        deposited.clear();
        depositedByMaterial.clear();
        if (project == null) return;

        for (Map.Entry<String, Integer> e : project.getMaterialsByType().entrySet()) {
            IntCounter c = new IntCounter();
            c.setNeeded(e.getValue());
            types.put(e.getKey(), c);
        }
        for (Map.Entry<CraftingHit, Integer> e : project.getHits().entrySet()) {
            IntCounter c = new IntCounter();
            c.setNeeded(e.getValue());
            hits.put(e.getKey(), c);
        }
        for (Map.Entry<HitType, Integer> e : project.getHitsByType().entrySet()) {
            IntCounter c = new IntCounter();
            c.setNeeded(e.getValue());
            hitTypes.put(e.getKey(), c);
        }
    }

    /**
     * Applies saved counters and item copies after {@link #setProject(WoodProject)}.
     * Needed values stay on the live project; only currents and deposited stacks are restored.
     */
    public void applySavedProgress(Map<String, Integer> materials, Map<String, Integer> hitCounts,
            List<ItemStack> savedDeposited) {
        deposited.clear();
        depositedByMaterial.clear();
        for (IntCounter c : types.values()) {
            c.setCurrent(0);
        }
        if (materials != null) {
            for (Map.Entry<String, Integer> e : materials.entrySet()) {
                WoodMaterial material = MaterialLoader.getByString(e.getKey());
                if (material == null || e.getValue() == null) continue;
                int amount = Math.max(0, e.getValue());
                depositedByMaterial.put(material, amount);
                IntCounter bucket = types.get(material.getType());
                if (bucket != null) bucket.increaseCurrent(amount);
            }
        }
        if (hitCounts != null) {
            for (Map.Entry<String, Integer> e : hitCounts.entrySet()) {
                CraftingHit hit = HitLoader.getByString(e.getKey());
                if (hit == null || e.getValue() == null) continue;
                IntCounter counter = hits.get(hit);
                if (counter == null) {
                    counter = new IntCounter();
                    hits.put(hit, counter);
                }
                counter.setCurrent(Math.max(0, e.getValue()));
            }
            for (IntCounter c : hitTypes.values()) {
                c.setCurrent(0);
            }
            for (Map.Entry<CraftingHit, IntCounter> e : hits.entrySet()) {
                HitType type = e.getKey().getType();
                if (type == null) continue;
                IntCounter bucket = hitTypes.get(type);
                if (bucket != null) bucket.increaseCurrent(e.getValue().getCurrent());
            }
        }
        if (savedDeposited != null) {
            deposited.addAll(savedDeposited);
        }
    }

    public StationFeedback addMaterial(WoodMaterial material, ItemStack stack) {
        if (project == null) return StationFeedback.NO_PROJECT;
        if (material == null) return StationFeedback.WRONG_TYPE;

        String type = material.getType();
        IntCounter bucket = types.get(type);
        if (bucket == null) return StationFeedback.WRONG_TYPE;
        if (bucket.isEqual()) return StationFeedback.CAPACITY;

        bucket.increaseCurrent(1);
        depositedByMaterial.merge(material, 1, Integer::sum);
        if (stack != null) {
            ItemStack copy = stack.clone();
            copy.setAmount(1);
            deposited.add(copy);
        }
        return StationFeedback.SUCCESS;
    }

    public StationFeedback hit(CraftingHit hit) {
        if (project == null) return StationFeedback.NO_PROJECT;
        if (!checkItems()) return StationFeedback.LACKING_ITEMS;
        if (hit == null || hit.getType() == null) return StationFeedback.WRONG_TYPE;
        if (!hitTypes.containsKey(hit.getType())) return StationFeedback.NONE;
        if (hitTypes.get(hit.getType()).isEqual()) return StationFeedback.CAPACITY;

        if (hits.containsKey(hit)) {
            hits.get(hit).increaseCurrent(1);
        } else {
            IntCounter counter = new IntCounter();
            counter.setCurrent(1);
            hits.put(hit, counter);
        }
        hitTypes.get(hit.getType()).increaseCurrent(1);
        return StationFeedback.SUCCESS;
    }

    /** True when every material-type bucket is exactly filled. */
    public boolean checkItems() {
        if (project == null) return false;
        for (IntCounter c : types.values()) {
            if (!c.isEqual()) return false;
        }
        return true;
    }

    /** True when every hit-type bucket is exactly filled. */
    public boolean checkHits() {
        if (project == null) return false;
        for (IntCounter c : hitTypes.values()) {
            if (!c.isEqual()) return false;
        }
        return true;
    }

    /** True when every required hit count matches and there are no extra leftover hits. */
    public boolean checkExactHits() {
        if (project == null) return false;
        for (CraftingHit required : project.getHits().keySet()) {
            IntCounter c = hits.get(required);
            if (c == null || !c.isEqual()) return false;
        }
        for (Map.Entry<CraftingHit, IntCounter> e : hits.entrySet()) {
            if (!e.getValue().isEqual()) return false;
        }
        return true;
    }

    /** True when deposited material ids and amounts match the project recipe. */
    public boolean checkExactRecipe() {
        if (project == null) return false;
        Map<WoodMaterial, Integer> recipe = project.getRecipe();
        if (depositedByMaterial.size() != recipe.size()) return false;
        for (Map.Entry<WoodMaterial, Integer> e : recipe.entrySet()) {
            Integer have = depositedByMaterial.get(e.getKey());
            if (have == null || !have.equals(e.getValue())) return false;
        }
        return true;
    }

    public StationFeedback canFinish() {
        if (project == null) return StationFeedback.NO_PROJECT;
        if (!checkItems()) return StationFeedback.LACKING_ITEMS;
        if (!checkExactHits()) return StationFeedback.LACKING_HITS;
        if (!checkExactRecipe()) return StationFeedback.RECIPE_MISMATCH;
        return StationFeedback.SUCCESS;
    }

    /**
     * Average of per-hit completion, with the same overshoot mapping as AdvancedCrafting.
     * Hits at or above 200% still count in the divisor but add 0 to the sum.
     */
    public double calculatePercentage() {
        int counter = 0;
        double amount = 0.0;
        for (CraftingHit h : hits.keySet()) {
            counter++;
            double d = hits.get(h).getPercentage();
            if (d >= 200.0) continue;
            if (d <= 100.0) amount = amount + d;
            if (d > 100.0 && d <= 200.0) amount = amount + (200.0 - d);
        }
        if (counter == 0) return 100.0;
        return Math.round(amount / counter);
    }

    public Quality getQuality() {
        return QualityLoader.getByAmount(calculatePercentage());
    }

    /** Clears the project and returns deposited items for the caller to refund. */
    public List<ItemStack> cancel() {
        List<ItemStack> refund = new ArrayList<>(deposited);
        project = null;
        types.clear();
        hits.clear();
        hitTypes.clear();
        deposited.clear();
        depositedByMaterial.clear();
        return refund;
    }
}
