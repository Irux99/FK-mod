package fr.irux.fk2000.gametest;

import fr.irux.fk2000.entity.missile.MissileEntity;
import fr.irux.fk2000.loading.FkChunkLoading;
import fr.irux.fk2000.registry.ModContent;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** A real flight beyond the former lifetime/range, across chunks outside the test arena. */
@GameTestHolder("iruxfk2000")
@PrefixGameTestTemplate(false)
public final class LongRangeGameTests {
    private LongRangeGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 1200, batch = "long_range")
    public static void coordinateMissileCrossesChunksAndReaches650Blocks(GameTestHelper helper) {
        var owner = VehicleGameTests.onlinePlayerFixture(helper);
        Vec3 origin = helper.absoluteVec(new Vec3(16, 0, 16));
        Vec3 start = new Vec3(origin.x, helper.getLevel().getMaxBuildHeight() - 20, origin.z);
        Vec3 destination = start.add(650, 0, 0);
        MissileEntity missile = ModContent.MISSILE.get().create(helper.getLevel());
        helper.assertTrue(missile != null, "The long-range missile must construct");
        missile.moveTo(start.x, start.y, start.z, 0, -90);
        missile.launchAt(owner.getUUID(), UUID.randomUUID(), destination);
        helper.assertTrue(helper.getLevel().addFreshEntity(missile), "The physical long-range flight must enter the server world");
        double[] furthest = {0};
        helper.onEachTick(() -> {
            furthest[0] = Math.max(furthest[0], missile.position().distanceTo(start));
            helper.assertTrue(helper.getTick() < 1190 || missile.isRemoved(),
                    "Long-range flight stalled: age=" + missile.tickCount + ", position=" + missile.position());
            if (!missile.isRemoved()) return;
            helper.assertTrue(missile.getTerminationReason() == MissileEntity.TerminationReason.DETONATED,
                    "The missile must reach its destination across newly loaded chunks: " + missile.getTerminationReason());
            helper.assertTrue(furthest[0] > 640 && missile.position().distanceTo(destination) < 4,
                    "A real missile must travel to the point 650 blocks away: distance=" + furthest[0]);
            helper.assertTrue(missile.tickCount > 240,
                    "The flight must genuinely exceed the previous 240-tick lifetime");
            helper.assertTrue(FkChunkLoading.ticketCount(helper.getLevel(), missile.getUUID()) == 0,
                    "The long-range flight must release every moving corridor ticket on detonation");
            VehicleGameTests.removePlayerFixture(helper, owner);
            helper.succeed();
        });
    }
}
