package com.smartypantsltd.lemoncosmetics.catalogue;

/**
 * One look the server's pack can draw: a base item's definition plus the
 * custom_model_data value that selects it. Drawing ANY stack with
 * {@code item_model = item} and {@code custom_model_data = [cmd]} shows this look.
 *
 * @param item     the item definition id, e.g. {@code minecraft:netherite_pickaxe}
 * @param cmd      the range_dispatch threshold that selects the model
 * @param model    the model it selects, e.g. {@code minecraft:custom/item/tool/bee/pickaxe}
 * @param name     a readable name, e.g. "Bee Pickaxe"
 * @param category a shelf for the picker, e.g. "Tool skins"
 * @param set      the sub-shelf (a tool set, a decoration theme), may be empty
 */
public record Cosmetic(String item, float cmd, String model, String name, String category, String set) {

    /** The key rules store: {@code item|cmd}. */
    public String key() {
        return key(item, cmd);
    }

    public static String key(String item, float cmd) {
        return item + "|" + (cmd == Math.rint(cmd) ? Integer.toString((int) cmd) : Float.toString(cmd));
    }

    /** "netherite pickaxe · 29", for tooltips. */
    public String source() {
        String it = item.startsWith("minecraft:") ? item.substring(10) : item;
        return it.replace('_', ' ') + " · " + (cmd == Math.rint(cmd) ? Integer.toString((int) cmd) : Float.toString(cmd));
    }
}
