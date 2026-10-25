package fr.irux.fk2000.registry;

import fr.irux.fk2000.IruxFk2000Mod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Original synthesized vehicle sounds; no external recordings or SBW assets. */
public final class ModSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, IruxFk2000Mod.MOD_ID);
    public static final RegistryObject<SoundEvent> RELOAD = SOUNDS.register("vehicle_reload", () ->
            SoundEvent.createVariableRangeEvent(new ResourceLocation(IruxFk2000Mod.MOD_ID, "vehicle_reload")));
    public static final RegistryObject<SoundEvent> MISSILE_IGNITION = SOUNDS.register("missile_ignition", () ->
            SoundEvent.createFixedRangeEvent(new ResourceLocation(IruxFk2000Mod.MOD_ID, "missile_ignition"), 160));
    public static final RegistryObject<SoundEvent> MISSILE_FLIGHT = SOUNDS.register("missile_flight", () ->
            SoundEvent.createVariableRangeEvent(new ResourceLocation(IruxFk2000Mod.MOD_ID, "missile_flight")));
    public static final RegistryObject<SoundEvent> MISSILE_EXPLOSION = SOUNDS.register("missile_explosion", () ->
            SoundEvent.createFixedRangeEvent(new ResourceLocation(IruxFk2000Mod.MOD_ID, "missile_explosion"), 192));

    public static void register(IEventBus bus) { SOUNDS.register(bus); }

    private ModSounds() {}
}
