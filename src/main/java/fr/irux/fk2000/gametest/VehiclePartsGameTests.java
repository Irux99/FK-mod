package fr.irux.fk2000.gametest;

import fr.irux.fk2000.entity.vehicle.Fk2000Entity;
import fr.irux.fk2000.entity.vehicle.TubeCapEntity;
import fr.irux.fk2000.entity.vehicle.WreckEntity;
import fr.irux.fk2000.registry.ModContent;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Physical debris and carcass persistence checks; production registries are used directly. */
@GameTestHolder("iruxfk2000")
@PrefixGameTestTemplate(false)
public final class VehiclePartsGameTests {
    private VehiclePartsGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void wreckRetainsOwnershipPoseAndOnlyCreativeCanClearIt(GameTestHelper helper) {
        Fk2000Entity source = helper.spawn(ModContent.VEHICLE.get(), new Vec3(16, 2, 16));
        Player owner = helper.makeMockSurvivalPlayer();
        source.setOwnerUUID(owner.getUUID());
        source.setYRot(90);
        WreckEntity wreck = ModContent.WRECK.get().create(helper.getLevel());
        helper.assertTrue(wreck != null, "The registered wreck must construct");
        wreck.initializeFrom(source);
        helper.assertTrue(owner.getUUID().equals(wreck.getOwnerUUID()), "The carcass must retain the truck's ownership");
        helper.assertTrue(Math.abs(wreck.getBoundingBox().getXsize() - 12) < .01,
                "The physical carcass must keep the long chassis footprint after rotation");
        CompoundTag saved = wreck.saveWithoutId(new CompoundTag());
        saved.putFloat("LauncherPose", .7F);
        saved.putFloat("WheelPose", 1.4F);
        WreckEntity restored = ModContent.WRECK.get().create(helper.getLevel());
        helper.assertTrue(restored != null, "A saved carcass must restore");
        restored.load(saved);
        helper.assertTrue(restored.isWreck() && owner.getUUID().equals(restored.getOwnerUUID()),
                "Saving and restoring a carcass must preserve its identity and owner");
        helper.assertTrue(Math.abs(restored.getLauncherProgress() - .7F) < .001
                        && Math.abs(restored.getWheelRotation() - 1.4F) < .001,
                "The destroyed launcher and wheel poses must survive saving");
        source.discard();
        helper.assertTrue(helper.getLevel().addFreshEntity(restored), "The restored carcass must enter the server world");
        owner.getAbilities().instabuild = false;
        helper.assertFalse(restored.hurt(helper.getLevel().damageSources().playerAttack(owner), 20),
                "Repeated survival attacks must not farm carcasses or retrigger explosions");
        helper.runAtTickTime(40, () -> {
            helper.assertFalse(restored.isRemoved(), "The saved carcass must remain after the living truck's death animation would end");
            helper.assertTrue(restored.canBeCollidedWith() && restored.isPickable(),
                    "The visible carcass must remain a solid selectable object");
            owner.getAbilities().instabuild = true;
            helper.assertTrue(restored.hurt(helper.getLevel().damageSources().playerAttack(owner), 1) && restored.isRemoved(),
                    "A creative builder must be able to clear the carcass without another explosion");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 180)
    public static void ejectedCoverRisesFallsAndExpiresAfterReloadingItsAge(GameTestHelper helper) {
        TubeCapEntity original = helper.spawn(ModContent.TUBE_CAP.get(), new Vec3(16, 3, 16));
        original.launch(new Vec3(.04, .8, 0));
        TubeCapEntity[] active = {original};
        double launchHeight = original.getY();
        double[] peak = {launchHeight};
        boolean[] landed = {false};
        helper.onEachTick(() -> {
            if (active[0].isRemoved()) return;
            peak[0] = Math.max(peak[0], active[0].getY());
            landed[0] |= active[0].onGround();
        });
        helper.runAtTickTime(28, () -> {
            helper.assertTrue(peak[0] > launchHeight + 2 && original.getY() < peak[0],
                    "A cover must physically rise and then fall under gravity, rather than being a stationary particle");
            helper.assertTrue(original.getSpin(1) > 90 && !original.isPickable() && !original.canBeCollidedWith(),
                    "A spinning launch cover must not intercept missiles or become a combat target");
        });
        helper.runAtTickTime(85, () -> {
            helper.assertTrue(landed[0] && original.onGround(), "The falling cover must collide with the actual structure floor");
            CompoundTag saved = original.saveWithoutId(new CompoundTag());
            TubeCapEntity restored = ModContent.TUBE_CAP.get().create(helper.getLevel());
            helper.assertTrue(restored != null, "A retained physical cover must restore");
            restored.load(saved);
            restored.setUUID(UUID.randomUUID());
            helper.assertTrue(restored.getLifeTicks() == original.getLifeTicks() && restored.getLifeTicks() >= 80,
                    "Saving a cover must preserve its remaining lifetime rather than restart it");
            original.discard();
            helper.assertTrue(helper.getLevel().addFreshEntity(restored), "The restored cover must enter the server world");
            active[0] = restored;
        });
        helper.runAtTickTime(TubeCapEntity.LIFETIME_TICKS + 5, () -> {
            helper.assertTrue(active[0].isRemoved(), "Launch covers must expire on time even after a save/reload");
            helper.succeed();
        });
    }
}
