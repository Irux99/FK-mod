package fr.irux.fk2000.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Shared hull indicator for the driver's HUD and server-backed tactical telemetry. */
final class VehicleHealthDisplay {
    private VehicleHealthDisplay() {}

    static void draw(GuiGraphics graphics, Font font, int x, int y, int width, float health, float maximum) {
        float fraction = maximum > 0 && Float.isFinite(maximum) && Float.isFinite(health)
                ? Mth.clamp(health / maximum, 0, 1) : 0;
        int color = fraction <= 0.25f ? 0xfff1645e : fraction <= 0.5f ? 0xffffbd55 : 0xff59e9b0;
        graphics.drawString(font, Component.translatable("screen.iruxfk2000.health",
                Math.max(0, (int) Math.ceil(health)), Math.max(1, (int) Math.ceil(maximum))), x, y, color, false);
        graphics.fill(x, y + 12, x + width, y + 19, 0xff10221e);
        graphics.fill(x + 1, y + 13, x + 1 + Math.round((width - 2) * fraction), y + 18, color);
        if (fraction <= 0.25f)
            graphics.drawString(font, Component.translatable("screen.iruxfk2000.health_critical"),
                    x, y + 23, color, false);
    }
}
