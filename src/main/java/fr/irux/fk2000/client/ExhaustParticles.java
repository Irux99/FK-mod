package fr.irux.fk2000.client;

import fr.irux.fk2000.registry.ModParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "iruxfk2000", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ExhaustParticles {
    private ExhaustParticles() {}
    @SubscribeEvent public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.EXHAUST_SMOKE.get(), SmokeProvider::new);
        event.registerSpriteSet(ModParticles.SHOCKWAVE.get(), ShockwaveProvider::new);
    }

    private static final class Smoke extends TextureSheetParticle {
        private final float initialSize;
        private Smoke(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
            super(level, x, y, z, dx, dy, dz);
            xd = dx; yd = dy; zd = dz; friction = .965f; gravity = -.004f;
            lifetime = 38 + random.nextInt(20); hasPhysics = false;
            initialSize = .30f + random.nextFloat() * .35f;
            quadSize = initialSize; alpha = 0;
            float shade = .72f + random.nextFloat() * .2f;
            setColor(shade, shade * .98f, shade * .95f);
            roll = oRoll = random.nextFloat() * Mth.TWO_PI;
            pickSprite(sprites);
        }
        @Override public void tick() {
            super.tick();
            float progress = age / (float) lifetime;
            alpha = Math.min(1, age / 4f) * (1 - progress) * .62f;
            quadSize = initialSize * (1 + progress * 3);
            oRoll = roll; roll += .008f;
        }
        @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
    }
    private record SmokeProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                                 double dx, double dy, double dz) {
            return new Smoke(level, x, y, z, dx, dy, dz, sprites);
        }
    }
    private static final class Shockwave extends TextureSheetParticle {
        private final float maximumRadius;
        private Shockwave(ClientLevel level, double x, double y, double z, double power, SpriteSet sprites) {
            super(level, x, y, z); xd = yd = zd = 0; hasPhysics = false;
            lifetime = 16; maximumRadius = (float) Math.max(8, Math.min(32, power * 4));
            alpha = .55f; setColor(.92f, .96f, 1f); pickSprite(sprites);
        }
        @Override public void tick() {
            super.tick();
            float progress = age / (float) lifetime;
            quadSize = .4f + progress * maximumRadius;
            alpha = .55f * (1 - progress) * (1 - progress);
        }
        @Override public int getLightColor(float partialTick) { return 15728880; }
        @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
    }
    private record ShockwaveProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                                 double power, double ignoredY, double ignoredZ) {
            return new Shockwave(level, x, y, z, power, sprites);
        }
    }
}
