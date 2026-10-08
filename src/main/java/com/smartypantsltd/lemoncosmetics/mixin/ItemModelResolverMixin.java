package com.smartypantsltd.lemoncosmetics.mixin;

import com.smartypantsltd.lemoncosmetics.Dresser;
import com.smartypantsltd.lemoncosmetics.LemonCosmetics;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every item the game draws (hand, hotbar, inventory, third person, worn on the head)
 * is resolved here. For YOUR items with a chosen look, resolve a dressed copy instead.
 * Drawing only: the real stack is untouched and nothing is sent anywhere.
 */
@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {

    private static boolean lemoncosmetics$failed;

    @Shadow
    public abstract void appendItemLayers(ItemStackRenderState state, ItemStack stack, ItemDisplayContext context,
                                          Level level, ItemOwner owner, int seed);

    @Inject(method = "appendItemLayers", at = @At("HEAD"), cancellable = true)
    private void lemoncosmetics$dress(ItemStackRenderState state, ItemStack stack, ItemDisplayContext context,
                                      Level level, ItemOwner owner, int seed, CallbackInfo ci) {
        if (Dresser.bypass) {
            return;
        }
        ItemStack dressed;
        try {
            dressed = Dresser.dress(stack, owner);
        } catch (Throwable t) {
            if (!lemoncosmetics$failed) {
                lemoncosmetics$failed = true;
                LemonCosmetics.LOG.error("LemonCosmetics could not dress an item; drawing it as normal", t);
            }
            return;
        }
        if (dressed == stack) {
            return;
        }
        Dresser.bypass = true;
        try {
            appendItemLayers(state, dressed, context, level, owner, seed);
        } finally {
            Dresser.bypass = false;
        }
        ci.cancel();
    }
}
