package fr.irux.fk2000.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.irux.fk2000.entity.vehicle.Fk2000Entity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Original, deliberately provisional cuboid artwork. No assets from SBW are used. */
public final class VehicleRenderer extends EntityRenderer<Fk2000Entity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("iruxfk2000", "textures/entity/fk2000.png");
    private final ModelPart model;
    private final ModelPart launcher;
    private final ModelPart radar;

    public VehicleRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 2.2f;
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("chassis", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-21, -17, -44, 42, 10, 88)
                .texOffs(0, 0).addBox(-19, -29, 20, 38, 18, 23)
                .texOffs(80, 80).addBox(-16, -27, 42, 32, 8, 1)
                .texOffs(80, 80).addBox(-20, -27, 22, 1, 8, 17)
                .texOffs(80, 80).addBox(19, -27, 22, 1, 8, 17)
                .texOffs(0, 0).addBox(-22, -10, 44, 44, 4, 3), PartPose.ZERO);
        int[] wheelZ = {-30, -10, 13, 32};
        for (int axle = 0; axle < wheelZ.length; axle++) {
            for (int side = 0; side < 2; side++) {
                root.addOrReplaceChild("wheel" + (axle * 2 + side), CubeListBuilder.create()
                        .texOffs(90, 0).addBox(-3, -7, -7, 6, 14, 14)
                        .texOffs(90, 34).addBox(-3.3f, -2, -2, 6.6f, 4, 4),
                        PartPose.offset(side == 0 ? -22 : 22, -7, wheelZ[axle]));
            }
        }
        PartDefinition launch = root.addOrReplaceChild("launcher", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-17, -4, -23, 34, 8, 43), PartPose.offset(0, -26.4f, -8));
        // Four visible tubes, separated rather than a single decorative launcher box.
        int index = 0;
        for (int side = 0; side < 2; side++) {
            for (int layer = 0; layer < 2; layer++) {
                launch.addOrReplaceChild("tube" + index++, CubeListBuilder.create().texOffs(0, 70)
                        .addBox(-4, -4, -22, 8, 8, 44)
                        .texOffs(90, 0).addBox(-3, -3, 22, 6, 6, 1),
                        PartPose.offset(side == 0 ? -10.24f : 10.24f, layer == 0 ? -7.68f : 7.68f, 0));
            }
        }
        root.addOrReplaceChild("radar_mast", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-2, -41, -37, 4, 24, 4), PartPose.ZERO);
        root.addOrReplaceChild("radar", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-13, -9, -2, 26, 18, 4)
                .texOffs(80, 80).addBox(-11, -7, -2.3f, 22, 14, 1), PartPose.offset(0, -43, -35));
        model = LayerDefinition.create(mesh, 512, 256).bakeRoot();
        launcher = model.getChild("launcher");
        radar = model.getChild("radar");
    }

    @Override
    public void render(Fk2000Entity entity, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        pose.pushPose();
        float yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        pose.mulPose(Axis.YP.rotationDegrees(180 - yaw));
        pose.scale(1, -1, -1);
        float wheelAngle = entity.getWheelRotation();
        for (int i = 0; i < 8; i++) model.getChild("wheel" + i).xRot = wheelAngle;
        radar.yRot = entity.isRadarEnabled() ? (float) ((entity.level().getGameTime() * .075 + partialTick * .075) % (Math.PI * 2)) : 0;
        launcher.xRot = entity.getLauncherProgress() * Mth.HALF_PI;
        model.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), packedLight, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(Fk2000Entity entity) { return TEXTURE; }
}
