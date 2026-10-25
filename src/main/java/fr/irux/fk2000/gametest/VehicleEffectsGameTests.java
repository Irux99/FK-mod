package fr.irux.fk2000.gametest;

import fr.irux.fk2000.config.ModConfig;
import fr.irux.fk2000.entity.missile.MissileEntity;
import fr.irux.fk2000.entity.vehicle.Fk2000Entity;
import fr.irux.fk2000.entity.vehicle.TubeCapEntity;
import fr.irux.fk2000.entity.vehicle.TubeCapMode;
import fr.irux.fk2000.entity.vehicle.WreckEntity;
import fr.irux.fk2000.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Destruction and launch mechanisms are exercised on real server entities, not renderer mocks. */
@GameTestHolder("iruxfk2000")
@PrefixGameTestTemplate(false)
public final class VehicleEffectsGameTests {
    private VehicleEffectsGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void fatalDamageCreatesOneCarcassAndProtectsEjectedOwner(GameTestHelper helper) {
        ServerPlayer owner = VehicleGameTests.onlinePlayerFixture(helper);
        Fk2000Entity vehicle = helper.spawn(ModContent.VEHICLE.get(), new Vec3(16, 2, 16));
        vehicle.setOwnerUUID(owner.getUUID());
        vehicle.setAutoFire(false);
        helper.setBlock(16, 1, 16, Blocks.STONE);
        helper.assertTrue(owner.startRiding(vehicle, true), "The test driver must actually occupy the truck before destruction");
        float driverHealth = owner.getHealth();
        var damage = helper.getLevel().damageSources().generic();
        helper.assertTrue(vehicle.hurt(damage, 10_000), "The live truck must accept real fatal damage");
        helper.assertTrue(vehicle.isRemoved(), "A destroyed truck must stop being a controllable live vehicle");
        var wrecks = helper.getLevel().getEntitiesOfClass(WreckEntity.class, arena(helper));
        helper.assertTrue(wrecks.size() == 1, "Fatal damage must produce exactly one physical carcass");
        WreckEntity wreck = wrecks.get(0);
        helper.assertTrue(owner.getUUID().equals(wreck.getOwnerUUID()), "The carcass must retain the destroyed truck's owner");
        helper.assertTrue(owner.getVehicle() == null && !vehicle.hasPassenger(owner),
                "Destruction must eject the real mounted driver");
        helper.assertFalse(wreck.getBoundingBox().intersects(owner.getBoundingBox()),
                "The ejected driver must appear outside the solid carcass");
        helper.assertTrue(owner.isAlive() && owner.getHealth() == driverHealth,
                "The ownership-aware explosion must not harm the ejected owner");
        helper.assertBlockPresent(Blocks.STONE, new BlockPos(16, 1, 16));
        vehicle.die(damage);
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(WreckEntity.class, arena(helper)).size() == 1,
                "A repeated death callback must not duplicate the wreck or its explosion");
        wreck.discard();
        VehicleGameTests.removePlayerFixture(helper, owner);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void threeCoverModesAndRearSuspensionFollowTheRealLaunch(GameTestHelper helper) {
        ServerPlayer owner = VehicleGameTests.onlinePlayerFixture(helper);
        TubeCapMode[] modes = {TubeCapMode.EJECT, TubeCapMode.HINGED, TubeCapMode.NONE};
        Fk2000Entity[] vehicles = new Fk2000Entity[3];
        int[] initialMissiles = new int[3];
        Vec3 destination = helper.absoluteVec(new Vec3(26, 40, 16));
        for (int i = 0; i < vehicles.length; i++) {
            Fk2000Entity vehicle = helper.spawn(ModContent.VEHICLE.get(), new Vec3(5 + 10 * i, 2, 16));
            vehicles[i] = vehicle;
            vehicle.setOwnerUUID(owner.getUUID());
            vehicle.setRadarEnabled(true);
            vehicle.setAutoAcquire(false);
            vehicle.setAutoFire(false);
            vehicle.setTubeCapMode(modes[i]);
            initialMissiles[i] = vehicle.getMissileCount();
            helper.assertTrue(vehicle.getTubeCapMode() == modes[i], "An idle launcher must accept the selected cover mechanism");
            helper.assertTrue(vehicle.setTargetCoordinates(destination) && vehicle.requestLaunch(),
                    "Each mechanism must start a real coordinate-target launch: " + modes[i]);
        }
        int preparation = ModConfig.PREPARATION_TICKS.get();
        helper.runAtTickTime(preparation - 3, () -> {
            helper.assertTrue(vehicles[1].isPreparingLaunch() && vehicles[1].getTubeCapProgress(0, 1) > .1F,
                    "The hinged cover must already be opening before the missile leaves its tube");
            for (int i = 0; i < vehicles.length; i++) {
                helper.assertTrue(vehicles[i].getMissileCount() == initialMissiles[i],
                        "Preparation and cover motion must not consume ammunition early: " + modes[i]);
            }
        });
        helper.runAtTickTime(preparation + 3, () -> {
            var covers = helper.getLevel().getEntitiesOfClass(TubeCapEntity.class, arena(helper));
            helper.assertTrue(covers.size() == 1,
                    "Only EJECT may create a physical flying cover; HINGED and NONE must not");
            helper.assertTrue((vehicles[0].getCapsPresentMask() & 1) == 0,
                    "EJECT must remove the launched tube's fixed cover when its physical copy leaves");
            helper.assertTrue(covers.get(0).getY() > vehicles[0].getY() + 6.7,
                    "The ejected cover must physically rise above its launch tube");
            helper.assertTrue(vehicles[1].getTubeCapProgress(0, 1) > .5F,
                    "A hinged cover must stay open while the missile clears the tube");
            helper.assertTrue(vehicles[2].getTubeCapProgress(0, 1) == 0,
                    "The open-tube mode must not animate a nonexistent hinge");
            for (int i = 0; i < vehicles.length; i++) {
                helper.assertTrue(vehicles[i].getMissileCount() == initialMissiles[i] - 1,
                        "Each mechanism must consume exactly one fired missile: " + modes[i]);
                helper.assertTrue(vehicles[i].getLastLaunchTube() == 0 && vehicles[i].getSuspensionCompression(1) > .05F,
                        "A real launch must compress the rear suspension in every cover mode: " + modes[i]);
            }
        });
        helper.runAtTickTime(preparation + 35, () -> {
            helper.assertTrue(vehicles[1].getTubeCapProgress(0, 1) <= .001F,
                    "The hinged cover must close again after the missile departs");
            for (Fk2000Entity vehicle : vehicles) {
                helper.assertTrue(vehicle.getSuspensionCompression(1) < .001F,
                        "The truck must settle back onto its normal suspension after the launch impulse");
                vehicle.discard();
            }
            helper.getLevel().getEntitiesOfClass(TubeCapEntity.class, arena(helper)).forEach(TubeCapEntity::discard);
            helper.getLevel().getEntitiesOfClass(MissileEntity.class, arena(helper)).forEach(MissileEntity::discard);
            VehicleGameTests.removePlayerFixture(helper, owner);
            helper.succeed();
        });
    }

    private static AABB arena(GameTestHelper helper) {
        return new AABB(helper.absoluteVec(Vec3.ZERO), helper.absoluteVec(new Vec3(32, 96, 32)));
    }
}
