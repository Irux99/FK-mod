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
            int redPixel = local[64 * RadarMaps.SIZE + 64], bluePixel = local[64 * RadarMaps.SIZE + 66];
            helper.assertTrue((redPixel >> 16 & 255) > (redPixel & 255), "A red terrain block must produce a red-dominant map pixel");
            helper.assertTrue((bluePixel & 255) > (bluePixel >> 16 & 255), "A blue terrain block must produce a blue-dominant map pixel");
            int before = level.getChunkSource().getLoadedChunksCount();
            int[] distant = RadarMaps.sample(level, center.getX(), center.getZ(), 2048);
            helper.assertTrue(distant.length == RadarMaps.PIXELS, "A distant radar map must remain a bounded 128 by 128 snapshot");
            helper.assertTrue(level.getChunkSource().getLoadedChunksCount() == before,
                    "Sampling a 4096-block-wide map must not load any distant terrain chunks: before="
                            + before + ", after=" + level.getChunkSource().getLoadedChunksCount());
            var atlas = fr.irux.fk2000.network.TerrainAtlas.get(level);
            var restored = fr.irux.fk2000.network.TerrainAtlas.load(atlas.save(new net.minecraft.nbt.CompoundTag()));
            helper.assertTrue(restored.color(red.getX(), red.getZ()) == atlas.color(red.getX(), red.getZ()), "Observed terrain must survive persistence");
            helper.assertTrue(restored.height(red.getX(), red.getZ()) == red.getY(), "Map targeting must retain the real surface height");
            helper.assertTrue(java.util.Arrays.stream(distant).anyMatch(c -> c != RadarMaps.UNKNOWN_COLOR), "Maximum zoom-out must retain observed terrain");
            helper.succeed();
        } finally {
            level.setBlockAndUpdate(red, oldRed); level.setBlockAndUpdate(blue, oldBlue);
        }
    }
}
