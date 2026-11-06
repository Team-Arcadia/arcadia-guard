package com.arcadia.arcadiaguard.api.event;

import com.arcadia.arcadiaguard.api.zone.IZone;
import net.neoforged.bus.api.Event;

/**
 * Fired on the NeoForge EventBus after a zone has been successfully renamed
 * (in-memory state updated, file move scheduled, child zones re-parented).
 *
 * <p>Listeners must be registered on {@code NeoForge.EVENT_BUS}.
 * This event is fired on the server thread.
 */
public class ZoneRenamedEvent extends Event {

    private final IZone zone;
    private final String oldName;

    /**
     * @param zone    the zone under its new name; never null
     * @param oldName the previous name; never null
     */
    public ZoneRenamedEvent(IZone zone, String oldName) {
        this.zone = zone;
        this.oldName = oldName;
    }

    /** @return the renamed zone (its {@code name()} is the new name) */
    public IZone zone() { return this.zone; }

    /** @return the zone's previous name */
    public String oldName() { return this.oldName; }

    /** @return the zone's new name */
    public String newName() { return this.zone.name(); }
}
