package fr.irux.fk2000.entity.missile;

import fr.irux.fk2000.compat.SbwCompatibility;
import fr.irux.fk2000.config.ClientEffectsConfig;
import fr.irux.fk2000.config.ModConfig;
import fr.irux.fk2000.entity.vehicle.WreckEntity;
import fr.irux.fk2000.loading.FkChunkLoading;
import fr.irux.fk2000.network.ExplosionEffects;
import fr.irux.fk2000.physics.GuidanceMath;
import fr.irux.fk2000.registry.ModSounds;
import fr.irux.fk2000.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.UUID;

/** Server-authoritative cold-launch missile. Clients only interpolate transforms and draw effects. */
@Mod.EventBusSubscriber(modid = "iruxfk2000")
public class MissileEntity extends Entity {
    public enum TerminationReason { ACTIVE, INVALID_SPAWN, LIFETIME, TARGET_LOST, UNLOADED_PATH, UNARMED_COLLISION, DETONATED }

    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> CORRECTION_X = SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> CORRECTION_Y = SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> CORRECTION_Z = SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.FLOAT);
    private static final int EJECTION_TICKS = 10;
    private static final int BOOST_TICKS = 12;
    private static final int LOST_TARGET_GRACE_TICKS = 20;

    private UUID ownerId;
    private UUID launcherId;
    private UUID targetId;
    private Vec3 fixedDestination;
    private Vec3 lastKnownTargetPosition;
    private Vec3 nose = new Vec3(0, 1, 0);
    private int stateTicks;
    private int flightTicks;
    private int lostTargetTicks;
    private int interpolationTicks;
    private double interpolationX, interpolationY, interpolationZ;
    private float interpolationYaw, interpolationPitch;
    private boolean hasDetonated;
    private Vec3 terrainWaypoint;
    private int nextTerrainScan;
    private TerminationReason terminationReason = TerminationReason.ACTIVE;

    public MissileEntity(EntityType<? extends MissileEntity> type, Level level) {
        super(type, level);
        setNoGravity(true); // Each launch phase applies its own physical gravity below.
        setYRot(0);
        setXRot(-90);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(STATE, FlightState.IDLE.ordinal());
        entityData.define(CORRECTION_X, 0f);
        entityData.define(CORRECTION_Y, 0f);
        entityData.define(CORRECTION_Z, 0f);
    }

    /** Called only by the launcher after its animated preparation; never accepted from a client. */
    public void launch(UUID owner, UUID launcher, Entity target) {
        if (level().isClientSide || getFlightState() != FlightState.IDLE || !target.isAlive()) return;
        ownerId = owner;
        launcherId = launcher;
        targetId = target.getUUID();
        fixedDestination = null;
        lastKnownTargetPosition = target.getBoundingBox().getCenter();
        nose = new Vec3(0, 1, 0);
        setXRot(-90);
        transitionTo(FlightState.PREPARING);
    }

    /** A fixed world position is a target in its own right, never a fabricated entity. */
    public void launchAt(UUID owner, UUID launcher, Vec3 destination) {
        if (level().isClientSide || getFlightState() != FlightState.IDLE || !validDestination(destination)) return;
        ownerId = owner;
        launcherId = launcher;
        targetId = null;
        fixedDestination = destination;
        lastKnownTargetPosition = destination;
        nose = new Vec3(0, 1, 0);
        setXRot(-90);
        transitionTo(FlightState.PREPARING);
    }

    private boolean validDestination(@Nullable Vec3 destination) {
        return destination != null && Double.isFinite(destination.x) && Double.isFinite(destination.y)
                && Double.isFinite(destination.z) && destination.y >= level().getMinBuildHeight()
                && destination.y < level().getMaxBuildHeight()
                && level().getWorldBorder().isWithinBounds(BlockPos.containing(destination));
    }

    @Nullable public UUID getTrackedTargetUUID() { return targetId; }
    @Nullable public Vec3 getLastKnownTargetPosition() {
        return fixedDestination == null ? lastKnownTargetPosition : fixedDestination;
    }
    public boolean hasFixedDestination() { return fixedDestination != null; }

    public FlightState getFlightState() { return FlightState.fromId(entityData.get(STATE)); }
    public boolean getMotorActive() { return getFlightState().motorActive(); }
    public UUID getLauncherUUID() { return launcherId; }

    public TerminationReason getTerminationReason() { return terminationReason; }
    public Vec3 getCorrection() {
        return new Vec3(entityData.get(CORRECTION_X), entityData.get(CORRECTION_Y), entityData.get(CORRECTION_Z));
    }

    private long lastClientPoseTick = Long.MIN_VALUE;

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) lastClientPoseTick = level().getGameTime();
        if (level().isClientSide) {
            interpolateClientTransform();
            drawFlightEffects();
            return;
        }
        if (getFlightState() == FlightState.IDLE) {
            if (tickCount > 20) expire(TerminationReason.INVALID_SPAWN);
            return;
        }
        int lifetime = fixedDestination == null ? ModConfig.MISSILE_LIFETIME.get() : ModConfig.COORDINATE_MISSILE_LIFETIME.get();
        if (++flightTicks > lifetime) {
            expire(TerminationReason.LIFETIME);
            return;
        }
        // Tickets restore the last known target chunk before resolving its UUID.
        // The manager is bounded and releases our transient tickets on removal.
        FkChunkLoading.refreshMissile(this, null);
        Entity target = resolveTarget();
        if (target == null && fixedDestination == null) {
            if (++lostTargetTicks > LOST_TARGET_GRACE_TICKS) {
                expire(TerminationReason.TARGET_LOST);
                return;
            }
        } else {
            lostTargetTicks = 0;
            if (target != null) {
                lastKnownTargetPosition = target.getBoundingBox().getCenter();
                FkChunkLoading.refreshMissile(this, target);
            }
        }
        ++stateTicks;
        updatePhysics(target);
        if (isRemoved() || getFlightState() == FlightState.PREPARING) return;
        updateOrientation();
        moveWithCollision(target);
    }

    private void updatePhysics(@Nullable Entity target) {
        switch (getFlightState()) {
            case PREPARING -> {
                if (stateTicks >= 1) {
                    setDeltaMovement(0, ModConfig.EJECTION_SPEED.get(), 0);
                    transitionTo(FlightState.EJECTING);
                    level().playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.7f, 1.7f);
                    ((ServerLevel) level()).sendParticles(ParticleTypes.CLOUD, getX(), getY(), getZ(), 28, .45, .12, .45, .09);
                }
            }
            case EJECTING -> {
                setDeltaMovement(getDeltaMovement().scale(0.995).add(0, -0.035, 0));
                if (stateTicks >= EJECTION_TICKS) transitionTo(FlightState.ORIENTING);
            }
            case ORIENTING -> orientWithThrusters(target);
            case BOOSTING, GUIDING -> guideUnderPower(target);
            case DETONATED -> discard();
            default -> { }
        }
    }

    private void orientWithThrusters(@Nullable Entity target) {
        Vec3 previousNose = nose;
        Vec3 wanted = aimDirection(target);
        double radians = Math.PI / Math.max(1, ModConfig.ORIENTATION_TICKS.get());
        nose = turn(nose, wanted, radians);
        // Cold-gas side jets change attitude and impart a small, opposite visible exhaust impulse.
        Vec3 correction = nose.subtract(previousNose);
        syncCorrection(correction);
        setDeltaMovement(getDeltaMovement().scale(0.985).add(correction.scale(0.08)).add(0, -0.025, 0));
        if (stateTicks >= ModConfig.ORIENTATION_TICKS.get()
                && (target == null && fixedDestination == null || nose.dot(wanted) > Math.cos(Math.toRadians(12)))) {
            transitionTo(FlightState.BOOSTING);
            syncCorrection(Vec3.ZERO);
            level().playSound(null, blockPosition(), ModSounds.MISSILE_IGNITION.get(), SoundSource.HOSTILE, 2.5f, 1f);
            ((ServerLevel) level()).sendParticles(ParticleTypes.FLASH, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
        }
    }

    private void guideUnderPower(@Nullable Entity target) {
        double turnRate = Math.toRadians(ModConfig.MISSILE_TURN_RATE.get());
        Vec3 wanted = aimDirection(target);
        Vec3 previousNose = nose;
        if (target != null || fixedDestination != null) nose = turn(nose, wanted, turnRate);
        syncCorrection(nose.subtract(previousNose));
        double currentSpeed = getDeltaMovement().length();
        double speedLimit = ModConfig.MISSILE_MAX_SPEED.get();
        if ((target != null || fixedDestination != null)
                && getDeltaMovement().normalize().dot(wanted) < Math.cos(Math.toRadians(15))) {
            // A 2.5-block/tick missile turning at five degrees has a ~29-block radius.
            // Reduce terminal speed progressively so low/nearby targets do not cause an endless orbit.
            double range = (target == null ? fixedDestination : target.getBoundingBox().getCenter()).distanceTo(position());
            double pursuitFloor = Math.max(.3, (target == null ? 0 : target.getDeltaMovement().length()) + .35);
            speedLimit = Math.min(speedLimit, Math.max(pursuitFloor, range * Math.sin(turnRate) * .8));
        }
        double acceleration = ModConfig.MISSILE_ACCELERATION.get();
        double speed = Mth.clamp(speedLimit, Math.max(.3, currentSpeed - acceleration * 2), currentSpeed + acceleration);
        // Accelerate along the engine axis while banking the actual trajectory at the same bounded rate.
        Vec3 velocityDirection = turn(getDeltaMovement().normalize(), nose, turnRate);
        setDeltaMovement(velocityDirection.scale(speed));
        if (getFlightState() == FlightState.BOOSTING && stateTicks >= BOOST_TICKS) transitionTo(FlightState.GUIDING);
    }

    private Vec3 aimDirection(@Nullable Entity target) {
        Vec3 center = target == null ? fixedDestination : target.getBoundingBox().getCenter();
        if (center == null) return nose;
        Vec3 velocity = target == null ? Vec3.ZERO : target.getDeltaMovement();
        GuidanceMath.Vector intercept = GuidanceMath.intercept(vector(position()), vector(center), vector(velocity),
                Math.max(getDeltaMovement().length(), 0.6), 60);
        Vec3 predicted = vec3(intercept);
        if (flightTicks >= nextTerrainScan) {
            nextTerrainScan = flightTicks + 5;
            terrainWaypoint = findTerrainWaypoint(predicted);
        }
        return (terrainWaypoint == null ? predicted : terrainWaypoint).subtract(position()).normalize();
    }

    /**
     * Plan the climb during cold orientation, before a fast missile reaches the hillside.
     * Heightmaps are queried only after the chunk manager has prepared a bounded corridor.
     * Three columns cover the immediate flight path without searching unloaded entities.
     */
    @Nullable
    private Vec3 findTerrainWaypoint(Vec3 predicted) {
        Vec3 horizontal = new Vec3(predicted.x - getX(), 0, predicted.z - getZ());
        double distance = horizontal.length();
        if (distance < 6) return null;
        Vec3 direction = horizontal.scale(1 / distance);
        Vec3 side = new Vec3(-direction.z, 0, direction.x);
        double turnRadius = ModConfig.MISSILE_MAX_SPEED.get()
                / Math.max(.01, Math.toRadians(ModConfig.MISSILE_TURN_RATE.get()));
        double clearance = Math.max(8, Math.min(24, turnRadius * .5));
        double requiredAltitude = Double.NEGATIVE_INFINITY;
        double firstObstacle = distance;
        double lastObstacle = 0;
        boolean obstructed = false;
        // A bounded scan prevents a very fast target's lead point making the work unbounded.
        double scanDistance = Math.min(distance - 3, 512);
        scanColumns: for (double along = 2; along <= scanDistance; along += 2) {
            for (int lateral = -1; lateral <= 1; lateral++) {
                Vec3 column = position().add(direction.scale(along)).add(side.scale(lateral * 1.5));
                BlockPos sample = BlockPos.containing(column);
                // A long coordinate shot may exceed the bounded loaded corridor.
                // Keep the obstacles already found instead of throwing that plan away.
                if (!level().hasChunkAt(sample)) break scanColumns;
                double directAltitude = Mth.lerp(along / distance, getY(), predicted.y);
                double localClearance = Math.min(clearance, Math.max(2, (distance - along) * .25));
                int top = surfaceNearFlightLine(sample, directAltitude, localClearance);
                if (top + localClearance > directAltitude) {
                    obstructed = true;
                    firstObstacle = Math.min(firstObstacle, along);
                    lastObstacle = Math.max(lastObstacle, along);
                    requiredAltitude = Math.max(requiredAltitude, top + localClearance);
                }
            }
        }
        if (!obstructed) return null;
        double altitude = Math.min(level().getMaxBuildHeight() - 3, requiredAltitude);
        if (altitude <= getY() + 1) {
            // Stay level until beyond the ridge. A high missile still needs this guard:
            // aiming down at a low target can otherwise send it back through the mountain.
            return position().add(direction.scale(Math.min(distance, lastObstacle + Math.max(6, turnRadius * .5))))
                    .multiply(1, 0, 1).add(0, Math.max(getY(), altitude), 0);
        }
        // Steep climb first: a waypoint on the far side would still aim through a close cliff.
        return position().add(direction.scale(Math.min(12, Math.max(2, firstObstacle * .35))))
                .multiply(1, 0, 1).add(0, altitude, 0);
    }

    /** A far overhead cave roof is not a mountain blocking the actual flight corridor. */
    private int surfaceNearFlightLine(BlockPos column, double lineAltitude, double clearance) {
        int heightmapTop = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        if (heightmapTop <= lineAltitude - clearance) return heightmapTop;
        int minimum = Math.max(level().getMinBuildHeight(), Mth.floor(lineAltitude - clearance));
        int maximum = Math.min(level().getMaxBuildHeight() - 1, Mth.floor(lineAltitude));
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos(column.getX(), maximum, column.getZ());
        for (int y = maximum; y >= minimum; y--) {
            probe.setY(y);
            if (level().getBlockState(probe).getCollisionShape(level(), probe).isEmpty()) continue;
            // Find the top of the connected obstruction, not an unrelated roof above open air.
            while (probe.getY() < level().getMaxBuildHeight() - 1
                    && !level().getBlockState(probe).getCollisionShape(level(), probe).isEmpty()) probe.move(0, 1, 0);
            return probe.getY();
        }
        return level().getMinBuildHeight() - 1;
    }

    private void updateOrientation() {
        float yaw = (float) Math.toDegrees(Math.atan2(-nose.x, nose.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(nose.y, nose.horizontalDistance()));
        setYRot(yaw);
        setXRot(pitch);
        hasImpulse = true;
    }

    private void moveWithCollision(@Nullable Entity target) {
        Vec3 start = position();
        Vec3 end = start.add(getDeltaMovement());
        if (!isPathLoaded(start, end)) {
            expire(TerminationReason.UNLOADED_PATH); // A refused/out-of-budget ticket never permits unchecked movement.
            return;
        }
        HitResult hit = level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 rayEnd = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level(), this, start, rayEnd,
                getBoundingBox().expandTowards(getDeltaMovement()).inflate(.3), this::canHit);
        if (entityHit != null) hit = entityHit;
        if (hit.getType() != HitResult.Type.MISS) {
            setPos(hit.getLocation());
            if (flightTicks > 5) detonate(); else expire(TerminationReason.UNARMED_COLLISION);
            return;
        }
        // Arm only after the main motor ignites so the ejection cannot detonate beside the vehicle.
        Vec3 targetPoint = target == null ? fixedDestination : target.getBoundingBox().getCenter();
        if (getMotorActive() && targetPoint != null && GuidanceMath.distanceToSegment(vector(targetPoint),
                vector(start), vector(end)) <= ModConfig.PROXIMITY_FUSE.get()) {
            setPos(end);
            detonate();
            return;
        }
        setPos(end);
    }

    private boolean isPathLoaded(Vec3 start, Vec3 end) {
        // Cover every chunk touched by the collision sweep, including diagonal side chunks.
        // Sampling endpoints alone can miss a corner crossing and let clip query unloaded terrain.
        AABB sweep = new AABB(start, end).inflate(.3);
        int minChunkX = Mth.floor(sweep.minX) >> 4;
        int maxChunkX = Mth.floor(sweep.maxX) >> 4;
        int minChunkZ = Mth.floor(sweep.minZ) >> 4;
        int maxChunkZ = Mth.floor(sweep.maxZ) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!level().hasChunkAt(new BlockPos(chunkX << 4, blockPosition().getY(), chunkZ << 4))) return false;
            }
        }
        return true;
    }

    private boolean canHit(Entity entity) {
        if (entity instanceof net.minecraftforge.entity.PartEntity<?> part) return canHit(part.getParent());
        if (entity == this || entity instanceof MissileEntity || entity instanceof WreckEntity
                || !entity.isAlive() || entity.isSpectator() || !entity.isPickable()) return false;
        if (entity.getUUID().equals(ownerId) || entity.getUUID().equals(launcherId)) return false;
        Entity owner = ownerId == null ? null : ((ServerLevel) level()).getEntity(ownerId);
        return !SbwCompatibility.isFriendly(this, ownerId, owner instanceof Player player ? player : null, entity);
    }

    @Nullable
    private Entity resolveTarget() {
        if (!(level() instanceof ServerLevel server) || targetId == null) return null;
        Entity entity = server.getEntity(targetId); // Loaded entities only: this lookup does not load chunks.
        if (entity == null || !entity.isAlive() || entity.isRemoved() || !canHit(entity)) return null;
        return entity;
    }

    private void detonate() {
        if (hasDetonated || level().isClientSide) return;
        hasDetonated = true;
        terminationReason = TerminationReason.DETONATED;
        transitionTo(FlightState.DETONATED);
        Entity owner = ownerId == null ? null : ((ServerLevel) level()).getEntity(ownerId);
        applyWarheadDamage(owner);
        // Keep the actual explosion for sound, particles, events and optional terrain damage.
        // Its entity list is cleared below: calibrated damage is applied exactly once above.
        double blastExtent = ModConfig.EXPLOSION_POWER.get() * 2 + 2;
        if (isPathLoaded(position().add(-blastExtent, 0, -blastExtent), position().add(blastExtent, 0, blastExtent))) {
            level().explode(this, damageSources().explosion(this, owner), null, getX(), getY(), getZ(),
                    ModConfig.EXPLOSION_POWER.get().floatValue(), false, ModConfig.BLOCK_DAMAGE.get()
                            ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
        }
        level().playSound(null, getX(), getY(), getZ(), ModSounds.MISSILE_EXPLOSION.get(), SoundSource.HOSTILE, 4f, 1f);
        ServerLevel server = (ServerLevel) level();
        ExplosionEffects.send(server, position(), ModConfig.EXPLOSION_POWER.get().floatValue());
        server.sendParticles(ParticleTypes.FLASH, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
        server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY(), getZ(), 32, 1.2, 1.2, 1.2, .08);
        discard();
    }

    private void applyWarheadDamage(@Nullable Entity owner) {
        double radius = Math.max(4, ModConfig.EXPLOSION_POWER.get() * 1.5);
        ResourceKey<DamageType> sbwExplosion = ResourceKey.create(Registries.DAMAGE_TYPE,
                new ResourceLocation("superbwarfare", "projectile_explosion"));
        DamageSource source = level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(sbwExplosion)
                .<DamageSource>map(holder -> new DamageSource(holder, this, owner))
                .orElseGet(() -> damageSources().explosion(this, owner));
        java.util.Set<UUID> damaged = new java.util.HashSet<>();
        for (Entity entity : level().getEntities(this, getBoundingBox().inflate(radius), this::canHit)) {
            AABB box = entity.getBoundingBox();
            Vec3 closest = new Vec3(Mth.clamp(getX(), box.minX, box.maxX),
                    Mth.clamp(getY(), box.minY, box.maxY), Mth.clamp(getZ(), box.minZ, box.maxZ));
            double distance = closest.distanceTo(position());
            if (distance >= radius || !isPathLoaded(position(), entity.getBoundingBox().getCenter())) continue;
            HitResult cover = level().clip(new ClipContext(position(), closest, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (cover.getType() != HitResult.Type.MISS && cover.getLocation().distanceTo(closest) > .25) continue;
            double falloff = 1 - Math.max(0, distance - ModConfig.PROXIMITY_FUSE.get())
                    / Math.max(.1, radius - ModConfig.PROXIMITY_FUSE.get());
            Entity recipient = entity instanceof net.minecraftforge.entity.PartEntity<?> part ? part.getParent() : entity;
            if (damaged.add(recipient.getUUID())) recipient.hurt(source, (float) (ModConfig.MISSILE_DAMAGE.get() * falloff));
        }
    }

    @SubscribeEvent
    public static void avoidDuplicateExplosionDamage(ExplosionEvent.Detonate event) {
        if (event.getExplosion().getDirectSourceEntity() instanceof MissileEntity) event.getAffectedEntities().clear();
    }

    private void expire(TerminationReason reason) {
        terminationReason = reason;
        if (level() instanceof ServerLevel server) server.sendParticles(ParticleTypes.POOF, getX(), getY(), getZ(), 6, .15, .15, .15, .01);
        discard();
    }

    private void transitionTo(FlightState next) {
        entityData.set(STATE, next.ordinal());
        stateTicks = 0;
    }

    private void syncCorrection(Vec3 correction) {
        entityData.set(CORRECTION_X, (float) correction.x);
        entityData.set(CORRECTION_Y, (float) correction.y);
        entityData.set(CORRECTION_Z, (float) correction.z);
    }

    private void drawFlightEffects() {
        Vec3 facing = getLookAngle();
        if (getMotorActive()) {
            // The supplied model's nose is +Z and its tail is -Z. Exhaust always
            // starts behind the tail, including downward during the initial climb.
            Vec3 exhaust = position().subtract(facing.scale(.95));
            Vec3 previous = exhaust.subtract(getDeltaMovement());
            int trailSteps = Math.max(2, Math.min(8, (int) Math.ceil(getDeltaMovement().length() * 3)));
            for (int i = 0; i < trailSteps; i++) {
                Vec3 point = previous.lerp(exhaust, (i + 1.) / trailSteps);
                // The emissive renderer supplies the flame attached to the nozzle.
                // Only cooling smoke remains along the path; detached fire sprites
                // would linger far behind the rocket like a firework trail.
                if (ClientEffectsConfig.EXHAUST_SMOKE.get()
                        && (i == trailSteps - 1 || i == trailSteps / 2 && tickCount % 2 == 0)) {
                    level().addParticle(ModParticles.EXHAUST_SMOKE.get(), point.x, point.y, point.z,
                            -facing.x * .12 + random.nextGaussian() * .015,
                            -facing.y * .12 + .012 + random.nextGaussian() * .015,
                            -facing.z * .12 + random.nextGaussian() * .015);
                }
            }
        } else if (getFlightState() == FlightState.EJECTING) {
            // The main motor stays off during cold ejection. Its compressed-gas
            // plume still leaves the rear toward the launch tube/ground.
            Vec3 exhaust = position().subtract(facing.scale(.95));
            level().addParticle(ParticleTypes.CLOUD, exhaust.x, exhaust.y, exhaust.z,
                    -facing.x * .16, -facing.y * .16, -facing.z * .16);
        }
        if (getFlightState() == FlightState.ORIENTING || getMotorActive()) {
            Vec3 correction = getCorrection();
            if (correction.lengthSqr() < 1.0e-8) return;
            for (GuidanceMath.ThrusterJet thruster : GuidanceMath.reactionJets(vector(facing), vector(correction))) {
                if (thruster.strength() < .05) continue;
                Vec3 jet = vec3(thruster.direction());
                Vec3 nozzle = position().subtract(facing.scale(.60)).add(jet.scale(.18));
                int count = (int) Math.ceil(ModConfig.THRUSTER_PARTICLES.get() * thruster.strength());
                double speed = .12 + thruster.strength() * .12;
                for (int i = 0; i < count; i++) {
                    level().addParticle(ParticleTypes.CLOUD, nozzle.x, nozzle.y, nozzle.z,
                            jet.x * speed, jet.y * speed, jet.z * speed);
                }
            }
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {
        // A client optimizer may skip entity ticks while network packets still arrive.
        // Apply the authoritative transform in that case; never simulate server physics here.
        if (level().isClientSide && (lastClientPoseTick == Long.MIN_VALUE
                || level().getGameTime() < lastClientPoseTick || level().getGameTime() - lastClientPoseTick > 2)) {
            setPos(x,y,z); setYRot(yaw); setXRot(pitch);
        }
        interpolationX = x;
        interpolationY = y;
        interpolationZ = z;
        interpolationYaw = yaw;
        interpolationPitch = pitch;
        interpolationTicks = teleport ? 1 : Math.max(1, Math.min(steps, 5));
    }

    private void interpolateClientTransform() {
        if (interpolationTicks <= 0) return;
        double fraction = 1.0 / interpolationTicks;
        setPos(Mth.lerp(fraction, getX(), interpolationX), Mth.lerp(fraction, getY(), interpolationY), Mth.lerp(fraction, getZ(), interpolationZ));
        setYRot(getYRot() + Mth.wrapDegrees(interpolationYaw - getYRot()) / interpolationTicks);
        setXRot(getXRot() + (interpolationPitch - getXRot()) / interpolationTicks);
        --interpolationTicks;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        launcherId = tag.hasUUID("Launcher") ? tag.getUUID("Launcher") : null;
        targetId = tag.hasUUID("Target") ? tag.getUUID("Target") : null;
        fixedDestination = tag.getBoolean("HasDestination")
                ? new Vec3(tag.getDouble("DestinationX"), tag.getDouble("DestinationY"), tag.getDouble("DestinationZ")) : null;
        if (!validDestination(fixedDestination)) fixedDestination = null;
        if (fixedDestination != null) targetId = null;
        lastKnownTargetPosition = tag.getBoolean("HasLastTarget")
                ? new Vec3(tag.getDouble("LastTargetX"), tag.getDouble("LastTargetY"), tag.getDouble("LastTargetZ")) : fixedDestination;
        if (!validDestination(lastKnownTargetPosition)) lastKnownTargetPosition = null;
        entityData.set(STATE, FlightState.fromId(tag.getInt("FlightState")).ordinal());
        stateTicks = Math.max(0, tag.getInt("StateTicks"));
        flightTicks = Math.max(0, tag.getInt("FlightTicks"));
        nose = new Vec3(tag.getDouble("NoseX"), tag.getDouble("NoseY"), tag.getDouble("NoseZ"));
        if (nose.lengthSqr() < .001) nose = new Vec3(0, 1, 0);
        else nose = nose.normalize();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        if (launcherId != null) tag.putUUID("Launcher", launcherId);
        if (targetId != null) tag.putUUID("Target", targetId);
        if (fixedDestination != null) {
            tag.putBoolean("HasDestination", true);
            tag.putDouble("DestinationX", fixedDestination.x);
            tag.putDouble("DestinationY", fixedDestination.y);
            tag.putDouble("DestinationZ", fixedDestination.z);
        }
        if (lastKnownTargetPosition != null) {
            tag.putBoolean("HasLastTarget", true);
            tag.putDouble("LastTargetX", lastKnownTargetPosition.x);
            tag.putDouble("LastTargetY", lastKnownTargetPosition.y);
            tag.putDouble("LastTargetZ", lastKnownTargetPosition.z);
        }
        tag.putInt("FlightState", getFlightState().ordinal());
        tag.putInt("StateTicks", stateTicks);
        tag.putInt("FlightTicks", flightTicks);
        tag.putDouble("NoseX", nose.x);
        tag.putDouble("NoseY", nose.y);
        tag.putDouble("NoseZ", nose.z);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }

    @Override
    public boolean isPickable() { return true; }

    private static GuidanceMath.Vector vector(Vec3 vector) { return new GuidanceMath.Vector(vector.x, vector.y, vector.z); }
    private static Vec3 vec3(GuidanceMath.Vector vector) { return new Vec3(vector.x(), vector.y(), vector.z()); }
    private static Vec3 turn(Vec3 from, Vec3 to, double radians) { return vec3(GuidanceMath.turnTowards(vector(from), vector(to), radians)); }
}
