package fr.irux.fk2000.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.irux.fk2000.entity.missile.MissileEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Bright transparent nozzle geometry on standard entity layers, compatible with shader-pack bloom. */
final class RocketPlume {
    private static final ResourceLocation TEXTURE = new ResourceLocation("iruxfk2000", "textures/entity/rocket-exhaust.png");
    private RocketPlume() {}
    static void render(MissileEntity missile, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE, false));
        float pulse = .94f + .06f * (float) Math.sin((missile.tickCount + partialTick) * 2.7);
        for (int plane = 0; plane < 3; plane++) {
            double angle = plane * Math.PI / 3;
            float dx = (float) Math.cos(angle), dy = (float) Math.sin(angle);
            draw(pose, vertices, dx, dy, .25f, 2.3f * pulse, .78f);
            draw(pose, vertices, dx, dy, .10f, 1.25f * pulse, .98f);
        }
    }
    private static void draw(PoseStack pose, VertexConsumer vertices, float dx, float dy, float radius, float length, float alpha) {
        // The supplied normalized model points along +Z: its engine and every plume vertex are behind -0.95.
        vertex(pose,vertices,-dx*radius,-dy*radius,-.95f,0,0,alpha);
        vertex(pose,vertices, dx*radius, dy*radius,-.95f,1,0,alpha);
        vertex(pose,vertices, dx*radius*2.3f,dy*radius*2.3f,-.95f-length,1,1,0);
        vertex(pose,vertices,-dx*radius*2.3f,-dy*radius*2.3f,-.95f-length,0,1,0);
        vertex(pose,vertices, dx*radius, dy*radius,-.95f,1,0,alpha);
        vertex(pose,vertices,-dx*radius,-dy*radius,-.95f,0,0,alpha);
        vertex(pose,vertices,-dx*radius*2.3f,-dy*radius*2.3f,-.95f-length,0,1,0);
        vertex(pose,vertices, dx*radius*2.3f,dy*radius*2.3f,-.95f-length,1,1,0);
    }
    private static void vertex(PoseStack pose, VertexConsumer vertices, float x,float y,float z,float u,float v,float alpha) {
        var entry=pose.last();
        vertices.vertex(entry.pose(),x,y,z).color(1f,1f,1f,alpha).uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(15728880).normal(entry.normal(),0,0,-1).endVertex();
    }
}
