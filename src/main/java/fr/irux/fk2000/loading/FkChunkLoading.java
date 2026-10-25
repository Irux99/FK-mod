package fr.irux.fk2000.loading;

import fr.irux.fk2000.IruxFk2000Mod;
import fr.irux.fk2000.config.ModConfig;
import fr.irux.fk2000.entity.missile.FlightState;
import fr.irux.fk2000.entity.missile.MissileEntity;
import fr.irux.fk2000.entity.vehicle.Fk2000Entity;
import fr.irux.fk2000.radar.RadarSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Bounded, entity-owned ticking tickets. Truck anchors persist; flight corridors do not. */
@Mod.EventBusSubscriber(modid = IruxFk2000Mod.MOD_ID)
public final class FkChunkLoading {
    private static final Map<ServerLevel, State> LEVELS = new IdentityHashMap<>();
    private static final int RESTORE_GRACE_TICKS = 200;
    private static final int MAX_MISSILE_TICKETS = 32;
    private static final int MAX_VEHICLE_TICKETS = 25 + 32; // Largest configured footprint plus the bounded selected contacts.
    private static final int FIXED_TARGET_LOAD_DISTANCE = 96;

    private FkChunkLoading() {}

    /** Register through FMLCommonSetupEvent.enqueueWork, before worlds are loaded. */
    public static void registerCallback() {
        ForgeChunkManager.setForcedChunkLoadingCallback(IruxFk2000Mod.MOD_ID, FkChunkLoading::validateRestoredTickets);
    }

    private static void validateRestoredTickets(ServerLevel level, ForgeChunkManager.TicketHelper helper) {
        State state = state(level);
        VehicleChunkAnchors anchors = VehicleChunkAnchors.get(level);
        helper.getBlockTickets().keySet().forEach(helper::removeAllTickets);
        int accepted = 0;
        int total = 0;
        for (var entry : helper.getEntityTickets().entrySet()) {
            UUID id = entry.getKey();
            // This mod only creates fully ticking entity tickets. Discard stale non-ticking ones.
            entry.getValue().getFirst().forEach((long chunk) -> helper.removeTicket(id, chunk, false));
            Long anchor = anchors.chunk(id);
            if (!enabled(ModConfig.VEHICLE_CHUNK_LOADING) || anchor == null
                    || accepted >= integer(ModConfig.MAX_CHUNK_LOADED_VEHICLES)) {
                // All missile tickets and unknown owners are removed during world restoration.
                helper.removeAllTickets(id);
                anchors.remove(id);
                continue;
            }
            Set<Long> expected = new LinkedHashSet<>();
            square(expected, new ChunkPos(anchor), integer(ModConfig.VEHICLE_CHUNK_RADIUS), Integer.MAX_VALUE);
            Set<Long> saved = new LinkedHashSet<>();
            for (long chunk : expected) {
                if (entry.getValue().getSecond().contains(chunk) && inBorder(level, new ChunkPos(chunk))
                        && total + saved.size() < integer(ModConfig.MAX_FORCED_CHUNKS)) saved.add(chunk);
            }
            // Radius/budget changes trim excess tickets without discarding the valid center anchor.
            entry.getValue().getSecond().forEach((long chunk) -> {
                if (!saved.contains(chunk)) helper.removeTicket(id, chunk, true);
            });
            if (saved.isEmpty()) {
                anchors.remove(id);
                continue;
            }
            accepted++;
            total += saved.size();
            Track active = state.entities.get(id);
            if (active != null) active.tickets.addAll(saved);
            else state.restoring.put(id, new Restoring(saved, level.getGameTime() + RESTORE_GRACE_TICKS));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void joined(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Entity entity = event.getEntity();
        if (entity instanceof Fk2000Entity || entity instanceof MissileEntity)
            state(level).entities.putIfAbsent(entity.getUUID(), new Track(entity));
    }

    @SubscribeEvent
    public static void left(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Entity entity = event.getEntity();
        if (!(entity instanceof Fk2000Entity) && !(entity instanceof MissileEntity)) return;
        State state = LEVELS.get(level);
        if (state == null) return;
        Entity.RemovalReason reason = entity.getRemovalReason();
        if (entity instanceof Fk2000Entity && reason != null && reason.shouldSave()) {
            // World shutdown/chunk serialization must retain the truck's persistent anchor.
            Track tracked = state.entities.remove(entity.getUUID());
            if (tracked != null && !tracked.tickets.isEmpty()) state.restoring.put(entity.getUUID(),
                    new Restoring(new LinkedHashSet<>(tracked.tickets), level.getGameTime() + RESTORE_GRACE_TICKS));
            return;
        }
        release(level, entity.getUUID(), true);
    }

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !(event.level instanceof ServerLevel level)) return;
        State state = LEVELS.get(level);
        if (state == null || state.stopping) return;
        for (var entry : new ArrayList<>(state.restoring.entrySet())) {
            Entity entity = level.getEntity(entry.getKey());
            if (entity instanceof Fk2000Entity vehicle && vehicle.isAlive() && vehicle.getOwnerUUID() != null) {
                state.entities.putIfAbsent(entity.getUUID(), new Track(entity));
                refreshVehicle(vehicle);
            } else if (level.getGameTime() >= entry.getValue().deadline) {
                release(level, entry.getKey(), true);
            }
        }
        for (Track track : new ArrayList<>(state.entities.values())) {
            Entity entity = track.entity;
            if (entity.isRemoved()) {
                release(level, entity.getUUID(), entity instanceof Fk2000Entity);
            } else if (!entity.isAddedToWorld()) {
                // A later join handler may have canceled the spawn. Such entities never get tickets.
                if (level.getGameTime() - track.observedAt > 2) state.entities.remove(entity.getUUID());
            } else if (entity instanceof Fk2000Entity vehicle) {
                refreshVehicle(vehicle);
            } else if (entity instanceof MissileEntity missile) {
                UUID target = missile.getTrackedTargetUUID();
                refreshMissile(missile, target == null ? null : level.getEntity(target));
            }
        }
    }

    public static void refreshVehicle(Fk2000Entity vehicle) {
        if (!(vehicle.level() instanceof ServerLevel level) || !vehicle.isAddedToWorld()) return;
        State state = state(level);
        if (state.stopping) return;
        if (!enabled(ModConfig.VEHICLE_CHUNK_LOADING) || !vehicle.isAlive() || vehicle.getOwnerUUID() == null) {
            release(level, vehicle.getUUID(), true);
            return;
        }
        Track track = state.entities.computeIfAbsent(vehicle.getUUID(), ignored -> new Track(vehicle));
        adoptRestored(state, track);
        if (track.tickets.isEmpty() && activeCount(state, Fk2000Entity.class) >= integer(ModConfig.MAX_CHUNK_LOADED_VEHICLES)) return;
        Set<Long> desired = new LinkedHashSet<>();
        square(desired, vehicle.chunkPosition(), integer(ModConfig.VEHICLE_CHUNK_RADIUS), Integer.MAX_VALUE);
        // Preserve already selected contacts while the operator leaves the area
        // or the launcher prepares. Unknown radar space is never loaded to search for targets.
        for (int selected : vehicle.getSelectedTargetIds()) {
            Entity target = level.getEntity(selected);
            if (target != null && RadarSystem.canTrack(vehicle, target)
                    && validPosition(level, target.position())) add(desired, target.chunkPosition(), MAX_VEHICLE_TICKETS);
        }
        updateTickets(level, state, track, desired);
        if (!track.tickets.isEmpty()) VehicleChunkAnchors.get(level).put(vehicle.getUUID(), vehicle.chunkPosition());
    }

    public static void refreshMissile(MissileEntity missile, @Nullable Entity target) {
        if (!(missile.level() instanceof ServerLevel level) || !missile.isAddedToWorld()) return;
        State state = state(level);
        if (state.stopping) return;
        if (!enabled(ModConfig.MISSILE_CHUNK_LOADING) || missile.isRemoved()
                || missile.getFlightState() == FlightState.IDLE || missile.getFlightState() == FlightState.DETONATED) {
            release(level, missile.getUUID(), false);
            return;
        }
        Track track = state.entities.computeIfAbsent(missile.getUUID(), ignored -> new Track(missile));
        if (track.tickets.isEmpty() && activeCount(state, MissileEntity.class) >= integer(ModConfig.MAX_CHUNK_LOADED_MISSILES)) return;
        if (target != null && target.isAlive()) {
            track.lastTarget = target.position();
            track.targetVelocity = target.getDeltaMovement();
            track.liveTarget = true;
        } else {
            Vec3 known = missile.getLastKnownTargetPosition();
            if (known != null) track.lastTarget = known;
        }
        Set<Long> desired = new LinkedHashSet<>();
        square(desired, missile.chunkPosition(), 1, MAX_MISSILE_TICKETS);
        if (track.lastTarget != null && validPosition(level, track.lastTarget)
                && (track.liveTarget || track.lastTarget.distanceToSqr(missile.position()) <= FIXED_TARGET_LOAD_DISTANCE * FIXED_TARGET_LOAD_DISTANCE)) {
            // A known entity may leave the original player-loaded area. Keep its current and next
            // chunk ticking, but never generate a distant coordinate target's area at launch.
            add(desired, new ChunkPos(BlockPos.containing(track.lastTarget)), MAX_MISSILE_TICKETS);
            add(desired, new ChunkPos(BlockPos.containing(track.lastTarget.add(track.targetVelocity.scale(12)))), MAX_MISSILE_TICKETS);
        }
        Vec3 motion = missile.getDeltaMovement();
        Vec3 direction = motion.horizontalDistanceSqr() > 1.0E-6 ? motion.normalize() : missile.getLookAngle();
        for (int i = 1; i <= integer(ModConfig.MISSILE_CHUNK_LOOKAHEAD); i++) {
            Vec3 ahead = missile.position().add(direction.scale(i * 16.0));
            if (validPosition(level, ahead)) square(desired, new ChunkPos(BlockPos.containing(ahead)), 1, MAX_MISSILE_TICKETS);
        }
        updateTickets(level, state, track, desired);
    }

    private static void adoptRestored(State state, Track track) {
        Restoring restored = state.restoring.remove(track.entity.getUUID());
        if (restored != null) track.tickets.addAll(restored.tickets);
    }

    private static void updateTickets(ServerLevel level, State state, Track track, Set<Long> desired) {
        desired.removeIf(chunk -> !inBorder(level, new ChunkPos(chunk)));
        for (long chunk : new HashSet<>(track.tickets)) {
            if (!desired.contains(chunk)) {
                ChunkPos pos = new ChunkPos(chunk);
                ForgeChunkManager.forceChunk(level, IruxFk2000Mod.MOD_ID, track.entity.getUUID(), pos.x, pos.z, false, true);
                track.tickets.remove(chunk);
            }
        }
        int total = ticketCount(state);
        for (long chunk : desired) {
            if (track.tickets.contains(chunk)) continue;
            if (total >= integer(ModConfig.MAX_FORCED_CHUNKS)) break;
            ChunkPos pos = new ChunkPos(chunk);
            ForgeChunkManager.forceChunk(level, IruxFk2000Mod.MOD_ID, track.entity.getUUID(), pos.x, pos.z, true, true);
            track.tickets.add(chunk);
            total++;
        }
    }

    public static void release(ServerLevel level, UUID owner, boolean removeAnchor) {
        State state = LEVELS.get(level);
        Set<Long> tickets = new LinkedHashSet<>();
        if (state != null) {
            Track track = state.entities.remove(owner);
            if (track != null) tickets.addAll(track.tickets);
            Restoring restoring = state.restoring.remove(owner);
            if (restoring != null) tickets.addAll(restoring.tickets);
        }
        for (long chunk : tickets) {
            ChunkPos pos = new ChunkPos(chunk);
            ForgeChunkManager.forceChunk(level, IruxFk2000Mod.MOD_ID, owner, pos.x, pos.z, false, true);
            ForgeChunkManager.forceChunk(level, IruxFk2000Mod.MOD_ID, owner, pos.x, pos.z, false, false);
        }
        if (removeAnchor) VehicleChunkAnchors.get(level).remove(owner);
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        for (var entry : LEVELS.entrySet()) {
            State state = entry.getValue();
            state.stopping = true;
            for (Track track : new ArrayList<>(state.entities.values())) {
                if (track.entity instanceof MissileEntity) {
                    release(entry.getKey(), track.entity.getUUID(), false);
                    // Flight corridors are transient: do not leave a saved missile to resume
                    // unexpectedly when somebody revisits that region after a restart.
                    track.entity.discard();
                }
            }
        }
    }

    @SubscribeEvent
    public static void unloaded(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) LEVELS.remove(level);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) { LEVELS.clear(); }

    /** Exposes the bounded active ticket count for server diagnostics and integration tests. */
    public static int ticketCount(ServerLevel level, UUID owner) {
        State state = LEVELS.get(level);
        if (state == null) return 0;
        Track track = state.entities.get(owner);
        Restoring restoring = state.restoring.get(owner);
        return (track == null ? 0 : track.tickets.size()) + (restoring == null ? 0 : restoring.tickets.size());
    }

    public static boolean ownsTicket(ServerLevel level, UUID owner, ChunkPos chunk) {
        State state = LEVELS.get(level);
        if (state == null) return false;
        Track track = state.entities.get(owner);
        Restoring restoring = state.restoring.get(owner);
        return track != null && track.tickets.contains(chunk.toLong())
                || restoring != null && restoring.tickets.contains(chunk.toLong());
    }

    private static int activeCount(State state, Class<?> type) {
        int count = 0;
        for (Track track : state.entities.values()) if (type.isInstance(track.entity) && !track.tickets.isEmpty()) count++;
        return count;
    }

    private static int ticketCount(State state) {
        int count = 0;
        for (Track track : state.entities.values()) count += track.tickets.size();
        for (Restoring restoring : state.restoring.values()) count += restoring.tickets.size();
        return count;
    }

    private static State state(ServerLevel level) { return LEVELS.computeIfAbsent(level, ignored -> new State()); }
    private static boolean enabled(ForgeConfigSpec.BooleanValue value) { return ModConfig.SPEC.isLoaded() ? value.get() : value.getDefault(); }
    private static int integer(ForgeConfigSpec.IntValue value) { return ModConfig.SPEC.isLoaded() ? value.get() : value.getDefault(); }
    private static boolean validPosition(ServerLevel level, Vec3 pos) {
        return Double.isFinite(pos.x) && Double.isFinite(pos.y) && Double.isFinite(pos.z)
                && level.getWorldBorder().isWithinBounds(BlockPos.containing(pos));
    }
    private static boolean inBorder(ServerLevel level, ChunkPos chunk) {
        return level.getWorldBorder().isWithinBounds(new BlockPos(chunk.getMinBlockX() + 8, 0, chunk.getMinBlockZ() + 8));
    }
    private static void square(Set<Long> result, ChunkPos center, int radius, int limit) {
        // Keep the center first so budget pressure never chooses a distant ticket over the entity itself.
        add(result, center, limit);
        for (int distance = 1; distance <= radius; distance++)
            for (int dx = -distance; dx <= distance; dx++) for (int dz = -distance; dz <= distance; dz++)
                if (Math.max(Math.abs(dx), Math.abs(dz)) == distance) add(result, new ChunkPos(center.x + dx, center.z + dz), limit);
    }
    private static void add(Set<Long> result, ChunkPos chunk, int limit) { if (result.size() < limit) result.add(chunk.toLong()); }

    private static final class State {
        final Map<UUID, Track> entities = new LinkedHashMap<>();
        final Map<UUID, Restoring> restoring = new HashMap<>();
        boolean stopping;
    }
    private static final class Track {
        final Entity entity;
        final Set<Long> tickets = new LinkedHashSet<>();
        final long observedAt;
        Vec3 lastTarget;
        Vec3 targetVelocity = Vec3.ZERO;
        boolean liveTarget;
        Track(Entity entity) { this.entity = entity; this.observedAt = entity.level().getGameTime(); }
    }
    private record Restoring(Set<Long> tickets, long deadline) {}
}
