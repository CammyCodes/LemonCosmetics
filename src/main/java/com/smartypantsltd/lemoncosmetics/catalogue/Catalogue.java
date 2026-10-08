package com.smartypantsltd.lemoncosmetics.catalogue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.smartypantsltd.lemoncosmetics.LemonCosmetics;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Every cosmetic the resource packs loaded right now can draw. Built from the
 * packs themselves, so it follows the server: when its pack is not loaded the
 * catalogue is empty and every rule quietly stops applying (a rule can never
 * turn your pickaxe into a plain dye on another server).
 */
public final class Catalogue {

    private static volatile Catalogue current = new Catalogue(List.of(), List.of(), "");

    private final List<Cosmetic> cosmetics;
    private final Map<String, Cosmetic> byKey = new HashMap<>();
    private final List<ArmourSet> armour;
    private final Set<String> armourAssets = new HashSet<>();
    private final String fingerprint;

    private Catalogue(List<Cosmetic> cosmetics, List<ArmourSet> armour, String fingerprint) {
        this.cosmetics = Collections.unmodifiableList(cosmetics);
        this.armour = Collections.unmodifiableList(armour);
        this.fingerprint = fingerprint;
        for (Cosmetic c : cosmetics) {
            byKey.putIfAbsent(c.key(), c);
        }
        for (ArmourSet a : armour) {
            armourAssets.add(a.asset());
        }
    }

    public static Catalogue get() {
        return current;
    }

    public List<Cosmetic> cosmetics() {
        return cosmetics;
    }

    public List<ArmourSet> armour() {
        return armour;
    }

    public boolean isEmpty() {
        return cosmetics.isEmpty() && armour.isEmpty();
    }

    public Cosmetic find(String item, float cmd) {
        return byKey.get(Cosmetic.key(item, cmd));
    }

    public boolean hasArmour(String asset) {
        return armourAssets.contains(asset);
    }

    public ArmourSet armourSet(String asset) {
        for (ArmourSet a : armour) {
            if (a.asset().equals(asset)) {
                return a;
            }
        }
        return null;
    }

    /** Which packs are loaded, in order; a change means the catalogue is stale. */
    public static String fingerprint(ResourceManager rm) {
        return rm.listPacks().map(PackResources::packId).collect(Collectors.joining("|"));
    }

    /** Rebuild if the loaded packs changed (cheap to call every second or so). */
    public static void refreshIfChanged(ResourceManager rm) {
        String fp = fingerprint(rm);
        if (!fp.equals(current.fingerprint)) {
            rebuild(rm);
        }
    }

    public static void rebuild(ResourceManager rm) {
        String fp = fingerprint(rm);
        List<Cosmetic> all = new ArrayList<>();
        Map<Identifier, Resource> items = rm.listResources("items", id -> id.getPath().endsWith(".json"));
        for (Map.Entry<Identifier, Resource> e : items.entrySet()) {
            String path = e.getKey().getPath();
            String itemId = e.getKey().getNamespace() + ":" + path.substring("items/".length(), path.length() - ".json".length());
            try (Reader r = e.getValue().openAsReader()) {
                JsonElement root = JsonParser.parseReader(r);
                all.addAll(CatalogueParser.parseItemDefinition(itemId, root));
            } catch (Exception ex) {
                LemonCosmetics.LOG.debug("Skipping item definition {}: {}", itemId, ex.toString());
            }
        }
        all.sort(CatalogueParser.order());

        List<ArmourSet> sets = new ArrayList<>();
        Map<Identifier, Resource> equipment = rm.listResources("equipment", id -> id.getPath().endsWith(".json"));
        for (Identifier id : equipment.keySet()) {
            String path = id.getPath();
            String name = path.substring("equipment/".length(), path.length() - ".json".length());
            // Vanilla's own materials are not cosmetics.
            if (id.getNamespace().equals("minecraft")) {
                continue;
            }
            sets.add(CatalogueParser.armourSet(id.getNamespace() + ":" + name, all));
        }
        sets.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));

        current = new Catalogue(all, sets, fp);
        if (!all.isEmpty() || !sets.isEmpty()) {
            LemonCosmetics.LOG.info("LemonCosmetics: {} cosmetics and {} armour sets in the loaded packs", all.size(), sets.size());
        }
    }
}
