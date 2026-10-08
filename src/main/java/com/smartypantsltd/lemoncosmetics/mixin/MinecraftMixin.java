package com.smartypantsltd.lemoncosmetics.mixin;

import com.smartypantsltd.lemoncosmetics.LemonCosmetics;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The per-tick chores. A fault here must never break the game. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Inject(method = "tick", at = @At("RETURN"))
    private void lemoncosmetics$tick(CallbackInfo ci) {
        try {
            LemonCosmetics.tick((Minecraft) (Object) this);
        } catch (Throwable t) {
            LemonCosmetics.LOG.error("LemonCosmetics tick failed", t);
        }
    }
}
