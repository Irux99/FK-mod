package fr.irux.fk2000.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Imported user model with client-only articulated joints; no SBW assets. */
final class ImportedMesh {
    private static final ResourceLocation WHITE = new ResourceLocation("iruxfk2000", "textures/entity/mesh-white.png");
    private record Part(float[] vertices, float x, float y, float z, String animation) {}
    static boolean isAvailable(String name) {
        var resources=Minecraft.getInstance().getResourceManager();
        return resources.getResource(new ResourceLocation("iruxfk2000","mesh/"+name+".json")).isPresent()
            && resources.getResource(new ResourceLocation("iruxfk2000","textures/entity/imported-"+name+".png")).isPresent();
    }
    private final List<Part> parts = new ArrayList<>();
    private final ResourceLocation texture;
    ImportedMesh(String name) { this(name, WHITE); }
    ImportedMesh(String name, ResourceLocation texture) {
        this.texture = texture;
        try (var stream = Minecraft.getInstance().getResourceManager().getResourceOrThrow(
                new ResourceLocation("iruxfk2000", "mesh/" + name + ".json")).open();
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.has("parts")) for (var value : root.getAsJsonArray("parts")) {
                JsonObject part = value.getAsJsonObject();
                JsonArray pivot = part.getAsJsonArray("pivot");
                parts.add(new Part(read(part.getAsJsonArray("vertices")),pivot.get(0).getAsFloat(),
                    pivot.get(1).getAsFloat(),pivot.get(2).getAsFloat(),part.get("animation").getAsString()));
            } else parts.add(new Part(read(root.getAsJsonArray("vertices")),0,0,0,""));
        } catch (Exception e) { throw new IllegalStateException("Cannot load imported mesh " + name, e); }
    }
    private static float[] read(JsonArray data) {
        if (data.size()%3!=0) throw new IllegalArgumentException("Mesh must contain complete triangles");
        float[] vertices = new float[data.size()*11];
        for (int i=0;i<data.size();i++) for(int j=0;j<11;j++)
            vertices[i*11+j]=j<data.get(i).getAsJsonArray().size()?data.get(i).getAsJsonArray().get(j).getAsFloat():.5f;
        // Canonical outward winding before the renderer's Y-axis conversion.
        for (int i=0;i<vertices.length;i+=33) {
            float ax=vertices[i+11]-vertices[i],ay=vertices[i+12]-vertices[i+1],az=vertices[i+13]-vertices[i+2];
            float bx=vertices[i+22]-vertices[i],by=vertices[i+23]-vertices[i+1],bz=vertices[i+24]-vertices[i+2];
            float dot=(ay*bz-az*by)*(vertices[i+3]+vertices[i+14]+vertices[i+25])
                +(az*bx-ax*bz)*(vertices[i+4]+vertices[i+15]+vertices[i+26])
                +(ax*by-ay*bx)*(vertices[i+5]+vertices[i+16]+vertices[i+27]);
            if (dot<0) for (int j=0;j<11;j++) { float value=vertices[i+11+j];vertices[i+11+j]=vertices[i+22+j];vertices[i+22+j]=value; }
        }
        return vertices;
    }
    void render(PoseStack pose, MultiBufferSource buffers, int light) { render(pose,buffers,light,0,0); }
    void render(PoseStack pose, MultiBufferSource buffers, int light, float wheelRotation, float launcherProgress) {
        render(pose, buffers, light, wheelRotation, launcherProgress, false);
    }
    void render(PoseStack pose, MultiBufferSource buffers, int light, float wheelRotation, float launcherProgress,
                boolean clearCabWindows) {
        render(pose, buffers, light, wheelRotation, launcherProgress, clearCabWindows, 0);
    }
    void render(PoseStack pose, MultiBufferSource buffers, int light, float wheelRotation, float launcherProgress,
                boolean clearCabWindows, float suspensionCompression) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(texture));
        for (Part part : parts) {
            pose.pushPose();
            // The sprung body moves around the front axle; tires remain planted on the road.
            if (!part.animation.equals("wheel")) VehicleVisuals.applyModelSuspension(pose, suspensionCompression);
            pose.translate(part.x,-part.y,part.z);
            if (part.animation.equals("wheel")) pose.mulPose(Axis.XP.rotation(-wheelRotation));
            if (part.animation.equals("launcher")) pose.mulPose(Axis.XP.rotation(launcherProgress*(float)Math.PI/2));
            var transform=pose.last();float[] v=part.vertices;
            for(int triangle=0;triangle<v.length;triangle+=33) {
                // The supplied windshield is an opaque closed slab. Remove only its
                // triangles for an interior camera; keep the body and exterior art.
                if (clearCabWindows && isCabWindow(v, triangle)) continue;
                for(int corner=0;corner<4;corner++) {
                int i=triangle+(corner==0?0:corner==1?2:1)*11;
                consumer.vertex(transform.pose(),v[i]-part.x,-(v[i+1]-part.y),v[i+2]-part.z)
                    .color(v[i+6],v[i+7],v[i+8],1f).uv(v[i+9],v[i+10])
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                    .normal(transform.normal(),v[i+3],-v[i+4],v[i+5]).endVertex();
                }
            }
            pose.popPose();
        }
    }

    private static boolean isCabWindow(float[] vertices, int triangle) {
        for (int corner = 0; corner < 3; corner++) {
            int i = triangle + corner * 11;
            if (Math.abs(vertices[i]) > 0.56F || vertices[i + 1] < 0.92F || vertices[i + 1] > 1.27F
                    || vertices[i + 2] < 2.39F || vertices[i + 2] > 2.44F) return false;
        }
        return true;
    }
}
