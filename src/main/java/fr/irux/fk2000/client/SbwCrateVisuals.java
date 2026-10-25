package fr.irux.fk2000.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterItemDecorationsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;

/** Adds our own markings while the installed SBW renderer draws its original crate. */
@Mod.EventBusSubscriber(modid = "iruxfk2000", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SbwCrateVisuals {
    private static final ResourceLocation CONTAINER = new ResourceLocation("superbwarfare", "container");
    private static final ResourceLocation VEHICLE = new ResourceLocation("iruxfk2000", "fk2000");
    private static final ResourceLocation LOGO = new ResourceLocation("iruxfk2000", "textures/gui/fk2000-crate-logo.png");
    private static final ClassValue<Field> ENTITY_TYPE = new ClassValue<>() {
        @Override protected Field computeValue(Class<?> type) {
            try { return type.getField("entityType"); }
            catch (NoSuchFieldException ignored) { return null; }
        }
    };

    private SbwCrateVisuals() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void itemDecorations(RegisterItemDecorationsEvent event) {
        if (!ModList.get().isLoaded("superbwarfare")) return;
        var item = ForgeRegistries.ITEMS.getValue(CONTAINER);
        if (item == null) return;
        // Forge decorators are additive: SBW keeps its own decorations for every other crate.
        event.register(item, (graphics, font, stack, x, y) -> {
            var tag = BlockItem.getBlockEntityData(stack);
            if (tag == null || !VEHICLE.toString().equals(tag.getString("EntityType"))) return false;
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 205);
            graphics.blit(LOGO, x + 3, y + 3, 0, 0, 10, 10, 10, 10);
            graphics.pose().popPose();
            return true;
        });
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void blockRenderer(EntityRenderersEvent.RegisterRenderers event) {
        if (!ModList.get().isLoaded("superbwarfare")) return;
        BlockEntityType<?> type = ForgeRegistries.BLOCK_ENTITY_TYPES.getValue(CONTAINER);
        if (type == null) return;
        try {
            // The default constructor exists in the 0.8.9-final GeckoLib renderer and
            // the 0.8.9.1/0.8.9.2 renderer. No SBW model, texture or code is bundled here.
            var constructor = Class.forName("com.atsuishio.superbwarfare.client.renderer.block.ContainerBlockEntityRenderer").getConstructor();
            var delegate = (BlockEntityRenderer<BlockEntity>) constructor.newInstance();
            event.registerBlockEntityRenderer((BlockEntityType) type, context -> new MarkedCrateRenderer(delegate));
        } catch (ReflectiveOperationException | LinkageError error) {
            LogUtils.getLogger().warn("FK-2000 crate marking unavailable for this SBW renderer; retaining its renderer", error);
        }
    }

    private static boolean isFkCrate(BlockEntity blockEntity) {
        try {
            Field field = ENTITY_TYPE.get(blockEntity.getClass());
            return field != null && field.get(blockEntity) instanceof EntityType<?> type
                    && VEHICLE.equals(EntityType.getKey(type));
        } catch (IllegalAccessException ignored) { return false; }
    }

    private record MarkedCrateRenderer(BlockEntityRenderer<BlockEntity> delegate) implements BlockEntityRenderer<BlockEntity> {
        @Override public void render(BlockEntity blockEntity, float partialTick, PoseStack pose,
                                     MultiBufferSource buffers, int light, int overlay) {
            delegate.render(blockEntity, partialTick, pose, buffers, light, overlay);
            if (!isFkCrate(blockEntity)) return;
            for (var property : blockEntity.getBlockState().getProperties()) {
                if (property instanceof BooleanProperty opened && property.getName().equals("opened")
                        && blockEntity.getBlockState().getValue(opened)) return;
            }
            // Closed SBW crates occupy x/z 0..1, y 0..15/16 in all supported releases.
            // Four signs remain legible regardless of which way the crate is placed.
            VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(LOGO));
            float lo = .18f, hi = .82f, bottom = .20f, top = .80f;
            quad(pose, vertices, light, hi, bottom, -.003f, lo, bottom, -.003f,
                    lo, top, -.003f, hi, top, -.003f, 0, 0, -1);
            quad(pose, vertices, light, lo, bottom, 1.003f, hi, bottom, 1.003f,
                    hi, top, 1.003f, lo, top, 1.003f, 0, 0, 1);
            quad(pose, vertices, light, -.003f, bottom, lo, -.003f, bottom, hi,
                    -.003f, top, hi, -.003f, top, lo, -1, 0, 0);
            quad(pose, vertices, light, 1.003f, bottom, hi, 1.003f, bottom, lo,
                    1.003f, top, lo, 1.003f, top, hi, 1, 0, 0);
        }

        @Override public boolean shouldRenderOffScreen(BlockEntity blockEntity) { return delegate.shouldRenderOffScreen(blockEntity); }
        @Override public int getViewDistance() { return delegate.getViewDistance(); }
        @Override public boolean shouldRender(BlockEntity blockEntity, Vec3 camera) { return delegate.shouldRender(blockEntity, camera); }
    }

    private static void quad(PoseStack pose, VertexConsumer vertices, int light,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float nx, float ny, float nz) {
        vertex(pose, vertices, light, x0, y0, z0, 0, 1, nx, ny, nz);
        vertex(pose, vertices, light, x1, y1, z1, 1, 1, nx, ny, nz);
        vertex(pose, vertices, light, x2, y2, z2, 1, 0, nx, ny, nz);
        vertex(pose, vertices, light, x3, y3, z3, 0, 0, nx, ny, nz);
    }

    private static void vertex(PoseStack pose, VertexConsumer vertices, int light, float x, float y, float z,
                               float u, float v, float nx, float ny, float nz) {
        var entry = pose.last();
        vertices.vertex(entry.pose(), x, y, z).color(255, 255, 255, 255).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(entry.normal(), nx, ny, nz).endVertex();
    }
}
