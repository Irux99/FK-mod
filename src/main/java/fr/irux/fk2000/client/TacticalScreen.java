package fr.irux.fk2000.client;

import fr.irux.fk2000.network.ModNetwork;
import fr.irux.fk2000.network.ModNetwork.Command;
import fr.irux.fk2000.network.VehicleStatus;
import fr.irux.fk2000.radar.RadarContact;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
    private Button engineButton, radarButton, acquireButton, autoFireButton;

    public TacticalScreen(int vehicleId) {
        super(Component.translatable("screen.iruxfk2000.tactical"));
        this.vehicleId = vehicleId;
    }

    @Override
    protected void init() {
        radarSize = Math.max(110, Math.min(280, Math.min(height - 145, width - 240)));
        radarX = Math.max(12, (width - radarSize - 208) / 2);
        radarY = height < 300 ? 52 : 66;
        buttonHeight = height < 300 ? 16 : 20;
        int spacing = buttonHeight + 4;
        controlsX = radarX + radarSize + 18;
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
            if (selectedId < 0) selectedId = s.targetId();
        }
    }

    private Component toggle(String key, boolean enabled) {
        return Component.translatable("button.iruxfk2000." + key).append(": ")
                .append(Component.translatable(enabled ? "options.on" : "options.off"));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, width, height, 0xf0101d25, 0xf0050d13);
        g.drawString(font, "IRUX / FK-2000", radarX, 20, GREEN, false);
        g.drawString(font, title, radarX, 35, 0xffc7d4dc, false);
        g.fill(radarX - 2, radarY - 2, radarX + radarSize + 2, radarY + radarSize + 2, 0xff296951);
        g.fill(radarX, radarY, radarX + radarSize, radarY + radarSize, 0xff041b16);
        int cx = radarX + radarSize / 2, cy = radarY + radarSize / 2, radius = radarSize / 2 - 9;
        for (int ring = 1; ring <= 4; ring++) drawCircle(g, cx, cy, radius * ring / 4, 0xff184b3c);
        g.hLine(radarX + 4, radarX + radarSize - 5, cy, 0xff184b3c);
        g.vLine(cx, radarY + 4, radarY + radarSize - 5, 0xff184b3c);
        g.drawCenteredString(font, "N", cx, radarY + 3, GREEN);
        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xffecf9f2);
        VehicleStatus s = ClientHooks.status();
        if (s == null || s.vehicleId() != vehicleId) {
            g.drawString(font, Component.translatable("screen.iruxfk2000.waiting"), radarX, radarY + radarSize + 8, 0xffeac878, false);
        } else {
            VehicleHealthDisplay.draw(g, font, controlsX, 20, 188, s.health(), s.maxHealth());
            double range = Math.max(1, s.radarRange());
            RadarContact hover = null;
            for (RadarContact contact : s.contacts()) {
                double dx = contact.x() - s.x(), dz = contact.z() - s.z();
                if (dx * dx + dz * dz > range * range) continue;
                int px = cx + (int) (dx / range * radius), py = cy + (int) (dz / range * radius);
                boolean locked = contact.entityId() == s.targetId();
                int color = locked ? 0xffffbd55 : contact.entityId() == selectedId ? 0xfff3faf7 : GREEN;
                g.fill(px - 2, py - 2, px + 3, py + 3, color);
                if (locked) {
                    g.hLine(px - 5, px + 5, py - 5, color);
                    g.hLine(px - 5, px + 5, py + 5, color);
                    g.vLine(px - 5, py - 5, py + 5, color);
                    g.vLine(px + 5, py - 5, py + 5, color);
                }
                if (Math.abs(mouseX - px) < 8 && Math.abs(mouseY - py) < 8) hover = contact;
            }
            int y = radarY + radarSize + 8;
            String speed = String.format(Locale.ROOT, "%.1f km/h", Math.abs(s.speed()) * 72);
            g.drawString(font, speed, radarX, y, GREEN, false);
            g.drawString(font, Component.translatable("screen.iruxfk2000.ammo", s.missiles()), radarX, y + 11, GREEN, false);
            g.drawString(font, Component.translatable("screen.iruxfk2000.range", (int) range), radarX, y + 22, GREEN, false);
            RadarContact chosen = s.contacts().stream().filter(c -> c.entityId() == selectedId).findFirst().orElse(null);
            if (chosen != null) {
                String targetLabel = Math.round(chosen.distance()) + " m / " + chosen.name();
                g.drawString(font, font.plainSubstrByWidth(targetLabel, radarSize), radarX, y + 35, 0xffffbd55, false);
            }
            if (hover != null) g.renderTooltip(font, Component.literal(hover.name() + " / " + Math.round(hover.distance()) + " m"), mouseX, mouseY);
        }
        g.drawString(font, Component.translatable("screen.iruxfk2000.drive_help"), radarX, height - 17, 0xff8da69b, false);
        super.render(g, mouseX, mouseY, partialTick);
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
        VehicleStatus s = ClientHooks.status();
        if (button == 0 && s != null && mouseX >= radarX && mouseX <= radarX + radarSize
                && mouseY >= radarY && mouseY <= radarY + radarSize) {
            int radius = radarSize / 2 - 9;
            for (RadarContact contact : s.contacts()) {
                double px = radarX + radarSize / 2.0 + (contact.x() - s.x()) / Math.max(1, s.radarRange()) * radius;
                double py = radarY + radarSize / 2.0 + (contact.z() - s.z()) / Math.max(1, s.radarRange()) * radius;
                if ((mouseX - px) * (mouseX - px) + (mouseY - py) * (mouseY - py) <= 81) {
                    selectedId = contact.entityId();
                    ModNetwork.sendCommand(Command.SELECT_TARGET, 0, 0, false, selectedId);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
