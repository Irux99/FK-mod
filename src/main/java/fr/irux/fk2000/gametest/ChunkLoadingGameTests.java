package fr.irux.fk2000.gametest;

import fr.irux.fk2000.IruxFk2000Mod;
import fr.irux.fk2000.entity.missile.MissileEntity;
import fr.irux.fk2000.entity.vehicle.Fk2000Entity;
import fr.irux.fk2000.loading.FkChunkLoading;
import fr.irux.fk2000.loading.VehicleChunkAnchors;
import fr.irux.fk2000.network.ModNetwork;
import fr.irux.fk2000.registry.ModContent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Exercises real Forge-owned ticking tickets, saved NBT, validation and server authorization. */
@GameTestHolder("iruxfk2000")
@PrefixGameTestTemplate(false)
public final class ChunkLoadingGameTests {
    private ChunkLoadingGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 60, batch = "chunk_loading")
    public static void truckAnchorRestoresAndRejectsOrphanTickets(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Fk2000Entity truck = helper.spawn(ModContent.VEHICLE.get(), new Vec3(16, 2, 16));
        truck.setOwnerUUID(UUID.randomUUID());
        FkChunkLoading.refreshVehicle(truck);
        helper.assertTrue(FkChunkLoading.ticketCount(level, truck.getUUID()) == 9,
                "The truck must own the configured 3x3 fully ticking chunk footprint");
        VehicleChunkAnchors anchors = VehicleChunkAnchors.get(level);
        CompoundTag savedAnchors = anchors.save(new CompoundTag());
        VehicleChunkAnchors restored = VehicleChunkAnchors.load(savedAnchors);
        helper.assertTrue(restored.contains(truck.getUUID())
                        && restored.chunk(truck.getUUID()) == truck.chunkPosition().toLong(),
                "The owned truck's current chunk anchor must survive SavedData serialization");
        ForcedChunksSavedData forced = forced(level);
        helper.assertTrue(savedTicketCount(forced, truck.getUUID()) == 9,
                "Forge's persisted data must contain fully ticking tickets for this exact truck UUID");
        UUID orphan = UUID.randomUUID();
        CompoundTag persisted = forced.save(new CompoundTag());
        ListTag modEntries = persisted.getList("ForgeForced", Tag.TAG_COMPOUND);
        for (int i = 0; i < modEntries.size(); i++) {
            CompoundTag mod = modEntries.getCompound(i);
            if (!IruxFk2000Mod.MOD_ID.equals(mod.getString("Mod"))) continue;
            CompoundTag chunk = mod.getList("ModForced", Tag.TAG_COMPOUND).getCompound(0);
            ListTag owners = chunk.getList("TickingEntities", Tag.TAG_INT_ARRAY);
            owners.add(NbtUtils.createUUID(orphan));
            chunk.put("TickingEntities", owners);
        }
        // A separate deserialized Forge SavedData fixture simulates world restoration.
        // It does not create an orphan runtime region ticket in the already-running server.
        ForcedChunksSavedData reloaded = ForcedChunksSavedData.load(persisted);
        helper.assertTrue(savedTicketCount(reloaded, orphan) == 1,
                "The deserialized orphan fixture must exist before validation");
        ForgeChunkManager.reinstatePersistentChunks(level, reloaded);
        helper.assertTrue(savedTicketCount(reloaded, orphan) == 0,
                "World-load validation must remove unregistered/orphan flight tickets");
        helper.assertTrue(savedTicketCount(reloaded, truck.getUUID()) == 9,
                "World-load validation must retain a valid persistent truck rather than delete it before entity loading");
        UUID truckId = truck.getUUID();
        truck.discard();
        helper.assertTrue(FkChunkLoading.ticketCount(level, truckId) == 0 && savedTicketCount(forced, truckId) == 0,
                "Deleting a truck must release both runtime and persisted ticking tickets");
        helper.assertFalse(anchors.contains(truckId), "A deleted truck must not leave a persistent anchor behind");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 70, batch = "chunk_loading")
    public static void remoteWorksFarAwayAndLogoutRetainsTruckTickets(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var owner = VehicleGameTests.onlinePlayerFixture(helper);
        var intruder = VehicleGameTests.onlinePlayerFixture(helper);
        Fk2000Entity truck = helper.spawn(ModContent.VEHICLE.get(), new Vec3(16, 2, 16));
        truck.setOwnerUUID(owner.getUUID());
        FkChunkLoading.refreshVehicle(truck);
        ItemStack remote = new ItemStack(ModContent.REMOTE.get());
        remote.getOrCreateTag().putUUID("Vehicle", truck.getUUID());
        remote.getOrCreateTag().putString("Dimension", level.dimension().location().toString());
        owner.setItemInHand(InteractionHand.MAIN_HAND, remote);
        owner.moveTo(truck.getX() + 384, truck.getY(), truck.getZ());
        intruder.setItemInHand(InteractionHand.MAIN_HAND, remote.copy());
        helper.assertTrue(owner.distanceToSqr(truck) > 192 * 192, "The remote fixture must be outside the previous hard distance limit");
        helper.assertTrue(ModNetwork.openRemote(owner, remote),
                "A paired owner must be able to open server telemetry outside client entity tracking range");
        helper.assertFalse(ModNetwork.openRemote(intruder, intruder.getMainHandItem()),
                "Unlimited distance must never allow a different player to control the vehicle");
        int firstTick = truck.tickCount;
        VehicleGameTests.removePlayerFixture(helper, owner);
        VehicleGameTests.removePlayerFixture(helper, intruder);
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(truck.tickCount > firstTick + 10, "A persistent truck must continue server simulation");
            helper.assertTrue(FkChunkLoading.ticketCount(level, truck.getUUID()) == 9,
                    "Player logout must not discard the truck's permanent ticking tickets");
            UUID id = truck.getUUID();
            truck.discard();
            helper.assertTrue(savedTicketCount(forced(level), id) == 0,
                    "The final truck removal must release persistent tickets even after its owner logs out");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60, batch = "chunk_loading")
    public static void missileCorridorLoadsAheadAndReleasesOnRemoval(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MissileEntity missile = ModContent.MISSILE.get().create(level);
        helper.assertTrue(missile != null, "The registered missile must construct");
        Vec3 start = helper.absoluteVec(new Vec3(16, 12, 16));
        missile.moveTo(start.x, start.y, start.z, -90, 0);
        missile.launchAt(UUID.randomUUID(), UUID.randomUUID(), start.add(160, 0, 0));
        helper.assertTrue(level.addFreshEntity(missile), "The flight fixture must enter the server world");
        missile.setDeltaMovement(1.8, 0, 0);
        FkChunkLoading.refreshMissile(missile, null);
        int count = FkChunkLoading.ticketCount(level, missile.getUUID());
        helper.assertTrue(count >= 9 && count <= 32, "A missile's moving flight corridor must have a bounded ticking ticket budget: " + count);
        ChunkPos ahead = new ChunkPos((int) Math.floor(start.x + 32) >> 4, (int) Math.floor(start.z) >> 4);
        helper.assertTrue(FkChunkLoading.ownsTicket(level, missile.getUUID(), ahead),
                "The missile must load a chunk ahead before its physical movement reaches that boundary");
        ChunkPos distant = new ChunkPos((int) Math.floor(start.x + 160) >> 4, (int) Math.floor(start.z) >> 4);
        helper.assertFalse(FkChunkLoading.ownsTicket(level, missile.getUUID(), distant),
                "A distant fixed coordinate must not be force-loaded at launch");
        UUID id = missile.getUUID();
        helper.assertTrue(savedTicketCount(forced(level), id) == count, "Forge must receive the real flight corridor tickets");
        missile.discard();
        helper.assertTrue(FkChunkLoading.ticketCount(level, id) == 0 && savedTicketCount(forced(level), id) == 0,
                "Impact, expiry or other missile removal must release its entire corridor");
        helper.assertFalse(VehicleChunkAnchors.get(level).contains(id), "A temporary missile must never become a persistent truck anchor");
        helper.succeed();
    }

    private static ForcedChunksSavedData forced(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(ForcedChunksSavedData::load, ForcedChunksSavedData::new, "chunks");
    }

    private static int savedTicketCount(ForcedChunksSavedData data, UUID owner) {
        CompoundTag nbt = data.save(new CompoundTag());
        int count = 0;
        ListTag mods = nbt.getList("ForgeForced", Tag.TAG_COMPOUND);
        for (int i = 0; i < mods.size(); i++) {
            CompoundTag mod = mods.getCompound(i);
            if (!IruxFk2000Mod.MOD_ID.equals(mod.getString("Mod"))) continue;
            ListTag chunks = mod.getList("ModForced", Tag.TAG_COMPOUND);
            for (int j = 0; j < chunks.size(); j++) {
                ListTag owners = chunks.getCompound(j).getList("TickingEntities", Tag.TAG_INT_ARRAY);
                for (int k = 0; k < owners.size(); k++) if (owner.equals(NbtUtils.loadUUID(owners.get(k)))) count++;
            }
        }
        return count;
    }
}
