package com.smartypantsltd.lemoncosmetics;

import com.smartypantsltd.lemoncosmetics.catalogue.ArmourSet;
import com.smartypantsltd.lemoncosmetics.catalogue.Catalogue;
import com.smartypantsltd.lemoncosmetics.catalogue.Cosmetic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;

import java.util.List;
import java.util.Optional;

/**
 * Decides what the game DRAWS for your own items, and nothing else.
 *
 * <p>It never changes a real item: it hands the renderer a copy with a different
 * {@code item_model} / {@code custom_model_data} (or a different equipment asset
 * for armour). The inventory, the server and other players all keep the real
 * stack. Only items owned by YOUR player are dressed; other players are left alone.
 */
public final class Dresser {

    /** Set while we render our own dressed copies and previews, so they are never re-dressed. */
    public static boolean bypass = false;

    private static final EquipmentSlot[] ARMOUR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private Dresser() {
    }

    /** A copy of {@code stack} drawn as {@code look}. */
    public static ItemStack wear(ItemStack stack, String lookItem, float lookCmd) {
        Identifier model = Identifier.tryParse(lookItem);
        if (model == null) {
            return stack;
        }
        ItemStack copy = stack.copy();
        copy.set(DataComponents.ITEM_MODEL, model);
        copy.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(lookCmd), List.of(), List.of(), List.of()));
        return copy;
    }

    /** A stand-alone stack that draws as {@code c} (for previews and the head slot). */
    public static ItemStack preview(Cosmetic c) {
        Item base = BuiltInRegistries.ITEM.getOptional(Identifier.tryParse(c.item())).orElse(Items.PAPER);
        return wear(new ItemStack(base), c.item(), c.cmd());
    }

    private static boolean isMine(ItemOwner owner) {
        if (owner == null) {
            return false;
        }
        LivingEntity le = owner.asLivingEntity();
        return le != null && le == Minecraft.getInstance().player;
    }

    /** Called for every item the game is about to draw. Returns {@code stack} itself when nothing applies. */
    public static ItemStack dress(ItemStack stack, ItemOwner owner) {
        if (bypass || stack.isEmpty() || !isMine(owner)) {
            return stack;
        }
        Config cfg = LemonCosmetics.config();
        Catalogue cat = Catalogue.get();
        if (!cfg.enabled || cat.isEmpty()) {
            return stack;
        }
        if (!cfg.items.isEmpty()) {
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            Config.ItemRule rule = Rules.pick(cfg.items, id, stack.getHoverName().getString());
            if (rule != null && cat.find(rule.lookItem, rule.lookCmd) != null) {
                return wear(stack, rule.lookItem, rule.lookCmd);
            }
        }
        if (cfg.armourIcons && !cfg.armour.isEmpty()) {
            Equippable eq = stack.get(DataComponents.EQUIPPABLE);
            if (eq != null && eq.assetId().isPresent()) {
                Config.ArmourChoice choice = cfg.armour.get(eq.slot().name());
                ArmourSet set = choice == null ? null : cat.armourSet(choice.asset);
                int i = slotIndex(eq.slot());
                if (set != null && i >= 0 && set.icons()[i] != null) {
                    Cosmetic icon = set.icons()[i];
                    return wear(stack, icon.item(), icon.cmd());
                }
            }
        }
        return stack;
    }

    private static int slotIndex(EquipmentSlot slot) {
        for (int i = 0; i < ARMOUR_SLOTS.length; i++) {
            if (ARMOUR_SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    /** After the game has worked out how to draw a humanoid: restyle YOUR armour and head. */
    public static void dressHumanoid(LivingEntity entity, HumanoidRenderState state, ItemModelResolver resolver) {
        if (entity != Minecraft.getInstance().player) {
            return;
        }
        Config cfg = LemonCosmetics.config();
        Catalogue cat = Catalogue.get();
        if (!cfg.enabled || cat.isEmpty()) {
            return;
        }
        if (!cfg.armour.isEmpty()) {
            state.headEquipment = armour(state.headEquipment, EquipmentSlot.HEAD, Items.IRON_HELMET, cfg, cat);
            state.chestEquipment = armour(state.chestEquipment, EquipmentSlot.CHEST, Items.IRON_CHESTPLATE, cfg, cat);
            state.legsEquipment = armour(state.legsEquipment, EquipmentSlot.LEGS, Items.IRON_LEGGINGS, cfg, cat);
            state.feetEquipment = armour(state.feetEquipment, EquipmentSlot.FEET, Items.IRON_BOOTS, cfg, cat);
        }
        Config.HeadChoice head = cfg.head;
        if (head != null) {
            Cosmetic c = cat.find(head.lookItem, head.lookCmd);
            if (c != null) {
                bypass = true;
                try {
                    resolver.updateForLiving(state.headItem, preview(c), ItemDisplayContext.HEAD, entity);
                } finally {
                    bypass = false;
                }
                state.wornHeadType = null;
                state.wornHeadProfile = null;
                if (cfg.headHidesHelmet) {
                    state.headEquipment = ItemStack.EMPTY;
                }
            }
        }
    }

    private static ItemStack armour(ItemStack worn, EquipmentSlot slot, Item stand, Config cfg, Catalogue cat) {
        Config.ArmourChoice choice = cfg.armour.get(slot.name());
        if (choice == null || !cat.hasArmour(choice.asset)) {
            return worn;
        }
        ItemStack base;
        if (worn.isEmpty()) {
            if (!cfg.armourWhenEmpty) {
                return worn;
            }
            base = new ItemStack(stand);
        } else {
            base = worn.copy();
        }
        Equippable eq = base.get(DataComponents.EQUIPPABLE);
        // An elytra, a carved pumpkin or a head in the slot is not armour: leave it be.
        if (eq == null || eq.slot() != slot || eq.assetId().isEmpty()) {
            return worn;
        }
        Identifier asset = Identifier.tryParse(choice.asset);
        if (asset == null) {
            return worn;
        }
        base.set(DataComponents.EQUIPPABLE, new Equippable(eq.slot(), eq.equipSound(),
                Optional.of(ResourceKey.create(EquipmentAssets.ROOT_ID, asset)), eq.cameraOverlay(),
                eq.allowedEntities(), eq.dispensable(), eq.swappable(), eq.damageOnHurt(),
                eq.equipOnInteract(), eq.canBeSheared(), eq.shearingSound()));
        return base;
    }
}
