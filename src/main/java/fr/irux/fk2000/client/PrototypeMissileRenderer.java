package fr.irux.fk2000.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.irux.fk2000.entity.missile.MissileEntity;
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

public final class PrototypeMissileRenderer extends EntityRenderer<MissileEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("iruxfk2000", "textures/entity/missile.png");
    private final ModelPart model;
    public PrototypeMissileRenderer(EntityRendererProvider.Context context) {
        super(context);
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-2, -2, -13, 4, 4, 26)
                .texOffs(0, 32).addBox(-1.5f, -1.5f, 13, 3, 3, 5)
                .texOffs(0, 32).addBox(-1, -1, 18, 2, 2, 3)
                .texOffs(30, 0).addBox(-6, -.5f, -11, 12, 1, 7)
                .texOffs(30, 0).addBox(-.5f, -6, -11, 1, 12, 7), PartPose.ZERO);
        model = LayerDefinition.create(mesh, 128, 64).bakeRoot();
        shadowRadius = .2f;
    }
    @Override
    public void render(MissileEntity missile, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180 - Mth.rotLerp(partialTick, missile.yRotO, missile.getYRot())));
        pose.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partialTick, missile.xRotO, missile.getXRot())));
        pose.scale(1, -1, -1);
        model.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), packedLight, OverlayTexture.NO_OVERLAY);
        if (missile.getMotorActive()) RocketPlume.render(missile,partialTick,pose,buffers);
        pose.popPose();
        super.render(missile, yaw, partialTick, pose, buffers, packedLight);
    }
    @Override
    public ResourceLocation getTextureLocation(MissileEntity entity) { return TEXTURE; }
}
