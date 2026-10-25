package fr.irux.fk2000.network;

import fr.irux.fk2000.IruxFk2000Mod;
import fr.irux.fk2000.client.ClientExplosionEffects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

/** A visual event only: the authoritative entity applies its own damage once. */
public final class ExplosionEffects {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(IruxFk2000Mod.MOD_ID, "explosion_effects"), () -> "1", "1"::equals, "1"::equals);
    private ExplosionEffects() {}

    public static void register() {
        CHANNEL.registerMessage(0, Blast.class, Blast::encode, Blast::decode, Blast::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void send(ServerLevel level, Vec3 origin, float power) {
        if (!Double.isFinite(origin.x) || !Double.isFinite(origin.y) || !Double.isFinite(origin.z)
                || !Float.isFinite(power)) return;
        CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                origin.x, origin.y, origin.z, 192, level.dimension())), new Blast(origin.x, origin.y, origin.z, power));
    }

    private record Blast(double x, double y, double z, float power) {
        private void encode(FriendlyByteBuf buffer) { buffer.writeDouble(x).writeDouble(y).writeDouble(z).writeFloat(power); }
        private static Blast decode(FriendlyByteBuf buffer) {
            return new Blast(buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readFloat());
        }
        private void handle(Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientExplosionEffects.accept(x, y, z, power)));
            context.get().setPacketHandled(true);
        }
    }
}
