package fr.irux.fk2000.entity.vehicle;

import net.minecraftforge.entity.PartEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

/** Small rotating chassis sections share the parent's health and interaction. */
public final class ChassisPart extends PartEntity<Fk2000Entity> {
    private final int index;
    ChassisPart(Fk2000Entity parent, int index) { super(parent); this.index = index; follow(); }
    void follow() {
        var bounds = VehicleGeometry.segmentBounds(getParent().position(), getParent().getYRot(), -5.25 + index * 1.5, 1.5);
        setPos(bounds.getCenter().x, bounds.minY, bounds.getCenter().z);
        setBoundingBox(bounds);
    }
    @Override protected void defineSynchedData() {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public boolean isPickable() { return getParent().isAlive(); }
    @Override public boolean canBeCollidedWith() { return getParent().isAlive(); }
    @Override public boolean is(Entity other) { return this == other || getParent() == other; }
    @Override public boolean hurt(DamageSource source, float damage) { return getParent().hurt(source, damage); }
    @Override public InteractionResult interact(Player player, InteractionHand hand) { return getParent().interact(player, hand); }
    @Override public boolean shouldBeSaved() { return false; }
}
