package fr.irux.fk2000.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.irux.fk2000.entity.vehicle.Fk2000Entity;
import fr.irux.fk2000.entity.vehicle.TubeCapMode;
import fr.irux.fk2000.entity.vehicle.VehicleGeometry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Original tube covers, shared by the imported and fallback vehicle renderers. */
final class VehicleVisuals {
    private static final ResourceLocation CAP = new ResourceLocation("iruxfk2000", "textures/entity/tube-cap.png");
    private VehicleVisuals() {}

    static void applyModelSuspension(PoseStack pose, float compression) {
        if (compression == 0) return;
        pose.translate(0, 0, 2.1);
        pose.mulPose(Axis.XP.rotationDegrees(3 * compression));
        pose.translate(0, 0, -2.1);
    }

    static void renderCaps(Fk2000Entity vehicle, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (vehicle.getTubeCapMode() == TubeCapMode.NONE) return;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partialTick, vehicle.yRotO, vehicle.getYRot())));
        pose.translate(0, 0, 4.2);
        pose.mulPose(Axis.XP.rotationDegrees(-3 * vehicle.getSuspensionCompression(partialTick)));
        pose.translate(0, 0, -4.2);
        for (int tube = 0; tube < 6; tube++) {
            if ((vehicle.getCapsPresentMask() & (1 << tube)) == 0) continue;
            var muzzle = VehicleGeometry.tubeMuzzleLocal(tube, vehicle.getLauncherProgress());
            pose.pushPose();
            pose.translate(muzzle.x, muzzle.y, muzzle.z);
            pose.mulPose(Axis.XP.rotationDegrees(-90 * vehicle.getLauncherProgress()));
            pose.translate(0, 0, .035);
            if (vehicle.getTubeCapMode() == TubeCapMode.HINGED) {
                pose.translate(.28, 0, 0);
                pose.mulPose(Axis.YP.rotationDegrees(vehicle.getTubeCapProgress(tube, partialTick) * 115));
                pose.translate(-.28, 0, 0);
            }
            renderCap(pose, buffers, light);
            pose.popPose();
        }
        pose.popPose();
    }

    static void renderCap(PoseStack pose, MultiBufferSource buffers, int light) {
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(CAP));
        for (int segment = 0; segment < 16; segment++) {
            double a = segment * Math.PI / 8, b = (segment + 1) * Math.PI / 8;
            float x1 = (float) Math.cos(a) * .28f, y1 = (float) Math.sin(a) * .28f;
            float x2 = (float) Math.cos(b) * .28f, y2 = (float) Math.sin(b) * .28f;
            vertex(pose, vertices, light, 0, 0, .03f, .5f, .5f, 0, 0, 1, 1);
            vertex(pose, vertices, light, x1, y1, .03f, x1 / .56f + .5f, y1 / .56f + .5f, 0, 0, 1, 1);
            vertex(pose, vertices, light, x2, y2, .03f, x2 / .56f + .5f, y2 / .56f + .5f, 0, 0, 1, 1);
            vertex(pose, vertices, light, x2, y2, .03f, x2 / .56f + .5f, y2 / .56f + .5f, 0, 0, 1, 1);
            vertex(pose, vertices, light, 0, 0, -.03f, .5f, .5f, 0, 0, -1, .6f);
            vertex(pose, vertices, light, x2, y2, -.03f, x2 / .56f + .5f, y2 / .56f + .5f, 0, 0, -1, .6f);
            vertex(pose, vertices, light, x1, y1, -.03f, x1 / .56f + .5f, y1 / .56f + .5f, 0, 0, -1, .6f);
            vertex(pose, vertices, light, x1, y1, -.03f, x1 / .56f + .5f, y1 / .56f + .5f, 0, 0, -1, .6f);
            vertex(pose, vertices, light, x1, y1, -.03f, 0, 0, x1, y1, 0, .7f);
            vertex(pose, vertices, light, x2, y2, -.03f, 1, 0, x2, y2, 0, .7f);
            vertex(pose, vertices, light, x2, y2, .03f, 1, 1, x2, y2, 0, .7f);
            vertex(pose, vertices, light, x1, y1, .03f, 0, 1, x1, y1, 0, .7f);
        }
    }

    private static void vertex(PoseStack pose, VertexConsumer vertices, int light,
                               float x, float y, float z, float u, float v, float nx, float ny, float nz, float shade) {
        var entry = pose.last();
        vertices.vertex(entry.pose(), x, y, z).color(shade, shade, shade, 1f).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(entry.normal(), nx, ny, nz).endVertex();
    }
}
