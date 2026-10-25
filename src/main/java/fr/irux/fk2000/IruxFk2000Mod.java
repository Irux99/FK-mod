package fr.irux.fk2000;

import fr.irux.fk2000.config.ModConfig;
import fr.irux.fk2000.network.ModNetwork;
import fr.irux.fk2000.registry.ModContent;
import fr.irux.fk2000.registry.ModSounds;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(IruxFk2000Mod.MOD_ID)
public final class IruxFk2000Mod {
    public static final String MOD_ID = "iruxfk2000";
    public IruxFk2000Mod() {
        ModContent.register(FMLJavaModLoadingContext.get().getModEventBus());
        ModSounds.register(FMLJavaModLoadingContext.get().getModEventBus());
        ModLoadingContext.get().registerConfig(Type.SERVER, ModConfig.SPEC);
        ModNetwork.register();
    }
}
