package fr.irux.fk2000.gametest;

import fr.irux.fk2000.compat.AerialTarget;
import fr.irux.fk2000.config.ModConfig;
import fr.irux.fk2000.entity.vehicle.Fk2000Entity;
import fr.irux.fk2000.item.RemoteControllerItem;
import fr.irux.fk2000.radar.RadarSystem;
import fr.irux.fk2000.registry.ModContent;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.common.util.FakePlayerFactory;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Dedicated-server integration checks. Fixtures add no registered production content.
 * The structure floor is at helper-relative Y=1 (the template is placed above the
 * structure block), so vehicle feet belong at Y=2 rather than inside the floor.
 */
@GameTestHolder("iruxfk2000")
@PrefixGameTestTemplate(false)
public final class VehicleGameTests {
    private VehicleGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void ownershipAndSafePersistence(GameTestHelper helper) {
        Fk2000Entity vehicle = helper.spawn(ModContent.VEHICLE.get(), new Vec3(6, 2, 6));
        Player owner = helper.makeMockSurvivalPlayer();
        Player intruder = helper.makeMockSurvivalPlayer();
        vehicle.setOwnerUUID(owner.getUUID());
        vehicle.setOwnerUUID(intruder.getUUID());
        helper.assertTrue(vehicle.canControl(owner), "Registered owner must be authorized");
        helper.assertFalse(vehicle.canControl(intruder), "Another player must not control the vehicle");
        helper.assertTrue(vehicle.getOwnerUUID().equals(owner.getUUID()), "Ownership must not be overwritten");
        helper.assertTrue(vehicle.getControllingPassenger() == null,
                "Vanilla vehicle-position packets must not acquire movement authority");
        vehicle.setEngineOn(true);
        vehicle.setDriveInput(1, 0, false);
        CompoundTag saved = new CompoundTag();
        vehicle.addAdditionalSaveData(saved);
        Fk2000Entity restored = ModContent.VEHICLE.get().create(helper.getLevel());
        helper.assertTrue(restored != null, "Registered vehicle entity must be constructible");
        restored.readAdditionalSaveData(saved);
        helper.assertTrue(owner.getUUID().equals(restored.getOwnerUUID()), "Owner must persist through NBT");
        helper.assertTrue(restored.getMissileCount() == vehicle.getMissileCount(), "Missile inventory must persist");
        helper.assertFalse(restored.isEngineOn(), "Reloading a saved vehicle must not restart its engine");
        helper.assertTrue(restored.getSpeed() == 0 && restored.getLockedTarget() == null,
                "Reloading must clear transient movement and target locks");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void reloadConsumesOneItemAndRejectsReplay(GameTestHelper helper) {
        Fk2000Entity vehicle = helper.spawn(ModContent.VEHICLE.get(), new Vec3(6, 2, 6));
        Player owner = helper.makeMockSurvivalPlayer();
        Player intruder = helper.makeMockSurvivalPlayer();
        owner.moveTo(vehicle.position());
        intruder.moveTo(vehicle.position());
        owner.getAbilities().instabuild = false;
        intruder.getAbilities().instabuild = false;
        vehicle.setOwnerUUID(owner.getUUID());
        CompoundTag saved = new CompoundTag();
        vehicle.addAdditionalSaveData(saved);
        saved.putInt("Missiles", 0);
        vehicle.readAdditionalSaveData(saved);
        owner.getInventory().add(new ItemStack(ModContent.MISSILE_ITEM.get(), 2));
        intruder.getInventory().add(new ItemStack(ModContent.MISSILE_ITEM.get(), 2));
        vehicle.reload(intruder);
        helper.assertTrue(vehicle.getMissileCount() == 0, "An unauthorized reload must be rejected");
        helper.assertTrue(countMissiles(intruder) == 2, "Unauthorized reload must not consume someone else's item");
        vehicle.reload(owner);
        helper.assertTrue(vehicle.getMissileCount() == 1, "A successful reload must add exactly one missile");
        helper.assertTrue(countMissiles(owner) == 1, "A successful reload must consume exactly one item");
        vehicle.reload(owner);
        helper.assertTrue(vehicle.getMissileCount() == 1 && countMissiles(owner) == 1,
                "Repeated reload commands must respect cooldown and never duplicate ammunition");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void creativeRemotePairingSurvivesStackCopy(GameTestHelper helper) {
        Fk2000Entity vehicle = helper.spawn(ModContent.VEHICLE.get(), new Vec3(6, 2, 6));
        Player owner = helper.makeMockSurvivalPlayer();
        owner.getAbilities().instabuild = true;
        owner.moveTo(vehicle.getX() + 3, vehicle.getY(), vehicle.getZ());
        owner.setShiftKeyDown(true);
        vehicle.setOwnerUUID(owner.getUUID());
        ItemStack remote = new ItemStack(ModContent.REMOTE.get());
        owner.setItemInHand(InteractionHand.MAIN_HAND, remote);
        // Exercise vanilla Player.interactOn, which passes a COPY to the item's
        // interactLivingEntity method in creative mode; direct item calls miss this bug.
        helper.assertTrue(owner.interactOn(vehicle, InteractionHand.MAIN_HAND).consumesAction(),
                "Sneak-interacting with the owner's remote must complete the pairing action");
        helper.assertTrue(owner.getItemInHand(InteractionHand.MAIN_HAND) == remote,
                "Pairing must retain the actual held inventory stack");
        helper.assertTrue(vehicle.getUUID().equals(RemoteControllerItem.boundVehicle(remote)),
                "The actual held remote must retain the vehicle UUID after creative interaction");
        helper.assertTrue(RemoteControllerItem.boundTo(remote, vehicle),
                "Pairing must retain the correct vehicle dimension");
        helper.assertTrue(remote.getCount() == 1, "Pairing must neither consume nor duplicate the remote");
        Player intruder = helper.makeMockSurvivalPlayer();
        intruder.getAbilities().instabuild = true;
        intruder.moveTo(owner.position());
        intruder.setShiftKeyDown(true);
        ItemStack unauthorizedRemote = new ItemStack(ModContent.REMOTE.get());
        intruder.setItemInHand(InteractionHand.MAIN_HAND, unauthorizedRemote);
        intruder.interactOn(vehicle, InteractionHand.MAIN_HAND);
        helper.assertTrue(RemoteControllerItem.boundVehicle(unauthorizedRemote) == null,
                "Creative mode must not allow another player to bind the vehicle");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 110)
    public static void driveCollidesAndExpiresWithoutInput(GameTestHelper helper) {
        Fk2000Entity vehicle = helper.spawn(ModContent.VEHICLE.get(), new Vec3(6, 2, 6));
        Player owner = helper.makeMockSurvivalPlayer();
        vehicle.setOwnerUUID(owner.getUUID());
        vehicle.setRadarEnabled(false);
        vehicle.setYRot(0);
        vehicle.setEngineOn(true);
        double initialZ = vehicle.getZ();
        for (int x = 3; x <= 9; x++) {
            for (int y = 1; y <= 4; y++) helper.setBlock(x, y, 10, Blocks.STONE);
        }
        helper.onEachTick(() -> {
            if (helper.getTick() <= 45) vehicle.setDriveInput(1, 0, false);
            // Back away from the wall before the watchdog check. A parked vehicle
            // already stopped by a wall would not test input expiration at all.
            else if (helper.getTick() <= 52) vehicle.setDriveInput(-1, 0, false);
        });
        helper.runAtTickTime(8, () -> {
            helper.assertTrue(vehicle.getSpeed() > 0, "Engine input must accelerate the actual entity");
            helper.assertTrue(vehicle.getZ() > initialZ + 0.1, "Acceleration must move the entity through the world");
        });
        helper.runAtTickTime(45, () -> {
            double wallZ = helper.absolutePos(new BlockPos(6, 1, 10)).getZ();
            helper.assertTrue(vehicle.getBoundingBox().maxZ <= wallZ + 0.05,
                    "The chassis must collide with solid terrain instead of crossing the wall: " + driveDiagnostic(vehicle, initialZ, wallZ));
            helper.assertTrue(vehicle.getZ() > initialZ + 0.5,
                    "The collision test must actually drive toward the wall: " + driveDiagnostic(vehicle, initialZ, wallZ));
        });
        helper.runAtTickTime(53, () -> {
            helper.assertTrue(vehicle.getSpeed() < -0.02,
                    "The watchdog check must begin with a vehicle actually reversing away from the wall");
        });
        helper.runAtTickTime(85, () -> {
            helper.assertTrue(Math.abs(vehicle.getSpeed()) < 0.001,
                    "Expired remote input must brake the vehicle to rest");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 500)
    public static void radarFiltersAlliesAndLauncherConsumesOnce(GameTestHelper helper) {
        Fk2000Entity vehicle = helper.spawn(ModContent.VEHICLE.get(), new Vec3(6, 2, 6));
        ServerPlayer owner = onlinePlayerFixture(helper);
        vehicle.setOwnerUUID(owner.getUUID());
        vehicle.setRadarEnabled(true);
        vehicle.setAutoAcquire(false);
        vehicle.setAutoFire(false);
        RadarFixture enemy = spawnRadarFixture(helper, new Vec3(18, 8, 18), null);
        RadarFixture ally = spawnRadarFixture(helper, new Vec3(18, 8, 20), owner.getUUID());
        ArmorStand ordinary = helper.spawn(EntityType.ARMOR_STAND, new Vec3(20, 8, 18));
        ordinary.setNoGravity(true);
        int initialMissiles = vehicle.getMissileCount();
        vehicle.selectTarget(enemy.getId());
        helper.assertTrue(vehicle.getLockedTarget() == null, "An unobserved entity ID must not create a lock");
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(vehicle.getRadarContacts().stream().anyMatch(c -> c.uuid().equals(enemy.getUUID())),
                    "Radar must detect a compatible aerial target");
            helper.assertFalse(vehicle.getRadarContacts().stream().anyMatch(c -> c.uuid().equals(ally.getUUID())),
                    "An aerial vehicle belonging to the owner must be protected");
            helper.assertFalse(vehicle.getRadarContacts().stream().anyMatch(c -> c.uuid().equals(ordinary.getUUID())),
                    "Ordinary non-aerial entities must not appear as aircraft");
            vehicle.selectTarget(enemy.getId());
            helper.assertTrue(vehicle.getLockedTarget() == enemy, "A scanned hostile target must be lockable");
            helper.assertTrue(vehicle.requestLaunch(), "A stable armed launcher must accept a valid fire order");
            helper.assertFalse(vehicle.requestLaunch(), "A repeated fire order must not prepare a second missile");
        });
        helper.runAtTickTime(12 + ModConfig.PREPARATION_TICKS.get() + 3, () -> {
            helper.assertTrue(vehicle.getMissileCount() == initialMissiles - 1,
                    "The completed preparation must spawn one missile and consume one ammunition unit");
            helper.assertFalse(vehicle.isPreparingLaunch(), "The launcher must leave its preparation state");
            helper.assertTrue(vehicle.getLauncherProgress() > 0,
                    "The launcher must visibly return from its deployed position");
            vehicle.setRadarEnabled(false);
            helper.assertTrue(vehicle.getLockedTarget() == null && vehicle.getRadarContacts().isEmpty(),
                    "Disabling radar must clear the lock and contact list");
            helper.assertFalse(vehicle.requestLaunch(), "A disabled radar must not launch");
            vehicle.setRadarEnabled(true);
            removePlayerFixture(helper, owner);
            helper.assertFalse(RadarSystem.canTrack(vehicle, enemy),
                    "An offline owner must stop engagement because team/IFF context is unavailable");
            helper.succeed();
        });
    }

    private static int countMissiles(Player player) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(ModContent.MISSILE_ITEM.get())) count += stack.getCount();
        }
        return count;
    }

    /** Forge's supported fake handler avoids the vanilla helper's channel-less login handshake. */
    static ServerPlayer onlinePlayerFixture(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ServerPlayer owner = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(id, "irux_" + id.toString().substring(0, 8)));
        owner.moveTo(helper.absoluteVec(new Vec3(9, 2, 6)));
        helper.getLevel().addNewPlayer(owner);
        helper.assertTrue(helper.getLevel().getPlayerByUUID(owner.getUUID()) == owner,
                "The test owner must be present in the server level's player list");
        return owner;
    }

    static void removePlayerFixture(GameTestHelper helper, ServerPlayer owner) {
        helper.getLevel().removePlayerImmediately(owner, Entity.RemovalReason.DISCARDED);
    }

    private static String driveDiagnostic(Fk2000Entity vehicle, double initialZ, double wallZ) {
        var bounds = vehicle.getBoundingBox();
        return "initialZ=" + initialZ + ", position=" + vehicle.position() + ", speed=" + vehicle.getSpeed()
                + ", yaw=" + vehicle.getYRot() + ", engine=" + vehicle.isEngineOn() + ", age=" + vehicle.tickCount
                + ", bbox=" + bounds + ", wallZ=" + wallZ + ", loadedCorners="
                + vehicle.level().hasChunkAt(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ)) + "/"
                + vehicle.level().hasChunkAt(BlockPos.containing(bounds.maxX - 1.0E-6, bounds.minY, bounds.maxZ - 1.0E-6));
    }

    private static RadarFixture spawnRadarFixture(GameTestHelper helper, Vec3 relativePosition, @Nullable UUID owner) {
        RadarFixture fixture = new RadarFixture(helper.getLevel(), owner);
        fixture.moveTo(helper.absoluteVec(relativePosition));
        helper.assertTrue(helper.getLevel().addFreshEntity(fixture), "Radar fixture must spawn in the server world");
        return fixture;
    }

    /** An integration-interface fixture; not a registered addon entity or an SBW stand-in. */
    private static final class RadarFixture extends ArmorStand implements AerialTarget, OwnableEntity {
        private final UUID owner;

        private RadarFixture(Level level, @Nullable UUID owner) {
            super(EntityType.ARMOR_STAND, level);
            this.owner = owner;
            setNoGravity(true);
            setInvulnerable(true);
        }

        @Override
        public boolean isAvailableToRadar() { return true; }

        @Nullable
        @Override
        public UUID getOwnerUUID() { return owner; }
    }
}
