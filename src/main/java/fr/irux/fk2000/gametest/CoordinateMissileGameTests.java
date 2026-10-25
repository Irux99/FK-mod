package fr.irux.fk2000.gametest;

import fr.irux.fk2000.config.ModConfig;
import fr.irux.fk2000.entity.missile.FlightState;
import fr.irux.fk2000.entity.missile.MissileEntity;
import fr.irux.fk2000.registry.ModContent;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Fixed coordinates exercise production guidance, collision, warhead and persistence without fake targets. */
@GameTestHolder("iruxfk2000")
@PrefixGameTestTemplate(false)
public final class CoordinateMissileGameTests {
    private CoordinateMissileGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void fixedCoordinateStrikeNeedsNoEntityLock(GameTestHelper helper) {
        ServerPlayer owner = VehicleGameTests.onlinePlayerFixture(helper);
        Pig target = helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(24.5, 30, 16.5));
        target.setNoGravity(true);
        float initialHealth = target.getHealth();
        Vec3 destination = target.getBoundingBox().getCenter();
        MissileEntity missile = missile(helper, new Vec3(4.5, 3, 16.5));
        missile.launchAt(owner.getUUID(), UUID.randomUUID(), destination);
        helper.assertTrue(missile.hasFixedDestination() && missile.getTrackedTargetUUID() == null,
                "A coordinate shot must retain the point without binding any entity UUID");
        helper.assertTrue(helper.getLevel().addFreshEntity(missile), "Coordinate missile must enter the server world");
        helper.onEachTick(() -> {
            if (!missile.isRemoved()) return;
            helper.assertTrue(missile.getTerminationReason() == MissileEntity.TerminationReason.DETONATED,
                    "A fixed point must not expire through lost-entity handling: " + missile.getTerminationReason());
            helper.assertTrue(missile.position().distanceTo(destination) <= ModConfig.PROXIMITY_FUSE.get() + ModConfig.MISSILE_MAX_SPEED.get() + .1,
                    "The fixed point must trigger the physical swept proximity fuse: " + missile.position() + " / " + destination);
            helper.assertTrue(!target.isAlive() || target.getHealth() < initialHealth,
                    "A coordinate strike must apply real server-side warhead damage at its destination");
            VehicleGameTests.removePlayerFixture(helper, owner);
            target.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void coordinatePersistenceUsesItsOwnFiniteLifetime(GameTestHelper helper) {
        ServerPlayer owner = VehicleGameTests.onlinePlayerFixture(helper);
        MissileEntity source = missile(helper, new Vec3(4.5, 3, 16.5));
        Vec3 destination = helper.absoluteVec(new Vec3(24.5, 75, 16.5));
        source.launchAt(owner.getUUID(), UUID.randomUUID(), destination);
        CompoundTag saved = new CompoundTag();
        source.saveWithoutId(saved);
        saved.putInt("FlightTicks", ModConfig.MISSILE_LIFETIME.get() + 1);
        helper.assertTrue(ModConfig.COORDINATE_MISSILE_LIFETIME.get() > ModConfig.MISSILE_LIFETIME.get(),
                "Default coordinate endurance must permit longer-range shots");
        MissileEntity restored = ModContent.MISSILE.get().create(helper.getLevel());
        helper.assertTrue(restored != null, "Restored missile must construct");
        restored.load(saved);
        helper.assertTrue(restored.hasFixedDestination() && restored.getTrackedTargetUUID() == null,
                "Coordinate target mode must survive NBT without requiring an entity");
        helper.assertTrue(restored.getLastKnownTargetPosition().distanceTo(destination) < 1.0e-8,
                "All three destination coordinates must survive NBT exactly");
        helper.assertTrue(helper.getLevel().addFreshEntity(restored), "Restored coordinate shot must enter the world");
        MissileEntity[] expired = {null};
        helper.runAtTickTime(3, () -> {
            helper.assertFalse(restored.isRemoved(), "The ordinary anti-air lifetime must not truncate a coordinate shot");
            CompoundTag end = new CompoundTag();
            restored.saveWithoutId(end);
            end.remove("UUID");
            end.putInt("FlightTicks", ModConfig.COORDINATE_MISSILE_LIFETIME.get());
            restored.discard();
            expired[0] = ModContent.MISSILE.get().create(helper.getLevel());
            helper.assertTrue(expired[0] != null, "Expiry fixture must construct");
            expired[0].load(end);
            helper.assertTrue(helper.getLevel().addFreshEntity(expired[0]), "Expiry fixture must enter the server world");
        });
        helper.runAtTickTime(6, () -> {
            helper.assertTrue(expired[0] != null && expired[0].isRemoved()
                            && expired[0].getTerminationReason() == MissileEntity.TerminationReason.LIFETIME,
                    "Coordinate endurance must remain finite and expire without an explosion");
            VehicleGameTests.removePlayerFixture(helper, owner);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void malformedCoordinatesNeverArmOrCreateATarget(GameTestHelper helper) {
        MissileEntity missile = missile(helper, new Vec3(4.5, 3, 16.5));
        UUID owner = UUID.randomUUID();
        missile.launchAt(owner, UUID.randomUUID(), new Vec3(Double.NaN, 0, 0));
        missile.launchAt(owner, UUID.randomUUID(), new Vec3(0, Double.POSITIVE_INFINITY, 0));
        missile.launchAt(owner, UUID.randomUUID(), new Vec3(0, helper.getLevel().getMaxBuildHeight() + 10, 0));
        missile.launchAt(owner, UUID.randomUUID(), new Vec3(1.0e9, 20, 0));
        helper.assertTrue(missile.getFlightState() == FlightState.IDLE && !missile.hasFixedDestination()
                        && missile.getTrackedTargetUUID() == null,
                "Malformed or out-of-world coordinates must never arm a missile or generate a fake target");
        helper.succeed();
    }

    private static MissileEntity missile(GameTestHelper helper, Vec3 relative) {
        MissileEntity missile = ModContent.MISSILE.get().create(helper.getLevel());
        helper.assertTrue(missile != null, "Registered missile must construct");
        Vec3 point = helper.absoluteVec(relative);
        missile.moveTo(point.x, point.y, point.z, 0, -90);
        return missile;
    }
}
