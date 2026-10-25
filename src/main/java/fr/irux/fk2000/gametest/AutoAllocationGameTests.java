package fr.irux.fk2000.gametest;

import fr.irux.fk2000.config.ModConfig;
import fr.irux.fk2000.compat.AerialTarget;
import fr.irux.fk2000.entity.missile.MissileEntity;
import fr.irux.fk2000.entity.vehicle.AutoFireMode;
import fr.irux.fk2000.entity.vehicle.Fk2000Entity;
import fr.irux.fk2000.radar.TargetMode;
import fr.irux.fk2000.registry.ModContent;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Counts actual spawned missiles and their saved targets, independently of UI queue counters. */
@GameTestHolder("iruxfk2000")
@PrefixGameTestTemplate(false)
public final class AutoAllocationGameTests {
    private AutoAllocationGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 190, batch = "auto_allocation")
    public static void selectedTargetsReceiveExactlyOneMissileEach(GameTestHelper helper) {
        Fixture f = new Fixture(helper, AutoFireMode.ONE_PER_TARGET);
        helper.runAtTickTime(12, () -> {
            f.truck.selectTarget(f.first.getId());
            f.truck.selectTarget(f.second.getId());
            helper.assertTrue(f.truck.getSelectedTargetIds().size() == 2, "Two observed contacts must be selectable together");
            f.truck.setAutoFire(true);
        });
        helper.onEachTick(f::observeMissiles);
        helper.runAtTickTime(160, () -> {
            helper.assertTrue(f.count(f.first) == 1 && f.count(f.second) == 1,
                    "Each selected aircraft must receive exactly one real missile: " + f.fired);
            helper.assertTrue(f.count(f.unselected) == 0 && f.truck.getMissileCount() == 1,
                    "An unselected contact must not consume the third missile even when automatic acquisition is enabled");
            helper.assertTrue(f.truck.getSelectedTargetIds().isEmpty() && f.truck.getLauncherProgress() < .001,
                    "The finished queue must remain empty and the launcher must fold down");
            f.close();
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 190, batch = "auto_allocation")
    public static void salvoStaysOnChosenTargetAndStopsAfterItsLoss(GameTestHelper helper) {
        Fixture f = new Fixture(helper, AutoFireMode.SALVO);
        helper.runAtTickTime(12, () -> { f.truck.selectTarget(f.first.getId()); f.truck.setAutoFire(true); });
        helper.onEachTick(f::observeMissiles);
        helper.runAtTickTime(95, () -> {
            helper.assertTrue(f.count(f.first) == 2, "The salvo must send its first two missiles to the same chosen target");
            f.first.discard();
        });
        helper.runAtTickTime(160, () -> {
            helper.assertTrue(f.count(f.first) == 2 && f.count(f.second) == 0 && f.count(f.unselected) == 0,
                    "Losing the chosen target must stop the salvo rather than allocate missiles to another contact: " + f.fired);
            helper.assertTrue(f.truck.getMissileCount() == 1 && !f.truck.isPreparingLaunch()
                            && f.truck.getLauncherProgress() < .001,
                    "The unused missile must remain loaded and the launcher must settle after losing its target");
            f.close();
            helper.succeed();
        });
    }

    private static final class Fixture {
        final GameTestHelper helper;
        final ServerPlayer owner;
        final Fk2000Entity truck;
        final ArmorStand first, second, unselected;
        final Set<UUID> seen = new HashSet<>();
        final Map<UUID, Integer> fired = new HashMap<>();
        Fixture(GameTestHelper helper, AutoFireMode mode) {
            this.helper = helper;
            owner = VehicleGameTests.onlinePlayerFixture(helper);
            truck = helper.spawn(ModContent.VEHICLE.get(), new Vec3(16, 2, 16));
            truck.setOwnerUUID(owner.getUUID());
            CompoundTag data = new CompoundTag(); truck.addAdditionalSaveData(data);
            data.putInt("Missiles", 3); truck.readAdditionalSaveData(data);
            truck.setTargetMode(TargetMode.AIRCRAFT);
            truck.setRadarEnabled(true); truck.setAutoAcquire(true); truck.setAutoFire(false);
            truck.setAutoFireMode(mode);
            first = target(new Vec3(24, 70, 8));
            second = target(new Vec3(24, 70, 24));
            unselected = target(new Vec3(8, 70, 24));
        }
        ArmorStand target(Vec3 position) {
            ArmorStand target = new Aircraft(helper.getLevel());
            target.moveTo(helper.absoluteVec(position));
            target.setNoGravity(true); target.setInvulnerable(true);
            helper.getLevel().addFreshEntity(target);
            return target;
        }
        void observeMissiles() {
            for (MissileEntity missile : helper.getLevel().getEntitiesOfClass(MissileEntity.class, arena())) {
                CompoundTag data = new CompoundTag(); missile.saveWithoutId(data);
                if (data.hasUUID("Launcher") && truck.getUUID().equals(data.getUUID("Launcher"))
                        && data.hasUUID("Target") && seen.add(missile.getUUID()))
                    fired.merge(data.getUUID("Target"), 1, Integer::sum);
            }
        }
        int count(ArmorStand target) { return fired.getOrDefault(target.getUUID(), 0); }
        AABB arena() { return new AABB(helper.absoluteVec(Vec3.ZERO), helper.absoluteVec(new Vec3(32, 96, 32))); }
        void close() {
            truck.discard(); first.discard(); second.discard(); unselected.discard();
            helper.getLevel().getEntitiesOfClass(MissileEntity.class, arena()).forEach(m -> {
                CompoundTag data = new CompoundTag(); m.saveWithoutId(data);
                if (data.hasUUID("Launcher") && truck.getUUID().equals(data.getUUID("Launcher"))) m.discard();
            });
            VehicleGameTests.removePlayerFixture(helper, owner);
        }
    }

    private static final class Aircraft extends ArmorStand implements AerialTarget {
        Aircraft(Level level) { super(EntityType.ARMOR_STAND, level); }
        @Override public boolean isAvailableToRadar() { return true; }
    }
}
