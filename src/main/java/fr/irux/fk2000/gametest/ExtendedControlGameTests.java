package fr.irux.fk2000.gametest;

import fr.irux.fk2000.compat.AerialTarget;
import fr.irux.fk2000.config.ModConfig;
import fr.irux.fk2000.entity.missile.FlightState;
import fr.irux.fk2000.entity.missile.MissileEntity;
import fr.irux.fk2000.entity.vehicle.Fk2000Entity;
import fr.irux.fk2000.loading.FkChunkLoading;
import fr.irux.fk2000.network.ModNetwork;
import fr.irux.fk2000.radar.RadarSystem;
import fr.irux.fk2000.registry.ModContent;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder("iruxfk2000")
@PrefixGameTestTemplate(false)
public final class ExtendedControlGameTests {
    private ExtendedControlGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100, batch = "extended_control")
    public static void authorizedRemoteActuallyFiresBeyondTheOldDistanceLimit(GameTestHelper helper) {
        var owner = VehicleGameTests.onlinePlayerFixture(helper);
        var truck = helper.spawn(ModContent.VEHICLE.get(), new Vec3(16, 2, 16));
        truck.setOwnerUUID(owner.getUUID());
        truck.setRadarEnabled(true);
        truck.setAutoFire(false);
        helper.assertTrue(truck.setTargetCoordinates(helper.absoluteVec(new Vec3(26, 45, 16))),
                "A remote launch must have an actual validated coordinate destination");
        ItemStack remote = new ItemStack(ModContent.REMOTE.get());
        remote.getOrCreateTag().putUUID("Vehicle", truck.getUUID());
        remote.getOrCreateTag().putString("Dimension", helper.getLevel().dimension().location().toString());
        owner.setItemInHand(InteractionHand.MAIN_HAND, remote);
        owner.moveTo(truck.getX() + 384, truck.getY(), truck.getZ());
        helper.assertTrue(ModNetwork.openRemote(owner, remote) && ModNetwork.authorizedVehicle(owner) == truck,
                "The real control session must authorize its paired owner 384 blocks away");
        int ammunition = truck.getMissileCount();
        helper.assertTrue(ModNetwork.authorizedVehicle(owner).requestLaunch(),
                "A validated distant control session must start the real launch sequence");
        helper.runAtTickTime(ModConfig.PREPARATION_TICKS.get() + 3, () -> {
            var missiles = helper.getLevel().getEntitiesOfClass(MissileEntity.class,
                    new AABB(helper.absoluteVec(Vec3.ZERO), helper.absoluteVec(new Vec3(32, 96, 32)))).stream()
                    .filter(m -> { CompoundTag data = new CompoundTag(); m.saveWithoutId(data);
                        return data.hasUUID("Launcher") && truck.getUUID().equals(data.getUUID("Launcher")); }).toList();
            helper.assertTrue(missiles.size() == 1 && truck.getMissileCount() == ammunition - 1,
                    "Distant fire must create one physical missile and consume exactly one ammunition unit");
            MissileEntity missile = missiles.get(0);
            helper.assertTrue(missile.getFlightState() == FlightState.EJECTING && !missile.getMotorActive()
                            && FkChunkLoading.ticketCount(helper.getLevel(), missile.getUUID()) > 0,
                    "The remote-fired missile must cold-eject with its own active chunk corridor");
            missile.discard(); truck.discard();
            VehicleGameTests.removePlayerFixture(helper, owner);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 30, batch = "extended_control")
    public static void knownAircraftAboveBuildHeightKeepsItsChunkTicket(GameTestHelper helper) {
        var owner = VehicleGameTests.onlinePlayerFixture(helper);
        Vec3 start = helper.absoluteVec(new Vec3(16, 10, 16));
        ArmorStand target = EntityType.ARMOR_STAND.create(helper.getLevel());
        helper.assertTrue(target != null, "The real airborne fixture must construct");
        target.moveTo(start.x + 200, helper.getLevel().getMaxBuildHeight() + 80, start.z, 0, 0);
        target.setNoGravity(true);
        helper.assertTrue(helper.getLevel().addFreshEntity(target), "The known aircraft fixture must enter the level");
        MissileEntity missile = ModContent.MISSILE.get().create(helper.getLevel());
        helper.assertTrue(missile != null, "The missile fixture must construct");
        missile.moveTo(start.x, start.y, start.z, 0, -90);
        missile.launch(owner.getUUID(), UUID.randomUUID(), target);
        helper.getLevel().addFreshEntity(missile);
        FkChunkLoading.refreshMissile(missile, target);
        helper.assertTrue(FkChunkLoading.ownsTicket(helper.getLevel(), missile.getUUID(), target.chunkPosition()),
                "Chunk tickets are two-dimensional: a known aircraft above the build ceiling must remain simulated");
        missile.discard(); target.discard();
        VehicleGameTests.removePlayerFixture(helper, owner);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30, batch = "extended_control")
    public static void engagementRangeControlsRealTargetEligibilityAndPersists(GameTestHelper helper) {
        var owner = VehicleGameTests.onlinePlayerFixture(helper);
        var truck = helper.spawn(ModContent.VEHICLE.get(), new Vec3(16, 2, 16));
        truck.setOwnerUUID(owner.getUUID()); truck.setRadarEnabled(true);
        RangeTarget target = new RangeTarget(helper.getLevel());
        target.moveTo(truck.getX() + 256, truck.getY() + 10, truck.getZ(), 0, 0);
        helper.getLevel().getChunk(target.chunkPosition().x, target.chunkPosition().z);
        helper.getLevel().addFreshEntity(target);
        helper.assertFalse(RadarSystem.canTrack(truck, target), "The original 160-block engagement range must reject a target at 256 blocks");
        truck.setEngagementRange(512);
        helper.assertTrue(RadarSystem.canTrack(truck, target), "Increasing engagement range must allow the actual loaded distant target");
        truck.setEngagementRange(Double.NaN);
        helper.assertTrue(truck.getEngagementRange() == 512, "A malformed range update must not poison server targeting");
        CompoundTag data = new CompoundTag(); truck.addAdditionalSaveData(data);
        var restored = ModContent.VEHICLE.get().create(helper.getLevel());
        helper.assertTrue(restored != null, "The saved vehicle must construct");
        restored.readAdditionalSaveData(data);
        helper.assertTrue(restored.getEngagementRange() == 512, "The selected engagement distance must survive NBT restoration");
        truck.setEngagementRange(64);
        helper.assertFalse(RadarSystem.canTrack(truck, target), "Reducing engagement range must reject the distant target again");
        truck.setEngagementRange(ModConfig.MAX_ENGAGEMENT_RANGE.get() + 1000);
        helper.assertTrue(truck.getEngagementRange() <= ModConfig.MAX_ENGAGEMENT_RANGE.get(),
                "The server administrator's maximum range must bound UI updates");
        target.discard(); truck.discard(); restored.discard();
        VehicleGameTests.removePlayerFixture(helper, owner);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 220, batch = "extended_control")
    public static void selectedAircraftKeepsTickingDuringDistantLaunchPreparation(GameTestHelper helper) {
        var level = helper.getLevel();
        var owner = VehicleGameTests.onlinePlayerFixture(helper);
        owner.setNoGravity(true); owner.setInvulnerable(true);
        var truck = helper.spawn(ModContent.VEHICLE.get(), new Vec3(16, 2, 16));
        truck.setOwnerUUID(owner.getUUID()); truck.setRadarEnabled(true); truck.setAutoAcquire(false);
        truck.setAutoFire(false); truck.setEngagementRange(512);
        RangeTarget target = new RangeTarget(level);
        // Place the aircraft in open sky, not inside natural stone outside the cleared arena.
        target.moveTo(truck.getX() + 200, level.getMaxBuildHeight() + 20, truck.getZ(), 0, 0);
        var targetChunk = target.chunkPosition();
        level.setChunkForced(targetChunk.x, targetChunk.z, true); // Only the fixture's initial observation.
        level.addFreshEntity(target);
        ItemStack remote = new ItemStack(ModContent.REMOTE.get());
        remote.getOrCreateTag().putUUID("Vehicle", truck.getUUID());
        remote.getOrCreateTag().putString("Dimension", level.dimension().location().toString());
        owner.setItemInHand(InteractionHand.MAIN_HAND, remote);
        int[] targetTickAtDeparture = {0};
        long[] departureTick = {-1};
        int ammunition = truck.getMissileCount();
        helper.onEachTick(() -> {
            if (departureTick[0] >= 0) return;
            helper.assertTrue(helper.getTick() < 150,
                    "The loaded aircraft fixture must become observable before departure; entity="
                            + (level.getEntity(target.getUUID()) != null) + ", eligible=" + RadarSystem.canTrack(truck, target));
            if (helper.getTick() < 12 || truck.getRadarContacts().stream().noneMatch(c -> c.uuid().equals(target.getUUID()))) return;
            truck.selectTarget(target.getId());
            helper.assertTrue(truck.getLockedTarget() == target, "A server-observed aircraft 200 blocks away must be selectable");
            FkChunkLoading.refreshVehicle(truck);
            helper.assertTrue(FkChunkLoading.ownsTicket(level, truck.getUUID(), targetChunk),
                    "The launcher must retain its already selected aircraft before a missile exists");
            level.setChunkForced(targetChunk.x, targetChunk.z, false);
            owner.moveTo(truck.getX() + 1024, level.getMaxBuildHeight() + 30, truck.getZ());
            targetTickAtDeparture[0] = target.tickCount;
            departureTick[0] = helper.getTick();
            helper.assertTrue(ModNetwork.openRemote(owner, remote) && ModNetwork.authorizedVehicle(owner).requestLaunch(),
                    "The owner must be able to begin firing at that aircraft from 1024 blocks away");
        });
        helper.onEachTick(() -> {
            if (departureTick[0] < 0 || helper.getTick() < departureTick[0] + ModConfig.PREPARATION_TICKS.get() + 3) return;
            helper.assertTrue(target.tickCount > targetTickAtDeparture[0] + 30 && truck.getMissileCount() == ammunition - 1,
                    "The selected aircraft must keep ticking throughout the real distant preparation and launch");
            helper.assertTrue(truck.getLockedTarget() == target,
                    "Leaving the simulation area must not erase a valid already selected target");
            level.getEntitiesOfClass(MissileEntity.class, truck.getBoundingBox().inflate(12, 60, 12)).forEach(m -> {
                CompoundTag data = new CompoundTag(); m.saveWithoutId(data);
                if (data.hasUUID("Launcher") && truck.getUUID().equals(data.getUUID("Launcher"))) m.discard();
            });
            truck.discard(); target.discard();
            VehicleGameTests.removePlayerFixture(helper, owner);
            helper.succeed();
        });
    }

    private static final class RangeTarget extends ArmorStand implements AerialTarget {
        RangeTarget(Level level) { super(EntityType.ARMOR_STAND, level); setNoGravity(true); }
        @Override public boolean isAvailableToRadar() { return true; }
    }
}
