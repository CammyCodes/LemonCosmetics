package com.smartypantsltd.lemoncosmetics;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * Real vanilla keybinds, listed and rebindable under Options &gt; Controls &gt;
 * LemonCosmetics. K is free in vanilla and in Nethermine and Nylah.
 */
public final class Keybinds {

    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("lemoncosmetics", "controls"));

    /** K: the cosmetics menu. */
    public static final KeyMapping MENU = new KeyMapping("key.lemoncosmetics.menu", 75, CATEGORY);
    /** Unbound: every look on or off at once. */
    public static final KeyMapping TOGGLE = new KeyMapping("key.lemoncosmetics.toggle", -1, CATEGORY);

    private Keybinds() {
    }

    public static KeyMapping[] all() {
        return new KeyMapping[] {MENU, TOGGLE};
    }

    /** Touching the class registers the mappings; the Options mixin calls this. */
    public static void init() {
        // Intentionally empty.
    }
}
