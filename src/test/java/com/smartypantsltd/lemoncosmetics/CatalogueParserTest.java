package com.smartypantsltd.lemoncosmetics;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.smartypantsltd.lemoncosmetics.catalogue.ArmourSet;
import com.smartypantsltd.lemoncosmetics.catalogue.CatalogueParser;
import com.smartypantsltd.lemoncosmetics.catalogue.Cosmetic;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogueParserTest {

    private static final String PICKAXE = """
            {"model":{"type":"range_dispatch","property":"custom_model_data",
              "fallback":{"type":"minecraft:model","model":"item/netherite_pickaxe"},
              "entries":[
                {"threshold":2,"model":{"type":"minecraft:model","model":"custom/item/tool/coal/pickaxe"}},
                {"threshold":29,"model":{"type":"minecraft:model","model":"custom/item/tool/arcanist/pickaxe"}},
                {"threshold":1001,"model":{"type":"minecraft:model","model":"item/netherite_pickaxe"}},
                {"threshold":40,"model":{"type":"minecraft:condition","property":"minecraft:using_item",
                   "on_true":{"type":"minecraft:model","model":"custom/item/tool/bee/bow_1"},
                   "on_false":{"type":"minecraft:model","model":"custom/item/tool/bee/bow"}}}
              ]}}
            """;

    @Test
    void eachCustomModelDataEntryIsALook() {
        List<Cosmetic> got = CatalogueParser.parseItemDefinition("minecraft:netherite_pickaxe", JsonParser.parseString(PICKAXE));
        assertEquals(3, got.size(), "the vanilla look at 1001 is not a cosmetic");
        Cosmetic arcanist = got.get(1);
        assertEquals("minecraft:netherite_pickaxe", arcanist.item());
        assertEquals(29f, arcanist.cmd());
        assertEquals("minecraft:custom/item/tool/arcanist/pickaxe", arcanist.model());
        assertEquals("Arcanist Pickaxe", arcanist.name());
        assertEquals("Tool skins", arcanist.category());
        assertEquals("minecraft:netherite_pickaxe|29", arcanist.key());
        assertEquals("minecraft:custom/item/tool/bee/bow", got.get(2).model(), "a nested look resolves to its resting model");
    }

    @Test
    void otherPropertiesAreNotLooks() {
        String json = """
                {"model":{"type":"range_dispatch","property":"minecraft:use_duration",
                  "entries":[{"threshold":0.5,"model":{"type":"minecraft:model","model":"custom/item/x"}}]}}
                """;
        assertTrue(CatalogueParser.parseItemDefinition("minecraft:bow", JsonParser.parseString(json)).isEmpty());
    }

    @Test
    void armourSetsFindTheirPieceIcons() {
        List<Cosmetic> all = List.of(
                new Cosmetic("minecraft:netherite_helmet", 30, "minecraft:custom/item/armor/bee/helmet", "Bee Helmet", "Armour", "Bee"),
                new Cosmetic("minecraft:netherite_boots", 30, "minecraft:custom/item/armor/stpatricks/boots", "Boots", "Armour", "Stpatricks"));
        ArmourSet bee = CatalogueParser.armourSet("custom:bee", all);
        assertNotNull(bee.icons()[0]);
        assertEquals("Bee", bee.name());
        ArmourSet paddy = CatalogueParser.armourSet("custom:st_patricks", all);
        assertNotNull(paddy.icons()[3], "st_patricks equipment pairs with the stpatricks folder");
    }

    /**
     * The real server pack, when this machine has one: thousands of looks, every key
     * unique per item. Skips (loudly) rather than passing when no pack is present.
     */
    @Test
    void theRealLemonCloudPack() throws Exception {
        File pack = newestPack();
        Assumptions.assumeTrue(pack != null, "no LemonCloud pack downloaded on this machine");
        List<Cosmetic> all = new ArrayList<>();
        List<String> equipment = new ArrayList<>();
        try (ZipFile z = new ZipFile(pack)) {
            Enumeration<? extends ZipEntry> en = z.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String n = e.getName();
                if (n.matches("assets/[^/]+/items/.+\\.json")) {
                    String ns = n.split("/")[1];
                    String item = ns + ":" + n.substring(n.indexOf("/items/") + 7, n.length() - 5);
                    try (var r = new InputStreamReader(z.getInputStream(e), StandardCharsets.UTF_8)) {
                        JsonElement root = JsonParser.parseReader(r);
                        all.addAll(CatalogueParser.parseItemDefinition(item, root));
                    }
                } else if (n.matches("assets/[^/]+/equipment/[^/]+\\.json")) {
                    equipment.add(n.split("/")[1] + ":" + n.substring(n.lastIndexOf('/') + 1, n.length() - 5));
                }
            }
        }
        assertTrue(all.size() > 1500, "found " + all.size() + " looks");
        assertEquals(all.size(), all.stream().map(Cosmetic::key).distinct().count(), "keys are unique");
        assertTrue(all.stream().anyMatch(c -> c.key().equals("minecraft:netherite_pickaxe|29")), "the Arcanist Pickaxe is there");
        int withIcons = 0;
        for (String asset : equipment) {
            ArmourSet s = CatalogueParser.armourSet(asset, all);
            if (s.icons()[0] != null && s.icons()[1] != null && s.icons()[2] != null && s.icons()[3] != null) {
                withIcons++;
            }
        }
        assertTrue(equipment.size() >= 40, "equipment sets: " + equipment.size());
        assertEquals(equipment.size(), withIcons, "every armour set has all four piece icons");
    }

    private static File newestPack() {
        File best = null;
        for (String inst : new String[] {"Fabulously Optimized (1)", "Fabulously Optimized 2"}) {
            File dl = new File("C:\\Users\\Cammy\\curseforge\\minecraft\\Instances\\" + inst + "\\downloads");
            File[] dirs = dl.listFiles(File::isDirectory);
            if (dirs == null) {
                continue;
            }
            for (File d : dirs) {
                File[] files = d.listFiles(File::isFile);
                if (files == null) {
                    continue;
                }
                for (File f : files) {
                    try (ZipFile z = new ZipFile(f)) {
                        ZipEntry meta = z.getEntry("pack.mcmeta");
                        if (meta == null) {
                            continue;
                        }
                        String text = new String(z.getInputStream(meta).readAllBytes(), StandardCharsets.UTF_8);
                        if (text.toLowerCase().contains("lemoncloud") && (best == null || f.lastModified() > best.lastModified())) {
                            best = f;
                        }
                    } catch (Exception ignored) {
                        // not a zip
                    }
                }
            }
        }
        return best;
    }
}
