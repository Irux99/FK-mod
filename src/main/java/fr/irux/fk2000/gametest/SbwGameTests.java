package fr.irux.fk2000.gametest;

import fr.irux.fk2000.compat.SbwCompatibility;
import fr.irux.fk2000.config.ModConfig;
import fr.irux.fk2000.entity.missile.FlightState;
import fr.irux.fk2000.entity.missile.MissileEntity;
import fr.irux.fk2000.entity.vehicle.Fk2000Entity;
import fr.irux.fk2000.radar.RadarSystem;
import fr.irux.fk2000.registry.ModContent;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.function.BiPredicate;

/**
 * Required integration tests run only in the iruxfk2000_sbw namespace, with one
 * ORIGINAL release jar installed. They fail if SBW is absent; there is no skipped
 * success path. Concrete entity IDs belong only to test fixtures, never detection.
 */
@GameTestHolder("iruxfk2000_sbw")
@PrefixGameTestTemplate(false)
public final class SbwGameTests {
    private SbwGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40, batch = "sbw_compatibility")
    public static void releaseAircraftAndHelicopterClassification(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("superbwarfare"), "This suite requires an actual Superb Warfare release");
        Entity airplane = spawnSbw(helper, "a_10a", new Vec3(8, 5, 8));
        Entity helicopter = spawnSbw(helper, "ah_6", new Vec3(18, 5, 8));
        Entity tank = spawnSbw(helper, "ztz_99a", new Vec3(8, 2, 20));
        helper.assertTrue(SbwCompatibility.isAerial(airplane), "The release's actual A-10A must be classified as aerial");
        helper.assertTrue(SbwCompatibility.isAerial(helicopter), "The release's actual AH-6 must be classified as aerial");
        helper.assertFalse(SbwCompatibility.isAerial(tank), "The release's actual ground tank must not be classified as aerial");
        try {
            Method wreck = airplane.getClass().getMethod("setWreck", boolean.class);
            wreck.invoke(airplane, true);
            helper.assertFalse(SbwCompatibility.isAerial(airplane), "A wrecked SBW aircraft must not be engaged");
        } catch (ReflectiveOperationException error) {
            helper.fail("The release's verified isWreck setter API failed: " + error);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 500, batch = "sbw_compatibility")
    @SuppressWarnings("unchecked")
    public static void releaseRadarAndIffRespectDriverAndTeams(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("superbwarfare"), "This suite requires an actual Superb Warfare release");
        Fk2000Entity vehicle = helper.spawn(ModContent.VEHICLE.get(), new Vec3(6, 2, 6));
        ServerPlayer owner = VehicleGameTests.onlinePlayerFixture(helper);
        vehicle.setOwnerUUID(owner.getUUID());
        vehicle.setRadarEnabled(true);
        vehicle.setAutoAcquire(false);
        vehicle.setAutoFire(false);
        int initialMissiles = vehicle.getMissileCount();
        Entity airplane = spawnSbw(helper, "a_10a", new Vec3(18, 8, 18));
        Entity helicopter = spawnSbw(helper, "ah_6", new Vec3(22, 8, 18));
        helper.assertTrue(RadarSystem.canTrack(vehicle, airplane), "An initially hostile release aircraft must be trackable");
        helper.assertTrue(RadarSystem.canTrack(vehicle, helicopter), "An initially hostile release helicopter must be trackable");
        try {
            Class<?> seekTool = Class.forName("com.atsuishio.superbwarfare.tools.SeekTool");
            Object field = seekTool.getField("IS_FRIENDLY").get(null);
            helper.assertTrue(field instanceof BiPredicate<?, ?>, "The actual release must expose its IFF predicate");
            BiPredicate<Entity, Entity> iff = (BiPredicate<Entity, Entity>) field;
            airplane.getClass().getMethod("setLastDriverUUID", String.class).invoke(airplane, owner.getStringUUID());
            helper.assertTrue(iff.test(owner, airplane), "SBW's own IFF must recognize the owner's last-driven aircraft");
            helper.assertFalse(RadarSystem.canTrack(vehicle, airplane), "The addon radar must reject the owner's last-driven aircraft");
        } catch (ReflectiveOperationException error) {
            VehicleGameTests.removePlayerFixture(helper, owner);
            helper.fail("The release's verified driver/IFF APIs failed: " + error);
            return;
        }
        Scoreboard scoreboard = helper.getLevel().getScoreboard();
        String teamName = "irux_" + owner.getStringUUID().substring(0, 8);
        PlayerTeam team = scoreboard.addPlayerTeam(teamName);
        scoreboard.addPlayerToTeam(owner.getScoreboardName(), team);
        scoreboard.addPlayerToTeam(helicopter.getScoreboardName(), team);
        helper.assertFalse(RadarSystem.canTrack(vehicle, helicopter), "Vanilla scoreboard allies must be protected");
        scoreboard.removePlayerTeam(team);
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(vehicle.getRadarContacts().stream().noneMatch(c -> c.uuid().equals(airplane.getUUID())),
                    "The actual server radar scan must omit the owner's SBW aircraft");
            helper.assertTrue(vehicle.getRadarContacts().stream().anyMatch(c -> c.uuid().equals(helicopter.getUUID())),
                    "The actual server radar scan must detect a hostile release helicopter after its team protection is removed");
            vehicle.selectTarget(helicopter.getId());
            helper.assertTrue(vehicle.getLockedTarget() == helicopter,
                    "The actual release helicopter must be manually lockable after the server scan");
            helper.assertTrue(vehicle.requestLaunch(), "The actual release helicopter must accept a validated fire order");
            helper.assertFalse(vehicle.requestLaunch(), "Repeated fire orders must not duplicate a release-target launch");
        });
        helper.runAtTickTime(12 + ModConfig.PREPARATION_TICKS.get() + 3, () -> {
            helper.assertTrue(vehicle.getMissileCount() == initialMissiles - 1,
                    "Launching at the actual release helicopter must consume exactly one missile");
            MissileEntity launched = helper.getLevel().getEntitiesOfClass(MissileEntity.class,
                    vehicle.getBoundingBox().inflate(8, 24, 8)).stream().filter(missile -> {
                        CompoundTag saved = new CompoundTag();
                        missile.saveWithoutId(saved);
                        return saved.hasUUID("Launcher") && vehicle.getUUID().equals(saved.getUUID("Launcher"));
                    }).findFirst().orElse(null);
            helper.assertTrue(launched != null, "A real missile entity must leave the launcher against the actual release target");
            helper.assertTrue(launched.getFlightState() == FlightState.EJECTING && !launched.getMotorActive(),
                    "The integrated launch must begin with cold ejection, before main-engine ignition");
            helper.assertTrue(launched.getY() > vehicle.getY() + 3.2,
                    "The integrated missile must physically rise above the launch tube");
            VehicleGameTests.removePlayerFixture(helper, owner);
            helper.succeed();
        });
    }

    private static Entity spawnSbw(GameTestHelper helper, String path, Vec3 relativePosition) {
        ResourceLocation id = new ResourceLocation("superbwarfare", path);
        helper.assertTrue(ForgeRegistries.ENTITY_TYPES.containsKey(id), "The release must register fixture " + id);
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
        helper.assertTrue(type != null, "The release fixture entity type must exist: " + id);
        Entity entity = type.create(helper.getLevel());
        helper.assertTrue(entity != null, "The release fixture must construct: " + id);
        entity.moveTo(helper.absoluteVec(relativePosition));
        entity.setNoGravity(true);
        helper.assertTrue(helper.getLevel().addFreshEntity(entity), "The release fixture must enter the server world: " + id);
        return entity;
    }
}
