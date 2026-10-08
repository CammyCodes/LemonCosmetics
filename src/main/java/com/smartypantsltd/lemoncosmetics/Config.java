package com.smartypantsltd.lemoncosmetics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@code config/lemoncosmetics.json}. Every field has a default, so a missing or old file just works. */
public final class Config {

    /** A look for one kind of item: "draw my netherite pickaxe as the Arcanist Pickaxe". */
    public static final class ItemRule {
        /** The item it restyles, e.g. {@code minecraft:netherite_pickaxe}. */
        public String target = "";
        /** Only items whose name contains this (any case); blank = every item of that kind. */
        public String nameContains = "";
        /** The look: the cosmetic's item definition and custom_model_data. */
        public String lookItem = "";
        public float lookCmd = 0;
        /** The look's name when it was picked, shown if the pack is not loaded. */
        public String lookName = "";
        public boolean enabled = true;
    }

    /** A worn look for one armour slot (an equipment asset such as {@code custom:bee}). */
    public static final class ArmourChoice {
        public String asset = "";
        public String name = "";
    }

    /** Something worn on the head: a hat, wings, a backpack (any item look). */
    public static final class HeadChoice {
        public String lookItem = "";
        public float lookCmd = 0;
        public String lookName = "";
    }

    /** The one switch. */
    public boolean enabled = true;
    public List<ItemRule> items = new ArrayList<>();
    /** Slot name (HEAD, CHEST, LEGS, FEET) to its armour look. */
    public Map<String, ArmourChoice> armour = new LinkedHashMap<>();
    public HeadChoice head = null;
    /** Show a chosen armour look even when nothing is worn in that slot. */
    public boolean armourWhenEmpty = false;
    /** Restyle the armour pieces' inventory icons to match the chosen sets. */
    public boolean armourIcons = true;
    /** A head look hides the helmet underneath it. */
    public boolean headHidesHelmet = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("lemoncosmetics.json");
    }

    public static Config load() {
        Path f = file();
        if (Files.exists(f)) {
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                Config c = GSON.fromJson(r, Config.class);
                if (c != null) {
                    c.sanitise();
                    return c;
                }
            } catch (Exception e) {
                LemonCosmetics.LOG.warn("lemoncosmetics.json unreadable, using defaults: {}", e.toString());
            }
        }
        Config c = new Config();
        c.save();
        return c;
    }

    void sanitise() {
        if (items == null) {
            items = new ArrayList<>();
        }
        items.removeIf(r -> r == null || r.target == null || r.target.isBlank());
        for (ItemRule r : items) {
            if (r.nameContains == null) {
                r.nameContains = "";
            }
            if (r.lookItem == null) {
                r.lookItem = "";
            }
            if (r.lookName == null) {
                r.lookName = "";
            }
        }
        if (armour == null) {
            armour = new LinkedHashMap<>();
        }
        armour.values().removeIf(a -> a == null || a.asset == null || a.asset.isBlank());
        if (head != null && (head.lookItem == null || head.lookItem.isBlank())) {
            head = null;
        }
    }

    public void save() {
        try {
            Files.createDirectories(file().getParent());
            try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
                GSON.toJson(this, w);
            }
        } catch (Exception e) {
            LemonCosmetics.LOG.warn("Could not save lemoncosmetics.json: {}", e.toString());
        }
    }
}
