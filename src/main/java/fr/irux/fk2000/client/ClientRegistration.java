package fr.irux.fk2000.client;

import fr.irux.fk2000.network.VehicleStatus;
import fr.irux.fk2000.registry.ModContent;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

@Mod.EventBusSubscriber(modid = "iruxfk2000", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientRegistration {
    private ClientRegistration() {}
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModContent.VEHICLE.get(), context -> ImportedMesh.isAvailable("truck") ? new VehicleRenderer(context) : new PrototypeVehicleRenderer(context));
        event.registerEntityRenderer(ModContent.MISSILE.get(), context -> ImportedMesh.isAvailable("missile") ? new MissileRenderer(context) : new PrototypeMissileRenderer(context));
        event.registerEntityRenderer(ModContent.WRECK.get(), WreckRenderer::new);
        event.registerEntityRenderer(ModContent.TUBE_CAP.get(), TubeCapRenderer::new);
    }
    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) { event.register(ClientHooks.TACTICAL); event.register(ClientHooks.FIRE);
        event.register(ClientHooks.ENGINE); event.register(ClientHooks.RADAR); }
    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("remote_camera", (gui, graphics, partialTick, width, height) -> {
            if (Minecraft.getInstance().screen != null) return;
            var vehicle = ClientHooks.hudVehicle();
            if (vehicle == null) return;
            VehicleStatus s = ClientHooks.status();
            if (s != null && s.vehicleId() != vehicle.getId()) s = null;
            int right = Math.min(width - 8, 342);
            graphics.fill(8, 8, right, 108, 0xc00a191a);
            var font = Minecraft.getInstance().font;
            graphics.drawString(font, Component.translatable(ClientHooks.isOnboard()
                    ? "screen.iruxfk2000.remote_cab" : "screen.iruxfk2000.driver_cab"), 15, 15, 0xff59e9b0, false);
            graphics.drawString(font, Component.translatable("screen.iruxfk2000.camera_status",
                    String.format(Locale.ROOT, "%.1f", Math.abs(s == null ? vehicle.getSpeed() : s.speed()) * 72),
                    s == null ? vehicle.getMissileCount() : s.missiles(),
                    Component.translatable((s == null ? vehicle.isRadarEnabled() : s.radar()) ? "options.on" : "options.off")), 15, 30, 0xffe0f4ea, false);
            int targetId = s == null ? -1 : s.targetId();
            var target = s == null ? null : s.contacts().stream().filter(c -> c.entityId() == targetId).findFirst().orElse(null);
            Component targetText = s != null && s.targetCoordinates() != null
                    && s.targetMode() == fr.irux.fk2000.radar.TargetMode.COORDINATES
                    ? Component.literal("XYZ " + Math.round(s.targetCoordinates().x) + " / "
                    + Math.round(s.targetCoordinates().y) + " / " + Math.round(s.targetCoordinates().z))
                    : target == null ? Component.translatable("screen.iruxfk2000.no_target")
                    : Component.literal(target.name() + " / " + Math.round(target.distance()) + " m");
            graphics.drawString(font, targetText
                    , 15, 43, 0xffffbd55, false);
            graphics.drawString(font, Component.translatable("screen.iruxfk2000.camera_help", ClientHooks.TACTICAL.getTranslatedKeyMessage(),
                    ClientHooks.FIRE.getTranslatedKeyMessage(), ClientHooks.ENGINE.getTranslatedKeyMessage(), ClientHooks.RADAR.getTranslatedKeyMessage()),
                    15, 56, 0xff8da69b, false);
            VehicleHealthDisplay.draw(graphics, font, 15, 73, right - 30,
                    s == null ? vehicle.getHealth() : s.health(), s == null ? vehicle.getMaxHealth() : s.maxHealth());
            if (ClientHooks.recentDamage()) {
                graphics.fill(0, 0, width, 3, 0xb0ff5544);
                graphics.fill(0, height - 3, width, height, 0xb0ff5544);
                graphics.fill(0, 0, 3, height, 0xb0ff5544);
                graphics.fill(width - 3, 0, width, height, 0xb0ff5544);
            }
        });
    }
}
