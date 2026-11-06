package com.arcadia.arcadiaguard.mixin;

import com.arcadia.arcadiaguard.config.ArcadiaGuardConfig;
import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.helper.FlagMixinHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Equivalent 1.20.1 de BlockDropsEvent (NeoForge) : supprime les drops d'un bloc casse
 * par un non-joueur (mob, piston, commande...) dans une zone ou block-break est refuse.
 * Les casses par joueur sont deja filtrees par BlockEvent.BreakEvent (HIGHEST).
 */
@Mixin(Block.class)
public abstract class BlockDropsMixin {

    private static boolean arcadiaguard$deny(Level level, BlockPos pos) {
        try {
            if (!ArcadiaGuardConfig.ENABLE_BLOCK_BREAK.get()) return false;
        } catch (Throwable t) {
            return false;
        }
        return FlagMixinHelper.isDenied(level, pos, BuiltinFlags.BLOCK_BREAK);
    }

    @Inject(
        method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V",
        at = @At("HEAD"), cancellable = true)
    private static void arcadiaguard$dropsSimple(BlockState state, Level level, BlockPos pos, CallbackInfo ci) {
        if (arcadiaguard$deny(level, pos)) ci.cancel();
    }

    @Inject(
        method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)V",
        at = @At("HEAD"), cancellable = true)
    private static void arcadiaguard$dropsAccessor(BlockState state, LevelAccessor accessor, BlockPos pos,
            BlockEntity be, CallbackInfo ci) {
        if (accessor instanceof Level level && arcadiaguard$deny(level, pos)) ci.cancel();
    }

    @Inject(
        method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V",
        at = @At("HEAD"), cancellable = true)
    private static void arcadiaguard$dropsBreaker(BlockState state, Level level, BlockPos pos,
            BlockEntity be, Entity breaker, ItemStack tool, CallbackInfo ci) {
        // Joueur : BreakEvent a deja decide (membres / bypass autorises a casser et a drop).
        if (breaker instanceof ServerPlayer) return;
        if (arcadiaguard$deny(level, pos)) ci.cancel();
    }
}
