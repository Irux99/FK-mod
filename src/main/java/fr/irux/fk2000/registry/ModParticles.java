package fr.irux.fk2000.registry;

import fr.irux.fk2000.IruxFk2000Mod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Own sprites on the standard particle atlas, with no replacement shader or framebuffer. */
public final class ModParticles {
    private static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, IruxFk2000Mod.MOD_ID);
    public static final RegistryObject<SimpleParticleType> EXHAUST_SMOKE = PARTICLES.register("exhaust_smoke", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> SHOCKWAVE = PARTICLES.register("shockwave", () -> new SimpleParticleType(true));
    public static void register(IEventBus bus) { PARTICLES.register(bus); }
    private ModParticles() {}
}
