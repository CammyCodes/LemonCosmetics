package com.smartypantsltd.lemoncosmetics.catalogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns a resource pack's item definitions into {@link Cosmetic}s. Pure: Gson in,
 * records out, no Minecraft classes, so the tests can run it on plain JSON.
 *
 * <p>A server pack gives a vanilla item many looks with a {@code range_dispatch}
 * on {@code custom_model_data}: threshold 29 on {@code netherite_pickaxe} draws
 * {@code custom/item/tool/arcanist/pickaxe}. Each such entry is one cosmetic.
 */
public final class CatalogueParser {

    private static final Map<String, String> TOP_NAMES = new HashMap<>();
    private static final List<String> TOP_ORDER = List.of(
            "Tool skins", "Armour", "Hats", "Pets", "Backpacks", "Plushies", "Figures", "Decorations");
    private static final List<String> TOP_LAST = List.of("Emojis", "Mobs", "Other");

    static {
        TOP_NAMES.put("tool", "Tool skins");
        TOP_NAMES.put("armor", "Armour");
        TOP_NAMES.put("hat", "Hats");
        TOP_NAMES.put("animal", "Pets");
        TOP_NAMES.put("plushie", "Plushies");
        TOP_NAMES.put("backpack", "Backpacks");
        TOP_NAMES.put("figure", "Figures");
        TOP_NAMES.put("decoration", "Decorations");
        TOP_NAMES.put("cropplants", "Crop plants");
        TOP_NAMES.put("cropdrops", "Crop drops");
        TOP_NAMES.put("cropbasics", "Crop pots");
        TOP_NAMES.put("enchantmentbook", "Enchantment books");
        TOP_NAMES.put("tm", "TMs");
        TOP_NAMES.put("genbuckets", "Gen buckets");
        TOP_NAMES.put("wateringcans", "Watering cans");
        TOP_NAMES.put("npc", "NPC");
        TOP_NAMES.put("marketstall", "Market stalls");
    }

    private CatalogueParser() {
    }

    /**
     * Every cosmetic one item definition offers.
     *
     * @param itemId the definition's id, e.g. {@code minecraft:netherite_pickaxe}
     * @param root   the parsed {@code items/<name>.json}
     */
    public static List<Cosmetic> parseItemDefinition(String itemId, JsonElement root) {
        List<Cosmetic> out = new ArrayList<>();
        if (root != null && root.isJsonObject()) {
            walk(itemId, root.getAsJsonObject().get("model"), out);
        }
        return out;
    }

    private static void walk(String itemId, JsonElement node, List<Cosmetic> out) {
        if (node == null) {
            return;
        }
        if (node.isJsonArray()) {
            for (JsonElement e : node.getAsJsonArray()) {
                walk(itemId, e, out);
            }
            return;
        }
        if (!node.isJsonObject()) {
            return;
        }
        JsonObject o = node.getAsJsonObject();
        String type = str(o, "type");
        String property = str(o, "property");
        if (type != null && type.endsWith("range_dispatch") && property != null && property.endsWith("custom_model_data")
                && intOr(o, "index", 0) == 0 && o.get("entries") instanceof JsonArray entries) {
            for (JsonElement entry : entries) {
                if (!entry.isJsonObject()) {
                    continue;
                }
                JsonObject eo = entry.getAsJsonObject();
                if (!eo.has("threshold")) {
                    continue;
                }
                float cmd;
                try {
                    cmd = eo.get("threshold").getAsFloat();
                } catch (RuntimeException ex) {
                    continue;
                }
                String model = firstModel(eo.get("model"));
                if (model != null && isCustom(model)) {
                    out.add(describe(itemId, cmd, model));
                }
            }
            walk(itemId, o.get("fallback"), out);
            return;
        }
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            if (e.getValue().isJsonObject() || e.getValue().isJsonArray()) {
                walk(itemId, e.getValue(), out);
            }
        }
    }

    /** The first plain model a (possibly nested) item model node draws. */
    static String firstModel(JsonElement node) {
        if (node == null) {
            return null;
        }
        if (node.isJsonArray()) {
            for (JsonElement e : node.getAsJsonArray()) {
                String m = firstModel(e);
                if (m != null) {
                    return m;
                }
            }
            return null;
        }
        if (!node.isJsonObject()) {
            return null;
        }
        JsonObject o = node.getAsJsonObject();
        String type = str(o, "type");
        if (type != null && type.endsWith("model") && !type.endsWith("special") && o.get("model") != null
                && o.get("model").isJsonPrimitive()) {
            return fullId(o.get("model").getAsString());
        }
        // Prefer the resting look: fallback / on_false before the alternatives.
        for (String k : new String[] {"fallback", "on_false", "model", "on_true", "cases", "entries"}) {
            String m = firstModel(o.get(k));
            if (m != null) {
                return m;
            }
        }
        return null;
    }

    /** Vanilla models (the dispatch's own fallback looks) are not cosmetics. */
    static boolean isCustom(String model) {
        int c = model.indexOf(':');
        String ns = model.substring(0, c);
        String path = model.substring(c + 1);
        return !(ns.equals("minecraft") && (path.startsWith("item/") || path.startsWith("block/")));
    }

    static String fullId(String id) {
        return id.indexOf(':') >= 0 ? id : "minecraft:" + id;
    }

    /** Name and shelf from the model's path. */
    static Cosmetic describe(String itemId, float cmd, String model) {
        int c = model.indexOf(':');
        String ns = model.substring(0, c);
        String[] parts = model.substring(c + 1).split("/");
        String leaf = humanize(parts[parts.length - 1].replaceFirst("^(nm|qmob)_", ""));
        String top;
        String sub = "";
        if (!ns.equals("minecraft")) {
            top = ns.equals("lgmobs") ? "Mobs" : humanize(ns);
            if (parts.length > 2) {
                sub = humanize(parts[1].replaceFirst("^(nm|qmob)_", ""));
            }
        } else if (parts.length > 1 && parts[0].equals("custom") && parts[1].equals("emojis")) {
            top = "Emojis";
        } else if (parts.length > 3 && parts[0].equals("custom") && parts[1].equals("item")) {
            String t = parts[2];
            sub = parts.length > 4 ? parts[3] : "";
            if (t.equals("animal") && sub.equals("pets")) {
                top = "Pets";
                sub = "";
            } else {
                top = TOP_NAMES.getOrDefault(t, humanize(t));
                sub = sub.isEmpty() ? "" : humanize(sub);
            }
        } else if (parts.length > 1 && parts[0].equals("custom")) {
            top = humanize(parts[1]);
        } else {
            top = "Other";
        }
        String name = leaf;
        if (!sub.isEmpty() && (top.equals("Tool skins") || top.equals("Armour") || top.equals("Mobs"))
                && !leaf.toLowerCase(Locale.ROOT).contains(sub.toLowerCase(Locale.ROOT))) {
            name = sub + " " + leaf;
        }
        return new Cosmetic(itemId, cmd, model, name, top, sub);
    }

    static String humanize(String s) {
        String spaced = s.replaceAll("[_\\-]+", " ").replaceAll("(?<=[a-z])(?=[A-Z])", " ").trim();
        StringBuilder b = new StringBuilder();
        for (String w : spaced.split("\\s+")) {
            if (w.isEmpty()) {
                continue;
            }
            if (b.length() > 0) {
                b.append(' ');
            }
            b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return b.toString();
    }

    /** Shelf order for the picker: cosmetics first, plumbing last. */
    public static Comparator<Cosmetic> order() {
        return Comparator.comparingInt((Cosmetic c) -> rank(c.category()))
                .thenComparing(Cosmetic::category)
                .thenComparing(Cosmetic::set)
                .thenComparing(Cosmetic::name)
                .thenComparing(Cosmetic::item);
    }

    private static int rank(String top) {
        int i = TOP_ORDER.indexOf(top);
        if (i >= 0) {
            return i;
        }
        int j = TOP_LAST.indexOf(top);
        return j >= 0 ? 100 + j : 50;
    }

    /**
     * Pairs each equipment asset with the inventory icons of its pieces, found by
     * the pack's convention {@code custom/item/armor/<set>/<helmet|chestplate|leggings|boots>}.
     */
    public static ArmourSet armourSet(String asset, List<Cosmetic> all) {
        int c = asset.indexOf(':');
        String name = c >= 0 ? asset.substring(c + 1) : asset;
        String key = name.replace("_", "");
        String[] slots = {"helmet", "chestplate", "leggings", "boots"};
        Cosmetic[] icons = new Cosmetic[4];
        for (int i = 0; i < 4; i++) {
            for (String folder : new String[] {key, name}) {
                String want = "minecraft:custom/item/armor/" + folder + "/" + slots[i];
                for (Cosmetic cos : all) {
                    if (cos.model().equals(want)) {
                        icons[i] = cos;
                        break;
                    }
                }
                if (icons[i] != null) {
                    break;
                }
            }
        }
        return new ArmourSet(asset, humanize(name), icons);
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static int intOr(JsonObject o, String k, int dflt) {
        JsonElement e = o.get(k);
        try {
            return e != null && e.isJsonPrimitive() ? e.getAsInt() : dflt;
        } catch (RuntimeException ex) {
            return dflt;
        }
    }
}
