package com.arcadia.arcadiaguard.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.arcadia.arcadiaguard.compat.luckperms.LuckPermsCompat;
import com.arcadia.arcadiaguard.compat.luckperms.LuckPermsPermissionChecker;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class ZonePermissionTest {

    @Mock CommandSourceStack source;
    @Mock ServerPlayer player;
    @Mock LuckPermsPermissionChecker checker;

    @BeforeAll
    static void bootstrap() throws Exception {
        // Forge 1.20.1: LoadingModList.of(List<ModFile>, List<ModInfo>, EarlyLoadingException)
        net.minecraft.SharedConstants.tryDetectVersion();
        try { net.minecraft.server.Bootstrap.bootStrap(); } catch (Throwable first) { /* Forge 1.20.1: 1er bootStrap hors FML echoue sur le bus (NetworkEvent), le second passe */ net.minecraft.server.Bootstrap.bootStrap(); }
    }

    // --- isViewOnly ---

    @Test
    void isViewOnly_nonPlayerEntity_returnsFalse() {
        Entity nonPlayer = mock(Entity.class);
        when(source.getEntity()).thenReturn(nonPlayer);

        assertFalse(ZonePermission.isViewOnly(source));
    }

    @Test
    void isViewOnly_luckPermsNotAvailable_returnsFalse() {
        when(source.getEntity()).thenReturn(player);
        try (MockedStatic<LuckPermsCompat> lp = mockStatic(LuckPermsCompat.class)) {
            lp.when(LuckPermsCompat::isAvailable).thenReturn(false);
            assertFalse(ZonePermission.isViewOnly(source));
        }
    }

    @Test
    void isViewOnly_hasViewAccess_noBypass_returnsTrue() {
        when(source.getEntity()).thenReturn(player);
        when(checker.hasViewAccess(player)).thenReturn(true);
        when(checker.hasBypass(player)).thenReturn(false);
        try (MockedStatic<LuckPermsCompat> lp = mockStatic(LuckPermsCompat.class)) {
            lp.when(LuckPermsCompat::isAvailable).thenReturn(true);
            lp.when(LuckPermsCompat::checker).thenReturn(checker);
            assertTrue(ZonePermission.isViewOnly(source));
        }
    }

    @Test
    void isViewOnly_hasViewAccessAndBypass_returnsFalse() {
        // bypass (arcadiaguard.* ou zone.bypass) prend la priorité sur arcadiaguard.view
        when(source.getEntity()).thenReturn(player);
        when(checker.hasViewAccess(player)).thenReturn(true);
        when(checker.hasBypass(player)).thenReturn(true);
        try (MockedStatic<LuckPermsCompat> lp = mockStatic(LuckPermsCompat.class)) {
            lp.when(LuckPermsCompat::isAvailable).thenReturn(true);
            lp.when(LuckPermsCompat::checker).thenReturn(checker);
            assertFalse(ZonePermission.isViewOnly(source),
                "Un admin complet (bypass) ne doit pas être considéré viewOnly");
        }
    }

    @Test
    void isViewOnly_noViewAccess_returnsFalse() {
        when(source.getEntity()).thenReturn(player);
        when(checker.hasViewAccess(player)).thenReturn(false);
        try (MockedStatic<LuckPermsCompat> lp = mockStatic(LuckPermsCompat.class)) {
            lp.when(LuckPermsCompat::isAvailable).thenReturn(true);
            lp.when(LuckPermsCompat::checker).thenReturn(checker);
            assertFalse(ZonePermission.isViewOnly(source));
        }
    }
}
