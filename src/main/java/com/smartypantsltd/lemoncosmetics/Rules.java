package com.smartypantsltd.lemoncosmetics;

import java.util.List;
import java.util.Locale;

/** Which rule restyles an item. Pure (strings in), so the tests can drive it. */
public final class Rules {

    private Rules() {
    }

    /**
     * The first enabled rule for this item, or null. A rule with a name filter
     * wins over a plain one for the same item, so "my pickaxe called Ethan's Pick"
     * can look different from the rest.
     */
    public static Config.ItemRule pick(List<Config.ItemRule> rules, String itemId, String hoverName) {
        Config.ItemRule plain = null;
        String name = hoverName == null ? "" : hoverName.toLowerCase(Locale.ROOT);
        for (Config.ItemRule r : rules) {
            if (!r.enabled || !itemId.equals(r.target) || r.lookItem.isBlank()) {
                continue;
            }
            String want = r.nameContains == null ? "" : r.nameContains.trim().toLowerCase(Locale.ROOT);
            if (want.isEmpty()) {
                if (plain == null) {
                    plain = r;
                }
            } else if (name.contains(want)) {
                return r;
            }
        }
        return plain;
    }
}
