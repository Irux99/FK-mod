package fr.irux.fk2000.compat;

import fr.irux.fk2000.entity.vehicle.Fk2000Entity;
import fr.irux.fk2000.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Uses SBW's own crate, animation, crowbar and deployment; no SBW asset is bundled. */
@Mod.EventBusSubscriber(modid = "iruxfk2000")
public final class SbwAssemblyIntegration {
    public static final ResourceLocation RECIPE = new ResourceLocation("iruxfk2000", "fk2000_assembling");
    private static final ResourceLocation CONTAINER = new ResourceLocation("superbwarfare", "container");
    private static final TagKey<net.minecraft.world.item.Item> CROWBARS = TagKey.create(Registries.ITEM,
            new ResourceLocation("forge", "tools/crowbar"));
    private SbwAssemblyIntegration() {}

    public static boolean isFkCrate(BlockEntity blockEntity) {
        return blockEntity != null && CONTAINER.equals(ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(blockEntity.getType()))
                && "iruxfk2000:fk2000".equals(blockEntity.saveWithoutMetadata().getString("EntityType"));
    }

    /** SBW checks a square based on EntityType.width. Our long chassis also needs its real rotated bounds checked. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void openCrate(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide || !event.getEntity().getMainHandItem().is(CROWBARS)) return;
        BlockPos pos = event.getPos();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!isFkCrate(blockEntity)) return;
        var state = level.getBlockState(pos);
        if (state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN)) return;
        // SBW calls its property "opened", rather than vanilla's "open".
        if (state.getProperties().stream().anyMatch(property -> property.getName().equals("opened")
                && Boolean.TRUE.equals(state.getValue(property)))) return;
        CompoundTag data = blockEntity.saveWithoutMetadata();
        CompoundTag entityData = data.getCompound("Entity").copy();
        Player player = event.getEntity();
        if (entityData.hasUUID("Owner") && !player.getUUID().equals(entityData.getUUID("Owner"))) {
            reject(event, "message.iruxfk2000.crate_owner");
            return;
        }
        Fk2000Entity preview = ModContent.VEHICLE.get().create(level);
        if (preview == null) {
            reject(event, "message.iruxfk2000.crate_space");
            return;
        }
        if (!entityData.isEmpty()) preview.load(entityData);
        float yaw = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? state.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot() : Direction.NORTH.toYRot();
        preview.moveTo(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, yaw, 0);
        // SBW adds up to +/-0.1 block random displacement to all three coordinates.
        AABB deployment = fr.irux.fk2000.entity.vehicle.VehicleGeometry.bodyBounds(preview.position(), preview.getYRot()).inflate(.11);
        if (!canDeploy(level, pos, preview, deployment)) {
            preview.discard();
            reject(event, "message.iruxfk2000.crate_space");
            return;
        }
        // A newly assembled crate has only EntityType. Persist complete initialized
        // defaults: loading an Owner-only tag would otherwise reset ammo/radar to zero.
        preview.setOwnerUUID(player.getUUID());
        preview.saveWithoutId(entityData);
        preview.discard();
        data.put("Entity", entityData);
        blockEntity.load(data);
        blockEntity.setChanged();
        level.sendBlockUpdated(pos, state, state, 3);
        // Continue into SBW's normal use(), including its space check and crowbar animation.
    }

    public static boolean canDeploy(Level level, BlockPos crate, Fk2000Entity preview, AABB bounds) {
        if (!level.getWorldBorder().isWithinBounds(bounds)) return false;
        int minChunkX = ((int) Math.floor(bounds.minX)) >> 4;
        int maxChunkX = ((int) Math.floor(bounds.maxX)) >> 4;
        int minChunkZ = ((int) Math.floor(bounds.minZ)) >> 4;
        int maxChunkZ = ((int) Math.floor(bounds.maxZ)) >> 4;
        for (int x = minChunkX; x <= maxChunkX; x++) for (int z = minChunkZ; z <= maxChunkZ; z++)
            if (!level.hasChunk(x, z)) return false;
        CollisionContext context = CollisionContext.of(preview);
        for (BlockPos at : BlockPos.betweenClosed((int) Math.floor(bounds.minX), (int) Math.floor(bounds.minY),
                (int) Math.floor(bounds.minZ), (int) Math.floor(bounds.maxX), (int) Math.floor(bounds.maxY),
                (int) Math.floor(bounds.maxZ))) {
            if (at.equals(crate)) continue;
            var shape = level.getBlockState(at).getCollisionShape(level, at, context).move(at.getX(), at.getY(), at.getZ());
            for (AABB obstacle : shape.toAabbs()) if (obstacle.intersects(bounds)) return false;
        }
        // Players can step aside during the opening animation; solid vehicles must not overlap.
        return level.getEntities(preview, bounds, entity -> !(entity instanceof Player)
                && entity.canBeCollidedWith()).isEmpty();
    }

    private static void reject(PlayerInteractEvent.RightClickBlock event, String message) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        event.getEntity().displayClientMessage(Component.translatable(message), true);
    }
}
