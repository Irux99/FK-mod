package fr.irux.fk2000.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.irux.fk2000.entity.vehicle.Fk2000Entity;
import fr.irux.fk2000.entity.vehicle.VehicleGeometry;
import net.minecraft.client.Minecraft;
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
public final class PrototypeVehicleRenderer extends EntityRenderer<Fk2000Entity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("iruxfk2000", "textures/entity/fk2000.png");
    private final ModelPart model;
    private final ModelPart launcher;
    private final ModelPart radar;

    public PrototypeVehicleRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 3.8f;
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("chassis", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-21, -17, -44, 42, 10, 88)
                .texOffs(0, 0).addBox(-22, -10, 44, 44, 4, 3), PartPose.ZERO);
        // Hollow cab: the pilot sits between the floor, roof and pillars.
        root.addOrReplaceChild("cab", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-19, -11, 20, 38, 2, 23)
                .addBox(-19, -25, 20, 38, 2, 23)
                .addBox(-19, -23, 20, 2, 12, 23)
                .addBox(17, -23, 20, 2, 12, 23)
                .addBox(-17, -23, 20, 34, 12, 2)
                .addBox(-17, -15, 41, 34, 4, 2), PartPose.ZERO);
        root.addOrReplaceChild("glass", CubeListBuilder.create().texOffs(80, 80)
                .addBox(-17, -23, 42, 34, 8, 1), PartPose.ZERO);
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
                .addBox(-8, -14, 0, 16, 16, 34), PartPose.offset(0, -19.6f, -42.7f));
        // Four visible tubes, separated rather than a single decorative launcher box.
        int index = 0;
        for (int side = 0; side < 3; side++) {
            for (int layer = 0; layer < 2; layer++) {
                launch.addOrReplaceChild("tube" + index++, CubeListBuilder.create().texOffs(0, 70)
                        .addBox(-1.75f, -1.75f, 0, 3.5f, 3.5f, 34)
                        .texOffs(90, 0).addBox(-1.5f, -1.5f, 34, 3, 3, 1),
                        PartPose.offset((side-1)*5.28f, layer == 0 ? -1.28f : -11.36f, 0));
            }
        }
        root.addOrReplaceChild("radar_mast", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-2, -41, 6, 4, 24, 4), PartPose.ZERO);
        root.addOrReplaceChild("radar", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-13, -9, -2, 26, 18, 4)
                .texOffs(80, 80).addBox(-11, -7, -2.3f, 22, 14, 1), PartPose.offset(0, -43, 8));
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
        pose.scale(VehicleGeometry.SCALE, -VehicleGeometry.SCALE, -VehicleGeometry.SCALE);
        Minecraft mc = Minecraft.getInstance();
        model.getChild("glass").visible = !(mc.options.getCameraType().isFirstPerson()
                && (mc.getCameraEntity() == entity || mc.player != null && mc.player.getVehicle() == entity));
        float wheelAngle = -entity.getWheelRotation();
        for (int i = 0; i < 8; i++) { model.getChild("wheel" + i).xRot = wheelAngle; model.getChild("wheel" + i).visible = false; }
        radar.yRot = entity.isRadarEnabled() ? (float) ((entity.level().getGameTime() * .075 + partialTick * .075) % (Math.PI * 2)) : 0;
        launcher.xRot = entity.getLauncherProgress() * Mth.HALF_PI;
        pose.pushPose();
        VehicleVisuals.applyModelSuspension(pose,entity.getSuspensionCompression(partialTick));
        model.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), packedLight, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        for (int i = 0; i < 8; i++) {
            ModelPart wheel = model.getChild("wheel" + i); wheel.visible = true;
            wheel.render(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)),packedLight,OverlayTexture.NO_OVERLAY);
        }
        pose.popPose();
        VehicleVisuals.renderCaps(entity,partialTick,pose,buffers,packedLight);
        super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(Fk2000Entity entity) { return TEXTURE; }
}
