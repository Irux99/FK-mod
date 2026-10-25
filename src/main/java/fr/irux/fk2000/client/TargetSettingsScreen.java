package fr.irux.fk2000.client;

import fr.irux.fk2000.config.ModConfig;
import fr.irux.fk2000.network.ModNetwork;
import fr.irux.fk2000.network.VehicleStatus;
import fr.irux.fk2000.radar.TargetCoordinates;
import fr.irux.fk2000.radar.TargetMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.math.BigDecimal;

/** Server-backed targeting and launch-cap settings; text entry never drives the truck. */
public final class TargetSettingsScreen extends Screen {
    private final TacticalScreen parent;
    private EditBox xInput, yInput, zInput, rangeInput;
    private Button modeButton, capButton, fireModeButton, applyButton, rangeApplyButton;
    private int left, formWidth, baseY, buttonHeight, row, coordinateLabelY, inputY, helpY, rangeY;

    public TargetSettingsScreen(TacticalScreen parent) {
        super(Component.translatable("screen.iruxfk2000.target_settings"));
        this.parent = parent;
    }

    @Override protected void init() {
        formWidth = Math.min(320, width - 32);
        left = (width - formWidth) / 2;
        baseY = Math.max(34, (height - 260) / 2);
        buttonHeight = height < 300 ? 18 : 20;
        row = buttonHeight + 4;
        modeButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            VehicleStatus status = ClientHooks.status();
            if (status != null) ModNetwork.sendTargetMode(status.targetMode().next());
        }).bounds(left, baseY, formWidth, buttonHeight).build());
        capButton = addRenderableWidget(Button.builder(Component.empty(), button ->
                ClientHooks.command(ModNetwork.Command.CYCLE_CAP_MODE))
                .bounds(left, baseY + row, formWidth, buttonHeight).build());
        fireModeButton = addRenderableWidget(Button.builder(Component.empty(), button ->
                ClientHooks.command(ModNetwork.Command.CYCLE_FIRE_MODE))
                .bounds(left, baseY + row * 2, formWidth, buttonHeight).build());
        rangeY = baseY + row * 3 + 2;
        rangeInput = addRenderableWidget(new EditBox(font, left + formWidth - 145, rangeY, 76, buttonHeight,
                Component.translatable("screen.iruxfk2000.engagement_range")));
        rangeInput.setMaxLength(4);
        rangeInput.setFilter(value -> value.matches("\\d{0,4}"));
        VehicleStatus current = ClientHooks.status();
        rangeInput.setValue(Integer.toString(current == null ? 160 : (int) Math.round(current.radarRange())));
        rangeApplyButton = addRenderableWidget(Button.builder(Component.translatable("button.iruxfk2000.apply_range"), button -> applyRange())
                .bounds(left + formWidth - 61, rangeY, 61, buttonHeight).build());
        rangeInput.setResponder(value -> updateValidity());
        coordinateLabelY = rangeY + buttonHeight + 9;
        inputY = coordinateLabelY + 22;
        helpY = inputY + buttonHeight + 6;
        int inputWidth = (formWidth - 16) / 3;
        xInput = coordinateField(left, inputY, inputWidth, "X");
        yInput = coordinateField(left + inputWidth + 8, inputY, inputWidth, "Y");
        zInput = coordinateField(left + (inputWidth + 8) * 2, inputY, inputWidth, "Z");
        VehicleStatus status = ClientHooks.status();
        Vec3 initial = status == null ? Vec3.ZERO : status.targetCoordinates() == null
                ? new Vec3(status.x(), status.y() + 20, status.z()) : status.targetCoordinates();
        xInput.setValue(format(initial.x));
        yInput.setValue(format(initial.y));
        zInput.setValue(format(initial.z));
        applyButton = addRenderableWidget(Button.builder(Component.translatable("button.iruxfk2000.apply_coordinates"), button -> applyCoordinates())
                .bounds(left, helpY + 25, formWidth, buttonHeight).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(left, helpY + 25 + row, formWidth, buttonHeight).build());
        xInput.setResponder(value -> updateValidity());
        yInput.setResponder(value -> updateValidity());
        zInput.setResponder(value -> updateValidity());
        tick();
    }

    private EditBox coordinateField(int x, int y, int size, String axis) {
        EditBox field = addRenderableWidget(new EditBox(font, x, y, size, buttonHeight, Component.literal(axis)));
        field.setMaxLength(24);
        field.setFilter(value -> value.matches("[-+]?\\d*([.,]\\d*)?"));
        return field;
    }

    private static String format(double coordinate) {
        return BigDecimal.valueOf(coordinate).setScale(3, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    private Vec3 enteredCoordinates() {
        try {
            return new Vec3(Double.parseDouble(xInput.getValue().replace(',', '.')),
                    Double.parseDouble(yInput.getValue().replace(',', '.')), Double.parseDouble(zInput.getValue().replace(',', '.')));
        } catch (NumberFormatException ignored) { return null; }
    }

    private boolean coordinatesValid() {
        VehicleStatus status = ClientHooks.status();
        return minecraft != null && minecraft.level != null && status != null
                && TargetCoordinates.isValid(minecraft.level, new Vec3(status.x(), status.y(), status.z()), enteredCoordinates());
    }

    private double enteredRange() {
        try { return Double.parseDouble(rangeInput.getValue()); }
        catch (NumberFormatException ignored) { return Double.NaN; }
    }

    private boolean rangeValid() {
        double range = enteredRange();
        return ClientHooks.status() != null && Double.isFinite(range) && range >= 16 && range <= ModConfig.MAX_ENGAGEMENT_RANGE.get();
    }

    private void applyRange() { if (rangeValid()) ModNetwork.sendEngagementRange(enteredRange()); }

    private void updateValidity() {
        if (applyButton != null) applyButton.active = coordinatesValid();
        if (rangeApplyButton != null) rangeApplyButton.active = rangeValid();
    }

    private void applyCoordinates() {
        if (!coordinatesValid()) return;
        Vec3 point = enteredCoordinates();
        ModNetwork.sendTargetCoordinates(point.x, point.y, point.z);
        onClose();
    }

    @Override public void tick() {
        VehicleStatus status = ClientHooks.status();
        modeButton.active = capButton.active = fireModeButton.active = status != null;
        if (status != null && status.targetMode() == TargetMode.COORDINATES) fireModeButton.active = false;
        modeButton.setMessage(Component.translatable("button.iruxfk2000.target_mode").append(": ")
                .append(Component.translatable((status == null ? TargetMode.AIRCRAFT : status.targetMode()).translationKey())));
        capButton.setMessage(Component.translatable("button.iruxfk2000.tube_caps").append(": ")
                .append(Component.translatable("tube_cap.iruxfk2000." + (status == null ? "none"
                        : status.tubeCapMode().name().toLowerCase(Locale.ROOT)))));
        fireModeButton.setMessage(Component.translatable("button.iruxfk2000.auto_fire_mode").append(": ")
                .append(Component.translatable("auto_fire.iruxfk2000." + (status == null ? "salvo"
                        : status.autoFireMode().name().toLowerCase(Locale.ROOT)))));
        xInput.tick(); yInput.tick(); zInput.tick(); rangeInput.tick();
        updateValidity();
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, width, height, 0xff101d25, 0xff050d13);
        graphics.drawCenteredString(font, title, width / 2, 20, 0xff59e9b0);
        graphics.drawString(font, Component.translatable("screen.iruxfk2000.engagement_range"), left, rangeY + 5,
                rangeValid() ? 0xffe0f4ea : 0xffffbd55, false);
        graphics.drawString(font, Component.translatable("screen.iruxfk2000.coordinate_target"), left, coordinateLabelY, 0xffe0f4ea, false);
        graphics.drawString(font, "X", xInput.getX(), coordinateLabelY + 12, 0xff8da69b, false);
        graphics.drawString(font, "Y", yInput.getX(), coordinateLabelY + 12, 0xff8da69b, false);
        graphics.drawString(font, "Z", zInput.getX(), coordinateLabelY + 12, 0xff8da69b, false);
        Component help = coordinatesValid() ? Component.translatable("screen.iruxfk2000.coordinates_help")
                : Component.translatable("screen.iruxfk2000.coordinates_invalid", ModConfig.COORDINATE_TARGET_RANGE.get().intValue());
        int color = coordinatesValid() ? 0xff8da69b : 0xffffbd55;
        int y = helpY;
        for (var line : font.split(help, formWidth)) {
            graphics.drawString(font, line, left, y, color, false);
            y += 10;
            if (y > helpY + 22) break;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (rangeInput.isFocused()) { applyRange(); return true; }
            if (coordinatesValid()) applyCoordinates();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
