package com.arcadia.arcadiaguard.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import java.util.List;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Degats d'explosion (cas Explosion Overhaul : degats appliques hors du chemin Detonate) :
 * classification du flag a verifier dans onLivingIncomingDamage.
 */
class ExplosionDamageFlagTest {

    @BeforeAll
    static void bootstrap() throws Exception {
        Class<?> lmlClass = Class.forName("net.neoforged.fml.loading.LoadingModList");
        lmlClass.getMethod("of", List.class, List.class, List.class, List.class, java.util.Map.class)
            .invoke(null, List.of(), List.of(), List.of(), List.of(), java.util.Map.of());
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void nonExplosionDamage_isIgnored() {
        assertNull(EntityEventHandler.explosionFlagForDamage(false, false, null));
    }

    @Test
    void fireworks_areIgnored() {
        assertNull(EntityEventHandler.explosionFlagForDamage(true, true, null));
    }

    @Test
    void creeperExplosion_checksCreeperFlag() {
        assertSame(BuiltinFlags.CREEPER_EXPLOSION,
            EntityEventHandler.explosionFlagForDamage(true, false, mock(Creeper.class)));
    }

    @Test
    void tntExplosion_checksTntFlag() {
        PrimedTnt tnt = mock(PrimedTnt.class);
        when(tnt.getType()).thenReturn((EntityType) EntityType.TNT);
        assertSame(BuiltinFlags.TNT_EXPLOSION, EntityEventHandler.explosionFlagForDamage(true, false, tnt));
    }

    @Test
    void otherExplosion_checksBlockExplosionFlag() {
        assertSame(BuiltinFlags.BLOCK_EXPLOSION, EntityEventHandler.explosionFlagForDamage(true, false, null));
        Zombie z = mock(Zombie.class);
        when(z.getType()).thenReturn((EntityType) EntityType.ZOMBIE);
        assertSame(BuiltinFlags.BLOCK_EXPLOSION, EntityEventHandler.explosionFlagForDamage(true, false, z));
    }
}
