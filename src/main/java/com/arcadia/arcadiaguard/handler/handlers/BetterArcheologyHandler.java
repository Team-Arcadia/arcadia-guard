package com.arcadia.arcadiaguard.handler.handlers;

import com.arcadia.arcadiaguard.config.ArcadiaGuardConfig;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.handler.HandlerRegistry.BlockBreakHandler;
import com.arcadia.arcadiaguard.util.ReflectionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.event.level.BlockEvent;
import org.slf4j.LoggerFactory;

public final class BetterArcheologyHandler implements BlockBreakHandler {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(BetterArcheologyHandler.class);
    /** M10: warn at most once if the reflection chain fails (mod API may have changed). */
    private final AtomicBoolean warnedReflection = new AtomicBoolean(false);

    private static final String ENCHANTMENT_HELPER_CLASS = "net.minecraft.world.item.enchantment.EnchantmentHelper";
    private static final ResourceKey<Enchantment> TUNNELING = ResourceKey.create(
        Registries.ENCHANTMENT,
        new ResourceLocation("betterarcheology", "tunneling")
    );

    private final GuardService guardService;

    public BetterArcheologyHandler(GuardService guardService) {
        this.guardService = guardService;
    }

    @Override
    public void handle(BlockEvent.BreakEvent event) {
        if (!ArcadiaGuardConfig.ENABLE_BETTERARCHEOLOGY.get()) return;
        if (!ModList.get().isLoaded("betterarcheology")) return;

        Object playerObj = ReflectionHelper.invoke(event, "getPlayer", new Class<?>[0]).orElse(null);
        if (!(playerObj instanceof ServerPlayer player)) return;
        if (ReflectionHelper.boolMethod(playerObj, "isShiftKeyDown", new Class<?>[0])) return;

        Object posObj = ReflectionHelper.invoke(event, "getPos", new Class<?>[0]).orElse(null);
        if (!(posObj instanceof BlockPos pos)) return;

        Object toolObj = ReflectionHelper.invoke(playerObj, "getMainHandItem", new Class<?>[0]).orElse(ItemStack.EMPTY);
        if (!(toolObj instanceof ItemStack tool) || tool.isEmpty() || !tool.isEnchanted()) return;

        Object levelObj = ReflectionHelper.invoke(playerObj, "serverLevel", new Class<?>[0]).orElse(null);
        if (levelObj == null) {
            if (warnedReflection.compareAndSet(false, true))
                LOG.warn("[ArcadiaGuard] BetterArchaeology reflection failed: could not get serverLevel (mod API may have changed)");
            return;
        }
        // PORT: 1.20.1 enchantments are static registry entries (no data-driven lookup).
        Enchantment tunneling = net.minecraft.core.registries.BuiltInRegistries.ENCHANTMENT.get(TUNNELING.location());
        if (tunneling == null) return;
        if (net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(tunneling, tool) < 1) return;

        BlockPos below = new BlockPos(
            ReflectionHelper.intMethod(pos, "getX"),
            ReflectionHelper.intMethod(pos, "getY") - 1,
            ReflectionHelper.intMethod(pos, "getZ")
        );

        // Si le bloc primaire est lui-meme en zone/dim protegee, on annule silencieusement :
        // BlockEventHandler envoie deja le message pour pos.
        if (isBlockBreakDenied(player, pos)) {
            event.setCanceled(true);
            return;
        }

        // Si le bloc du dessous est protege (zone OU dim flag), on annule avec message.
        if (this.guardService.blockIfFlagDenied(
            player,
            below,
            com.arcadia.arcadiaguard.flag.BuiltinFlags.BLOCK_BREAK,
            "betterarcheology:tunneling",
            ArcadiaGuardConfig.MESSAGE_BETTERARCHEOLOGY.get()
        ).blocked()) {
            event.setCanceled(true);
        }
    }

    private boolean isBlockBreakDenied(ServerPlayer player, BlockPos pos) {
        if (this.guardService.shouldBypass(player)) return false;
        return this.guardService.isZoneDenying(player.serverLevel(), pos,
            com.arcadia.arcadiaguard.flag.BuiltinFlags.BLOCK_BREAK);
    }
}
