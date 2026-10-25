package fr.irux.fk2000.entity.vehicle;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/** A saved physical carcass, deliberately separate from the controllable, chunk-loading vehicle. */
public final class WreckEntity extends Entity implements OwnableEntity {
    private static final TagKey<Item> CROWBARS = TagKey.create(Registries.ITEM,
            new ResourceLocation("forge", "tools/crowbar"));
    private static final EntityDataAccessor<Optional<UUID>> OWNER =
            SynchedEntityData.defineId(WreckEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Float> LAUNCHER =
            SynchedEntityData.defineId(WreckEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> WHEELS =
            SynchedEntityData.defineId(WreckEntity.class, EntityDataSerializers.FLOAT);

    public WreckEntity(EntityType<? extends WreckEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(OWNER, Optional.empty());
        entityData.define(LAUNCHER, 0F);
        entityData.define(WHEELS, 0F);
    }

    /** Called before addFreshEntity, so the spawn packet carries the destroyed truck's final pose. */
    public void initializeFrom(Fk2000Entity vehicle) {
        if (level().isClientSide) return;
        moveTo(vehicle.getX(), vehicle.getY(), vehicle.getZ(), vehicle.getYRot(), 0);
        entityData.set(OWNER, Optional.ofNullable(vehicle.getOwnerUUID()));
        entityData.set(LAUNCHER, Mth.clamp(vehicle.getLauncherProgress(), 0, 1));
        entityData.set(WHEELS, vehicle.getWheelRotation());
        setDeltaMovement(0, Math.min(0, vehicle.getDeltaMovement().y), 0);
    }

    @Nullable
    @Override
    public UUID getOwnerUUID() { return entityData.get(OWNER).orElse(null); }

    public boolean isWreck() { return true; }
    public float getLauncherProgress() { return entityData.get(LAUNCHER); }
    public float getWheelRotation() { return entityData.get(WHEELS); }

    @Override
    protected AABB makeBoundingBox() { return VehicleGeometry.bodyBounds(position(), getYRot()); }

    @Override
    public void setYRot(float yaw) {
        super.setYRot(yaw);
        setBoundingBox(makeBoundingBox());
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        AABB body = getBoundingBox();
        return new AABB(body.minX, body.minY, body.minZ, body.maxX, body.minY + 8, body.maxZ);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        Vec3 velocity = getDeltaMovement();
        if (!isNoGravity()) velocity = velocity.add(0, -.08, 0);
        velocity = new Vec3(velocity.x * .8, Math.max(-1.5, velocity.y), velocity.z * .8);
        if (!isLoaded(getBoundingBox().expandTowards(velocity))) {
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        setDeltaMovement(velocity);
        move(MoverType.SELF, velocity);
        if (verticalCollision) setDeltaMovement(velocity.multiply(1, 0, 1));
    }

    private boolean isLoaded(AABB bounds) {
        for (int x = Mth.floor(bounds.minX) >> 4; x <= Mth.floor(bounds.maxX) >> 4; x++) {
            for (int z = Mth.floor(bounds.minZ) >> 4; z <= Mth.floor(bounds.maxZ) >> 4; z++) {
                if (!level().hasChunkAt(new BlockPos(x << 4, Mth.floor(bounds.minY), z << 4))) return false;
            }
        }
        return true;
    }

    @Override
    public boolean isPickable() { return true; }

    @Override
    public boolean canBeCollidedWith() { return !isRemoved(); }

    @Override
    public boolean isPushable() { return false; }

    /** The owner can clear a wreck in survival without creating another explosion or duplicate salvage. */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()
                || !player.getItemInHand(hand).is(CROWBARS)) return InteractionResult.PASS;
        if (isRemoved() || player.isSpectator() || player.level() != level()
                || player.distanceToSqr(this) > 64 || getOwnerUUID() == null
                || !getOwnerUUID().equals(player.getUUID())) return InteractionResult.FAIL;
        if (!level().isClientSide) discard();
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** Creative builders can clear a carcass; combat cannot repeatedly explode it or farm drops. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && source.getEntity() instanceof Player player
                && player.getAbilities().instabuild) {
            discard();
            return true;
        }
        return false;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (getOwnerUUID() != null) tag.putUUID("Owner", getOwnerUUID());
        tag.putFloat("LauncherPose", getLauncherProgress());
        tag.putFloat("WheelPose", getWheelRotation());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(OWNER, tag.hasUUID("Owner") ? Optional.of(tag.getUUID("Owner")) : Optional.empty());
        float launcher = tag.getFloat("LauncherPose");
        float wheels = tag.getFloat("WheelPose");
        entityData.set(LAUNCHER, Float.isFinite(launcher) ? Mth.clamp(launcher, 0, 1) : 0);
        entityData.set(WHEELS, Float.isFinite(wheels) ? wheels : 0);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
