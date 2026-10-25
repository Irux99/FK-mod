package fr.irux.fk2000.radar;

import fr.irux.fk2000.config.ModConfig;
import fr.irux.fk2000.entity.vehicle.Fk2000Entity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Coordinate validation never searches for an entity or loads the destination chunk. */
public final class TargetCoordinates {
    private TargetCoordinates() {}

    public static boolean isValid(Fk2000Entity vehicle, Vec3 destination) {
        return isValid(vehicle.level(), vehicle.position(), destination);
    }

    public static boolean isValid(Level level, Vec3 source, Vec3 destination) {
        if (destination == null || !Double.isFinite(destination.x) || !Double.isFinite(destination.y)
                || !Double.isFinite(destination.z) || Math.abs(destination.x) > 29_999_984
                || Math.abs(destination.z) > 29_999_984 || destination.y < level.getMinBuildHeight()
                || destination.y >= level.getMaxBuildHeight()) return false;
        double range = ModConfig.COORDINATE_TARGET_RANGE.get();
        return source.distanceToSqr(destination) <= range * range
                && level.getWorldBorder().isWithinBounds(BlockPos.containing(destination));
    }
}
