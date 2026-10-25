package fr.irux.fk2000.radar;

import fr.irux.fk2000.compat.SbwCompatibility;
import fr.irux.fk2000.entity.vehicle.Fk2000Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.AbstractMinecart;

import java.util.Comparator;
import java.util.List;

/** Bounded scans over already loaded entities. No chunk tickets or entity-ID lists. */
public final class RadarSystem {
    public static final int SCAN_INTERVAL = 10;
    public static final int MAX_CONTACTS = 32;

    private RadarSystem() {}

    public static boolean canTrack(Fk2000Entity source, Entity target) {
        if (!(source.level() instanceof ServerLevel) || !source.isRadarEnabled()
                || target == null || target.level() != source.level()) return false;
        double range = source.getEngagementRange();
        if (source.distanceToSqr(target) > range * range || !isEligibleForMode(source.getTargetMode(), target)) return false;
        Player owner = source.getOwnerUUID() == null ? null : source.level().getPlayerByUUID(source.getOwnerUUID());
        return !SbwCompatibility.isFriendly(source, source.getOwnerUUID(), owner, target);
    }

    public static boolean isEligibleForMode(TargetMode mode, Entity target) {
        if (target == null || !target.isAlive() || target.isRemoved() || target.isSpectator() || !target.isPickable()) return false;
        return switch (mode) {
            case AIRCRAFT -> SbwCompatibility.isAerial(target);
            case SBW_VEHICLES -> SbwCompatibility.isVehicle(target);
            case ALL_ENTITIES -> target instanceof LivingEntity || target instanceof Boat
                    || target instanceof AbstractMinecart || SbwCompatibility.isVehicle(target);
            case COORDINATES -> false;
        };
    }

    public static List<RadarContact> scan(Fk2000Entity source) {
        if (!source.isRadarEnabled() || source.getTargetMode() == TargetMode.COORDINATES
                || !(source.level() instanceof ServerLevel)) return List.of();
        double range = source.getEngagementRange();
        return source.level().getEntities(source, source.getBoundingBox().inflate(range),
                        entity -> canTrack(source, entity)).stream()
                .sorted(Comparator.comparingDouble(source::distanceToSqr))
                .limit(MAX_CONTACTS)
                .map(entity -> new RadarContact(entity.getId(), entity.getUUID(), entity.getName().getString(),
                        entity.getX(), entity.getY(), entity.getZ(), source.distanceTo(entity)))
                .toList();
    }
}
