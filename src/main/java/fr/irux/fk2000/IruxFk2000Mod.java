package fr.irux.fk2000;

import fr.irux.fk2000.config.ModConfig;
import fr.irux.fk2000.config.ClientEffectsConfig;
import fr.irux.fk2000.loading.FkChunkLoading;
import fr.irux.fk2000.network.ExplosionEffects;
import fr.irux.fk2000.network.RadarMaps;
import fr.irux.fk2000.network.ModNetwork;
import fr.irux.fk2000.registry.ModContent;
import fr.irux.fk2000.registry.ModParticles;
import fr.irux.fk2000.registry.ModSounds;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod(IruxFk2000Mod.MOD_ID)
public final class IruxFk2000Mod {
    public static final String MOD_ID = "iruxfk2000";
    public IruxFk2000Mod() {
        ModContent.register(FMLJavaModLoadingContext.get().getModEventBus());
        ModSounds.register(FMLJavaModLoadingContext.get().getModEventBus());
        ModParticles.register(FMLJavaModLoadingContext.get().getModEventBus());
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);
        ModLoadingContext.get().registerConfig(Type.SERVER, ModConfig.SPEC);
        ModLoadingContext.get().registerConfig(Type.CLIENT, ClientEffectsConfig.SPEC);
        ModNetwork.register();
        ExplosionEffects.register();
        RadarMaps.register();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(FkChunkLoading::registerCallback);
    }
}
