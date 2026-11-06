package com.arcadia.arcadiaguard.handler;

import com.arcadia.arcadiaguard.config.ArcadiaGuardConfig;
import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.helper.FlagMixinHelper;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;

/**
 * Handles block break and block place, checking {@link BuiltinFlags#BLOCK_BREAK} and
 * {@link BuiltinFlags#BLOCK_PLACE} on the zone. Both are denied by default (flag = false).
 */
public final class BlockEventHandler {

    private final GuardService guard;

    public BlockEventHandler(GuardService guard) {
        this.guard = guard;
    }

    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!ArcadiaGuardConfig.ENABLE_BLOCK_BREAK.get()) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        // L4 TODO: add early-out via internal.hasZoneInChunk(event.getPos()) once InternalZoneProvider exposes it
        var result = guard.blockIfFlagDenied(player, event.getPos(), BuiltinFlags.BLOCK_BREAK,
                "block_break", "arcadiaguard.message.block_break");
        if (result.blocked()) {
            event.setCanceled(true);
        } else if (!event.isCanceled()) {
            allowContainerDropsIfBreakDenied(player, event.getPos());
        }
    }

    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!ArcadiaGuardConfig.ENABLE_BLOCK_BREAK.get()) return;
        if (event.isCanceled()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (guard.shouldBypass(player) || isMemberAt(player, event.getPos())) {
            allowContainerDropsIfBreakDenied(player, event.getPos());
        }
    }

    // PORT-TODO: BlockDropsEvent does not exist on Forge 1.20.1. Player breaks are already
    // denied by BlockEvent.BreakEvent (HIGHEST), so no drops occur; drop suppression for
    // non-player breakers in denied zones is disabled on this port.

    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!ArcadiaGuardConfig.ENABLE_BLOCK_PLACE.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (guard.blockIfFlagDenied(player, event.getPos(), BuiltinFlags.BLOCK_PLACE,
                "block_place", "arcadiaguard.message.block_place").blocked()) {
            event.setCanceled(true);
        }
    }

    private boolean isMemberAt(ServerPlayer player, BlockPos pos) {
        Optional<ProtectedZone> zoneOpt = guard.zoneManager().findZoneContaining(player.serverLevel(), pos)
            .map(zone -> (ProtectedZone) zone);
        return zoneOpt.isPresent() && guard.isZoneMember(player, zoneOpt.get());
    }

    private String zoneNameAt(Level level, BlockPos pos) {
        return guard.zoneManager().findZoneContaining(level, pos)
            .map(zone -> ((ProtectedZone) zone).name())
            .orElse("(dimension)");
    }

    private void allowContainerDropsIfBreakDenied(ServerPlayer player, BlockPos pos) {
        if (guard.isZoneDenying(player.serverLevel(), pos, BuiltinFlags.BLOCK_BREAK)) {
            FlagMixinHelper.allowContainerDropOnce(player.serverLevel(), pos);
        }
    }
}
