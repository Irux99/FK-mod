package fr.irux.fk2000.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.irux.fk2000.entity.vehicle.TubeCapEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public final class TubeCapRenderer extends EntityRenderer<TubeCapEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("iruxfk2000", "textures/entity/tube-cap.png");
    public TubeCapRenderer(EntityRendererProvider.Context context) { super(context); shadowRadius = .28f; }
    @Override public void render(TubeCapEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees(-90 + entity.getSpin(partialTick)));
        pose.mulPose(Axis.ZP.rotationDegrees(entity.getSpin(partialTick) * .35f));
        VehicleVisuals.renderCap(pose, buffers, light);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }
    @Override public ResourceLocation getTextureLocation(TubeCapEntity entity) { return TEXTURE; }
}
