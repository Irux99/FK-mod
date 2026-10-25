package fr.irux.fk2000.entity.vehicle;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.NetworkHooks;

/** Short-lived physical launch-tube cover; it creates no chunk tickets and cannot be a combat target. */
public final class TubeCapEntity extends Entity {
    public static final int LIFETIME_TICKS = 160;
    private static final EntityDataAccessor<Float> SPIN =
            SynchedEntityData.defineId(TubeCapEntity.class, EntityDataSerializers.FLOAT);
    private int lifeTicks;
    private float previousSpin;

    public TubeCapEntity(EntityType<? extends TubeCapEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() { entityData.define(SPIN, 0F); }

    public void launch(Vec3 velocity) {
        if (level().isClientSide || !Double.isFinite(velocity.x) || !Double.isFinite(velocity.y)
                || !Double.isFinite(velocity.z)) return;
        setDeltaMovement(velocity.lengthSqr() > 16 ? velocity.normalize().scale(4) : velocity);
        hasImpulse = true;
    }

    public float getSpin(float partialTick) { return Mth.lerp(partialTick, previousSpin, entityData.get(SPIN)); }
    public int getLifeTicks() { return lifeTicks; }

    @Override
    public void tick() {
        previousSpin = entityData.get(SPIN);
        super.tick();
        if (level().isClientSide) return;
        if (++lifeTicks >= LIFETIME_TICKS) {
            discard();
            return;
        }
        Vec3 velocity = getDeltaMovement();
        if (!isNoGravity()) velocity = velocity.add(0, -.04, 0);
        if (!isLoaded(getBoundingBox().expandTowards(velocity))) {
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        setDeltaMovement(velocity);
        move(MoverType.SELF, velocity);
        if (onGround()) {
            setDeltaMovement(velocity.multiply(.6, 0, .6));
        } else {
            entityData.set(SPIN, entityData.get(SPIN) + 22);
            setDeltaMovement(velocity.scale(.98));
            if (horizontalCollision) setDeltaMovement(getDeltaMovement().multiply(-.2, 1, -.2));
            if (verticalCollision && velocity.y > 0) setDeltaMovement(getDeltaMovement().multiply(1, -.1, 1));
        }
        hasImpulse = true;
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
    public boolean isPickable() { return false; }

    @Override
    public boolean canBeCollidedWith() { return false; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("LifeTicks", lifeTicks);
        tag.putFloat("Spin", entityData.get(SPIN));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        lifeTicks = Mth.clamp(tag.getInt("LifeTicks"), 0, LIFETIME_TICKS);
        float spin = tag.getFloat("Spin");
        entityData.set(SPIN, Float.isFinite(spin) ? spin : 0);
        previousSpin = entityData.get(SPIN);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
