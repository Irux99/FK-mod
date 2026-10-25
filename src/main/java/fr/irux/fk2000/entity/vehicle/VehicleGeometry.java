package fr.irux.fk2000.entity.vehicle;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shared dimensions for the enlarged truck, its renderers and server attachments. */
public final class VehicleGeometry {
    public static final float SCALE = 2.0F;
    public static final float BODY_WIDTH = 3.70F;
    public static final float BODY_LENGTH = 12.0F;
    public static final float BODY_HEIGHT = 4.40F;
    public static final float MAX_STEP = 2.10F;
    public static final double WHEEL_RADIUS = 0.38877 * SCALE;
    public static final Vec3 DRIVER_SEAT = new Vec3(-0.55, 0.80, 4.35);
    public static final Vec3 DRIVER_EYE = new Vec3(-0.55, 2.35, 4.35);

    private VehicleGeometry() {}

    public static Vec3 localToWorld(Vec3 origin, float yawDegrees, Vec3 local) {
        double yaw = Math.toRadians(yawDegrees);
        return origin.add(Math.cos(yaw) * local.x - Math.sin(yaw) * local.z, local.y,
                Math.sin(yaw) * local.x + Math.cos(yaw) * local.z);
    }

    /** Six tube mouths, including launcher articulation; returned positions already include SCALE. */
    public static Vec3 tubeMuzzleLocal(int tube, float launcherProgress) {
        int selected = Math.floorMod(tube, 6);
        double angle = Math.max(0, Math.min(1, launcherProgress)) * Math.PI / 2;
        double hingeY = 1.224;
        double hingeZ = -2.67;
        double relativeY = (selected < 3 ? 1.304 : 1.934) - hingeY;
        double relativeZ = -.535 - hingeZ;
        return new Vec3((selected % 3 - 1) * .33 * SCALE,
                (hingeY + relativeY * Math.cos(angle) + relativeZ * Math.sin(angle)) * SCALE,
                (hingeZ + relativeZ * Math.cos(angle) - relativeY * Math.sin(angle)) * SCALE);
    }

    public static AABB segmentBounds(Vec3 origin, float yaw, double offset, double length) {
        Vec3 center = localToWorld(origin, yaw, new Vec3(0, 0, offset));
        double angle = Math.toRadians(yaw);
        double x = (Math.abs(Math.cos(angle)) * BODY_WIDTH + Math.abs(Math.sin(angle)) * length) / 2;
        double z = (Math.abs(Math.sin(angle)) * BODY_WIDTH + Math.abs(Math.cos(angle)) * length) / 2;
        return new AABB(center.x - x, center.y, center.z - z, center.x + x, center.y + BODY_HEIGHT, center.z + z);
    }

    /** Vanilla collisions require an AABB: rotate the rectangular chassis, not a square footprint. */
    public static AABB bodyBounds(Vec3 position, float yawDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double halfX = (Math.abs(Math.cos(yaw)) * BODY_WIDTH + Math.abs(Math.sin(yaw)) * BODY_LENGTH) * 0.5;
        double halfZ = (Math.abs(Math.sin(yaw)) * BODY_WIDTH + Math.abs(Math.cos(yaw)) * BODY_LENGTH) * 0.5;
        return new AABB(position.x - halfX, position.y, position.z - halfZ,
                position.x + halfX, position.y + BODY_HEIGHT, position.z + halfZ);
    }
}
