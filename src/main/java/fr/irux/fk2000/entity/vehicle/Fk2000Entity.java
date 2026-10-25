package fr.irux.fk2000.entity.vehicle;

import fr.irux.fk2000.config.ModConfig;
import fr.irux.fk2000.compat.SbwCompatibility;
import fr.irux.fk2000.entity.missile.MissileEntity;
import fr.irux.fk2000.loading.FkChunkLoading;
import fr.irux.fk2000.network.ExplosionEffects;
import fr.irux.fk2000.radar.RadarContact;
import fr.irux.fk2000.radar.RadarSystem;
import fr.irux.fk2000.radar.TargetMode;
import fr.irux.fk2000.radar.TargetCoordinates;
import fr.irux.fk2000.registry.ModContent;
import fr.irux.fk2000.registry.ModSounds;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-authoritative wheeled vehicle. Horizontal velocity is physical movement,
 * so vanilla's terrain collision and step-up rules apply to the entire chassis.
 * Remote input is transient and expires; no controller can leave a vehicle driving
 * after disconnecting or leaving its loaded chunk.
 */
@Mod.EventBusSubscriber(modid = "iruxfk2000")
public class Fk2000Entity extends PathfinderMob implements OwnableEntity {
    private static final EntityDataAccessor<Optional<UUID>> OWNER =
            SynchedEntityData.defineId(Fk2000Entity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> ENGINE =
            SynchedEntityData.defineId(Fk2000Entity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> RADAR =
            SynchedEntityData.defineId(Fk2000Entity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> AUTO_ACQUIRE =
            SynchedEntityData.defineId(Fk2000Entity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> AUTO_FIRE =
            SynchedEntityData.defineId(Fk2000Entity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> MISSILES =
            SynchedEntityData.defineId(Fk2000Entity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SPEED =
            SynchedEntityData.defineId(Fk2000Entity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> LAUNCHER =
            SynchedEntityData.defineId(Fk2000Entity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> WHEEL_ROLL =
            SynchedEntityData.defineId(Fk2000Entity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> TARGET =
            SynchedEntityData.defineId(Fk2000Entity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TARGET_MODE =
            SynchedEntityData.defineId(Fk2000Entity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> AUTO_FIRE_MODE =
            SynchedEntityData.defineId(Fk2000Entity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> ENGAGEMENT_RANGE =
            SynchedEntityData.defineId(Fk2000Entity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> CAP_MODE =
            SynchedEntityData.defineId(Fk2000Entity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CAPS_PRESENT =
            SynchedEntityData.defineId(Fk2000Entity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LAST_LAUNCH_TUBE =
            SynchedEntityData.defineId(Fk2000Entity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SUSPENSION =
            SynchedEntityData.defineId(Fk2000Entity.class, EntityDataSerializers.FLOAT);
    private static final List<EntityDataAccessor<Float>> CAP_PROGRESS = List.of(
            SynchedEntityData.defineId(Fk2000Entity.class, EntityDataSerializers.FLOAT),
            SynchedEntityData.defineId(Fk2000Entity.class, EntityDataSerializers.FLOAT),
            SynchedEntityData.defineId(Fk2000Entity.class, EntityDataSerializers.FLOAT),
            SynchedEntityData.defineId(Fk2000Entity.class, EntityDataSerializers.FLOAT),
            SynchedEntityData.defineId(Fk2000Entity.class, EntityDataSerializers.FLOAT),
            SynchedEntityData.defineId(Fk2000Entity.class, EntityDataSerializers.FLOAT));

    private List<RadarContact> radarContacts = List.of();
    private UUID targetUUID;
    private Vec3 targetCoordinates;
    private final LinkedHashSet<UUID> selectedTargets = new LinkedHashSet<>();
    private boolean salvoTargetLatched;
    private float throttle;
    private float steering;
    private boolean brake = true;
    private int lastInputTick = -100;
    private int preparationTicks;
    private int launchCooldown;
    private int reloadCooldown;
    private boolean preparing;
    private boolean automaticPreparation;
    private final float[] previousCapProgress = new float[6];
    private final int[] capCloseDelay = new int[6];
    private float previousSuspension;
    private float suspensionVelocity;
    private boolean destructionStarted;

    public Fk2000Entity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setNoAi(true);
        setPersistenceRequired();
        setMaxUpStep(VehicleGeometry.MAX_STEP);
        if (!level.isClientSide) {
            entityData.set(CAP_MODE, ModConfig.TUBE_CAP_MODE.get().ordinal());
            setEngagementRange(ModConfig.RADAR_RANGE.get());
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 240.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, 12.0)
                .add(Attributes.FOLLOW_RANGE, 0.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(OWNER, Optional.empty());
        entityData.define(ENGINE, false);
        entityData.define(RADAR, false);
        entityData.define(AUTO_ACQUIRE, false);
        entityData.define(AUTO_FIRE, false);
        entityData.define(MISSILES, 0);
        entityData.define(SPEED, 0F);
        entityData.define(LAUNCHER, 0F);
        entityData.define(WHEEL_ROLL, 0F);
        entityData.define(TARGET, -1);
        entityData.define(TARGET_MODE, TargetMode.AIRCRAFT.ordinal());
        entityData.define(AUTO_FIRE_MODE, AutoFireMode.SALVO.ordinal());
        entityData.define(ENGAGEMENT_RANGE, 160F);
        entityData.define(CAP_MODE, TubeCapMode.EJECT.ordinal());
        entityData.define(CAPS_PRESENT, 0x3f);
        entityData.define(LAST_LAUNCH_TUBE, -1);
        entityData.define(SUSPENSION, 0F);
        for (var accessor : CAP_PROGRESS) entityData.define(accessor, 0F);
    }

    /** Called by our placement item, never by a client packet. */
    public void setOwnerUUID(UUID owner) {
        if (level().isClientSide || getOwnerUUID() != null) return;
        entityData.set(OWNER, Optional.of(owner));
        entityData.set(MISSILES, ModConfig.MISSILE_CAPACITY.get());
        entityData.set(RADAR, ModConfig.AUTO_RADAR.get());
        entityData.set(AUTO_ACQUIRE, ModConfig.AUTO_RADAR.get());
        entityData.set(AUTO_FIRE, ModConfig.AUTO_FIRE.get());
    }

    @Nullable
    @Override
    public UUID getOwnerUUID() { return entityData.get(OWNER).orElse(null); }

    public boolean canControl(Player player) {
        if (!isAlive() || player.isSpectator() || player.level() != level()) return false;
        UUID owner = getOwnerUUID();
        return owner != null && owner.equals(player.getUUID());
    }

    public boolean isEngineOn() { return entityData.get(ENGINE); }

    public void setEngineOn(boolean running) {
        if (level().isClientSide) return;
        entityData.set(ENGINE, running);
        if (!running) clearDriveInput();
    }

    public boolean isRadarEnabled() { return entityData.get(RADAR); }

    public void setRadarEnabled(boolean active) {
        if (level().isClientSide) return;
        entityData.set(RADAR, active);
        if (!active) {
            clearTarget();
            radarContacts = List.of();
        }
    }

    public boolean isAutoAcquire() { return entityData.get(AUTO_ACQUIRE); }
    public void setAutoAcquire(boolean active) {
        if (!level().isClientSide) entityData.set(AUTO_ACQUIRE, active);
    }

    public boolean isAutoFire() { return entityData.get(AUTO_FIRE); }
    public void setAutoFire(boolean active) {
        if (level().isClientSide) return;
        entityData.set(AUTO_FIRE, active);
        if (!active && automaticPreparation) {
            preparing = false;
            automaticPreparation = false;
            preparationTicks = 0;
        }
    }

    public int getMissileCount() { return entityData.get(MISSILES); }
    /** Signed horizontal speed in blocks per tick. */
    public float getSpeed() { return entityData.get(SPEED); }
    public float getLauncherProgress() { return entityData.get(LAUNCHER); }
    /** Cumulative wheel angle, in radians, synchronized from actual server travel. */
    public float getWheelRotation() { return entityData.get(WHEEL_ROLL); }
    public List<RadarContact> getRadarContacts() { return radarContacts; }
    public boolean isPreparingLaunch() { return preparing; }

    public TargetMode getTargetMode() {
        return TargetMode.values()[Mth.clamp(entityData.get(TARGET_MODE), 0, TargetMode.values().length - 1)];
    }

    public double getEngagementRange() { return entityData.get(ENGAGEMENT_RANGE); }

    public void setEngagementRange(double range) {
        if (level().isClientSide || !Double.isFinite(range)) return;
        entityData.set(ENGAGEMENT_RANGE, (float) Mth.clamp(range, 16, ModConfig.MAX_ENGAGEMENT_RANGE.get()));
    }

    public void setTargetMode(TargetMode mode) {
        if (level().isClientSide || mode == null || getTargetMode() == mode) return;
        clearTarget();
        selectedTargets.clear();
        salvoTargetLatched = false;
        radarContacts = List.of();
        entityData.set(TARGET_MODE, mode.ordinal());
        if (mode != TargetMode.COORDINATES) targetCoordinates = null;
        else setAutoFireMode(AutoFireMode.SALVO);
    }

    public AutoFireMode getAutoFireMode() {
        return AutoFireMode.values()[Mth.clamp(entityData.get(AUTO_FIRE_MODE), 0, AutoFireMode.values().length - 1)];
    }

    /** Changing allocation cancels the current engagement, so a stale launch cannot escape the new selection. */
    public void setAutoFireMode(AutoFireMode mode) {
        if (level().isClientSide || mode == null || getAutoFireMode() == mode
                || getTargetMode() == TargetMode.COORDINATES && mode == AutoFireMode.ONE_PER_TARGET) return;
        setAutoFire(false);
        clearTarget();
        selectedTargets.clear();
        salvoTargetLatched = false;
        entityData.set(AUTO_FIRE_MODE, mode.ordinal());
    }

    public List<Integer> getSelectedTargetIds() {
        if (getAutoFireMode() == AutoFireMode.SALVO) return getLockedTargetId() >= 0 ? List.of(getLockedTargetId()) : List.of();
        if (!(level() instanceof ServerLevel server)) return List.of();
        return selectedTargets.stream().map(server::getEntity).filter(java.util.Objects::nonNull).map(Entity::getId).toList();
    }

    @Nullable
    public Vec3 getTargetCoordinates() { return targetCoordinates; }

    public boolean setTargetCoordinates(Vec3 coordinates) {
        if (level().isClientSide || coordinates == null || !TargetCoordinates.isValid(this, coordinates)) return false;
        setTargetMode(TargetMode.COORDINATES);
        clearTarget();
        targetCoordinates = coordinates;
        return true;
    }

    public TubeCapMode getTubeCapMode() {
        return TubeCapMode.values()[Mth.clamp(entityData.get(CAP_MODE), 0, TubeCapMode.values().length - 1)];
    }

    /** Covers may be changed only while idle, never in the middle of a physical launch. */
    public void setTubeCapMode(TubeCapMode mode) {
        if (level().isClientSide || mode == null || preparing) return;
        for (var accessor : CAP_PROGRESS) if (entityData.get(accessor) > .01f) return;
        entityData.set(CAP_MODE, mode.ordinal());
        entityData.set(CAPS_PRESENT, 0x3f);
    }

    public int getCapsPresentMask() { return entityData.get(CAPS_PRESENT); }
    public int getLastLaunchTube() { return entityData.get(LAST_LAUNCH_TUBE); }

    public float getTubeCapProgress(int tube, float partialTick) {
        int selected = Math.floorMod(tube, 6);
        return Mth.lerp(Mth.clamp(partialTick, 0, 1), previousCapProgress[selected], entityData.get(CAP_PROGRESS.get(selected)));
    }

    public float getSuspensionCompression(float partialTick) {
        return Mth.lerp(Mth.clamp(partialTick, 0, 1), previousSuspension, entityData.get(SUSPENSION));
    }

    public Vec3 getDriverSeatPosition(float partialTick) {
        return VehicleGeometry.localToWorld(interpolatedPosition(partialTick),
                Mth.rotLerp(partialTick, yRotO, getYRot()), VehicleGeometry.DRIVER_SEAT);
    }

    /** First-person and remote cameras use the same cab anchor without moving the player. */
    public Vec3 getDriverEyePosition(float partialTick) {
        return VehicleGeometry.localToWorld(interpolatedPosition(partialTick),
                Mth.rotLerp(partialTick, yRotO, getYRot()), VehicleGeometry.DRIVER_EYE);
    }

    private Vec3 interpolatedPosition(float partialTick) {
        return new Vec3(Mth.lerp(partialTick, xo, getX()), Mth.lerp(partialTick, yo, getY()),
                Mth.lerp(partialTick, zo, getZ()));
    }

    @Override
    protected AABB makeBoundingBox() { return VehicleGeometry.bodyBounds(position(), getYRot()); }

    @Override
    public void setYRot(float yaw) {
        super.setYRot(yaw);
        setBoundingBox(makeBoundingBox());
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        // The elevated radar and raised launcher extend above the solid chassis.
        AABB body = getBoundingBox();
        return new AABB(body.minX, body.minY, body.minZ, body.maxX, body.minY + 8.0, body.maxZ);
    }

    public void setDriveInput(float throttle, float steering, boolean brake) {
        if (level().isClientSide || !Float.isFinite(throttle) || !Float.isFinite(steering)) return;
        this.throttle = Mth.clamp(throttle, -1F, 1F);
        this.steering = Mth.clamp(steering, -1F, 1F);
        this.brake = brake;
        lastInputTick = tickCount;
    }

    public void clearDriveInput() {
        throttle = 0;
        steering = 0;
        brake = true;
        lastInputTick = -100;
    }

    public void selectTarget(int entityId) {
        if (level().isClientSide || !isRadarEnabled() || getTargetMode() == TargetMode.COORDINATES) return;
        if (entityId < 0) {
            clearTarget();
            selectedTargets.clear();
            salvoTargetLatched = true;
            return;
        }
        // A forged entity ID must still occur in the current server-produced scan.
        Entity selected = level().getEntity(entityId);
        boolean observed = radarContacts.stream().anyMatch(contact -> contact.entityId() == entityId
                && selected != null && contact.uuid().equals(selected.getUUID()));
        if (observed && RadarSystem.canTrack(this, selected)) {
            if (getAutoFireMode() == AutoFireMode.ONE_PER_TARGET) {
                UUID id = selected.getUUID();
                if (!selectedTargets.remove(id) && selectedTargets.size() < 32) selectedTargets.add(id);
                refreshQueuedTarget();
            } else {
                if (targetUUID != null && !targetUUID.equals(selected.getUUID())) clearTarget();
                targetUUID = selected.getUUID();
                entityData.set(TARGET, selected.getId());
                salvoTargetLatched = true;
            }
        }
    }

    private void refreshQueuedTarget() {
        if (!(level() instanceof ServerLevel server)) return;
        selectedTargets.removeIf(id -> !RadarSystem.canTrack(this, server.getEntity(id)));
        UUID next = selectedTargets.isEmpty() ? null : selectedTargets.iterator().next();
        if (java.util.Objects.equals(targetUUID, next)) return;
        clearTarget();
        if (next != null) {
            Entity entity = server.getEntity(next);
            if (entity != null) {
                targetUUID = next;
                entityData.set(TARGET, entity.getId());
            }
        }
    }

    @Nullable
    public Entity getLockedTarget() {
        if (level() instanceof ServerLevel server && targetUUID != null) return server.getEntity(targetUUID);
        return entityData.get(TARGET) < 0 ? null : level().getEntity(entityData.get(TARGET));
    }

    public int getLockedTargetId() { return entityData.get(TARGET); }

    private void clearTarget() {
        targetUUID = null;
        entityData.set(TARGET, -1);
        preparing = false;
        automaticPreparation = false;
        preparationTicks = 0;
    }

    public boolean requestLaunch() {
        return beginLaunch(false);
    }

    private boolean beginLaunch(boolean automatic) {
        if (level().isClientSide || !isRadarEnabled() || preparing || launchCooldown > 0
                || getMissileCount() <= 0 || !hasValidFireTarget()) return false;
        // Cold launch needs a stable platform; driving commands automatically brake during preparation.
        if (Math.abs(getSpeed()) > 0.08F) return false;
        preparing = true;
        automaticPreparation = automatic;
        // An already raised automatic launcher can fire without folding down
        // and repeating the entire mechanical deployment for every missile.
        preparationTicks = Math.round(getLauncherProgress() * ModConfig.PREPARATION_TICKS.get());
        if (getTubeCapMode() == TubeCapMode.EJECT) {
            // The six ready tubes can be replenished internally from a larger configured magazine.
            entityData.set(CAPS_PRESENT, getCapsPresentMask() | (1 << nextLaunchTube()));
        }
        clearDriveInput();
        return true;
    }

    private boolean hasValidFireTarget() {
        return getTargetMode() == TargetMode.COORDINATES
                ? targetCoordinates != null && TargetCoordinates.isValid(this, targetCoordinates)
                : RadarSystem.canTrack(this, getLockedTarget());
    }

    private int nextLaunchTube() { return Math.floorMod(ModConfig.MISSILE_CAPACITY.get() - getMissileCount(), 6); }

    /** Reload exactly one physical missile item. Repeated packets cannot duplicate ammunition. */
    public void reload(Player player) {
        if (level().isClientSide || !canControl(player) || player.distanceToSqr(this) > 64.0
                || preparing || reloadCooldown > 0 || getMissileCount() >= ModConfig.MISSILE_CAPACITY.get()) return;
        if (!player.getAbilities().instabuild) {
            ItemStack missile = ItemStack.EMPTY;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (stack.is(ModContent.MISSILE_ITEM.get())) { missile = stack; break; }
            }
            if (missile.isEmpty()) return;
            missile.shrink(1);
            player.getInventory().setChanged();
        }
        entityData.set(MISSILES, getMissileCount() + 1);
        entityData.set(CAPS_PRESENT, getCapsPresentMask() | (1 << nextLaunchTube()));
        reloadCooldown = ModConfig.RELOAD_TICKS.get();
        level().playSound(null, blockPosition(), ModSounds.RELOAD.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
    }

    @Override
    public void tick() {
        previousSuspension = entityData.get(SUSPENSION);
        for (int tube = 0; tube < 6; tube++) previousCapProgress[tube] = entityData.get(CAP_PROGRESS.get(tube));
        super.tick();
        if (!level().isClientSide && isAlive()) {
            FkChunkLoading.refreshVehicle(this);
            if (launchCooldown > 0) launchCooldown--;
            if (reloadCooldown > 0) reloadCooldown--;
            tickDrive();
            tickRadar();
            tickLauncher();
            tickLaunchMechanics();
        }
        // Keep the renderer and mounted passenger oriented with the chassis.
        yBodyRot = getYRot();
        yHeadRot = getYRot();
    }

    @Override
    public void travel(Vec3 ignored) {
        // Mob AI and LivingEntity locomotion must not apply an additional movement step.
        // Vehicle movement is integrated once, by tickDrive on the logical server.
    }

    private void tickDrive() {
        if (tickCount - lastInputTick > 10 || !isEngineOn() || preparing) {
            throttle = 0;
            steering = 0;
            brake = true;
        }
        double speed = getSpeed();
        double acceleration = ModConfig.VEHICLE_ACCELERATION.get();
        double braking = ModConfig.BRAKING.get();
        double maxSpeed = ModConfig.VEHICLE_MAX_SPEED.get();
        if (brake) speed = approachZero(speed, braking);
        else if (Math.abs(throttle) > 0.001F) {
            if (Math.signum(speed) != Math.signum(throttle) && Math.abs(speed) > 0.01) {
                speed = approachZero(speed, braking);
            } else speed += throttle * acceleration;
        } else speed = approachZero(speed, acceleration * 0.3);
        speed = Mth.clamp(speed, -maxSpeed * 0.45, maxSpeed);
        if (isInWaterOrBubble()) speed *= 0.85;
        float rotation = (float) (steering * Math.min(3.0, Math.abs(speed) * 9.0) * Math.signum(speed));
        float nextYaw = getYRot() + rotation;
        AABB nextBounds = VehicleGeometry.bodyBounds(position(), nextYaw);
        // Turning cannot sweep the long rear chassis through a cliff or a wall.
        if (rotation != 0 && isLoadedFootprint(nextBounds)
                && level().noCollision(this, nextBounds.deflate(1.0E-5))) {
            setYRot(nextYaw);
        }
        double yaw = Math.toRadians(getYRot());
        Vec3 oldMotion = getDeltaMovement();
        double vertical = isNoGravity() ? oldMotion.y : Math.max(-1.5, oldMotion.y - 0.08);
        if (onGround() && vertical < 0) vertical = -0.08;
        Vec3 motion = new Vec3(-Math.sin(yaw) * speed, vertical, Math.cos(yaw) * speed);
        if (!isLoadedFootprint(getBoundingBox().expandTowards(motion).inflate(0, VehicleGeometry.MAX_STEP, 0))) {
            // A remote command never creates tickets or pulls neighboring chunks into memory.
            // Check before vanilla collision queries, which may otherwise request terrain.
            speed = 0;
            motion = new Vec3(0, vertical, 0);
            clearDriveInput();
        }
        setDeltaMovement(motion);
        double oldX = getX();
        double oldZ = getZ();
        move(MoverType.SELF, motion);
        if (horizontalCollision) {
            double actualSpeed = Math.sqrt((getX() - oldX) * (getX() - oldX) + (getZ() - oldZ) * (getZ() - oldZ));
            speed = Math.copySign(Math.min(Math.abs(speed), actualSpeed), speed);
        }
        if (verticalCollision) setDeltaMovement(getDeltaMovement().multiply(1, 0, 1));
        entityData.set(SPEED, (float) speed);
        double traveled = Math.sqrt((getX() - oldX) * (getX() - oldX) + (getZ() - oldZ) * (getZ() - oldZ));
        float wheelAngle = entityData.get(WHEEL_ROLL) + (float) (Math.copySign(traveled, speed) / VehicleGeometry.WHEEL_RADIUS);
        entityData.set(WHEEL_ROLL, (float) (wheelAngle % (2 * Math.PI)));
        hasImpulse = true;
    }

    private static double approachZero(double value, double amount) {
        return value > 0 ? Math.max(0, value - amount) : Math.min(0, value + amount);
    }

    private boolean isLoadedFootprint(AABB bounds) {
        return level().hasChunkAt(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ))
                && level().hasChunkAt(BlockPos.containing(bounds.minX, bounds.minY, bounds.maxZ - 1.0E-6))
                && level().hasChunkAt(BlockPos.containing(bounds.maxX - 1.0E-6, bounds.minY, bounds.minZ))
                && level().hasChunkAt(BlockPos.containing(bounds.maxX - 1.0E-6, bounds.minY, bounds.maxZ - 1.0E-6));
    }

    private void tickRadar() {
        if (!isRadarEnabled()) return;
        if (getAutoFireMode() == AutoFireMode.ONE_PER_TARGET) refreshQueuedTarget();
        if (getTargetMode() == TargetMode.COORDINATES) {
            radarContacts = List.of();
            if (targetCoordinates != null && !TargetCoordinates.isValid(this, targetCoordinates)) targetCoordinates = null;
        }
        Entity target = getLockedTarget();
        if (target != null && !RadarSystem.canTrack(this, target)) clearTarget();
        if (targetUUID != null && target == null) clearTarget();
        if (getTargetMode() != TargetMode.COORDINATES && tickCount % RadarSystem.SCAN_INTERVAL == 0) {
            radarContacts = RadarSystem.scan(this);
            if (getAutoFireMode() == AutoFireMode.SALVO && !salvoTargetLatched
                    && isAutoAcquire() && targetUUID == null && !radarContacts.isEmpty()) {
                selectTarget(radarContacts.get(0).entityId());
            }
        }
        // IFF requires an online owner for autonomous engagement; unknown team data never fires.
        if (isAutoFire() && getOwnerUUID() != null && level().getPlayerByUUID(getOwnerUUID()) != null) beginLaunch(true);
    }

    private void tickLauncher() {
        float current = getLauncherProgress();
        int duration = Math.max(1, ModConfig.PREPARATION_TICKS.get());
        if (preparing) {
            Entity target = getLockedTarget();
            if (!isRadarEnabled() || !hasValidFireTarget() || getMissileCount() <= 0) {
                preparing = false;
                automaticPreparation = false;
                preparationTicks = 0;
            } else {
                preparationTicks++;
                entityData.set(LAUNCHER, Math.min(1F, preparationTicks / (float) duration));
                if (preparationTicks >= duration) fireMissile(target);
            }
        } else if (current > 0 && !shouldKeepLauncherRaised()) {
            entityData.set(LAUNCHER, Math.max(0, current - 1F / duration));
        }
    }

    private boolean shouldKeepLauncherRaised() {
        return isAutoFire() && isRadarEnabled() && getMissileCount() > 0
                && Math.abs(getSpeed()) <= 0.08F && getOwnerUUID() != null
                && level().getPlayerByUUID(getOwnerUUID()) != null
                && hasValidFireTarget();
    }

    private void fireMissile(@Nullable Entity target) {
        MissileEntity missile = ModContent.MISSILE.get().create(level());
        if (missile != null) {
            int tube = nextLaunchTube();
            Vec3 mouth = VehicleGeometry.localToWorld(position(), getYRot(), VehicleGeometry.tubeMuzzleLocal(tube, 1));
            missile.moveTo(mouth.x, mouth.y, mouth.z, getYRot(), -90);
            if (getTargetMode() == TargetMode.COORDINATES && targetCoordinates != null) {
                missile.launchAt(getOwnerUUID(), getUUID(), targetCoordinates);
            } else if (target != null) missile.launch(getOwnerUUID(), getUUID(), target);
            else return;
            if (level().addFreshEntity(missile)) {
                animateLaunch(tube, mouth);
                entityData.set(MISSILES, getMissileCount() - 1);
                level().playSound(null, blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.HOSTILE, 1.0F, 0.6F);
                launchCooldown = isAutoFire() ? ModConfig.AUTO_FIRE_INTERVAL_TICKS.get() : Math.max(durationForCooldown(), 20);
                if (getAutoFireMode() == AutoFireMode.ONE_PER_TARGET && target != null) {
                    selectedTargets.remove(target.getUUID());
                    refreshQueuedTarget();
                }
            }
        }
        preparing = false;
        automaticPreparation = false;
        preparationTicks = 0;
    }

    private int durationForCooldown() { return ModConfig.PREPARATION_TICKS.get() + 20; }

    private void animateLaunch(int tube, Vec3 mouth) {
        entityData.set(LAST_LAUNCH_TUBE, tube);
        suspensionVelocity = Math.min(.8F, suspensionVelocity + .6F);
        if (getTubeCapMode() == TubeCapMode.EJECT) {
            TubeCapEntity cap = ModContent.TUBE_CAP.get().create(level());
            if (cap != null) {
                cap.moveTo(mouth.x, mouth.y + .15, mouth.z, getYRot(), 0);
                cap.launch(new Vec3((random.nextDouble() - .5) * .10, 1.6, (random.nextDouble() - .5) * .10));
                level().addFreshEntity(cap);
            }
            entityData.set(CAPS_PRESENT, getCapsPresentMask() & ~(1 << tube));
        } else if (getTubeCapMode() == TubeCapMode.HINGED) {
            entityData.set(CAP_PROGRESS.get(tube), 1F);
            capCloseDelay[tube] = 4;
        }
    }

    private void tickLaunchMechanics() {
        float compression = entityData.get(SUSPENSION);
        suspensionVelocity = (suspensionVelocity - compression * .20F) * .70F;
        compression = Mth.clamp(compression + suspensionVelocity, 0, 1);
        if (compression < .001F && Math.abs(suspensionVelocity) < .005F || compression == 0 && suspensionVelocity < 0) {
            compression = 0;
            suspensionVelocity = 0;
        }
        entityData.set(SUSPENSION, compression);
        int activeTube = nextLaunchTube();
        int duration = ModConfig.PREPARATION_TICKS.get();
        for (int tube = 0; tube < 6; tube++) {
            float open = entityData.get(CAP_PROGRESS.get(tube));
            if (getTubeCapMode() == TubeCapMode.HINGED && preparing && tube == activeTube) {
                open = Math.max(open, Mth.clamp((preparationTicks - (duration - 8)) / 8F, 0, 1));
            } else if (capCloseDelay[tube] > 0) capCloseDelay[tube]--;
            else open = Math.max(0, open - .10F);
            entityData.set(CAP_PROGRESS.get(tube), open);
        }
    }

    @Override
    public void die(DamageSource source) {
        if (destructionStarted) return;
        super.die(source);
        destructionStarted = true;
        if (!(level() instanceof ServerLevel server)) return;
        clearDriveInput();
        setEngineOn(false);
        setRadarEnabled(false);
        setAutoFire(false);
        entityData.set(SPEED, 0F);
        for (Entity passenger : List.copyOf(getPassengers())) {
            Vec3 exit = passenger instanceof LivingEntity living ? getDismountLocationForPassenger(living)
                    : position().add(VehicleGeometry.BODY_WIDTH + 1, 0, 0);
            passenger.stopRiding();
            passenger.teleportTo(exit.x, exit.y, exit.z);
        }
        WreckEntity wreck = ModContent.WRECK.get().create(server);
        if (wreck != null) {
            wreck.initializeFrom(this);
            server.addFreshEntity(wreck);
        }
        float power = ModConfig.VEHICLE_EXPLOSION_POWER.get().floatValue();
        applyDestructionDamage(power);
        server.explode(this, damageSources().explosion(this, getOwner()), null,
                getX(), getY() + 1, getZ(), power, false,
                ModConfig.BLOCK_DAMAGE.get() ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
        server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.5, getZ(), 48, 2.2, 1, 3.5, .09);
        server.playSound(null, getX(), getY(), getZ(), ModSounds.MISSILE_EXPLOSION.get(), SoundSource.HOSTILE, 3F, .65F);
        ExplosionEffects.send(server, position().add(0, 1, 0), power);
        discard();
    }

    private void applyDestructionDamage(float power) {
        double radius = Math.max(3, power * 2);
        Entity owner = getOwner();
        DamageSource blast = damageSources().explosion(this, owner);
        for (Entity entity : level().getEntities(this, getBoundingBox().inflate(radius))) {
            if (!entity.isAlive() || entity.isSpectator() || entity instanceof WreckEntity || entity instanceof TubeCapEntity
                    || SbwCompatibility.isFriendly(this, getOwnerUUID(), owner instanceof Player player ? player : null, entity)) continue;
            Vec3 center = entity.getBoundingBox().getCenter();
            double distance = center.distanceTo(getBoundingBox().getCenter());
            if (distance >= radius) continue;
            Vec3 origin = position().add(0, 1.5, 0);
            if (!isLoadedFootprint(new AABB(origin, center))) continue;
            var hit = level().clip(new net.minecraft.world.level.ClipContext(origin, center,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, this));
            if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) continue;
            entity.hurt(blast, (float) (ModConfig.VEHICLE_EXPLOSION_DAMAGE.get() * (1 - distance / radius)));
        }
    }

    /** The calibrated, IFF-filtered damage above replaces the vanilla entity damage exactly once. */
    @SubscribeEvent
    public static void protectAlliesFromDestruction(ExplosionEvent.Detonate event) {
        if (event.getExplosion().getDirectSourceEntity() instanceof Fk2000Entity vehicle && vehicle.destructionStarted)
            event.getAffectedEntities().clear();
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (getOwnerUUID() == null && !level().isClientSide) setOwnerUUID(player.getUUID());
        if (!canControl(player)) return InteractionResult.FAIL;
        ItemStack held = player.getItemInHand(hand);
        if (held.is(ModContent.MISSILE_ITEM.get())) {
            if (!player.isShiftKeyDown()) return InteractionResult.PASS;
            if (!level().isClientSide) reload(player);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        // Nonempty utility items, especially the remote, retain their own interactLivingEntity handler.
        if (!held.isEmpty()) return InteractionResult.PASS;
        if (!level().isClientSide && !player.isShiftKeyDown()) {
            setEngineOn(true);
            player.startRiding(this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) { return getPassengers().isEmpty() && passenger instanceof Player; }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        // Deliberately suppress vanilla client vehicle-position packets. Riding input uses
        // the same validated DRIVE messages as the remote; only this entity moves itself.
        return null;
    }

    @Override
    public double getPassengersRidingOffset() { return VehicleGeometry.DRIVER_SEAT.y; }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction movement) {
        if (!hasPassenger(passenger)) return;
        Vec3 seat = VehicleGeometry.localToWorld(position(), getYRot(), VehicleGeometry.DRIVER_SEAT);
        movement.accept(passenger, seat.x, seat.y, seat.z);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        // Exit beside the front door rather than inside the long vehicle hitbox.
        for (double side : new double[] {-1, 1}) {
            Vec3 door = VehicleGeometry.localToWorld(position(), getYRot(),
                    new Vec3(side * (VehicleGeometry.BODY_WIDTH * 0.5 + 0.85), 0, VehicleGeometry.DRIVER_SEAT.z));
            for (int dy = 2; dy >= -3; dy--) {
                Vec3 exit = new Vec3(door.x, getY() + dy, door.z);
                BlockPos floor = BlockPos.containing(exit).below();
                if (!level().hasChunkAt(floor)) continue;
                if (level().getBlockState(floor).getCollisionShape(level(), floor).isEmpty()) continue;
                if (level().noCollision(passenger, passenger.getDimensions(Pose.STANDING).makeBoundingBox(exit))) return exit;
            }
        }
        return position().add(0, VehicleGeometry.BODY_HEIGHT + 0.1, 0);
    }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean canBeCollidedWith() { return isAlive(); }

    @Override
    public boolean removeWhenFarAway(double distance) { return false; }

    @Override
    public void checkDespawn() { /* Vehicle ownership persists even without nearby players. */ }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (getOwnerUUID() != null) tag.putUUID("Owner", getOwnerUUID());
        tag.putBoolean("Radar", isRadarEnabled());
        tag.putBoolean("AutoAcquire", isAutoAcquire());
        tag.putBoolean("AutoFire", isAutoFire());
        tag.putInt("Missiles", getMissileCount());
        tag.putInt("ReloadCooldown", reloadCooldown);
        tag.putInt("LaunchCooldown", launchCooldown);
        tag.putString("TubeCapMode", getTubeCapMode().name());
        tag.putInt("CapsPresent", getCapsPresentMask());
        tag.putString("TargetMode", getTargetMode().name());
        tag.putString("AutoFireMode", getAutoFireMode().name());
        tag.putDouble("EngagementRange", getEngagementRange());
        tag.putBoolean("SalvoTargetLatched", salvoTargetLatched);
        if (targetCoordinates != null) {
            tag.putDouble("TargetX", targetCoordinates.x);
            tag.putDouble("TargetY", targetCoordinates.y);
            tag.putDouble("TargetZ", targetCoordinates.z);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(OWNER, tag.hasUUID("Owner") ? Optional.of(tag.getUUID("Owner")) : Optional.empty());
        entityData.set(RADAR, tag.getBoolean("Radar"));
        entityData.set(AUTO_ACQUIRE, tag.getBoolean("AutoAcquire"));
        entityData.set(AUTO_FIRE, tag.getBoolean("AutoFire"));
        entityData.set(MISSILES, Mth.clamp(tag.getInt("Missiles"), 0, ModConfig.MISSILE_CAPACITY.get()));
        reloadCooldown = Mth.clamp(tag.getInt("ReloadCooldown"), 0, ModConfig.RELOAD_TICKS.get());
        launchCooldown = Mth.clamp(tag.getInt("LaunchCooldown"), 0,
                Math.max(durationForCooldown(), ModConfig.AUTO_FIRE_INTERVAL_TICKS.get()));
        try { entityData.set(CAP_MODE, TubeCapMode.valueOf(tag.getString("TubeCapMode")).ordinal()); }
        catch (IllegalArgumentException ignored) { entityData.set(CAP_MODE, ModConfig.TUBE_CAP_MODE.get().ordinal()); }
        entityData.set(CAPS_PRESENT, tag.contains("CapsPresent") ? tag.getInt("CapsPresent") & 0x3f : 0x3f);
        try { entityData.set(TARGET_MODE, TargetMode.valueOf(tag.getString("TargetMode")).ordinal()); }
        catch (IllegalArgumentException ignored) { entityData.set(TARGET_MODE, TargetMode.AIRCRAFT.ordinal()); }
        try { entityData.set(AUTO_FIRE_MODE, AutoFireMode.valueOf(tag.getString("AutoFireMode")).ordinal()); }
        catch (IllegalArgumentException ignored) { entityData.set(AUTO_FIRE_MODE, AutoFireMode.SALVO.ordinal()); }
        if (getTargetMode() == TargetMode.COORDINATES) entityData.set(AUTO_FIRE_MODE, AutoFireMode.SALVO.ordinal());
        selectedTargets.clear();
        salvoTargetLatched = tag.getBoolean("SalvoTargetLatched");
        double engagementRange = tag.contains("EngagementRange") ? tag.getDouble("EngagementRange") : ModConfig.RADAR_RANGE.get();
        setEngagementRange(Double.isFinite(engagementRange) ? engagementRange : ModConfig.RADAR_RANGE.get());
        targetCoordinates = tag.contains("TargetX") && tag.contains("TargetY") && tag.contains("TargetZ")
                ? new Vec3(tag.getDouble("TargetX"), tag.getDouble("TargetY"), tag.getDouble("TargetZ")) : null;
        if (targetCoordinates != null && (!Double.isFinite(targetCoordinates.x) || !Double.isFinite(targetCoordinates.y)
                || !Double.isFinite(targetCoordinates.z) || getTargetMode() != TargetMode.COORDINATES)) targetCoordinates = null;
        // Loading never resumes movement, partial launches, or a stale lock.
        clearDriveInput();
        setEngineOn(false);
        clearTarget();
        entityData.set(SPEED, 0F);
        entityData.set(LAUNCHER, 0F);
        entityData.set(SUSPENSION, 0F);
        suspensionVelocity = 0;
        previousSuspension = 0;
        for (int tube = 0; tube < 6; tube++) {
            entityData.set(CAP_PROGRESS.get(tube), 0F);
            previousCapProgress[tube] = 0;
            capCloseDelay[tube] = 0;
        }
    }
}
