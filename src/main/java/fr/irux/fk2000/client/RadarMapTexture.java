package fr.irux.fk2000.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import fr.irux.fk2000.network.RadarMaps;
import fr.irux.fk2000.network.VehicleStatus;

/** A screen-owned GPU texture; closing or replacing the tactical screen frees both CPU and GPU images. */
public final class RadarMapTexture implements AutoCloseable {
    private DynamicTexture texture;
    private ResourceLocation location;
    private int radius;
    private double centerX, centerZ;
    private int[] lastObserved;

    public static void accept(int vehicleId, int radius, double centerX, double centerZ, int[] colors) {
        if (Minecraft.getInstance().screen instanceof TacticalScreen screen)
            screen.acceptTerrainMap(vehicleId, radius, centerX, centerZ, colors);
    }

    void update(int radius, double x, double z, int[] pixels) {
        if (pixels.length != RadarMaps.PIXELS || radius < RadarMaps.MIN_RADIUS || radius > RadarMaps.MAX_RADIUS
                || !Double.isFinite(x) || !Double.isFinite(z)) return;
        if (texture == null) {
            texture = new DynamicTexture(RadarMaps.SIZE, RadarMaps.SIZE, false);
            location = Minecraft.getInstance().getTextureManager().register("fk2000_radar_terrain", texture);
        }
        NativeImage image = texture.getPixels();
        if (image == null) return;
        boolean sameView = this.radius == radius && Math.abs(centerX - x) < .001 && Math.abs(centerZ - z) < .001;
        if (!sameView || lastObserved == null) lastObserved = pixels.clone();
        for (int row = 0; row < RadarMaps.SIZE; row++) for (int column = 0; column < RadarMaps.SIZE; column++) {
            int index = row * RadarMaps.SIZE + column;
            int argb = pixels[index];
            // A map keeps its last observation when a distant area stops ticking.
            // Position/radius changes discard this small cache rather than moving stale terrain.
            if (sameView && argb == RadarMaps.UNKNOWN_COLOR) argb = lastObserved[index];
            else lastObserved[index] = argb;
            int abgr = argb & 0xff00ff00 | (argb >>> 16 & 255) | (argb & 255) << 16;
            image.setPixelRGBA(column, row, abgr);
        }
        texture.upload();
        this.radius = radius; centerX = x; centerZ = z;
    }

    void render(GuiGraphics graphics, int cx, int cy, int pixelRadius, int viewRadius, VehicleStatus status) {
        if (location == null || status == null) return;
        int size = Math.max(1, (int)Math.round(pixelRadius * 2.0 * radius / viewRadius));
        int x = cx - size / 2 + (int) Math.round((centerX - status.x()) / viewRadius * pixelRadius);
        int y = cy - size / 2 + (int) Math.round((centerZ - status.z()) / viewRadius * pixelRadius);
        graphics.enableScissor(cx - pixelRadius, cy - pixelRadius, cx + pixelRadius, cy + pixelRadius);
        graphics.blit(location, x, y, 0f, 0f, size, size, size, size);
        graphics.disableScissor();
    }

    @Override public void close() {
        if (location != null) Minecraft.getInstance().getTextureManager().release(location);
        location = null; texture = null; lastObserved = null;
    }
}
