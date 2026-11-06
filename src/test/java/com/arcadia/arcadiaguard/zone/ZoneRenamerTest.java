package com.arcadia.arcadiaguard.zone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Logique de renommage de zone (/ag zone rename + GUI RENAME_ZONE). */
class ZoneRenamerTest {

    private static final String DIM = "minecraft:overworld";

    private static ProtectedZone zone(String name, String parent) {
        return new ProtectedZone(name, DIM, 0, 0, 0, 10, 10, 10, new HashSet<>(), parent, 3);
    }

    private static Map<String, ProtectedZone> map(ProtectedZone... zones) {
        Map<String, ProtectedZone> m = new ConcurrentHashMap<>();
        for (ProtectedZone z : zones) m.put(z.name(), z);
        return m;
    }

    @Test
    void rename_success_preservesAllState() {
        ProtectedZone orig = zone("spawn", "world");
        UUID owner = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        orig.setRole(owner, ZoneRole.OWNER);
        orig.whitelistAdd(guest);
        orig.setFlag("pvp", false);
        orig.setEnabled(false);
        orig.setInheritDimFlags(false);
        orig.blockItem(ResourceLocation.parse("minecraft:tnt"));
        Map<String, ProtectedZone> m = map(orig, zone("world", null));

        ZoneRenamer.Result r = ZoneRenamer.rename(m, "spawn", "hub");

        assertTrue(r.success());
        assertFalse(m.containsKey("spawn"));
        ProtectedZone hub = m.get("hub");
        assertSame(r.newZone(), hub);
        assertEquals("hub", hub.name());
        assertEquals(DIM, hub.dimension());
        assertEquals(10, hub.maxX());
        assertEquals(3, hub.priority());
        assertEquals("world", hub.parent());
        assertEquals(ZoneRole.OWNER, hub.memberRoles().get(owner));
        assertTrue(hub.whitelistedPlayers().contains(guest));
        assertEquals(false, hub.flagValues().get("pvp"));
        assertFalse(hub.enabled());
        assertFalse(hub.inheritDimFlags());
        assertTrue(hub.blockedItems().contains(ResourceLocation.parse("minecraft:tnt")));
    }

    @Test
    void rename_existingTarget_refused() {
        Map<String, ProtectedZone> m = map(zone("a", null), zone("b", null));
        assertEquals(ZoneRenamer.Status.ALREADY_EXISTS, ZoneRenamer.rename(m, "a", "b").status());
        assertTrue(m.containsKey("a"));
        assertTrue(m.containsKey("b"));
    }

    @Test
    void rename_unknownSource_refused() {
        Map<String, ProtectedZone> m = map(zone("a", null));
        assertEquals(ZoneRenamer.Status.NOT_FOUND, ZoneRenamer.rename(m, "nope", "c").status());
        assertEquals(ZoneRenamer.Status.NOT_FOUND, ZoneRenamer.rename(null, "a", "c").status());
    }

    @Test
    void rename_invalidName_refused() {
        Map<String, ProtectedZone> m = map(zone("a", null));
        assertEquals(ZoneRenamer.Status.INVALID_NAME, ZoneRenamer.rename(m, "a", "bad name!").status());
        assertEquals(ZoneRenamer.Status.INVALID_NAME, ZoneRenamer.rename(m, "a", "").status());
        assertEquals(ZoneRenamer.Status.INVALID_NAME, ZoneRenamer.rename(m, "a", "x".repeat(65)).status());
        assertEquals(ZoneRenamer.Status.INVALID_NAME, ZoneRenamer.rename(m, "a", "../evil").status());
        assertTrue(m.containsKey("a"));
    }

    @Test
    void rename_childrenFollowNewParentName() {
        ProtectedZone c1 = zone("c1", "parent");
        ProtectedZone c2 = zone("c2", "parent");
        ProtectedZone other = zone("other", "elsewhere");
        Map<String, ProtectedZone> m = map(zone("parent", null), c1, c2, other, zone("elsewhere", null));

        ZoneRenamer.Result r = ZoneRenamer.rename(m, "parent", "root");

        assertTrue(r.success());
        assertEquals("root", c1.parent());
        assertEquals("root", c2.parent());
        assertEquals("elsewhere", other.parent());
        assertEquals(2, r.children().size());
    }

    @Test
    void renamedCopy_isDeep() {
        ProtectedZone orig = zone("a", null);
        orig.setFlag("pvp", true);
        ProtectedZone copy = orig.renamedCopy("b");
        copy.setFlag("pvp", false);
        assertEquals(true, orig.flagValues().get("pvp"));
        assertNull(copy.parent());
    }
}
