package com.arcadia.arcadiaguard.zone;

import com.arcadia.arcadiaguard.ArcadiaGuardPaths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Pure (Minecraft-free) rename logic over a dimension's zone map, so it can be unit-tested.
 * Zone names are unique per dimension (same rule as {@link InternalZoneProvider#add}).
 */
public final class ZoneRenamer {

    public enum Status { OK, NOT_FOUND, ALREADY_EXISTS, INVALID_NAME }

    /**
     * @param status   outcome
     * @param oldZone  the zone before rename (null unless OK)
     * @param newZone  the rebuilt zone under the new name (null unless OK)
     * @param children child zones whose {@code parent} was rewritten to the new name
     */
    public record Result(Status status, @Nullable ProtectedZone oldZone,
                         @Nullable ProtectedZone newZone, List<ProtectedZone> children) {
        public boolean success() { return status == Status.OK; }
        static Result of(Status s) { return new Result(s, null, null, List.of()); }
    }

    private ZoneRenamer() {}

    /**
     * Renames {@code oldName} to {@code newName} inside {@code zones} (keys are lower-case names).
     * On success the map holds the new zone under the new key and children point to the new name.
     */
    public static Result rename(@Nullable Map<String, ProtectedZone> zones, String oldName, String newName) {
        if (newName == null || !ArcadiaGuardPaths.isValidZoneName(newName)) return Result.of(Status.INVALID_NAME);
        if (zones == null || oldName == null) return Result.of(Status.NOT_FOUND);
        String oldKey = oldName.toLowerCase(Locale.ROOT);
        String newKey = newName.toLowerCase(Locale.ROOT);
        ProtectedZone old = zones.get(oldKey);
        if (old == null) return Result.of(Status.NOT_FOUND);
        if (zones.containsKey(newKey)) return Result.of(Status.ALREADY_EXISTS);

        ProtectedZone renamed = old.renamedCopy(newKey);
        zones.put(newKey, renamed);
        zones.remove(oldKey);
        List<ProtectedZone> children = new ArrayList<>();
        for (ProtectedZone z : zones.values()) {
            if (z.parent() != null && z.parent().equalsIgnoreCase(oldKey)) {
                z.setParent(newKey);
                children.add(z);
            }
        }
        return new Result(Status.OK, old, renamed, children);
    }
}
