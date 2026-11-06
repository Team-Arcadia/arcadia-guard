package com.arcadia.arcadiaguard.mixin;

import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.helper.FlagMixinHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Containers.class)
public abstract class ContainersMixin {

    @Inject(
        method = "dropContents(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/Container;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void arcadiaguard$blockProtectedContainerDrops(Level level, BlockPos pos,
            net.minecraft.world.Container container, CallbackInfo ci) {
        // 1.20.1 : dropContentsOnDestroy n'existe pas. On ne vise que le cas "bloc conteneur
        // detruit" (onRemove) : le Container est le BlockEntity de pos et le bloc a pos a deja
        // ete remplace. Les autres appels (minecarts, entites, drops de mods) passent.
        if (!(container instanceof net.minecraft.world.level.block.entity.BlockEntity be)) return;
        if (!pos.equals(be.getBlockPos())) return;
        if (level.getBlockState(pos).is(be.getBlockState().getBlock())) return;
        if (!FlagMixinHelper.mayDeny(level, BuiltinFlags.BLOCK_BREAK)) return;
        if (!FlagMixinHelper.isDenied(level, pos, BuiltinFlags.BLOCK_BREAK)) return;
        if (FlagMixinHelper.consumeContainerDropAllowance(level, pos)) return;
        ci.cancel();
    }
}
