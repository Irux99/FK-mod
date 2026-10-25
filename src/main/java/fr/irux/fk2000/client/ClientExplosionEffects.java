package fr.irux.fk2000.client;

import fr.irux.fk2000.config.ClientEffectsConfig;
import fr.irux.fk2000.physics.ShockwaveMath;
import fr.irux.fk2000.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/** An atlas particle and small additive camera events; shader packs retain their entire render pipeline. */
@Mod.EventBusSubscriber(modid = "iruxfk2000", value = Dist.CLIENT)
public final class ClientExplosionEffects {
    private record Pulse(long tick, double strength, double arrivalDelay) {}
    private static final List<Pulse> PULSES = new ArrayList<>();
    private static ClientLevel previousLevel;
    private ClientExplosionEffects() {}

    public static void accept(double x, double y, double z, float power) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(power) || power <= 0) return;
        if (client.level != previousLevel) { PULSES.clear(); previousLevel = client.level; }
        client.level.addParticle(ModParticles.SHOCKWAVE.get(), true, x, y, z, Math.min(12, power), 0, 0);
        double distance = client.gameRenderer.getMainCamera().getPosition().distanceTo(new Vec3(x, y, z));
        double strength = ShockwaveMath.amplitude(distance, power);
        if (strength > 0) {
            if (PULSES.size() >= 16) PULSES.remove(0);
            PULSES.add(new Pulse(client.level.getGameTime(), strength, distance / 12));
        }
    }

    private static double intensity(double partialTick) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || !ClientEffectsConfig.SHOCKWAVE_CAMERA.get()) return 0;
        double value = 0;
        for (Pulse pulse : PULSES) value += pulse.strength * ShockwaveMath.pulse(
                client.level.getGameTime() + partialTick - pulse.tick - pulse.arrivalDelay);
        return Math.max(-1, Math.min(1, value)) * ClientEffectsConfig.SHOCKWAVE_STRENGTH.get();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void fov(ViewportEvent.ComputeFov event) {
        if (event.usedConfiguredFov()) event.setFOV(event.getFOV() * (1 - .10 * intensity(event.getPartialTick())));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void shake(ViewportEvent.ComputeCameraAngles event) {
        double pressure = intensity(event.getPartialTick());
        if (pressure == 0 || Minecraft.getInstance().level == null) return;
        double time = Minecraft.getInstance().level.getGameTime() + event.getPartialTick();
        event.setPitch(event.getPitch() + (float) (Math.sin(time * 2.3) * pressure * 1.1));
        event.setYaw(event.getYaw() + (float) (Math.sin(time * 1.8) * pressure * .7));
        event.setRoll(event.getRoll() + (float) (Math.sin(time * 2.7) * pressure * .8));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var level = Minecraft.getInstance().level;
        if (level != previousLevel) { PULSES.clear(); previousLevel = level; }
        if (level != null) PULSES.removeIf(pulse -> level.getGameTime() - pulse.tick > 42);
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { PULSES.clear(); previousLevel = null; }
}
