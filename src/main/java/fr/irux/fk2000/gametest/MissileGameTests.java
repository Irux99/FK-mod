package fr.irux.fk2000.gametest;

import fr.irux.fk2000.config.ModConfig;
import fr.irux.fk2000.entity.missile.FlightState;
import fr.irux.fk2000.entity.missile.MissileEntity;
import fr.irux.fk2000.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.EnumSet;
import java.util.UUID;

/** Tests production entities in an actual dedicated Forge server; never emulates Minecraft classes. */
@GameTestHolder("iruxfk2000")
@PrefixGameTestTemplate(false)
public final class MissileGameTests {
    private MissileGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void coldLaunchTurnsBeforeIgnition(GameTestHelper helper) {
        ArmorStand target = target(helper, new Vec3(25.5, 28, 4.5));
        ServerPlayer owner = VehicleGameTests.onlinePlayerFixture(helper);
        MissileEntity missile = missile(helper, new Vec3(4.5, 3, 4.5), target, owner);
        Vec3 origin = missile.position();
        EnumSet<FlightState> observed = EnumSet.noneOf(FlightState.class);
        Vec3[] previousNose = {missile.getLookAngle()};
        helper.onEachTick(() -> {
            helper.assertFalse(missile.isRemoved(), "Missile disappeared before completing cold launch: " + diagnostic(missile));
            FlightState state = missile.getFlightState();
            observed.add(state);
            if (state == FlightState.EJECTING) {
                helper.assertFalse(missile.getMotorActive(), "Main motor ignited during cold ejection");
                helper.assertTrue(missile.getY() > origin.y, "Cold launch must physically move upward");
                helper.assertTrue(missile.getDeltaMovement().y > 0, "Cold ejection requires real vertical velocity");
                helper.assertTrue(Math.abs(missile.getX() - origin.x) < .001
                        && Math.abs(missile.getZ() - origin.z) < .001, "Cold ejection must remain vertical");
            }
            if (state == FlightState.ORIENTING) {
                helper.assertFalse(missile.getMotorActive(), "Main motor must stay off during side-jet orientation");
                double dot = Math.max(-1, Math.min(1, previousNose[0].dot(missile.getLookAngle())));
                helper.assertTrue(Math.acos(dot) <= Math.PI / ModConfig.ORIENTATION_TICKS.get() + .005,
                        "Missile nose snapped instead of rotating progressively");
            }
            previousNose[0] = missile.getLookAngle();
            if (state == FlightState.BOOSTING) {
                helper.assertTrue(observed.contains(FlightState.EJECTING), "Ejection phase was skipped");
                helper.assertTrue(observed.contains(FlightState.ORIENTING), "Orientation phase was skipped");
                helper.assertTrue(missile.getMotorActive(), "Engine must ignite in boosting phase");
                missile.discard();
                target.discard();
                VehicleGameTests.removePlayerFixture(helper, owner);
                helper.succeed();
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void solidImpactDetonatesWithoutDestroyingTerrain(GameTestHelper helper) {
        helper.assertFalse(ModConfig.BLOCK_DAMAGE.get(), "This safety test requires the default blockDamage=false");
        ArmorStand target = target(helper, new Vec3(25.5, 28, 4.5));
        helper.setBlock(new BlockPos(4, 10, 4), Blocks.STONE);
        ServerPlayer owner = VehicleGameTests.onlinePlayerFixture(helper);
        MissileEntity missile = missile(helper, new Vec3(4.5, 3, 4.5), target, owner);
        helper.succeedWhen(() -> {
            helper.assertTrue(missile.isRemoved(), "Missile has not reached the solid block yet");
            helper.assertTrue(missile.getFlightState() == FlightState.DETONATED, "Solid impact must detonate the armed missile");
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(4, 10, 4));
            target.discard();
            VehicleGameTests.removePlayerFixture(helper, owner);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void lostTargetExpiresWithoutDetonation(GameTestHelper helper) {
        ArmorStand target = target(helper, new Vec3(25.5, 28, 4.5));
        ServerPlayer owner = VehicleGameTests.onlinePlayerFixture(helper);
        MissileEntity missile = missile(helper, new Vec3(4.5, 3, 4.5), target, owner);
        helper.startSequence()
                .thenIdle(12)
                .thenExecute(target::discard)
                .thenIdle(10)
                .thenExecute(() -> helper.assertFalse(missile.isRemoved(), "Target-loss grace period was skipped: " + diagnostic(missile)))
                .thenIdle(13)
                .thenExecute(() -> {
                    helper.assertTrue(missile.isRemoved(), "Lost-target missile must expire instead of chasing forever");
                    helper.assertTrue(missile.getFlightState() != FlightState.DETONATED,
                            "Losing a target must not produce an uncontrolled explosion");
                    helper.assertTrue(missile.getTerminationReason() == MissileEntity.TerminationReason.TARGET_LOST,
                            "Target loss must be the actual expiry reason: " + diagnostic(missile));
                    VehicleGameTests.removePlayerFixture(helper, owner);
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void persistedLifetimeExpiresWithoutDetonation(GameTestHelper helper) {
        ArmorStand target = target(helper, new Vec3(25.5, 28, 4.5));
        ServerPlayer owner = VehicleGameTests.onlinePlayerFixture(helper);
        MissileEntity missile = missile(helper, new Vec3(4.5, 3, 4.5), target, owner);
        CompoundTag saved = new CompoundTag();
        missile.saveWithoutId(saved);
        saved.putInt("FlightTicks", ModConfig.MISSILE_LIFETIME.get() - 5);
        // Exercise real production NBT restoration; do not mutate global server configuration.
        missile.load(saved);
        helper.startSequence().thenIdle(8).thenExecute(() -> {
            helper.assertTrue(missile.isRemoved(), "Restored flight lifetime must still expire on the server");
            helper.assertTrue(missile.getFlightState() != FlightState.DETONATED, "Expired fuel must not detonate");
            helper.assertTrue(missile.getTerminationReason() == MissileEntity.TerminationReason.LIFETIME,
                    "Restored missile must expire from its lifetime: " + diagnostic(missile));
            target.discard();
            VehicleGameTests.removePlayerFixture(helper, owner);
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void poweredGuidanceInterceptsAerialTarget(GameTestHelper helper) {
        assertFlightCorridorClear(helper);
        Pig target = helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(24.5, 75, 4.5));
        target.setNoGravity(true);
        target.setDeltaMovement(Vec3.ZERO);
        float initialHealth = target.getHealth();
        ServerPlayer owner = VehicleGameTests.onlinePlayerFixture(helper);
        MissileEntity missile = missile(helper, new Vec3(4.5, 3, 4.5), target, owner);
        EnumSet<FlightState> observed = EnumSet.noneOf(FlightState.class);
        helper.onEachTick(() -> {
            observed.add(missile.getFlightState());
            helper.assertTrue(helper.getTick() < 198 || missile.isRemoved(),
                    "Stationary intercept stalled: " + diagnostic(missile) + ", target=" + target.position() + ", velocity=" + missile.getDeltaMovement());
            if (!missile.isRemoved()) return;
            helper.assertTrue(missile.getFlightState() == FlightState.DETONATED,
                    "Guided missile expired before intercepting its stationary airborne target: " + diagnostic(missile));
            helper.assertTrue(observed.contains(FlightState.BOOSTING) && observed.contains(FlightState.GUIDING),
                    "Guided impact must exercise the actual powered flight phases");
            helper.assertTrue(!target.isAlive() || target.getHealth() < initialHealth,
                    "Server-side detonation must damage the intercepted target");
            target.discard();
            VehicleGameTests.removePlayerFixture(helper, owner);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void poweredGuidanceTracksMovingTarget(GameTestHelper helper) {
        assertFlightCorridorClear(helper);
        Pig target = helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(24.5, 75, 4.5));
        target.setNoGravity(true);
        Vec3 initialPosition = target.position();
        float initialHealth = target.getHealth();
        ServerPlayer owner = VehicleGameTests.onlinePlayerFixture(helper);
        MissileEntity missile = missile(helper, new Vec3(4.5, 3, 4.5), target, owner);
        EnumSet<FlightState> observed = EnumSet.noneOf(FlightState.class);
        Vec3[] previousPosition = {initialPosition};
        double[] travelled = {0};
        double[] maximumSpeed = {0};
        helper.onEachTick(() -> {
            observed.add(missile.getFlightState());
            helper.assertTrue(helper.getTick() < 198 || missile.isRemoved(),
                    "Moving intercept stalled: " + diagnostic(missile) + ", target=" + target.position() + ", velocity=" + missile.getDeltaMovement());
            travelled[0] += target.position().distanceTo(previousPosition[0]);
            previousPosition[0] = target.position();
            maximumSpeed[0] = Math.max(maximumSpeed[0], target.getDeltaMovement().length());
            // This real entity traverses the loaded arena while production guidance reads its velocity.
            if (target.isAlive()) target.setDeltaMovement(0, 0, .10);
            if (!missile.isRemoved()) return;
            String details = diagnostic(missile) + ", states=" + observed + ", gameTick=" + helper.getTick()
                    + ", initialTarget=" + initialPosition + ", finalTarget=" + target.position()
                    + ", targetAge=" + target.tickCount + ", targetVelocity=" + target.getDeltaMovement()
                    + ", totalTravel=" + travelled[0] + ", peakSpeed=" + maximumSpeed[0]
                    + ", targetHealth=" + target.getHealth() + "/" + initialHealth;
            helper.assertTrue(missile.getFlightState() == FlightState.DETONATED,
                    "Guidance did not intercept the moving airborne target: " + details);
            helper.assertTrue(observed.contains(FlightState.BOOSTING) && observed.contains(FlightState.GUIDING),
                    "Moving interception must exercise powered guidance: " + details);
            helper.assertTrue(target.getZ() - initialPosition.z > 4,
                    "The target must actually move during the guided interception: " + details);
            helper.assertTrue(!target.isAlive() || target.getHealth() < initialHealth,
                    "A moving target must receive real server-side explosion damage: " + details);
            target.discard();
            VehicleGameTests.removePlayerFixture(helper, owner);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void guidanceClimbsOverMountainBeforeIntercept(GameTestHelper helper) {
        // The ridge is taller than the direct path and the complete cold-ejection arc.
        // It spans the arena's width, so reaching the target requires climbing above it.
        for (int x = 14; x <= 17; x++) {
            for (int y = 2; y <= 44; y++) {
                for (int z = 1; z <= 30; z++) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
        }
        Pig target = helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(26.5, 30, 7.5));
        target.setNoGravity(true);
        float initialHealth = target.getHealth();
        ServerPlayer owner = VehicleGameTests.onlinePlayerFixture(helper);
        MissileEntity missile = missile(helper, new Vec3(4.5, 3, 7.5), target, owner);
        double ridgeTop = helper.absolutePos(new BlockPos(14, 45, 7)).getY();
        Vec3 launchPosition = missile.position();
        Vec3 corridor = target.position().subtract(launchPosition).multiply(1, 0, 1).normalize();
        boolean[] crossedAbove = {false};
        double[] peakAltitude = {missile.getY()};
        helper.onEachTick(() -> {
            double progress = missile.position().subtract(launchPosition).dot(corridor);
            peakAltitude[0] = Math.max(peakAltitude[0], missile.getY());
            if (progress >= 9.5 && progress < 13.5 && missile.getY() > ridgeTop + 1) crossedAbove[0] = true;
            helper.assertTrue(helper.getTick() < 238 || missile.isRemoved(),
                    "Mountain intercept stalled: " + diagnostic(missile) + ", target=" + target.position()
                            + ", peakY=" + peakAltitude[0] + ", ridgeTop=" + ridgeTop + ", progress=" + progress);
            if (!missile.isRemoved()) return;
            helper.assertTrue(missile.getTerminationReason() == MissileEntity.TerminationReason.DETONATED,
                    "Missile must reach the target beyond the mountain: " + diagnostic(missile));
            helper.assertTrue(crossedAbove[0], "Missile must physically cross above the mountain instead of striking or bypassing it: " + diagnostic(missile));
            helper.assertTrue(!target.isAlive() || target.getHealth() < initialHealth,
                    "The target beyond the intact mountain must receive server-side damage");
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(14, 44, 7));
            target.discard();
            VehicleGameTests.removePlayerFixture(helper, owner);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 220)
    public static void terminalGuidanceInterceptsLowAltitudeTarget(GameTestHelper helper) {
        Pig target = helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(26.5, 10, 7.5));
        target.setNoGravity(true);
        float initialHealth = target.getHealth();
        ServerPlayer owner = VehicleGameTests.onlinePlayerFixture(helper);
        MissileEntity missile = missile(helper, new Vec3(4.5, 3, 7.5), target, owner);
        helper.onEachTick(() -> {
            helper.assertTrue(helper.getTick() < 218 || missile.isRemoved(),
                    "Low target intercept stalled: " + diagnostic(missile) + ", target=" + target.position()
                            + ", velocity=" + missile.getDeltaMovement());
            if (!missile.isRemoved()) return;
            helper.assertTrue(missile.getTerminationReason() == MissileEntity.TerminationReason.DETONATED,
                    "Guidance must reach a nearby low aircraft instead of orbiting it: " + diagnostic(missile));
            helper.assertTrue(!target.isAlive() || target.getHealth() < initialHealth,
                    "A target ten blocks above flat ground must receive real warhead damage");
            target.discard();
            VehicleGameTests.removePlayerFixture(helper, owner);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void terminalGuidanceClosesOnFastManeuveringTarget(GameTestHelper helper) {
        assertFlightCorridorClear(helper);
        // A physical ~0.72-block/tick circular course stays inside the loaded fixture.
        // It exposes terminal-speed caps which can intercept slow mobs but never catch aircraft.
        Pig target = helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(25, 75, 16));
        target.setNoGravity(true);
        float initialHealth = target.getHealth();
        ServerPlayer owner = VehicleGameTests.onlinePlayerFixture(helper);
        MissileEntity missile = missile(helper, new Vec3(4.5, 3, 4.5), target, owner);
        Vec3[] previous = {target.position()};
        double[] travelled = {0};
        int[] fastTicks = {0};
        helper.onEachTick(() -> {
            double travelledThisTick = target.position().distanceTo(previous[0]);
            travelled[0] += travelledThisTick;
            if (travelledThisTick > .65) fastTicks[0]++;
            previous[0] = target.position();
            double angle = (helper.getTick() + 1) * .08;
            Vec3 nextPoint = helper.absoluteVec(new Vec3(16 + Math.cos(angle) * 9, 75, 16 + Math.sin(angle) * 9));
            Vec3 input = nextPoint.subtract(target.position());
            if (input.length() > .8) input = input.normalize().scale(.8);
            if (target.isAlive()) target.setDeltaMovement(input);
            String details = diagnostic(missile) + ", target=" + target.position() + ", travelled=" + travelled[0]
                    + ", fastTicks=" + fastTicks[0] + ", missileVelocity=" + missile.getDeltaMovement();
            helper.assertTrue(helper.getTick() < 238 || missile.isRemoved(), "Fast-aircraft intercept stalled: " + details);
            if (!missile.isRemoved()) return;
            helper.assertTrue(missile.getTerminationReason() == MissileEntity.TerminationReason.DETONATED,
                    "A missile must close on a fast maneuvering target: " + details);
            helper.assertTrue(travelled[0] > 25 && fastTicks[0] >= 20,
                    "The target must really move at aircraft speed before impact: " + details);
            helper.assertTrue(!target.isAlive() || target.getHealth() < initialHealth,
                    "The fast maneuvering target must receive real server damage: " + details);
            target.discard();
            VehicleGameTests.removePlayerFixture(helper, owner);
            helper.succeed();
        });
    }

    private static String diagnostic(MissileEntity missile) {
        return "reason=" + missile.getTerminationReason() + ", state=" + missile.getFlightState()
                + ", age=" + missile.tickCount + ", position=" + missile.position();
    }

    private static void assertFlightCorridorClear(GameTestHelper helper) {
        // The original 96-block-high fixture explicitly clears the whole flight
        // volume. These checks also catch a loader truncating it to 48 blocks.
        for (int y = 74; y <= 76; y++) {
            for (int z = 4; z <= 26; z++) {
                helper.assertBlockPresent(Blocks.AIR, new BlockPos(24, y, z));
            }
        }
    }

    private static ArmorStand target(GameTestHelper helper, Vec3 relativePosition) {
        ArmorStand target = helper.spawn(EntityType.ARMOR_STAND, relativePosition);
        target.setNoGravity(true);
        return target;
    }

    private static MissileEntity missile(GameTestHelper helper, Vec3 relativePosition, Entity target, ServerPlayer owner) {
        MissileEntity missile = ModContent.MISSILE.get().create(helper.getLevel());
        helper.assertTrue(missile != null, "Registered missile entity could not be created");
        Vec3 position = helper.absoluteVec(relativePosition);
        missile.moveTo(position.x, position.y, position.z, 0, -90);
        missile.launch(owner.getUUID(), UUID.randomUUID(), target);
        helper.assertTrue(helper.getLevel().addFreshEntity(missile), "Server refused to spawn the registered missile");
        return missile;
    }
}
