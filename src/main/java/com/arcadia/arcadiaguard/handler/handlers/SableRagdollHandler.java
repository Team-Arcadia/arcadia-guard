package com.arcadia.arcadiaguard.handler.handlers;

import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.handler.HandlerRegistry.DynamicEventHandler;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Blocks player ragdolls (Sable: Ragdolls mod) inside protected zones when SABLE_RAGDOLL=deny.
 *
 * <p>Listens to {@code dev.leo.sableplayerragdoll.api.RagdollStartEvent} (game bus, cancellable,
 * fired before the ragdoll is assembled). No compile dependency: the event is resolved by name
 * and the player read via reflection, so registration is skipped if the mod is absent.
 */
public final class SableRagdollHandler implements DynamicEventHandler {

    static final String EVENT_CLASS = "dev.leo.sableplayerragdoll.api.RagdollStartEvent";

    private final GuardService guardService;

    public SableRagdollHandler(GuardService guardService) {
        this.guardService = guardService;
    }

    @Override
    public String eventClassName() {
        return EVENT_CLASS;
    }

    @Override
    public void handle(Event event) {
        if (!(event instanceof ICancellableEvent cancellable)) return;
        ServerPlayer player = extractPlayer(event);
        if (player == null) return;
        if (guardService.shouldBypass(player)) return;

        var pos = player.blockPosition();
        ProtectedZone zone = guardService.zoneManager().findZoneContaining(player.serverLevel(), pos)
            .map(z -> (ProtectedZone) z).orElse(null);
        if (zone != null && guardService.isZoneMember(player, zone)) return;
        if (!guardService.isZoneDenying(player.serverLevel(), pos, BuiltinFlags.SABLE_RAGDOLL)) return;

        cancellable.setCanceled(true);
        player.displayClientMessage(
            Component.translatable("arcadiaguard.message.sable_ragdoll").withStyle(ChatFormatting.RED), true);
        guardService.auditDenied(player, zone != null ? zone.name() : "(dimension)", pos,
            BuiltinFlags.SABLE_RAGDOLL, "sable_ragdoll");
    }

    static ServerPlayer extractPlayer(Event event) {
        try {
            Object player = event.getClass().getMethod("player").invoke(event);
            return player instanceof ServerPlayer sp ? sp : null;
        } catch (Exception e) {
            return null;
        }
    }
}
