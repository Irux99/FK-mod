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

    /** Vanilla collisions require an AABB: rotate the rectangular chassis, not a square footprint. */
    public static AABB bodyBounds(Vec3 position, float yawDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double halfX = (Math.abs(Math.cos(yaw)) * BODY_WIDTH + Math.abs(Math.sin(yaw)) * BODY_LENGTH) * 0.5;
        double halfZ = (Math.abs(Math.sin(yaw)) * BODY_WIDTH + Math.abs(Math.cos(yaw)) * BODY_LENGTH) * 0.5;
        return new AABB(position.x - halfX, position.y, position.z - halfZ,
                position.x + halfX, position.y + BODY_HEIGHT, position.z + halfZ);
    }
}
