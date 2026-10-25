package fr.irux.fk2000.network;

import fr.irux.fk2000.IruxFk2000Mod;
import fr.irux.fk2000.client.RadarMapTexture;
import fr.irux.fk2000.entity.vehicle.Fk2000Entity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Small snapshots of observed terrain around the authorized remote truck; sampling never loads a chunk. */
@Mod.EventBusSubscriber(modid = IruxFk2000Mod.MOD_ID)
public final class RadarMaps {
    public static final int SIZE = 128, PIXELS = SIZE * SIZE, MIN_RADIUS = 32, MAX_RADIUS = 2048;
    public static final int UNKNOWN_COLOR = 0xff08131a;
    private static final Map<UUID, Long> LAST_POINT = new HashMap<>();
    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(IruxFk2000Mod.MOD_ID, "radar_maps"), () -> "2", "2"::equals, "2"::equals);
    private RadarMaps() {}

    public static void register() {
        CHANNEL.registerMessage(0, Request.class, Request::encode, Request::decode, Request::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2, Point.class, Point::encode, Point::decode, Point::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, Snapshot.class, Snapshot::encode, Snapshot::decode, Snapshot::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void selectPoint(int vehicleId, double x, double z) {
        CHANNEL.sendToServer(new Point(vehicleId, x, z));
    }

    private record Point(int vehicleId, double x, double z) {
        private void encode(FriendlyByteBuf b) { b.writeVarInt(vehicleId).writeDouble(x).writeDouble(z); }
        private static Point decode(FriendlyByteBuf b) { return new Point(b.readVarInt(), b.readDouble(), b.readDouble()); }
        private void handle(Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> {
                ServerPlayer player = context.get().getSender();
                if (player == null || !Double.isFinite(x) || !Double.isFinite(z) || Math.abs(x) > 30000000 || Math.abs(z) > 30000000) return;
                Fk2000Entity vehicle = ModNetwork.authorizedVehicle(player);
                if (vehicle == null || vehicle.getId() != vehicleId || vehicle.getTargetMode() != fr.irux.fk2000.radar.TargetMode.COORDINATES) return;
                long now = player.serverLevel().getGameTime();
                Long last = LAST_POINT.get(player.getUUID());
                if (last != null && now >= last && now - last < 5) return;
                LAST_POINT.put(player.getUUID(), now);
                Integer height = TerrainAtlas.get(player.serverLevel()).height((int)Math.floor(x), (int)Math.floor(z));
                if (height == null || !vehicle.setTargetCoordinates(new net.minecraft.world.phys.Vec3(Math.floor(x) + .5, height + 1, Math.floor(z) + .5)))
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable("screen.iruxfk2000.map_point_unknown"), true);
            });
            context.get().setPacketHandled(true);
        }
    }

    public static void request(int vehicleId, int radius) {
        if (vehicleId >= 0 && radius >= MIN_RADIUS && radius <= MAX_RADIUS)
            CHANNEL.sendToServer(new Request(vehicleId, radius));
    }

    private static void respond(ServerPlayer player, int vehicleId, int radius) {
        if (radius < MIN_RADIUS || radius > MAX_RADIUS) return;
        Fk2000Entity vehicle = ModNetwork.authorizedVehicle(player);
        if (vehicle == null || vehicle.getId() != vehicleId) return;
        long tick = player.server.overworld().getGameTime();
        Long previous = LAST_REQUEST.get(player.getUUID());
        if (previous != null && tick - previous >= 0 && tick - previous < 20) return;
        LAST_REQUEST.put(player.getUUID(), tick);
        int[] colors = sample(player.serverLevel(), vehicle.getX(), vehicle.getZ(), radius);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new Snapshot(vehicleId, radius, vehicle.getX(), vehicle.getZ(), colors));
    }

    /** Public for dedicated-server tests: fixed allocation and loaded-column queries only. */
    public static int[] sample(ServerLevel level, double centerX, double centerZ, int radius) {
        if (!Double.isFinite(centerX) || !Double.isFinite(centerZ) || radius < MIN_RADIUS || radius > MAX_RADIUS)
            throw new IllegalArgumentException("Invalid radar map bounds");
        TerrainAtlas atlas = TerrainAtlas.get(level);
        // Capture a detailed neighbourhood once; retain it across zoom and restarts.
        int chunkX = (int)Math.floor(centerX) >> 4, chunkZ = (int)Math.floor(centerZ) >> 4;
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++)
            atlas.observe(level, chunkX + dx, chunkZ + dz);
        int[] colors = new int[PIXELS];
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            int columnX = (int)Math.floor(centerX + ((x + .5) / SIZE * 2 - 1) * radius);
            int columnZ = (int)Math.floor(centerZ + ((y + .5) / SIZE * 2 - 1) * radius);
            int color = atlas.observeColumn(level, columnX, columnZ);
            // At overview scale a pixel spans several chunks. Include observed
            // columns inside that footprint rather than missing the entire base.
            if (color == UNKNOWN_COLOR && radius * 2 > SIZE) {
                int half = Math.max(1, radius / SIZE);
                outer: for (int dz = -half; dz <= half; dz += Math.max(1, half))
                    for (int dx = -half; dx <= half; dx += Math.max(1, half)) {
                        color = atlas.color(columnX + dx, columnZ + dz);
                        if (color != UNKNOWN_COLOR) break outer;
                    }
            }
            colors[y * SIZE + x] = color;
        }
        return colors;
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { LAST_REQUEST.remove(event.getEntity().getUUID()); LAST_POINT.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { LAST_REQUEST.clear(); LAST_POINT.clear(); }

    private record Request(int vehicleId, int radius) {
        private void encode(FriendlyByteBuf buffer) { buffer.writeVarInt(vehicleId).writeVarInt(radius); }
        private static Request decode(FriendlyByteBuf buffer) { return new Request(buffer.readVarInt(), buffer.readVarInt()); }
        private void handle(Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> { if (context.get().getSender() != null) respond(context.get().getSender(), vehicleId, radius); });
            context.get().setPacketHandled(true);
        }
    }

    private record Snapshot(int vehicleId, int radius, double centerX, double centerZ, int[] colors) {
        private void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(vehicleId).writeVarInt(radius).writeDouble(centerX).writeDouble(centerZ);
            buffer.writeVarInt(colors.length);
            for (int color : colors) buffer.writeInt(color);
        }
        private static Snapshot decode(FriendlyByteBuf buffer) {
            int vehicle = buffer.readVarInt(), radius = buffer.readVarInt();
            double x = buffer.readDouble(), z = buffer.readDouble();
            int size = buffer.readVarInt();
            if (size != PIXELS) throw new IllegalArgumentException("Radar terrain snapshot must contain exactly 16384 pixels");
            int[] pixels = new int[size];
            for (int i = 0; i < size; i++) pixels[i] = buffer.readInt();
            return new Snapshot(vehicle, radius, x, z, pixels);
        }
        private void handle(Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> RadarMapTexture.accept(vehicleId, radius, centerX, centerZ, colors)));
            context.get().setPacketHandled(true);
        }
    }
}
