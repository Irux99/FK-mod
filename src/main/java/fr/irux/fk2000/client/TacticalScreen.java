package fr.irux.fk2000.client;

import fr.irux.fk2000.network.ModNetwork;
import fr.irux.fk2000.network.ModNetwork.Command;
import fr.irux.fk2000.network.VehicleStatus;
import fr.irux.fk2000.network.RadarMaps;
import fr.irux.fk2000.physics.RadarDisplayMath;
import fr.irux.fk2000.radar.RadarContact;
import fr.irux.fk2000.radar.TargetMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/** Interactive radar drawing from server snapshots, never from a client-side target scan. */
public final class TacticalScreen extends Screen {
    private static final int GREEN = 0xff59e9b0;
    private final int vehicleId;
    private int radarX, radarY, radarSize, controlsX, buttonHeight;
    private int selectedId = -1;
    private boolean forward, backward, left, right, brake;
    private Button engineButton, radarButton, acquireButton, autoFireButton, targetModeButton;
    private Button zoomInButton, zoomOutButton;
    private final RadarMapTexture terrainMap = new RadarMapTexture();
    private int viewRadius;
    private long lastMapRequestTick = -1000;

    public TacticalScreen(int vehicleId) {
        super(Component.translatable("screen.iruxfk2000.tactical"));
        this.vehicleId = vehicleId;
    }

    @Override
    protected void init() {
        radarSize = Math.max(110, Math.min(280, Math.min(height - 160, width - 240)));
        radarX = Math.max(12, (width - radarSize - 208) / 2);
        radarY = height < 300 ? 58 : 66;
        buttonHeight = height < 300 ? 16 : 20;
        int spacing = buttonHeight + 4;
        controlsX = radarX + radarSize + 18;
        if (viewRadius == 0) {
            VehicleStatus status = ClientHooks.status();
            viewRadius = Math.max(RadarMaps.MIN_RADIUS, Math.min(RadarMaps.MAX_RADIUS,
                    status == null ? 160 : (int) Math.round(status.radarRange())));
        }
        lastMapRequestTick = -1000;
        zoomInButton = addRenderableWidget(Button.builder(Component.literal("+"), button -> zoom(true))
                .bounds(radarX + radarSize - 17, radarY + radarSize - 34, 14, 14)
                .tooltip(Tooltip.create(Component.translatable("screen.iruxfk2000.zoom_in"))).build());
        zoomOutButton = addRenderableWidget(Button.builder(Component.literal("−"), button -> zoom(false))
                .bounds(radarX + radarSize - 17, radarY + radarSize - 18, 14, 14)
                .tooltip(Tooltip.create(Component.translatable("screen.iruxfk2000.zoom_out"))).build());
        targetModeButton = addRenderableWidget(Button.builder(Component.translatable(TargetMode.AIRCRAFT.translationKey()).append(" >"), button ->
                minecraft.setScreen(new TargetSettingsScreen(this)))
                .bounds(radarX, 35, radarSize, 18)
                .tooltip(Tooltip.create(Component.translatable("screen.iruxfk2000.target_settings"))).build());
        int y = radarY;
        engineButton = button("engine", y, () -> ClientHooks.command(Command.ENGINE)); y += spacing;
        radarButton = button("radar", y, () -> ClientHooks.command(Command.RADAR)); y += spacing;
        acquireButton = button("auto_acquire", y, () -> ClientHooks.command(Command.AUTO_ACQUIRE)); y += spacing;
        autoFireButton = button("auto_fire", y, () -> ClientHooks.command(Command.AUTO_FIRE)); y += spacing + 6;
        button("launch", y, () -> ClientHooks.command(Command.FIRE)); y += spacing;
        button("reload", y, () -> ClientHooks.command(Command.RELOAD)); y += spacing + 6;
        button("camera", y, ClientHooks::openOnboard);
        y += spacing + 4;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(controlsX, Math.min(height - 28, Math.max(y, radarY + radarSize + 17)), 188, buttonHeight).build());
    }

    private Button button(String key, int y, Runnable action) {
        return addRenderableWidget(Button.builder(Component.translatable("button.iruxfk2000." + key), b -> action.run())
                .bounds(controlsX, y, 188, buttonHeight).build());
    }

    @Override
    public void tick() {
        ModNetwork.sendCommand(Command.DRIVE, (forward ? 1 : 0) - (backward ? 1 : 0),
                (right ? 1 : 0) - (left ? 1 : 0), brake, -1);
        VehicleStatus s = ClientHooks.status();
        if (s != null) {
            engineButton.setMessage(toggle("engine", s.engine()));
            radarButton.setMessage(toggle("radar", s.radar()));
            acquireButton.setMessage(toggle("auto_acquire", s.autoAcquire()));
            autoFireButton.setMessage(toggle("auto_fire", s.autoFire()));
            targetModeButton.setMessage(Component.translatable(s.targetMode().translationKey()).append(" >"));
            if (selectedId < 0) selectedId = s.targetId();
            if (minecraft != null && minecraft.level != null) {
                long tick = minecraft.level.getGameTime();
                if (tick - lastMapRequestTick >= 40 || tick < lastMapRequestTick) {
                    RadarMaps.request(vehicleId, viewRadius);
                    lastMapRequestTick = tick;
                }
            }
        }
    }

    private void zoom(boolean closer) {
        int next = RadarDisplayMath.zoom(viewRadius, closer);
        if (next != viewRadius) { viewRadius = next; lastMapRequestTick = -1000; }
    }

    void acceptTerrainMap(int vehicleId, int radius, double x, double z, int[] colors) {
        if (this.vehicleId == vehicleId && radius == viewRadius) terrainMap.update(radius, x, z, colors);
    }

    private Component toggle(String key, boolean enabled) {
        return Component.translatable("button.iruxfk2000." + key).append(": ")
                .append(Component.translatable(enabled ? "options.on" : "options.off"));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, width, height, 0xf0101d25, 0xf0050d13);
        g.drawString(font, "IRUX / FK-2000", radarX, 8, GREEN, false);
        g.drawString(font, font.plainSubstrByWidth(title.getString(), radarSize + 8), radarX, 21, 0xffc7d4dc, false);
        g.fill(radarX - 2, radarY - 2, radarX + radarSize + 2, radarY + radarSize + 2, 0xff296951);
        g.fill(radarX, radarY, radarX + radarSize, radarY + radarSize, 0xff041b16);
        int cx = radarX + radarSize / 2, cy = radarY + radarSize / 2, radius = radarSize / 2 - 9;
        VehicleStatus s = ClientHooks.status();
        terrainMap.render(g, cx, cy, radius, viewRadius, s);
        for (int ring = 1; ring <= 4; ring++) drawCircle(g, cx, cy, radius * ring / 4, 0xff184b3c);
        g.hLine(radarX + 4, radarX + radarSize - 5, cy, 0xff184b3c);
        g.vLine(cx, radarY + 4, radarY + radarSize - 5, 0xff184b3c);
        g.drawCenteredString(font, "N", cx, radarY + 3, GREEN);
        g.drawString(font, "?", radarX + radarSize - 10, radarY + 3, 0xffc4d5d1, false);
        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xffecf9f2);
        double sweep = minecraft == null || minecraft.level == null ? 0
                : (minecraft.level.getGameTime() + partialTick) / 120.0 * Math.PI * 2 - Math.PI / 2;
        if (s != null && s.radar()) {
            for (int trail = 7; trail >= 0; trail--) {
                double angle = sweep - trail * .035;
                drawLine(g,cx,cy,cx + (int) (Math.cos(angle) * radius),cy + (int) (Math.sin(angle) * radius),
                        trail == 0 ? 0xff74f8b8 : 0x1824c27d);
            }
        }
        g.drawString(font, Component.translatable("screen.iruxfk2000.zoom_radius", viewRadius),
                radarX + 4, radarY + radarSize - 11, 0xffc4e4d4, false);
        if (s == null || s.vehicleId() != vehicleId) {
            g.drawString(font, Component.translatable("screen.iruxfk2000.waiting"), radarX, radarY + radarSize + 8, 0xffeac878, false);
        } else {
            VehicleHealthDisplay.draw(g, font, controlsX, 8, 188, s.health(), s.maxHealth());
            double range = Math.max(1, viewRadius);
            RadarContact hover = nearestContact(s, mouseX, mouseY);
            for (RadarContact contact : s.contacts()) {
                double dx = contact.x() - s.x(), dz = contact.z() - s.z();
                if (dx * dx + dz * dz > range * range) continue;
                int px = cx + (int) (dx / range * radius), py = cy + (int) (dz / range * radius);
                boolean locked = contact.entityId() == s.targetId();
                boolean queued = s.selectedTargetIds().contains(contact.entityId());
                int color = locked ? 0xffffbd55 : queued ? 0xff74cfff : contact.entityId() == selectedId ? 0xfff3faf7 : GREEN;
                double echo = RadarDisplayMath.brightness(Math.atan2(dz, dx), sweep);
                color = shade(color, locked || queued ? Math.max(.7, echo) : echo);
                g.fill(px - 3, py - 3, px + 4, py + 4, 0xff03100b);
                g.fill(px - 2, py - 2, px + 3, py + 3, color);
                if (queued) {
                    g.hLine(px - 4, px + 4, py - 4, color);
                    g.hLine(px - 4, px + 4, py + 4, color);
                    g.vLine(px - 4, py - 4, py + 4, color);
                    g.vLine(px + 4, py - 4, py + 4, color);
                }
                if (locked) {
                    g.hLine(px - 5, px + 5, py - 5, color);
                    g.hLine(px - 5, px + 5, py + 5, color);
                    g.vLine(px - 5, py - 5, py + 5, color);
                    g.vLine(px + 5, py - 5, py + 5, color);
                }
            }
            if (s.targetMode() == TargetMode.COORDINATES && s.targetCoordinates() != null) {
                double dx = s.targetCoordinates().x - s.x(), dz = s.targetCoordinates().z - s.z();
                double scale = radius / Math.max(range, Math.sqrt(dx * dx + dz * dz));
                int px = cx + (int) (dx * scale), py = cy + (int) (dz * scale);
                g.hLine(px - 4, px + 4, py, 0xffffbd55);
                g.vLine(px, py - 4, py + 4, 0xffffbd55);
            }
            int y = radarY + radarSize + 8;
            if (!s.selectedTargetIds().isEmpty())
                g.drawString(font, Component.translatable("screen.iruxfk2000.queued_targets", s.selectedTargetIds().size()),
                        controlsX, radarY - 13, 0xff74cfff, false);
            String speed = String.format(Locale.ROOT, "%.1f km/h", Math.abs(s.speed()) * 72);
            g.drawString(font, speed, radarX, y, GREEN, false);
            g.drawString(font, Component.translatable("screen.iruxfk2000.ammo", s.missiles()), radarX, y + 11, GREEN, false);
            g.drawString(font, Component.translatable("screen.iruxfk2000.range", (int) s.radarRange()), radarX, y + 22, GREEN, false);
            RadarContact chosen = s.contacts().stream().filter(c -> c.entityId() == selectedId).findFirst().orElse(null);
            if (chosen != null) {
                String targetLabel = Math.round(chosen.distance()) + " m / " + chosen.name();
                g.drawString(font, font.plainSubstrByWidth(targetLabel, radarSize), radarX, y + 35, 0xffffbd55, false);
            }
            if (s.targetMode() == TargetMode.COORDINATES && s.targetCoordinates() != null) {
                String point = String.format(Locale.ROOT, "X %.0f Y %.0f Z %.0f", s.targetCoordinates().x,
                        s.targetCoordinates().y, s.targetCoordinates().z);
                g.drawString(font, font.plainSubstrByWidth(point, radarSize), radarX, y + 35, 0xffffbd55, false);
                if (mouseX >= radarX && mouseX <= radarX + radarSize && mouseY >= y + 32 && mouseY <= y + 46)
                    g.renderTooltip(font, Component.literal(point), mouseX, mouseY);
            }
            if (hover != null) g.renderTooltip(font, Component.literal(hover.name() + " / " + Math.round(hover.distance()) + " m"), mouseX, mouseY);
        }
        g.drawString(font, Component.translatable("screen.iruxfk2000.drive_help"), radarX, height - 17, 0xff8da69b, false);
        super.render(g, mouseX, mouseY, partialTick);
        if (mouseX >= radarX + radarSize - 13 && mouseX <= radarX + radarSize
                && mouseY >= radarY && mouseY <= radarY + 14)
            g.renderTooltip(font, Component.translatable("screen.iruxfk2000.terrain_unknown"), mouseX, mouseY);
    }

    private static int shade(int rgb, double brightness) {
        int red = (int) ((rgb >> 16 & 255) * brightness);
        int green = (int) ((rgb >> 8 & 255) * brightness);
        int blue = (int) ((rgb & 255) * brightness);
        return 0xff000000 | red << 16 | green << 8 | blue;
    }

    private static void drawLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        while (true) {
            graphics.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            int twice = error * 2;
            if (twice >= dy) { error += dy; x0 += sx; }
            if (twice <= dx) { error += dx; y0 += sy; }
        }
    }

    private static void drawCircle(GuiGraphics g, int x, int y, int radius, int color) {
        for (int degree = 0; degree < 360; degree += 3) {
            double angle = Math.toRadians(degree);
            int px = x + (int) Math.round(Math.cos(angle) * radius);
            int py = y + (int) Math.round(Math.sin(angle) * radius);
            g.fill(px, py, px + 1, py + 1, color);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (zoomInButton.isMouseOver(mouseX, mouseY) || zoomOutButton.isMouseOver(mouseX, mouseY))
            return super.mouseClicked(mouseX, mouseY, button);
        VehicleStatus s = ClientHooks.status();
        if (button == 0 && s != null && mouseX >= radarX && mouseX <= radarX + radarSize
                && mouseY >= radarY && mouseY <= radarY + radarSize) {
            RadarContact contact = nearestContact(s, mouseX, mouseY);
            if (contact != null) {
                selectedId = contact.entityId();
                ModNetwork.sendCommand(Command.SELECT_TARGET, 0, 0, false, selectedId);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Overlapping hit areas select the closest echo, consistently with its tooltip. */
    private RadarContact nearestContact(VehicleStatus status, double mouseX, double mouseY) {
        RadarContact nearest = null;
        double closest = 81;
        int radius = radarSize / 2 - 9;
        for (RadarContact contact : status.contacts()) {
            double dx = contact.x() - status.x(), dz = contact.z() - status.z();
            if (dx * dx + dz * dz > (double) viewRadius * viewRadius) continue;
            double px = radarX + radarSize / 2.0 + dx / viewRadius * radius;
            double py = radarY + radarSize / 2.0 + dz / viewRadius * radius;
            double distance = (mouseX - px) * (mouseX - px) + (mouseY - py) * (mouseY - py);
            if (distance <= closest) { closest = distance; nearest = contact; }
        }
        return nearest;
    }

    @Override public boolean mouseScrolled(double x, double y, double delta) {
        if (x >= radarX && x <= radarX + radarSize && y >= radarY && y <= radarY + radarSize && delta != 0) {
            zoom(delta > 0); return true;
        }
        return super.mouseScrolled(x, y, delta);
    }

    private boolean setDriveKey(int key, int scanCode, boolean pressed) {
        if (minecraft == null) return false;
        if (minecraft.options.keyUp.matches(key, scanCode) || key == GLFW.GLFW_KEY_UP) forward = pressed;
        else if (minecraft.options.keyDown.matches(key, scanCode) || key == GLFW.GLFW_KEY_DOWN) backward = pressed;
        else if (minecraft.options.keyLeft.matches(key, scanCode) || key == GLFW.GLFW_KEY_LEFT) left = pressed;
        else if (minecraft.options.keyRight.matches(key, scanCode) || key == GLFW.GLFW_KEY_RIGHT) right = pressed;
        else if (minecraft.options.keyJump.matches(key, scanCode)) brake = pressed;
        else return false;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (getFocused() instanceof net.minecraft.client.gui.components.EditBox) return super.keyPressed(keyCode, scanCode, modifiers);
        if (setDriveKey(keyCode, scanCode, true)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (setDriveKey(keyCode, scanCode, false)) return true;
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        terrainMap.close();
        ModNetwork.sendCommand(Command.DRIVE, 0, 0, true, -1);
        forward = backward = left = right = brake = false;
    }

    @Override
    public void onClose() {
        ClientHooks.closeRemote(true);
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
