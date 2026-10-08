package com.smartypantsltd.lemoncosmetics;

import com.smartypantsltd.lemoncosmetics.catalogue.Catalogue;
import com.smartypantsltd.lemoncosmetics.ui.CosmeticsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The mod's state: its config, and the per-tick chores (keys, noticing a new pack). */
public final class LemonCosmetics {

    public static final Logger LOG = LoggerFactory.getLogger("LemonCosmetics");

    private static Config config;
    private static int tick;

    private LemonCosmetics() {
    }

    static void init() {
        config = Config.load();
        LOG.info("LemonCosmetics ready: {} item looks, {} armour looks{}", config.items.size(), config.armour.size(),
                config.head != null ? ", a head look" : "");
    }

    public static Config config() {
        if (config == null) {
            config = Config.load();
        }
        return config;
    }

    public static void tick(Minecraft mc) {
        // The server's pack arrives (and changes) by a resource reload: notice it.
        if (++tick % 20 == 0) {
            try {
                Catalogue.refreshIfChanged(mc.getResourceManager());
            } catch (Exception e) {
                LOG.warn("Could not read the loaded packs: {}", e.toString());
            }
        }
        while (Keybinds.MENU.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new CosmeticsScreen());
            }
        }
        while (Keybinds.TOGGLE.consumeClick()) {
            Config c = config();
            c.enabled = !c.enabled;
            c.save();
            if (mc.player != null) {
                mc.player.sendOverlayMessage(Component.literal(c.enabled ? "Cosmetics on" : "Cosmetics off"));
            }
        }
    }
}
