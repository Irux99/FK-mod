package fr.irux.fk2000.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server-owned settings; Forge synchronizes SERVER configs to connected clients. Units: ticks, blocks. */
public final class ModConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue VEHICLE_MAX_SPEED, VEHICLE_ACCELERATION, BRAKING,
        REMOTE_RANGE, RADAR_RANGE, EJECTION_SPEED, MISSILE_ACCELERATION, MISSILE_MAX_SPEED,
        MISSILE_TURN_RATE, MISSILE_DAMAGE, EXPLOSION_POWER, PROXIMITY_FUSE;
    public static final ForgeConfigSpec.IntValue MISSILE_CAPACITY, PREPARATION_TICKS, RELOAD_TICKS,
        ORIENTATION_TICKS, THRUSTER_PARTICLES, MISSILE_LIFETIME, AUTO_FIRE_INTERVAL_TICKS;
    public static final ForgeConfigSpec.BooleanValue AUTO_RADAR, AUTO_FIRE, BLOCK_DAMAGE;
    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("vehicle");
        VEHICLE_MAX_SPEED = b.comment("Blocks/tick; 0.45 = 9 blocks/second.").defineInRange("maxSpeed", .45, .05, 2.);
        VEHICLE_ACCELERATION = b.defineInRange("acceleration", .012, .001, .2);
        BRAKING = b.defineInRange("braking", .035, .001, .5);
        REMOTE_RANGE = b.comment("Never loads chunks. Vehicle must already be loaded.").defineInRange("remoteRange", 192., 8., 1024.);
        MISSILE_CAPACITY = b.defineInRange("missileCapacity", 8, 1, 16);
        PREPARATION_TICKS = b.defineInRange("preparationTicks", 40, 10, 400);
        RELOAD_TICKS = b.comment("Minimum ticks between individual missile reloads; replaces the old reloadTicks setting.")
            .defineInRange("quickReloadTicks", 16, 1, 2400);
        AUTO_FIRE_INTERVAL_TICKS = b.comment("Ticks between automatic shots once the launcher is upright. Initial preparation still applies.")
            .defineInRange("autoFireIntervalTicks", 30, 10, 400);
        b.pop().push("radar");
        RADAR_RANGE = b.defineInRange("range", 160., 16., 512.);
        AUTO_RADAR = b.comment("Automatic acquisition is separate from automatic firing.").define("autoAcquire", true);
        AUTO_FIRE = b.comment("Automatic firing is OFF by default; player must explicitly enable it.").define("autoFire", false);
        b.pop().push("missile");
        EJECTION_SPEED = b.comment("Initial vertical velocity in blocks/tick; main engine OFF.").defineInRange("ejectionSpeed", 1.2, .3, 3.);
        ORIENTATION_TICKS = b.defineInRange("orientationTicks", 20, 5, 120);
        THRUSTER_PARTICLES = b.defineInRange("thrusterParticles", 2, 0, 8);
        MISSILE_ACCELERATION = b.defineInRange("acceleration", .08, .005, .5);
        MISSILE_MAX_SPEED = b.defineInRange("maxSpeed", 2.5, .3, 8.);
        MISSILE_TURN_RATE = b.comment("Maximum heading change in degrees/tick.").defineInRange("turnDegreesPerTick", 5., .1, 30.);
        MISSILE_LIFETIME = b.defineInRange("lifetimeTicks", 240, 40, 1200);
        MISSILE_DAMAGE = b.comment("Targeted anti-air warhead damage before the target's armor. Also applies with terrain damage disabled.")
            .defineInRange("warheadDamage", 160., 1., 2000.);
        EXPLOSION_POWER = b.defineInRange("explosionPower", 4., .1, 12.);
        PROXIMITY_FUSE = b.defineInRange("proximityFuse", 2., .1, 8.);
        BLOCK_DAMAGE = b.comment("False protects terrain. Entity explosion damage still applies.").define("blockDamage", false);
        b.pop();
        SPEC = b.build();
    }
    private ModConfig() {}
}
