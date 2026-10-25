package fr.irux.fk2000.gametest;

import fr.irux.fk2000.network.RadarMaps;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("iruxfk2000")
@PrefixGameTestTemplate(false)
public final class RadarMapGameTests {
    private RadarMapGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 30, batch = "radar_map")
    public static void terrainMapReadsRealBlockColorsWithoutLoadingFarChunks(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(16, 2, 16));
        // The underground test arena has a natural roof. These are real topmost
        // terrain blocks, ensuring the snapshot exercises the heightmap and MapColor.
        BlockPos red = new BlockPos(center.getX(), level.getMaxBuildHeight() - 1, center.getZ());
        BlockPos blue = red.east();
        var oldRed = level.getBlockState(red); var oldBlue = level.getBlockState(blue);
        try {
            level.setBlockAndUpdate(red, Blocks.RED_WOOL.defaultBlockState());
            level.setBlockAndUpdate(blue, Blocks.BLUE_WOOL.defaultBlockState());
            int[] local = RadarMaps.sample(level, center.getX(), center.getZ(), 32);
            int redPixel = local[32 * 64 + 32], bluePixel = local[32 * 64 + 33];
            helper.assertTrue((redPixel >> 16 & 255) > (redPixel & 255), "A red terrain block must produce a red-dominant map pixel");
            helper.assertTrue((bluePixel & 255) > (bluePixel >> 16 & 255), "A blue terrain block must produce a blue-dominant map pixel");
            int before = level.getChunkSource().getLoadedChunksCount();
            int[] distant = RadarMaps.sample(level, center.getX(), center.getZ(), 2048);
            helper.assertTrue(distant.length == 4096, "A distant radar map must remain a bounded 64 by 64 snapshot");
            helper.assertTrue(level.getChunkSource().getLoadedChunksCount() == before,
                    "Sampling a 4096-block-wide map must not load any distant terrain chunks: before="
                            + before + ", after=" + level.getChunkSource().getLoadedChunksCount());
            helper.succeed();
        } finally {
            level.setBlockAndUpdate(red, oldRed); level.setBlockAndUpdate(blue, oldBlue);
        }
    }
}
