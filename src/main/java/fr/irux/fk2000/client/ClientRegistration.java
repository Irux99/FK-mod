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
        event.registerEntityRenderer(ModContent.VEHICLE.get(), VehicleRenderer::new);
        event.registerEntityRenderer(ModContent.MISSILE.get(), MissileRenderer::new);
    }
    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) { event.register(ClientHooks.TACTICAL); event.register(ClientHooks.FIRE);
        event.register(ClientHooks.ENGINE); event.register(ClientHooks.RADAR); }
    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("remote_camera", (gui, graphics, partialTick, width, height) -> {
            VehicleStatus s = ClientHooks.status();
            if (!ClientHooks.isOnboard() || s == null) return;
            graphics.fill(8, 8, Math.min(width - 8, 342), 70, 0xb00a191a);
            var font = Minecraft.getInstance().font;
            graphics.drawString(font, "FK-2000 / CAM", 15, 15, 0xff59e9b0, false);
            graphics.drawString(font, Component.translatable("screen.iruxfk2000.camera_status",
                    String.format(Locale.ROOT, "%.1f", Math.abs(s.speed()) * 72), s.missiles(),
                    Component.translatable(s.radar() ? "options.on" : "options.off")), 15, 30, 0xffe0f4ea, false);
            var target = s.contacts().stream().filter(c -> c.entityId() == s.targetId()).findFirst().orElse(null);
            graphics.drawString(font, target == null ? Component.translatable("screen.iruxfk2000.no_target")
                    : Component.literal(target.name() + " / " + Math.round(target.distance()) + " m"), 15, 43, 0xffffbd55, false);
            graphics.drawString(font, Component.translatable("screen.iruxfk2000.camera_help", ClientHooks.TACTICAL.getTranslatedKeyMessage(),
                    ClientHooks.FIRE.getTranslatedKeyMessage(), ClientHooks.ENGINE.getTranslatedKeyMessage(), ClientHooks.RADAR.getTranslatedKeyMessage()),
                    15, 56, 0xff8da69b, false);
        });
    }
}
