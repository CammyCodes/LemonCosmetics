package com.smartypantsltd.lemoncosmetics.catalogue;

/**
 * A worn armour look from the pack's {@code equipment/} folder.
 *
 * @param asset the equipment asset id, e.g. {@code custom:bee}
 * @param name  a readable name, e.g. "Bee"
 * @param icons the inventory icons of its pieces, head/chest/legs/feet order;
 *              an entry is null when the pack has no icon for that piece
 */
public record ArmourSet(String asset, String name, Cosmetic[] icons) {
}
