package com.arcadia.arcadiaguard.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * PORT: data components do not exist on 1.20.1; the wand mode is stored in the stack NBT.
 */
public final class ModDataComponents {

    public static final String WAND_MODE_TAG = "arcadiaguard_wand_mode";

    public static WandMode getWandMode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(WAND_MODE_TAG) ? WandMode.VIEW : WandMode.EDIT;
    }

    public static void setWandMode(ItemStack stack, WandMode mode) {
        stack.getOrCreateTag().putBoolean(WAND_MODE_TAG, mode == WandMode.VIEW);
    }

    public static void register(IEventBus modBus) {
        // nothing to register on 1.20.1
    }

    private ModDataComponents() {}
}
