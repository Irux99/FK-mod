package fr.irux.fk2000.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.irux.fk2000.entity.vehicle.WreckEntity;
import fr.irux.fk2000.entity.vehicle.VehicleGeometry;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Burned metal is original artwork; the private imported mesh remains inside the game JAR. */
public final class WreckRenderer extends EntityRenderer<WreckEntity> {
    private static final ResourceLocation CHARRED = new ResourceLocation("iruxfk2000", "textures/entity/charred-metal.png");
    private final ImportedMesh mesh;
    private final ModelPart fallback;
    public WreckRenderer(EntityRendererProvider.Context context) {
        super(context); shadowRadius = 3.8f;
        mesh = ImportedMesh.isAvailable("truck") ? new ImportedMesh("truck", CHARRED) : null;
        MeshDefinition definition = new MeshDefinition();
        var root = definition.getRoot();
        root.addOrReplaceChild("hull", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-15, -17, -47, 30, 10, 94)
                .addBox(-14, -31, 22, 28, 14, 24)
                .addBox(-11, -25, -43, 22, 10, 45), PartPose.ZERO);
        for (int axle = 0; axle < 4; axle++) for (int side = 0; side < 2; side++)
            root.addOrReplaceChild("wheel" + axle + side, CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-3, -6, -6, 6, 12, 12), PartPose.offset(side == 0 ? -16 : 16, -7, -31 + axle * 21));
        fallback = LayerDefinition.create(definition, 128, 128).bakeRoot();
    }
    @Override public void render(WreckEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180 - Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot())));
        pose.scale(VehicleGeometry.SCALE, -VehicleGeometry.SCALE, -VehicleGeometry.SCALE);
        if (mesh != null) mesh.render(pose, buffers, light, entity.getWheelRotation(), entity.getLauncherProgress());
        else fallback.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(CHARRED)), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }
    @Override public ResourceLocation getTextureLocation(WreckEntity entity) { return CHARRED; }
}
