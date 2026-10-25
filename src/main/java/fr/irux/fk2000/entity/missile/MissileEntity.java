package fr.irux.fk2000.entity.missile;

import fr.irux.fk2000.compat.SbwCompatibility;
import fr.irux.fk2000.config.ModConfig;
import fr.irux.fk2000.physics.GuidanceMath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.UUID;

/** Server-authoritative cold-launch missile. Clients only interpolate transforms and draw effects. */
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
    private Vec3 nose = new Vec3(0, 1, 0);
    private int stateTicks;
    private int flightTicks;
    private int lostTargetTicks;
    private int interpolationTicks;
    private double interpolationX, interpolationY, interpolationZ;
    private float interpolationYaw, interpolationPitch;
    private boolean hasDetonated;
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
        nose = new Vec3(0, 1, 0);
        setXRot(-90);
        transitionTo(FlightState.PREPARING);
    }

    public FlightState getFlightState() { return FlightState.fromId(entityData.get(STATE)); }
    public boolean getMotorActive() { return getFlightState().motorActive(); }
    public TerminationReason getTerminationReason() { return terminationReason; }
    public Vec3 getCorrection() {
        return new Vec3(entityData.get(CORRECTION_X), entityData.get(CORRECTION_Y), entityData.get(CORRECTION_Z));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            interpolateClientTransform();
            drawFlightEffects();
            return;
        }
        if (getFlightState() == FlightState.IDLE) {
            if (tickCount > 20) expire(TerminationReason.INVALID_SPAWN);
            return;
        }
        if (++flightTicks > ModConfig.MISSILE_LIFETIME.get()) {
            expire(TerminationReason.LIFETIME);
            return;
        }
        Entity target = resolveTarget();
        if (target == null) {
            if (++lostTargetTicks > LOST_TARGET_GRACE_TICKS) {
                expire(TerminationReason.TARGET_LOST);
                return;
            }
        } else {
            lostTargetTicks = 0;
        }
        ++stateTicks;
        updatePhysics(target);
        if (isRemoved() || getFlightState() == FlightState.PREPARING) return;
        updateOrientation();
        moveWithCollision(target);
        if (getMotorActive() && flightTicks % 8 == 0) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.HOSTILE, 0.8f, 1.4f);
        }
    }

    private void updatePhysics(@Nullable Entity target) {
        switch (getFlightState()) {
            case PREPARING -> {
                if (stateTicks >= 1) {
                    setDeltaMovement(0, ModConfig.EJECTION_SPEED.get(), 0);
                    transitionTo(FlightState.EJECTING);
                    level().playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.7f, 1.7f);
                    ((ServerLevel) level()).sendParticles(ParticleTypes.CLOUD, getX(), getY(), getZ(), 12, .22, .08, .22, .03);
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
        Vec3 wanted = target == null ? nose : aimDirection(target);
        double radians = Math.PI / Math.max(1, ModConfig.ORIENTATION_TICKS.get());
        nose = turn(nose, wanted, radians);
        // Cold-gas side jets change attitude and impart a small, opposite visible exhaust impulse.
        Vec3 correction = nose.subtract(previousNose);
        syncCorrection(correction);
        setDeltaMovement(getDeltaMovement().scale(0.985).add(correction.scale(0.08)).add(0, -0.025, 0));
        if (stateTicks >= ModConfig.ORIENTATION_TICKS.get()
                && (target == null || nose.dot(wanted) > Math.cos(Math.toRadians(12)))) {
            transitionTo(FlightState.BOOSTING);
            syncCorrection(Vec3.ZERO);
            level().playSound(null, blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 2f, .7f);
        }
    }

    private void guideUnderPower(@Nullable Entity target) {
        if (target != null) nose = turn(nose, aimDirection(target), Math.toRadians(ModConfig.MISSILE_TURN_RATE.get()));
        double speed = Math.min(ModConfig.MISSILE_MAX_SPEED.get(), getDeltaMovement().length() + ModConfig.MISSILE_ACCELERATION.get());
        // Accelerate along the engine axis while banking the actual trajectory at the same bounded rate.
        Vec3 velocityDirection = turn(getDeltaMovement().normalize(), nose, Math.toRadians(ModConfig.MISSILE_TURN_RATE.get()));
        setDeltaMovement(velocityDirection.scale(speed));
        if (getFlightState() == FlightState.BOOSTING && stateTicks >= BOOST_TICKS) transitionTo(FlightState.GUIDING);
    }

    private Vec3 aimDirection(Entity target) {
        Vec3 center = target.getBoundingBox().getCenter();
        GuidanceMath.Vector intercept = GuidanceMath.intercept(vector(position()), vector(center), vector(target.getDeltaMovement()),
                Math.max(getDeltaMovement().length(), 0.6), 60);
        return vec3(intercept).subtract(position()).normalize();
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
            expire(TerminationReason.UNLOADED_PATH); // Never force chunks to remain loaded or load a remote target chunk.
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
        if (getMotorActive() && target != null && GuidanceMath.distanceToSegment(vector(target.getBoundingBox().getCenter()),
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
        if (entity == this || entity instanceof MissileEntity || !entity.isAlive() || entity.isSpectator() || !entity.isPickable()) return false;
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
        level().explode(owner == null ? this : owner, getX(), getY(), getZ(),
                ModConfig.EXPLOSION_POWER.get().floatValue(), ModConfig.BLOCK_DAMAGE.get()
                        ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
        discard();
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
            Vec3 exhaust = position().subtract(facing.scale(.7));
            level().addParticle(ParticleTypes.FLAME, exhaust.x, exhaust.y, exhaust.z, -facing.x * .15, -facing.y * .15, -facing.z * .15);
            if (tickCount % 2 == 0) level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, exhaust.x, exhaust.y, exhaust.z,
                    -facing.x * .04, -facing.y * .04, -facing.z * .04);
        } else if (getFlightState() == FlightState.ORIENTING) {
            Vec3 correction = getCorrection();
            if (correction.lengthSqr() < 1.0e-8) return;
            Vec3 jet = correction.normalize().scale(-1);
            Vec3 nozzle = position().add(facing.scale(.4)).add(jet.scale(.18));
            int count = ModConfig.THRUSTER_PARTICLES.get();
            for (int i = 0; i < count; i++) {
                level().addParticle(ParticleTypes.END_ROD, nozzle.x, nozzle.y, nozzle.z, jet.x * .12, jet.y * .12, jet.z * .12);
            }
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {
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
