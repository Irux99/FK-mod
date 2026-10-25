package fr.irux.fk2000.gametest;

import com.mojang.logging.LogUtils;
import fr.irux.fk2000.entity.missile.MissileEntity;
import fr.irux.fk2000.registry.ModContent;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

/** Uses the release's actual damage modifiers and health, including allied blast protection. */
@GameTestHolder("iruxfk2000_sbw")
@PrefixGameTestTemplate(false)
public final class MissileSbwGameTests {
    private MissileSbwGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 220, batch = "sbw_compatibility")
    public static void warheadDamagesRealHelicopterAndProtectsFriendlyAircraft(GameTestHelper helper) {
        ServerPlayer owner = VehicleGameTests.onlinePlayerFixture(helper);
        Entity hostile = helicopter(helper, new Vec3(24.5, 70, 7.5));
        Entity friendly = helicopter(helper, new Vec3(27.5, 70, 7.5));
        try {
            friendly.getClass().getMethod("setLastDriverUUID", String.class).invoke(friendly, owner.getStringUUID());
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("The actual release must expose its known owner/IFF setter", error);
        }
        float hostileHealth = health(hostile);
        float friendlyHealth = health(friendly);
        Vec3 hostilePosition = hostile.position();
        Vec3 friendlyPosition = friendly.position();
        MissileEntity missile = ModContent.MISSILE.get().create(helper.getLevel());
        helper.assertTrue(missile != null, "Registered missile must construct");
        Vec3 start = helper.absoluteVec(new Vec3(4.5, 3, 7.5));
        missile.moveTo(start.x, start.y, start.z, 0, -90);
        missile.launch(owner.getUUID(), UUID.randomUUID(), hostile);
        helper.assertTrue(helper.getLevel().addFreshEntity(missile), "Missile must enter the actual release's server world");
        helper.onEachTick(() -> {
            // Keep parked, unpowered fixtures in the air while exercising real production hurt().
            hostile.setPos(hostilePosition);
            hostile.setDeltaMovement(Vec3.ZERO);
            friendly.setPos(friendlyPosition);
            friendly.setDeltaMovement(Vec3.ZERO);
            helper.assertTrue(helper.getTick() < 218 || missile.isRemoved(),
                    "Actual AH-6 intercept stalled: state=" + missile.getFlightState() + ", position=" + missile.position()
                            + ", target=" + hostilePosition + ", velocity=" + missile.getDeltaMovement());
            if (!missile.isRemoved()) return;
            helper.assertTrue(missile.getTerminationReason() == MissileEntity.TerminationReason.DETONATED,
                    "The FK missile must intercept a real AH-6: " + missile.getTerminationReason() + " at " + missile.position());
            float actualDamage = hostileHealth - health(hostile);
            LogUtils.getLogger().info("FK-2000 warhead integration: actual AH-6 health {} -> {}, damage {}, allied AH-6 {} -> {}",
                    hostileHealth, health(hostile), actualDamage, friendlyHealth, health(friendly));
            helper.assertTrue(actualDamage >= 50,
                    "The real AH-6 must lose substantial health after its SBW armor modifiers: " + actualDamage + " / " + hostileHealth);
            helper.assertTrue(health(friendly) == friendlyHealth,
                    "A nearby owner's aircraft must take no warhead or vanilla blast damage: " + health(friendly) + " / " + friendlyHealth);
            hostile.discard();
            friendly.discard();
            VehicleGameTests.removePlayerFixture(helper, owner);
            helper.succeed();
        });
    }

    private static Entity helicopter(GameTestHelper helper, Vec3 relativePosition) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("superbwarfare", "ah_6"));
        helper.assertTrue(type != null, "This integration test requires the actual SBW AH-6");
        Entity entity = type.create(helper.getLevel());
        helper.assertTrue(entity != null, "The actual release helicopter must construct");
        entity.moveTo(helper.absoluteVec(relativePosition));
        entity.setNoGravity(true);
        helper.assertTrue(helper.getLevel().addFreshEntity(entity), "The actual release helicopter must enter the world");
        return entity;
    }

    private static float health(Entity entity) {
        try {
            return ((Number) entity.getClass().getMethod("getHealth").invoke(entity)).floatValue();
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("The actual release must expose its known vehicle health getter", error);
        }
    }
}
