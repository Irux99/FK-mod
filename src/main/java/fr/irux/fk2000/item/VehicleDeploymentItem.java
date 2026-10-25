package fr.irux.fk2000.item;

import fr.irux.fk2000.entity.vehicle.Fk2000Entity;
import fr.irux.fk2000.entity.vehicle.VehicleGeometry;
import fr.irux.fk2000.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
        // Put the long chassis beyond the clicked position, rather than around the
        // player's feet. The rear bumper remains just beyond the placement point.
        double yaw = Math.toRadians(context.getPlayer().getYRot());
        double offset = VehicleGeometry.BODY_LENGTH / 2.0 + .5;
        pos = pos.add(-Math.sin(yaw) * offset, 0, Math.cos(yaw) * offset);
        vehicle.moveTo(pos.x, pos.y, pos.z, context.getPlayer().getYRot(), 0);
        vehicle.setOwnerUUID(context.getPlayer().getUUID());
        var bounds = vehicle.getBoundingBox();
        ServerLevel level = (ServerLevel) context.getLevel();
        for (int x = (int) Math.floor(bounds.minX) >> 4; x <= (int) Math.floor(bounds.maxX) >> 4; x++) {
            for (int z = (int) Math.floor(bounds.minZ) >> 4; z <= (int) Math.floor(bounds.maxZ) >> 4; z++) {
                if (!level.hasChunkAt(new BlockPos(x << 4, (int) pos.y, z << 4))) {
                    vehicle.discard();
                    return InteractionResult.FAIL;
                }
            }
        }
        if (!context.getLevel().getWorldBorder().isWithinBounds(vehicle.getBoundingBox())
                || !context.getLevel().noCollision(vehicle) || !context.getLevel().addFreshEntity(vehicle)) {
            vehicle.discard();
            return InteractionResult.FAIL;
        }
        if (!context.getPlayer().getAbilities().instabuild) context.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }
}
