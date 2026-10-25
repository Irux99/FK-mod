package fr.irux.fk2000.config;

import fr.irux.fk2000.entity.vehicle.TubeCapMode;
import net.minecraftforge.common.ForgeConfigSpec;

/** Server-owned settings; Forge synchronizes SERVER configs to connected clients. Units: ticks, blocks. */
public final class ModConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue VEHICLE_MAX_SPEED, VEHICLE_ACCELERATION, BRAKING,
        REMOTE_RANGE, RADAR_RANGE, EJECTION_SPEED, MISSILE_ACCELERATION, MISSILE_MAX_SPEED,
        MISSILE_TURN_RATE, MISSILE_DAMAGE, EXPLOSION_POWER, PROXIMITY_FUSE,
        COORDINATE_TARGET_RANGE, VEHICLE_EXPLOSION_POWER, VEHICLE_EXPLOSION_DAMAGE, MAX_ENGAGEMENT_RANGE;
    public static final ForgeConfigSpec.IntValue MISSILE_CAPACITY, PREPARATION_TICKS, RELOAD_TICKS,
        ORIENTATION_TICKS, THRUSTER_PARTICLES, MISSILE_LIFETIME, AUTO_FIRE_INTERVAL_TICKS,
        COORDINATE_MISSILE_LIFETIME, VEHICLE_CHUNK_RADIUS, MISSILE_CHUNK_LOOKAHEAD,
        MAX_CHUNK_LOADED_VEHICLES, MAX_CHUNK_LOADED_MISSILES, MAX_FORCED_CHUNKS;
    public static final ForgeConfigSpec.BooleanValue AUTO_RADAR, AUTO_FIRE, BLOCK_DAMAGE,
        VEHICLE_CHUNK_LOADING, MISSILE_CHUNK_LOADING, UNLIMITED_REMOTE_RANGE;
    public static final ForgeConfigSpec.EnumValue<TubeCapMode> TUBE_CAP_MODE;
    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("vehicle");
        VEHICLE_MAX_SPEED = b.comment("Blocks/tick; 0.45 = 9 blocks/second.").defineInRange("maxSpeed", .45, .05, 2.);
        VEHICLE_ACCELERATION = b.defineInRange("acceleration", .012, .001, .2);
        BRAKING = b.defineInRange("braking", .035, .001, .5);
        REMOTE_RANGE = b.comment("Used when chunkLoading.unlimitedRemoteRange is disabled; same-dimension ownership checks always apply.")
            .defineInRange("remoteRange", 192., 8., 30000000.);
        MISSILE_CAPACITY = b.defineInRange("missileCapacity", 8, 1, 16);
        PREPARATION_TICKS = b.defineInRange("preparationTicks", 40, 10, 400);
        RELOAD_TICKS = b.comment("Minimum ticks between individual missile reloads; replaces the old reloadTicks setting.")
            .defineInRange("quickReloadTicks", 16, 1, 2400);
        AUTO_FIRE_INTERVAL_TICKS = b.comment("Ticks between automatic shots once the launcher is upright. Initial preparation still applies.")
            .defineInRange("autoFireIntervalTicks", 30, 10, 400);
        TUBE_CAP_MODE = b.comment("Default launch-tube closure: EJECT throws the cap, HINGED opens and recloses it, NONE leaves the tube open.")
            .defineEnum("tubeCapMode", TubeCapMode.EJECT);
        VEHICLE_EXPLOSION_POWER = b.defineInRange("destructionExplosionPower", 4., .1, 12.);
        VEHICLE_EXPLOSION_DAMAGE = b.defineInRange("destructionExplosionDamage", 80., 0., 1000.);
        b.pop().push("radar");
        RADAR_RANGE = b.defineInRange("range", 160., 16., 512.);
        MAX_ENGAGEMENT_RANGE = b.comment("Maximum selectable engagement range per vehicle; detection uses loaded entities.")
            .defineInRange("maxEngagementRange", 2048., 64., 8192.);
        AUTO_RADAR = b.comment("Automatic acquisition is separate from automatic firing.").define("autoAcquire", true);
        AUTO_FIRE = b.comment("Automatic firing is OFF by default; player must explicitly enable it.").define("autoFire", false);
        COORDINATE_TARGET_RANGE = b.comment("Maximum distance from launcher to a manual X/Y/Z destination, in the same dimension.")
            .defineInRange("coordinateTargetRange", 4096., 16., 30000000.);
        b.pop().push("missile");
        EJECTION_SPEED = b.comment("Initial vertical velocity in blocks/tick; main engine OFF.").defineInRange("ejectionSpeed", 1.2, .3, 3.);
        ORIENTATION_TICKS = b.defineInRange("orientationTicks", 20, 5, 120);
        THRUSTER_PARTICLES = b.defineInRange("thrusterParticles", 2, 0, 8);
        MISSILE_ACCELERATION = b.defineInRange("acceleration", .08, .005, .5);
        MISSILE_MAX_SPEED = b.defineInRange("maxSpeed", 2.5, .3, 8.);
        MISSILE_TURN_RATE = b.comment("Maximum heading change in degrees/tick.").defineInRange("turnDegreesPerTick", 5., .1, 30.);
        MISSILE_LIFETIME = b.comment("Guided flight lifetime for extended engagement ranges; replaces the old lifetimeTicks setting.")
            .defineInRange("guidedLifetimeTicks", 2400, 40, 12000);
        COORDINATE_MISSILE_LIFETIME = b.comment("Flight lifetime for manually entered coordinates; 3600 ticks is three minutes.")
            .defineInRange("coordinateLifetimeTicks", 3600, 40, 12000);
        MISSILE_DAMAGE = b.comment("Targeted anti-air warhead damage before the target's armor. Also applies with terrain damage disabled.")
            .defineInRange("warheadDamage", 160., 1., 2000.);
        EXPLOSION_POWER = b.defineInRange("explosionPower", 4., .1, 12.);
        PROXIMITY_FUSE = b.defineInRange("proximityFuse", 2., .1, 8.);
        BLOCK_DAMAGE = b.comment("False protects terrain. Entity explosion damage still applies.").define("blockDamage", false);
        b.pop().push("chunkLoading");
        VEHICLE_CHUNK_LOADING = b.comment("Keep a bounded area around each FK-2000 ticking, including when its owner is far away.")
            .define("vehicles", true);
        MISSILE_CHUNK_LOADING = b.comment("Load a bounded moving corridor for active missiles and release it after the flight.")
            .define("missiles", true);
        UNLIMITED_REMOTE_RANGE = b.comment("Allow the owner's paired remote to control a ticket-loaded truck anywhere in the same dimension.")
            .define("unlimitedRemoteRange", true);
        VEHICLE_CHUNK_RADIUS = b.defineInRange("vehicleRadius", 1, 0, 2);
        MISSILE_CHUNK_LOOKAHEAD = b.defineInRange("missileLookahead", 3, 1, 6);
        MAX_CHUNK_LOADED_VEHICLES = b.defineInRange("maxVehicles", 16, 1, 256);
        MAX_CHUNK_LOADED_MISSILES = b.defineInRange("maxMissiles", 32, 1, 256);
        MAX_FORCED_CHUNKS = b.comment("Global ticket budget per dimension. Entries beyond the budget cannot load extra terrain.")
            .defineInRange("maxForcedChunks", 1024, 64, 8192);
        b.pop();
        SPEC = b.build();
    }
    private ModConfig() {}
}
