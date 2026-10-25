package fr.irux.fk2000.client;

import fr.irux.fk2000.config.ClientEffectsConfig;
import fr.irux.fk2000.entity.missile.MissileEntity;
import fr.irux.fk2000.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** One moving OpenAL source per missile, rather than overlapping server sound broadcasts. */
@Mod.EventBusSubscriber(modid = "iruxfk2000", value = Dist.CLIENT)
public final class ClientMissileAudio {
    private static final Map<UUID, MotorSound> SOUNDS = new HashMap<>();
    private static ClientLevel previousLevel;
    private ClientMissileAudio() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level != previousLevel) { stopAll(); previousLevel = client.level; }
        SOUNDS.values().removeIf(MotorSound::isStopped);
        if (client.level == null || client.isPaused()) return;
        for (var entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof MissileEntity missile) || !missile.getMotorActive() || missile.isRemoved()
                    || SOUNDS.containsKey(missile.getUUID())) continue;
            MotorSound sound = new MotorSound(missile);
            SOUNDS.put(missile.getUUID(), sound);
            client.getSoundManager().play(sound);
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { stopAll(); previousLevel = null; }

    private static void stopAll() {
        var manager = Minecraft.getInstance().getSoundManager();
        for (MotorSound sound : SOUNDS.values()) manager.stop(sound);
        SOUNDS.clear();
    }

    private static final class MotorSound extends AbstractTickableSoundInstance {
        private final MissileEntity missile;
        private int age, fadeRemaining = -1;
        private float lastLiveVolume;

        private MotorSound(MissileEntity missile) {
            super(ModSounds.MISSILE_FLIGHT.get(), SoundSource.HOSTILE, RandomSource.create());
            this.missile = missile;
            looping = true; delay = 0; relative = false; attenuation = SoundInstance.Attenuation.LINEAR;
            x = missile.getX(); y = missile.getY(); z = missile.getZ();
            volume = .01f; pitch = 1;
        }

        @Override public boolean canStartSilent() { return true; }
        @Override public void tick() {
            if (fadeRemaining < 0 && (missile.isRemoved() || !missile.getMotorActive()
                    || Minecraft.getInstance().level != missile.level())) fadeRemaining = 8;
            if (fadeRemaining >= 0) {
                volume = lastLiveVolume * fadeRemaining / 8f;
                if (fadeRemaining-- <= 0) stop();
                return;
            }
            x = missile.getX(); y = missile.getY(); z = missile.getZ();
            lastLiveVolume = ClientEffectsConfig.MOTOR_VOLUME.get().floatValue() * Math.min(1, ++age / 3f);
            volume = lastLiveVolume;
            // The sound engine positions the listener at the actual camera, including remote views.
            Vec3 listener = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            Vec3 towardListener = listener.subtract(missile.position()).normalize();
            double radialVelocity = missile.getDeltaMovement().dot(towardListener);
            pitch = (float) Mth.clamp(1 + radialVelocity * .055, .85, 1.18);
        }
    }
}
