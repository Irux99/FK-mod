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
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Licensed user truck with articulated wheels/launcher and an original radar assembly. */
public final class VehicleRenderer extends EntityRenderer<Fk2000Entity> {
    private static final ResourceLocation RADAR_TEXTURE = new ResourceLocation("iruxfk2000", "textures/entity/radar.png");
    private final ImportedMesh truck;
    private final ModelPart radarAssembly;
    private final ModelPart radar;
    public VehicleRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius=3.8f;
        truck=new ImportedMesh("truck",new ResourceLocation("iruxfk2000","textures/entity/imported-truck.png"));
        MeshDefinition mesh=new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("mast",CubeListBuilder.create().texOffs(0,0)
            .addBox(-1.2f,-41,6.8f,2.4f,22,2.4f)
            .addBox(-5,-21,4,10,2,8),PartPose.ZERO);
        CubeListBuilder panel=CubeListBuilder.create().texOffs(0,0).addBox(-13,-9,-2,26,18,4)
            .texOffs(80,80).addBox(-11,-7,-2.3f,22,14,.7f);
        for(int x=-10;x<=10;x+=4) panel.texOffs(90,34).addBox(x-.18f,-7,-2.5f,.36f,14,.3f);
        for(int y=-6;y<=6;y+=3) panel.texOffs(90,34).addBox(-11,y-.18f,-2.5f,22,.36f,.3f);
        mesh.getRoot().addOrReplaceChild("radar",panel,PartPose.offset(0,-43,8));
        radarAssembly=LayerDefinition.create(mesh,512,256).bakeRoot();radar=radarAssembly.getChild("radar");
    }
    @Override public void render(Fk2000Entity entity,float entityYaw,float partialTick,PoseStack pose,MultiBufferSource buffers,int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180-Mth.rotLerp(partialTick,entity.yRotO,entity.getYRot())));
        pose.scale(VehicleGeometry.SCALE,-VehicleGeometry.SCALE,-VehicleGeometry.SCALE);
        Minecraft mc = Minecraft.getInstance();
        boolean cabView = mc.options.getCameraType().isFirstPerson()
                && (mc.getCameraEntity() == entity || mc.player != null && mc.player.getVehicle() == entity);
        truck.render(pose,buffers,light,entity.getWheelRotation(),entity.getLauncherProgress(),cabView);
        radar.yRot=entity.isRadarEnabled()?(float)(((entity.level().getGameTime()+partialTick)*.075)%(Math.PI*2)):0;
        radarAssembly.render(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(RADAR_TEXTURE)),light,OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(entity,entityYaw,partialTick,pose,buffers,light);
    }
    @Override public ResourceLocation getTextureLocation(Fk2000Entity entity) { return RADAR_TEXTURE; }
}
