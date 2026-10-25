package fr.irux.fk2000.gametest;

import fr.irux.fk2000.entity.vehicle.Fk2000Entity;
import fr.irux.fk2000.radar.RadarSystem;
import fr.irux.fk2000.radar.TargetMode;
import fr.irux.fk2000.registry.ModContent;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder("iruxfk2000_sbw")
@PrefixGameTestTemplate(false)
public final class SbwRadarModeGameTests {
    private SbwRadarModeGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40, batch = "sbw_compatibility")
    public static void sbwModeIncludesGroundVehiclesAndStillRejectsFriendlyWrecks(GameTestHelper helper) {
        var owner = VehicleGameTests.onlinePlayerFixture(helper);
        Fk2000Entity vehicle = helper.spawn(ModContent.VEHICLE.get(), new Vec3(6, 2, 6));
        vehicle.setOwnerUUID(owner.getUUID());
        vehicle.setRadarEnabled(true);
        Entity tank = spawn(helper, "ztz_99a", new Vec3(20, 2, 8));
        Entity helicopter = spawn(helper, "ah_6", new Vec3(20, 10, 22));
        try {
        helper.assertFalse(RadarSystem.canTrack(vehicle, tank), "Aircraft mode must retain its original ground-target exclusion");
        helper.assertTrue(RadarSystem.canTrack(vehicle, helicopter), "Aircraft mode must still recognize the actual AH-6");
        vehicle.setTargetMode(TargetMode.SBW_VEHICLES);
        helper.assertTrue(RadarSystem.canTrack(vehicle, tank), "SBW-vehicles mode must allow the actual release's ground tank");
        helper.assertTrue(RadarSystem.canTrack(vehicle, helicopter), "SBW-vehicles mode must retain actual aircraft targets");
        try {
            tank.getClass().getMethod("setLastDriverUUID", String.class).invoke(tank, owner.getStringUUID());
            helper.assertFalse(RadarSystem.canTrack(vehicle, tank), "The real SBW owner/IFF must veto a friendly ground vehicle");
            tank.getClass().getMethod("setLastDriverUUID", String.class).invoke(tank, "undefined");
            tank.getClass().getMethod("setWreck", boolean.class).invoke(tank, true);
            helper.assertFalse(RadarSystem.canTrack(vehicle, tank), "A wrecked SBW ground vehicle must not be a live target");
            vehicle.setTargetMode(TargetMode.ALL_ENTITIES);
            helper.assertFalse(RadarSystem.canTrack(vehicle, tank), "All-entities mode must not restore wrecked SBW vehicles as targets");
        } catch (ReflectiveOperationException error) { throw new AssertionError("The release's verified ownership/wreck APIs must exist", error); }
        helper.succeed();
        } finally {
            vehicle.discard();
            tank.discard();
            helicopter.discard();
            VehicleGameTests.removePlayerFixture(helper, owner);
        }
    }

    private static Entity spawn(GameTestHelper helper, String path, Vec3 position) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("superbwarfare", path));
        helper.assertTrue(type != null, "The actual SBW release must register " + path);
        Entity entity = type.create(helper.getLevel());
        helper.assertTrue(entity != null, "The actual SBW entity must construct: " + path);
        entity.moveTo(helper.absoluteVec(position));
        entity.setNoGravity(true);
        helper.assertTrue(helper.getLevel().addFreshEntity(entity), "The actual SBW fixture must spawn: " + path);
        return entity;
    }
}
