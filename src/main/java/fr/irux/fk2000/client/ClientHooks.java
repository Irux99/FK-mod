package fr.irux.fk2000.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.irux.fk2000.entity.vehicle.Fk2000Entity;
import fr.irux.fk2000.network.ModNetwork;
import fr.irux.fk2000.network.ModNetwork.Command;
import fr.irux.fk2000.network.VehicleStatus;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Client session state only: every command is independently checked by the server. */
@Mod.EventBusSubscriber(modid = "iruxfk2000", value = Dist.CLIENT)
public final class ClientHooks {
    public static final KeyMapping TACTICAL = new KeyMapping("key.iruxfk2000.tactical", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G, "key.categories.iruxfk2000");
    public static final KeyMapping FIRE = new KeyMapping("key.iruxfk2000.fire", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V, "key.categories.iruxfk2000");
    public static final KeyMapping ENGINE = new KeyMapping("key.iruxfk2000.engine", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_I, "key.categories.iruxfk2000");
    public static final KeyMapping RADAR = new KeyMapping("key.iruxfk2000.radar", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R, "key.categories.iruxfk2000");
    private static int remoteVehicleId = -1;
    private static VehicleStatus status;
    private static boolean onboard;
    private static CameraType previousCameraType;
    private static long lastStatusTick;
    private static ResourceKey<Level> sessionDimension;

    private ClientHooks() {}

    public static VehicleStatus status() { return status; }
    public static int remoteVehicleId() { return remoteVehicleId; }
    public static boolean isOnboard() { return onboard; }

    public static void openRemote(int vehicleId) {
        Minecraft mc = Minecraft.getInstance();
        if (vehicleId < 0) {
            if (mc.screen instanceof TacticalScreen) mc.setScreen(null);
            closeRemote(false);
            return;
        }
        restoreCamera();
        sessionDimension = mc.level == null ? null : mc.level.dimension();
        if (remoteVehicleId != vehicleId) status = null;
        remoteVehicleId = vehicleId;
        lastStatusTick = mc.level == null ? 0 : mc.level.getGameTime();
        mc.setScreen(new TacticalScreen(vehicleId));
    }

    public static void acceptStatus(VehicleStatus incoming) {
        if (incoming.vehicleId() != remoteVehicleId) return;
        status = incoming;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) lastStatusTick = mc.level.getGameTime();
    }

    public static void command(Command action) { ModNetwork.sendCommand(action, 0, 0, false, -1); }

    public static void openOnboard() {
        Minecraft mc = Minecraft.getInstance();
        Entity vehicle = mc.level == null ? null : mc.level.getEntity(remoteVehicleId);
        if (!(vehicle instanceof Fk2000Entity)) {
            if (mc.player != null) mc.player.displayClientMessage(Component.translatable("message.iruxfk2000.camera_unavailable"), true);
            return;
        }
        previousCameraType = mc.options.getCameraType();
        mc.setScreen(null);
        onboard = true;
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.setCameraEntity(vehicle);
    }

    public static void restoreCamera() {
        Minecraft mc = Minecraft.getInstance();
        if (onboard) {
            mc.setCameraEntity(mc.player);
            if (previousCameraType != null) mc.options.setCameraType(previousCameraType);
        }
        onboard = false;
        previousCameraType = null;
    }

    public static void closeRemote(boolean notifyServer) {
        if (notifyServer && Minecraft.getInstance().getConnection() != null && remoteVehicleId >= 0) command(Command.CLOSE);
        restoreCamera();
        remoteVehicleId = -1;
        status = null;
        sessionDimension = null;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            closeRemote(false);
            return;
        }
        if (remoteVehicleId >= 0 && (!mc.player.isAlive() || !mc.level.dimension().equals(sessionDimension) || mc.level.getGameTime() - lastStatusTick > 100)) {
            if (mc.screen instanceof TacticalScreen) mc.setScreen(null);
            closeRemote(true);
            return;
        }
        while (TACTICAL.consumeClick()) {
            if (onboard) {
                restoreCamera();
                mc.setScreen(new TacticalScreen(remoteVehicleId));
            } else if (mc.player.getVehicle() instanceof Fk2000Entity vehicle && mc.screen == null) {
                // The server accepts commands for the authorized ridden vehicle as well.
                remoteVehicleId = vehicle.getId();
                lastStatusTick = mc.level.getGameTime();
                sessionDimension = mc.level.dimension();
                mc.setScreen(new TacticalScreen(remoteVehicleId));
            }
        }
        if (onboard) {
            Entity vehicle = mc.level.getEntity(remoteVehicleId);
            if (!(vehicle instanceof Fk2000Entity) || vehicle.isRemoved() || mc.screen != null) {
                closeRemote(true);
                return;
            }
        }
        boolean controlsActive = mc.screen == null && (onboard || mc.player.getVehicle() instanceof Fk2000Entity);
        while (FIRE.consumeClick()) if (controlsActive) command(Command.FIRE);
        while (ENGINE.consumeClick()) if (controlsActive) command(Command.ENGINE);
        while (RADAR.consumeClick()) if (controlsActive) command(Command.RADAR);
        if (controlsActive) {
            float throttle = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
            float steering = (mc.options.keyRight.isDown() ? 1 : 0) - (mc.options.keyLeft.isDown() ? 1 : 0);
            ModNetwork.sendCommand(Command.DRIVE, throttle, steering, mc.options.keyJump.isDown(), -1);
        }
    }

    @SubscribeEvent
    public static void suppressWalking(MovementInputUpdateEvent event) {
        if (!onboard) return;
        var input = event.getInput();
        input.forwardImpulse = 0;
        input.leftImpulse = 0;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void hideRemoteHands(RenderHandEvent event) {
        if (onboard) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) { closeRemote(false); }
}
