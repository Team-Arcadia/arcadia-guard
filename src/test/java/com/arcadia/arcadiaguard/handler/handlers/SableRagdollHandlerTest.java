package com.arcadia.arcadiaguard.handler.handlers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.zone.ZoneManager;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Flag sable-ragdoll : annulation de RagdollStartEvent (simule par un event de test au meme contrat). */
@ExtendWith(MockitoExtension.class)
class SableRagdollHandlerTest {

    @Mock private GuardService guardService;
    @Mock private ZoneManager zoneManager;
    @Mock private ServerPlayer player;

    /** Meme contrat que RagdollStartEvent : player() + ICancellableEvent. */
    public static final class FakeRagdollEvent extends Event implements ICancellableEvent {
        private final ServerPlayer player;
        public FakeRagdollEvent(ServerPlayer player) { this.player = player; }
        public ServerPlayer player() { return player; }
    }

    @BeforeAll
    static void bootstrap() throws Exception {
        Class<?> lmlClass = Class.forName("net.neoforged.fml.loading.LoadingModList");
        lmlClass.getMethod("of", List.class, List.class, List.class, List.class, java.util.Map.class)
            .invoke(null, List.of(), List.of(), List.of(), List.of(), java.util.Map.of());
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @BeforeEach
    void setUp() {
        lenient().when(guardService.zoneManager()).thenReturn(zoneManager);
        lenient().when(player.blockPosition()).thenReturn(BlockPos.ZERO);
    }

    @Test
    void eventClassName_matchesSableApi() {
        assertEquals("dev.leo.sableplayerragdoll.api.RagdollStartEvent",
            new SableRagdollHandler(guardService).eventClassName());
    }

    @Test
    void denied_cancelsAndAudits() {
        when(guardService.isZoneDenying(any(), any(), eq(BuiltinFlags.SABLE_RAGDOLL))).thenReturn(true);
        var event = new FakeRagdollEvent(player);
        new SableRagdollHandler(guardService).handle(event);
        assertTrue(event.isCanceled());
        verify(guardService).auditDenied(eq(player), eq("(dimension)"), any(), eq(BuiltinFlags.SABLE_RAGDOLL), any());
    }

    @Test
    void allowed_doesNotCancel() {
        when(guardService.isZoneDenying(any(), any(), eq(BuiltinFlags.SABLE_RAGDOLL))).thenReturn(false);
        var event = new FakeRagdollEvent(player);
        new SableRagdollHandler(guardService).handle(event);
        assertFalse(event.isCanceled());
    }

    @Test
    void bypass_doesNotCancel() {
        when(guardService.shouldBypass(player)).thenReturn(true);
        var event = new FakeRagdollEvent(player);
        new SableRagdollHandler(guardService).handle(event);
        assertFalse(event.isCanceled());
        verify(guardService, never()).isZoneDenying(any(), any(), any(com.arcadia.arcadiaguard.api.flag.BooleanFlag.class));
    }

    @Test
    void nullPlayer_isIgnored() {
        var event = new FakeRagdollEvent(null);
        assertDoesNotThrow(() -> new SableRagdollHandler(guardService).handle(event));
        assertFalse(event.isCanceled());
    }
}
