package fr.irux.fk2000.entity.vehicle;

import fr.irux.fk2000.config.ModConfig;
import fr.irux.fk2000.entity.missile.MissileEntity;
import fr.irux.fk2000.radar.RadarContact;
import fr.irux.fk2000.radar.RadarSystem;
import fr.irux.fk2000.registry.ModContent;
import fr.irux.fk2000.registry.ModSounds;
import net.minecraft.core.BlockPos;
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

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-authoritative wheeled vehicle. Horizontal velocity is physical movement,
 * so vanilla's terrain collision and step-up rules apply to the entire chassis.
 * Remote input is transient and expires; no controller can leave a vehicle driving
 * after disconnecting or leaving its loaded chunk.
 */
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

    private List<RadarContact> radarContacts = List.of();
    private UUID targetUUID;
    private float throttle;
    private float steering;
    private boolean brake = true;
    private int lastInputTick = -100;
    private int preparationTicks;
    private int launchCooldown;
    private int reloadCooldown;
    private boolean preparing;
    private boolean automaticPreparation;

    public Fk2000Entity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setNoAi(true);
        setPersistenceRequired();
        setMaxUpStep(VehicleGeometry.MAX_STEP);
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
        if (level().isClientSide || !isRadarEnabled()) return;
        if (entityId < 0) { clearTarget(); return; }
        // A forged entity ID must still occur in the current server-produced scan.
        Entity selected = level().getEntity(entityId);
        boolean observed = radarContacts.stream().anyMatch(contact -> contact.entityId() == entityId
                && selected != null && contact.uuid().equals(selected.getUUID()));
        if (observed && RadarSystem.canTrack(this, selected)) {
            targetUUID = selected.getUUID();
            entityData.set(TARGET, selected.getId());
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
                || getMissileCount() <= 0 || !RadarSystem.canTrack(this, getLockedTarget())) return false;
        // Cold launch needs a stable platform; driving commands automatically brake during preparation.
        if (Math.abs(getSpeed()) > 0.08F) return false;
        preparing = true;
        automaticPreparation = automatic;
        // An already raised automatic launcher can fire without folding down
        // and repeating the entire mechanical deployment for every missile.
        preparationTicks = Math.round(getLauncherProgress() * ModConfig.PREPARATION_TICKS.get());
        clearDriveInput();
        return true;
    }

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
        reloadCooldown = ModConfig.RELOAD_TICKS.get();
        level().playSound(null, blockPosition(), ModSounds.RELOAD.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && isAlive()) {
            if (launchCooldown > 0) launchCooldown--;
            if (reloadCooldown > 0) reloadCooldown--;
            tickDrive();
            tickRadar();
            tickLauncher();
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
        Entity target = getLockedTarget();
        if (target != null && !RadarSystem.canTrack(this, target)) clearTarget();
        if (targetUUID != null && target == null) clearTarget();
        if (tickCount % RadarSystem.SCAN_INTERVAL == 0) {
            radarContacts = RadarSystem.scan(this);
            if (isAutoAcquire() && targetUUID == null && !radarContacts.isEmpty()) {
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
            if (!isRadarEnabled() || !RadarSystem.canTrack(this, target) || getMissileCount() <= 0) {
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
                && RadarSystem.canTrack(this, getLockedTarget());
    }

    private void fireMissile(Entity target) {
        MissileEntity missile = ModContent.MISSILE.get().create(level());
        if (missile != null) {
            int tube = (ModConfig.MISSILE_CAPACITY.get() - getMissileCount()) % 6;
            double yaw = Math.toRadians(getYRot());
            double lateral = (tube % 3 - 1) * 0.33 * VehicleGeometry.SCALE;
            double longitudinal = (tube < 3 ? -2.75 : -3.38) * VehicleGeometry.SCALE;
            double x = getX() + Math.cos(yaw) * lateral - Math.sin(yaw) * longitudinal;
            double z = getZ() + Math.sin(yaw) * lateral + Math.cos(yaw) * longitudinal;
            missile.moveTo(x, getY() + 3.36 * VehicleGeometry.SCALE, z, getYRot(), -90);
            missile.launch(getOwnerUUID(), getUUID(), target);
            if (level().addFreshEntity(missile)) {
                entityData.set(MISSILES, getMissileCount() - 1);
                level().playSound(null, blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.HOSTILE, 1.0F, 0.6F);
                launchCooldown = isAutoFire() ? ModConfig.AUTO_FIRE_INTERVAL_TICKS.get() : Math.max(durationForCooldown(), 20);
            }
        }
        preparing = false;
        automaticPreparation = false;
        preparationTicks = 0;
    }

    private int durationForCooldown() { return ModConfig.PREPARATION_TICKS.get() + 20; }

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
        // Loading never resumes movement, partial launches, or a stale lock.
        clearDriveInput();
        setEngineOn(false);
        clearTarget();
        entityData.set(SPEED, 0F);
        entityData.set(LAUNCHER, 0F);
    }
}
