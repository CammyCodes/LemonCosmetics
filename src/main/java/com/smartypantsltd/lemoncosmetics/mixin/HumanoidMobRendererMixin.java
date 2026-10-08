package com.smartypantsltd.lemoncosmetics.mixin;

import com.smartypantsltd.lemoncosmetics.Dresser;
import com.smartypantsltd.lemoncosmetics.LemonCosmetics;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The player renderer (AvatarRenderer) fills its armour slots here, after the head
 * item, so this is the last word on what YOUR player wears on screen.
 */
@Mixin(HumanoidMobRenderer.class)
public abstract class HumanoidMobRendererMixin {

    private static boolean lemoncosmetics$failed;

    @Inject(method = "extractHumanoidRenderState", at = @At("TAIL"))
    private static void lemoncosmetics$dress(LivingEntity entity, HumanoidRenderState state, float partialTick,
                                             ItemModelResolver resolver, CallbackInfo ci) {
        try {
            Dresser.dressHumanoid(entity, state, resolver);
        } catch (Throwable t) {
            if (!lemoncosmetics$failed) {
                lemoncosmetics$failed = true;
                LemonCosmetics.LOG.error("LemonCosmetics could not dress the player; drawing as normal", t);
            }
        }
    }
}
