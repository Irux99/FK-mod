package fr.irux.fk2000.gametest;

import fr.irux.fk2000.config.ModConfig;
import fr.irux.fk2000.entity.vehicle.Fk2000Entity;
import fr.irux.fk2000.radar.RadarSystem;
import fr.irux.fk2000.radar.TargetCoordinates;
import fr.irux.fk2000.radar.TargetMode;
import fr.irux.fk2000.registry.ModContent;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("iruxfk2000")
@PrefixGameTestTemplate(false)
public final class RadarModeGameTests {
    private RadarModeGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void entityModeIncludesMobsAndKeepsAlliedExclusions(GameTestHelper helper) {
        ServerPlayer owner = VehicleGameTests.onlinePlayerFixture(helper);
        Fk2000Entity vehicle = helper.spawn(ModContent.VEHICLE.get(), new Vec3(16, 2, 16));
        try {
        vehicle.setOwnerUUID(owner.getUUID());
        vehicle.setRadarEnabled(true);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(24, 2, 16));
        var stand = helper.spawn(EntityType.ARMOR_STAND, new Vec3(24, 2, 20));
        var boat = helper.spawn(EntityType.BOAT, new Vec3(24, 2, 24));
        helper.assertFalse(RadarSystem.canTrack(vehicle, pig), "The default aircraft mode must not select an ordinary mob");
        vehicle.setTargetMode(TargetMode.ALL_ENTITIES);
        helper.assertTrue(RadarSystem.canTrack(vehicle, pig), "All-entities mode must allow a living mob");
        helper.assertTrue(RadarSystem.canTrack(vehicle, stand), "All-entities mode must allow a pickable armor stand");
        helper.assertTrue(RadarSystem.canTrack(vehicle, boat), "All-entities mode must allow a vanilla vehicle");
        helper.assertFalse(RadarSystem.canTrack(vehicle, owner), "Target-mode changes must not disable protection of the owner");
        helper.assertFalse(RadarSystem.canTrack(vehicle, vehicle), "The truck must never target itself");
        ItemEntity dropped = new ItemEntity(helper.getLevel(), pig.getX(), pig.getY(), pig.getZ(), new ItemStack(Items.STONE));
        helper.assertFalse(RadarSystem.isEligibleForMode(TargetMode.ALL_ENTITIES, dropped),
                "Dropped items and decorative non-target entities must not fill the bounded target list");
        var scoreboard = helper.getLevel().getScoreboard();
        var team = scoreboard.addPlayerTeam("radar_" + owner.getStringUUID().substring(0, 8));
        scoreboard.addPlayerToTeam(owner.getScoreboardName(), team);
        scoreboard.addPlayerToTeam(pig.getScoreboardName(), team);
        helper.assertFalse(RadarSystem.canTrack(vehicle, pig), "Friendly teams remain protected in all-entities mode");
        scoreboard.removePlayerTeam(team);
        vehicle.setTargetMode(TargetMode.SBW_VEHICLES);
        helper.assertFalse(RadarSystem.canTrack(vehicle, pig), "SBW-vehicles mode must not include ordinary mobs");
        helper.succeed();
        } finally {
            vehicle.discard();
            VehicleGameTests.removePlayerFixture(helper, owner);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void manualCoordinatesValidatePersistAndDoNotFabricateEntities(GameTestHelper helper) {
        ServerPlayer owner = VehicleGameTests.onlinePlayerFixture(helper);
        Fk2000Entity vehicle = helper.spawn(ModContent.VEHICLE.get(), new Vec3(16, 2, 16));
        try {
        vehicle.setOwnerUUID(owner.getUUID());
        vehicle.setRadarEnabled(true);
        Vec3 destination = helper.absoluteVec(new Vec3(26.25, 60.5, 20.75));
        helper.assertTrue(vehicle.setTargetCoordinates(destination), "Finite loaded or unloaded world coordinates in range must be accepted");
        helper.assertTrue(vehicle.getTargetMode() == TargetMode.COORDINATES && destination.equals(vehicle.getTargetCoordinates()),
                "Applying coordinates must select a fixed destination mode");
        helper.assertTrue(vehicle.getLockedTarget() == null && RadarSystem.scan(vehicle).isEmpty(),
                "Coordinate targeting must not fabricate an entity or radar contact");
        helper.assertFalse(vehicle.setTargetCoordinates(new Vec3(Double.NaN, destination.y, destination.z)),
                "Non-finite coordinates must be rejected on the server");
        helper.assertFalse(TargetCoordinates.isValid(vehicle, new Vec3(destination.x, Double.POSITIVE_INFINITY, destination.z)),
                "Infinite coordinate input must be rejected");
        helper.assertFalse(TargetCoordinates.isValid(vehicle, vehicle.position().add(ModConfig.COORDINATE_TARGET_RANGE.get() + 1, 0, 0)),
                "Coordinate targets outside the configured range must be rejected");
        helper.assertFalse(TargetCoordinates.isValid(vehicle, new Vec3(vehicle.getX(), helper.getLevel().getMaxBuildHeight(), vehicle.getZ())),
                "The exclusive maximum build height must be enforced");
        helper.assertFalse(TargetCoordinates.isValid(vehicle, new Vec3(30_000_001, destination.y, destination.z)),
                "Coordinates outside Minecraft's legal world must be rejected before integer conversion");
        helper.assertTrue(destination.equals(vehicle.getTargetCoordinates()), "Invalid input must not overwrite a valid destination");
        CompoundTag saved = new CompoundTag();
        vehicle.saveWithoutId(saved);
        Fk2000Entity restored = ModContent.VEHICLE.get().create(helper.getLevel());
        helper.assertTrue(restored != null, "The registered truck must construct for a persistence check");
        restored.load(saved);
        helper.assertTrue(restored.getTargetMode() == TargetMode.COORDINATES
                        && destination.equals(restored.getTargetCoordinates()),
                "Fixed destinations and their precise double coordinates must survive world reload");
        helper.assertTrue(vehicle.requestLaunch(), "A coordinate destination must be launchable without an entity lock");
        vehicle.setTargetMode(TargetMode.AIRCRAFT);
        helper.assertTrue(vehicle.getTargetCoordinates() == null && !vehicle.isPreparingLaunch(),
                "Changing away from coordinates must clear the destination and cancel its launch preparation");
        helper.succeed();
        } finally {
            vehicle.discard();
            VehicleGameTests.removePlayerFixture(helper, owner);
        }
    }
}
