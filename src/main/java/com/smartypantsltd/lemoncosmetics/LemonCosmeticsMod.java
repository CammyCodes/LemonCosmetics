package com.smartypantsltd.lemoncosmetics;

import net.fabricmc.api.ClientModInitializer;

/** Fabric entrypoint. LemonCosmetics is client-only. */
public final class LemonCosmeticsMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        LemonCosmetics.init();
    }
}
