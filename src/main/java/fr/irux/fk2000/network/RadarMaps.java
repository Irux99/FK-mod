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
    public static final int SIZE = 64, PIXELS = SIZE * SIZE, MIN_RADIUS = 32, MAX_RADIUS = 2048;
    public static final int UNKNOWN_COLOR = 0xff08131a;
    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(IruxFk2000Mod.MOD_ID, "radar_maps"), () -> "1", "1"::equals, "1"::equals);
    private RadarMaps() {}

    public static void register() {
        CHANNEL.registerMessage(0, Request.class, Request::encode, Request::decode, Request::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, Snapshot.class, Snapshot::encode, Snapshot::decode, Snapshot::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
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
        int[] colors = new int[PIXELS];
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            int columnX = (int) Math.floor(centerX + ((x + .5) / SIZE * 2 - 1) * radius);
            int columnZ = (int) Math.floor(centerZ + ((y + .5) / SIZE * 2 - 1) * radius);
            int index = y * SIZE + x;
            colors[index] = UNKNOWN_COLOR;
            // Query resident chunks without completing pending generation futures.
            var chunk = level.getChunkSource().getChunkNow(columnX >> 4, columnZ >> 4);
            if (chunk == null) continue;
            int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, columnX & 15, columnZ & 15);
            if (top < level.getMinBuildHeight()) continue;
            BlockPos surface = new BlockPos(columnX, top, columnZ);
            MapColor material = chunk.getBlockState(surface).getMapColor(level, surface);
            if (material == MapColor.NONE) continue;
            int rgb = material.col;
            // The subdued terrain remains readable behind bright radar contacts.
            int red = (rgb >> 16 & 255) * 62 / 100;
            int green = (rgb >> 8 & 255) * 70 / 100;
            int blue = (rgb & 255) * 62 / 100;
            colors[index] = 0xff000000 | red << 16 | green << 8 | blue;
        }
        return colors;
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { LAST_REQUEST.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { LAST_REQUEST.clear(); }

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
            if (size != PIXELS) throw new IllegalArgumentException("Radar terrain snapshot must contain exactly 4096 pixels");
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
