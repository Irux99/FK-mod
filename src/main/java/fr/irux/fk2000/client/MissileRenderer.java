package fr.irux.fk2000.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.irux.fk2000.entity.missile.MissileEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Supplied missile geometry, normalized along +Z with its original diffuse UVs. */
public final class MissileRenderer extends EntityRenderer<MissileEntity> {
    private static final ResourceLocation TEXTURE=new ResourceLocation("iruxfk2000","textures/entity/imported-missile.png");
    private final ImportedMesh missileMesh;
    public MissileRenderer(EntityRendererProvider.Context context) { super(context);missileMesh=new ImportedMesh("missile",TEXTURE);shadowRadius=.2f; }
    @Override public void render(MissileEntity missile,float yaw,float partialTick,PoseStack pose,MultiBufferSource buffers,int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180-Mth.rotLerp(partialTick,missile.yRotO,missile.getYRot())));
        pose.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partialTick,missile.xRotO,missile.getXRot())));
        pose.scale(1,-1,-1);
        missileMesh.render(pose,buffers,light);
        pose.popPose();
        super.render(missile,yaw,partialTick,pose,buffers,light);
    }
    @Override public ResourceLocation getTextureLocation(MissileEntity entity) { return TEXTURE; }
}
