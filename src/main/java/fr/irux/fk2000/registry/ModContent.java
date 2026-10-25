package fr.irux.fk2000.registry;

import fr.irux.fk2000.IruxFk2000Mod;
import fr.irux.fk2000.entity.missile.MissileEntity;
import fr.irux.fk2000.entity.vehicle.Fk2000Entity;
import fr.irux.fk2000.entity.vehicle.VehicleGeometry;
import fr.irux.fk2000.entity.vehicle.WreckEntity;
import fr.irux.fk2000.entity.vehicle.TubeCapEntity;
import fr.irux.fk2000.item.RemoteControllerItem;
import fr.irux.fk2000.item.VehicleDeploymentItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModContent {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, IruxFk2000Mod.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, IruxFk2000Mod.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, IruxFk2000Mod.MOD_ID);
    public static final RegistryObject<EntityType<Fk2000Entity>> VEHICLE = ENTITIES.register("fk2000", () ->
        EntityType.Builder.of(Fk2000Entity::new, MobCategory.MISC).sized(VehicleGeometry.BODY_WIDTH, VehicleGeometry.BODY_HEIGHT).clientTrackingRange(16).updateInterval(1).build("iruxfk2000:fk2000"));
    public static final RegistryObject<EntityType<MissileEntity>> MISSILE = ENTITIES.register("missile", () ->
        EntityType.Builder.<MissileEntity>of(MissileEntity::new, MobCategory.MISC).sized(.3F, 1.4F).clientTrackingRange(16).updateInterval(1).fireImmune().build("iruxfk2000:missile"));
    public static final RegistryObject<EntityType<WreckEntity>> WRECK = ENTITIES.register("fk2000_wreck", () ->
        EntityType.Builder.<WreckEntity>of(WreckEntity::new, MobCategory.MISC).sized(VehicleGeometry.BODY_WIDTH, VehicleGeometry.BODY_HEIGHT)
            .clientTrackingRange(16).updateInterval(3).fireImmune().build("iruxfk2000:fk2000_wreck"));
    public static final RegistryObject<EntityType<TubeCapEntity>> TUBE_CAP = ENTITIES.register("tube_cap", () ->
        EntityType.Builder.<TubeCapEntity>of(TubeCapEntity::new, MobCategory.MISC).sized(.6F, .12F)
            .clientTrackingRange(12).updateInterval(1).fireImmune().noSave().build("iruxfk2000:tube_cap"));
    public static final RegistryObject<Item> DEPLOYMENT = ITEMS.register("fk2000_deployment", () -> new VehicleDeploymentItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> REMOTE = ITEMS.register("remote_controller", () -> new RemoteControllerItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> MISSILE_ITEM = ITEMS.register("missile", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("equipment", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.iruxfk2000")).icon(() -> new ItemStack(DEPLOYMENT.get()))
        .displayItems((parameters, output) -> { output.accept(DEPLOYMENT.get()); output.accept(REMOTE.get()); output.accept(MISSILE_ITEM.get()); }).build());
    public static void register(IEventBus bus) {
        ENTITIES.register(bus); ITEMS.register(bus); TABS.register(bus);
        bus.addListener(ModContent::attributes);
    }
    private static void attributes(EntityAttributeCreationEvent event) { event.put(VEHICLE.get(), Fk2000Entity.createAttributes().build()); }
    private ModContent() {}
}
