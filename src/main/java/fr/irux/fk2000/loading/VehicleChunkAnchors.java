package fr.irux.fk2000.loading;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Identifies persistent truck tickets; missile tickets are deliberately absent. */
public final class VehicleChunkAnchors extends SavedData {
    private final Map<UUID, Long> anchors = new HashMap<>();

    public static VehicleChunkAnchors get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(VehicleChunkAnchors::load,
                VehicleChunkAnchors::new, "iruxfk2000_chunk_anchors");
    }

    public static VehicleChunkAnchors load(CompoundTag tag) {
        VehicleChunkAnchors data = new VehicleChunkAnchors();
        ListTag entries = tag.getList("Vehicles", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (entry.hasUUID("Vehicle")) data.anchors.put(entry.getUUID("Vehicle"), entry.getLong("Chunk"));
        }
        return data;
    }

    public boolean contains(UUID vehicle) { return anchors.containsKey(vehicle); }
    public Long chunk(UUID vehicle) { return anchors.get(vehicle); }

    public void put(UUID vehicle, ChunkPos chunk) {
        Long previous = anchors.put(vehicle, chunk.toLong());
        if (previous == null || previous.longValue() != chunk.toLong()) setDirty();
    }

    public void remove(UUID vehicle) {
        if (anchors.remove(vehicle) != null) setDirty();
    }

    @Override public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        anchors.forEach((vehicle, chunk) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Vehicle", vehicle);
            entry.putLong("Chunk", chunk);
            entries.add(entry);
        });
        tag.put("Vehicles", entries);
        return tag;
    }
}
