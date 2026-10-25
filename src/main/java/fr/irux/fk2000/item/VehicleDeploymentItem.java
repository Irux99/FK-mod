package fr.irux.fk2000.item;

import fr.irux.fk2000.entity.vehicle.Fk2000Entity;
import fr.irux.fk2000.registry.ModContent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

/** Deployment is atomic and server-side; blocked placements never consume the item. */
public final class VehicleDeploymentItem extends Item {
    public VehicleDeploymentItem(Properties properties) { super(properties); }
    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() == null) return InteractionResult.FAIL;
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        Fk2000Entity vehicle = ModContent.VEHICLE.get().create(context.getLevel());
        if (vehicle == null) return InteractionResult.FAIL;
        Vec3 pos = Vec3.atBottomCenterOf(context.getClickedPos().relative(context.getClickedFace()));
        vehicle.moveTo(pos.x, pos.y, pos.z, context.getPlayer().getYRot(), 0);
        vehicle.setOwnerUUID(context.getPlayer().getUUID());
        if (!context.getLevel().getWorldBorder().isWithinBounds(vehicle.getBoundingBox())
                || !context.getLevel().noCollision(vehicle) || !context.getLevel().addFreshEntity(vehicle)) {
            vehicle.discard();
            return InteractionResult.FAIL;
        }
        if (!context.getPlayer().getAbilities().instabuild) context.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }
}
