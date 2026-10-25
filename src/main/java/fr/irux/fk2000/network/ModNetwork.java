package fr.irux.fk2000.network;

import fr.irux.fk2000.IruxFk2000Mod;
import fr.irux.fk2000.client.ClientHooks;
import fr.irux.fk2000.config.ModConfig;
import fr.irux.fk2000.entity.vehicle.Fk2000Entity;
import fr.irux.fk2000.item.RemoteControllerItem;
import fr.irux.fk2000.radar.RadarContact;
import fr.irux.fk2000.radar.TargetMode;
import fr.irux.fk2000.radar.TargetCoordinates;
import fr.irux.fk2000.entity.vehicle.TubeCapMode;
import fr.irux.fk2000.entity.vehicle.AutoFireMode;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = IruxFk2000Mod.MOD_ID)
public final class ModNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(IruxFk2000Mod.MOD_ID, "control"), () -> "3", "3"::equals, "3"::equals);
    private static final Map<UUID, UUID> SESSIONS = new HashMap<>();
    private static final Map<UUID, Rate> RATES = new HashMap<>();
    public enum Command { DRIVE, ENGINE, RADAR, AUTO_ACQUIRE, AUTO_FIRE, SELECT_TARGET, FIRE, RELOAD, CYCLE_CAP_MODE, CYCLE_FIRE_MODE, CLOSE }
    public static void register() {
        CHANNEL.registerMessage(0, ControlPacket.class, ControlPacket::encode, ControlPacket::decode,
            ControlPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, StatusPacket.class, StatusPacket::encode, StatusPacket::decode,
            StatusPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(2, OpenPacket.class, OpenPacket::encode, OpenPacket::decode,
            OpenPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(3, TargetModePacket.class, TargetModePacket::encode, TargetModePacket::decode,
            TargetModePacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(4, TargetCoordinatesPacket.class, TargetCoordinatesPacket::encode, TargetCoordinatesPacket::decode,
            TargetCoordinatesPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(5, EngagementRangePacket.class, EngagementRangePacket::encode, EngagementRangePacket::decode,
            EngagementRangePacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
    public static void sendCommand(Command action, float throttle, float steering, boolean brake, int targetId) {
        CHANNEL.sendToServer(new ControlPacket(action, throttle, steering, brake, targetId));
    }
    public static void sendTargetMode(TargetMode mode) { CHANNEL.sendToServer(new TargetModePacket(mode)); }
    public static void sendEngagementRange(double range) { CHANNEL.sendToServer(new EngagementRangePacket(range)); }
    public static void sendTargetCoordinates(double x, double y, double z) {
        CHANNEL.sendToServer(new TargetCoordinatesPacket(new Vec3(x, y, z)));
    }
    public static boolean openRemote(ServerPlayer player, ItemStack stack) {
        UUID uuid = RemoteControllerItem.boundVehicle(stack);
        if (uuid == null || !(player.serverLevel().getEntity(uuid) instanceof Fk2000Entity vehicle)
            || !usable(player, vehicle) || !RemoteControllerItem.boundTo(stack, vehicle)) return false;
        if (player.isPassenger() && player.getVehicle() != vehicle) return false;
        close(player);
        SESSIONS.put(player.getUUID(), uuid);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenPacket(vehicle.getId()));
        sendStatus(player, vehicle);
        return true;
    }
    private static boolean usable(ServerPlayer player, Fk2000Entity vehicle) {
        return player.isAlive() && !player.isSpectator() && vehicle.isAlive() && vehicle.level() == player.level()
            && vehicle.canControl(player) && (ModConfig.UNLIMITED_REMOTE_RANGE.get()
                || player.distanceToSqr(vehicle) <= Math.pow(ModConfig.REMOTE_RANGE.get(), 2));
    }
    private static Fk2000Entity controlled(ServerPlayer player) {
        if (player.getVehicle() instanceof Fk2000Entity mounted && usable(player, mounted)) return mounted;
        UUID id = SESSIONS.get(player.getUUID());
        if (id == null || !(player.serverLevel().getEntity(id) instanceof Fk2000Entity vehicle) || !usable(player, vehicle)) return null;
        return RemoteControllerItem.boundTo(player.getMainHandItem(), vehicle)
            || RemoteControllerItem.boundTo(player.getOffhandItem(), vehicle) ? vehicle : null;
    }
    /** Shared authorization for auxiliary radar snapshots; no entity ID supplied by the client grants control. */
    public static Fk2000Entity authorizedVehicle(ServerPlayer player) { return controlled(player); }
    private static void close(ServerPlayer player) {
        UUID id = SESSIONS.remove(player.getUUID());
        if (id != null && player.serverLevel().getEntity(id) instanceof Fk2000Entity vehicle && vehicle.canControl(player)) vehicle.clearDriveInput();
    }
    private static boolean allowCommand(ServerPlayer player) {
        Rate rate = RATES.computeIfAbsent(player.getUUID(), id -> new Rate());
        int tick = player.tickCount;
        if (rate.tick != tick) { rate.tick = tick; rate.count = 0; }
        return ++rate.count <= 8;
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.tickCount % 5 != 0) return;
        Fk2000Entity vehicle = controlled(player);
        if (vehicle != null) sendStatus(player, vehicle);
        else if (SESSIONS.containsKey(player.getUUID())) {
            close(player);
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenPacket(-1));
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { close(player); RATES.remove(player.getUUID()); }
    }
    @SubscribeEvent public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) close(player);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SESSIONS.clear(); RATES.clear(); }
    private static void sendStatus(ServerPlayer player, Fk2000Entity v) {
        List<RadarContact> contacts = v.getRadarContacts().stream().limit(32).toList();
        VehicleStatus status = new VehicleStatus(v.getId(), v.isEngineOn(), v.isRadarEnabled(), v.isAutoAcquire(), v.isAutoFire(),
            v.getMissileCount(), v.getHealth(), v.getMaxHealth(), v.getSpeed(), v.getLauncherProgress(), v.getLockedTargetId(),
            v.getX(), v.getY(), v.getZ(), v.getEngagementRange(), v.getTargetMode(), v.getTargetCoordinates(), v.getTubeCapMode(), v.getAutoFireMode(), v.getSelectedTargetIds().stream().limit(32).toList(), contacts);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new StatusPacket(status));
    }
    private record ControlPacket(Command action, float throttle, float steering, boolean brake, int targetId) {
        static void encode(ControlPacket p, FriendlyByteBuf b) { b.writeEnum(p.action); b.writeFloat(p.throttle); b.writeFloat(p.steering); b.writeBoolean(p.brake); b.writeVarInt(p.targetId); }
        static ControlPacket decode(FriendlyByteBuf b) { return new ControlPacket(b.readEnum(Command.class), b.readFloat(), b.readFloat(), b.readBoolean(), b.readVarInt()); }
        static void handle(ControlPacket p, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !allowCommand(player)) return;
                if (p.action == Command.CLOSE) { close(player); return; }
                Fk2000Entity vehicle = controlled(player);
                if (vehicle == null) return;
                switch (p.action) {
                    case DRIVE -> { if (Float.isFinite(p.throttle) && Float.isFinite(p.steering))
                        vehicle.setDriveInput(Math.max(-1, Math.min(1, p.throttle)), Math.max(-1, Math.min(1, p.steering)), p.brake); }
                    case ENGINE -> vehicle.setEngineOn(!vehicle.isEngineOn());
                    case RADAR -> vehicle.setRadarEnabled(!vehicle.isRadarEnabled());
                    case AUTO_ACQUIRE -> vehicle.setAutoAcquire(!vehicle.isAutoAcquire());
                    case AUTO_FIRE -> vehicle.setAutoFire(!vehicle.isAutoFire());
                    case SELECT_TARGET -> vehicle.selectTarget(p.targetId);
                    case FIRE -> vehicle.requestLaunch();
                    case RELOAD -> vehicle.reload(player);
                    case CYCLE_CAP_MODE -> vehicle.setTubeCapMode(vehicle.getTubeCapMode().next());
                    case CYCLE_FIRE_MODE -> vehicle.setAutoFireMode(vehicle.getAutoFireMode().next());
                    default -> { }
                }
            });
            context.setPacketHandled(true);
        }
    }
    private record TargetModePacket(TargetMode mode) {
        static void encode(TargetModePacket packet, FriendlyByteBuf buffer) { buffer.writeEnum(packet.mode); }
        static TargetModePacket decode(FriendlyByteBuf buffer) { return new TargetModePacket(buffer.readEnum(TargetMode.class)); }
        static void handle(TargetModePacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !allowCommand(player)) return;
                Fk2000Entity vehicle = controlled(player);
                if (vehicle != null) vehicle.setTargetMode(packet.mode);
            });
            context.setPacketHandled(true);
        }
    }
    private record TargetCoordinatesPacket(Vec3 coordinates) {
        static void encode(TargetCoordinatesPacket packet, FriendlyByteBuf buffer) {
            buffer.writeDouble(packet.coordinates.x); buffer.writeDouble(packet.coordinates.y); buffer.writeDouble(packet.coordinates.z);
        }
        static TargetCoordinatesPacket decode(FriendlyByteBuf buffer) {
            return new TargetCoordinatesPacket(new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
        }
        static void handle(TargetCoordinatesPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !allowCommand(player)) return;
                Fk2000Entity vehicle = controlled(player);
                if (vehicle != null && TargetCoordinates.isValid(vehicle, packet.coordinates))
                    vehicle.setTargetCoordinates(packet.coordinates);
            });
            context.setPacketHandled(true);
        }
    }
    private record EngagementRangePacket(double range) {
        static void encode(EngagementRangePacket packet, FriendlyByteBuf buffer) { buffer.writeDouble(packet.range); }
        static EngagementRangePacket decode(FriendlyByteBuf buffer) { return new EngagementRangePacket(buffer.readDouble()); }
        static void handle(EngagementRangePacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !Double.isFinite(packet.range) || !allowCommand(player)) return;
                Fk2000Entity vehicle = controlled(player);
                if (vehicle != null) vehicle.setEngagementRange(packet.range);
            });
            context.setPacketHandled(true);
        }
    }
    private record OpenPacket(int entityId) {
        static void encode(OpenPacket p, FriendlyByteBuf b) { b.writeVarInt(p.entityId); }
        static OpenPacket decode(FriendlyByteBuf b) { return new OpenPacket(b.readVarInt()); }
        static void handle(OpenPacket p, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context c = supplier.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openRemote(p.entityId)));
            c.setPacketHandled(true);
        }
    }
    private record StatusPacket(VehicleStatus status) {
        static void encode(StatusPacket p, FriendlyByteBuf b) {
            VehicleStatus s = p.status;
            b.writeVarInt(s.vehicleId()); b.writeBoolean(s.engine()); b.writeBoolean(s.radar());
            b.writeBoolean(s.autoAcquire()); b.writeBoolean(s.autoFire()); b.writeVarInt(s.missiles());
            b.writeFloat(s.health()); b.writeFloat(s.maxHealth());
            b.writeFloat(s.speed()); b.writeFloat(s.launcher()); b.writeVarInt(s.targetId());
            b.writeDouble(s.x()); b.writeDouble(s.y()); b.writeDouble(s.z()); b.writeDouble(s.radarRange());
            b.writeEnum(s.targetMode()); b.writeBoolean(s.targetCoordinates() != null);
            if (s.targetCoordinates() != null) {
                b.writeDouble(s.targetCoordinates().x); b.writeDouble(s.targetCoordinates().y); b.writeDouble(s.targetCoordinates().z);
            }
            b.writeEnum(s.tubeCapMode()); b.writeEnum(s.autoFireMode());
            b.writeVarInt(s.selectedTargetIds().size());
            for (int id : s.selectedTargetIds()) b.writeVarInt(id);
            b.writeVarInt(s.contacts().size());
            for (RadarContact contact : s.contacts()) {
                b.writeVarInt(contact.entityId()); b.writeUUID(contact.uuid());
                String name = contact.name(); b.writeUtf(name.substring(0, Math.min(name.length(), 128)), 128);
                b.writeDouble(contact.x()); b.writeDouble(contact.y()); b.writeDouble(contact.z()); b.writeDouble(contact.distance());
            }
        }
        static StatusPacket decode(FriendlyByteBuf b) {
            int id = b.readVarInt(); boolean engine = b.readBoolean(), radar = b.readBoolean(), acquire = b.readBoolean(), fire = b.readBoolean();
            int missiles = b.readVarInt(); float health = b.readFloat(), maxHealth = b.readFloat();
            float speed = b.readFloat(), launcher = b.readFloat(); int target = b.readVarInt();
            double x = b.readDouble(), y = b.readDouble(), z = b.readDouble(), range = b.readDouble();
            TargetMode mode = b.readEnum(TargetMode.class);
            Vec3 coordinates = b.readBoolean() ? new Vec3(b.readDouble(), b.readDouble(), b.readDouble()) : null;
            TubeCapMode capMode = b.readEnum(TubeCapMode.class);
            AutoFireMode fireMode = b.readEnum(AutoFireMode.class);
            int selectedCount = b.readVarInt();
            if (selectedCount < 0 || selectedCount > 32) throw new IllegalArgumentException("Invalid selected target count");
            List<Integer> selected = new ArrayList<>(selectedCount);
            for (int i = 0; i < selectedCount; i++) selected.add(b.readVarInt());
            int size = b.readVarInt(); if (size < 0 || size > 32) throw new IllegalArgumentException("Invalid radar contact count");
            List<RadarContact> contacts = new ArrayList<>(size);
            for (int i = 0; i < size; i++) contacts.add(new RadarContact(b.readVarInt(), b.readUUID(), b.readUtf(128), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble()));
            return new StatusPacket(new VehicleStatus(id, engine, radar, acquire, fire, missiles, health, maxHealth, speed, launcher, target, x, y, z, range, mode, coordinates, capMode, fireMode, selected, contacts));
        }
        static void handle(StatusPacket p, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context c = supplier.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.acceptStatus(p.status)));
            c.setPacketHandled(true);
        }
    }
    private static final class Rate { int tick = -1; int count; }
    private ModNetwork() {}
}
