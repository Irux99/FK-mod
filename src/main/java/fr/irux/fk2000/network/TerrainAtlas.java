package fr.irux.fk2000.network;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import java.util.LinkedHashMap;

/** Bounded per-dimension atlas of observed block columns; never requests terrain. */
public final class TerrainAtlas extends SavedData {
    private static final int LIMIT = 2048;
    private record Tile(int[] colors, int[] heights) {}
    private final LinkedHashMap<Long, Tile> tiles = new LinkedHashMap<>();
    public static TerrainAtlas get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TerrainAtlas::load, TerrainAtlas::new, "iruxfk2000_terrain");
    }
    private static int index(int x, int z) { return (z & 15) * 16 + (x & 15); }
    public int color(int x, int z) {
        Tile tile = tiles.get(ChunkPos.asLong(x >> 4, z >> 4));
        int c = tile == null ? 0 : tile.colors[index(x,z)];
        return c == 0 ? RadarMaps.UNKNOWN_COLOR : c;
    }
    public Integer height(int x, int z) {
        Tile tile = tiles.get(ChunkPos.asLong(x >> 4, z >> 4));
        return tile == null || tile.colors[index(x,z)] == 0 ? null : tile.heights[index(x,z)];
    }
    public void observe(ServerLevel level, int cx, int cz) {
        if (level.getChunkSource().getChunkNow(cx,cz) == null) return;
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) observeColumn(level, (cx << 4) + x, (cz << 4) + z);
    }
    public int observeColumn(ServerLevel level, int x, int z) {
        var chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        if (chunk == null) return color(x,z);
        int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
        if (y < level.getMinBuildHeight()) return color(x,z);
        BlockPos pos = new BlockPos(x,y,z);
        var state = chunk.getBlockState(pos);
        MapColor material = state.getMapColor(level, pos);
        if (material == MapColor.NONE) return color(x,z);
        int north = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, Math.max(0,(z & 15)-1));
        double shade = Math.max(.55, Math.min(1.05, .86 + (y - north) * .055));
        if (!state.getFluidState().isEmpty()) {
            int depth = 0;
            while (depth < 12 && !chunk.getBlockState(pos.below(depth)).getFluidState().isEmpty()) depth++;
            shade *= 1 - depth * .022;
        }
        int rgb = material.col;
        var biome = level.getBiome(pos).value();
        if (state.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)) rgb = biome.getGrassColor(x,z);
        else if (state.is(net.minecraft.tags.BlockTags.LEAVES)) rgb = biome.getFoliageColor();
        else if (state.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) rgb = biome.getWaterColor();
        int c = 0xff000000 | Math.min(255,(int)((rgb >> 16 & 255)*shade)) << 16
                | Math.min(255,(int)((rgb >> 8 & 255)*shade)) << 8 | Math.min(255,(int)((rgb & 255)*shade));
        long key = ChunkPos.asLong(x >> 4,z >> 4);
        Tile tile = tiles.get(key);
        if (tile == null) {
            if (tiles.size() >= LIMIT) tiles.remove(tiles.keySet().iterator().next());
            tile = new Tile(new int[256],new int[256]); tiles.put(key,tile);
        }
        int i = index(x,z);
        if (tile.colors[i] != c || tile.heights[i] != y) { tile.colors[i] = c; tile.heights[i] = y; setDirty(); }
        return c;
    }
    public static TerrainAtlas load(CompoundTag root) {
        TerrainAtlas atlas = new TerrainAtlas(); ListTag list = root.getList("Tiles", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(LIMIT,list.size()); i++) {
            CompoundTag t = list.getCompound(i); int[] c = t.getIntArray("Colors"), h = t.getIntArray("Heights");
            if (c.length == 256 && h.length == 256) atlas.tiles.put(t.getLong("Chunk"),new Tile(c,h));
        }
        return atlas;
    }
    @Override public CompoundTag save(CompoundTag root) {
        ListTag list = new ListTag(); tiles.forEach((key,tile) -> {
            CompoundTag t = new CompoundTag(); t.putLong("Chunk",key); t.putIntArray("Colors",tile.colors); t.putIntArray("Heights",tile.heights); list.add(t);
        }); root.put("Tiles",list); return root;
    }
}
