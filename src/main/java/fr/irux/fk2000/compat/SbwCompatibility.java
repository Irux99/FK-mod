package fr.irux.fk2000.compat;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * Narrow optional bridge verified against the official 0.8.9.x source branches.
 * No SBW classes enter our public signatures or entity inheritance hierarchy.
 * Future incompatible APIs fail closed instead of selecting arbitrary entities.
 */
public final class SbwCompatibility {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> AERIAL_TYPES = Set.of("AIRPLANE", "HELICOPTER", "DRONE", "AIRSHIP");
    private static final TagKey<net.minecraft.world.entity.EntityType<?>> AERIAL_TARGETS =
            TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("iruxfk2000", "aerial_targets"));
    private static final SbwCompatibility INSTANCE = new SbwCompatibility();

    private Class<?> vehicleClass;
    private Method vehicleType;
    private Method wreck;
    private Method lastDriver;
    private BiPredicate<Entity, Entity> friendly;
    private boolean bridgeAvailable;
    private boolean warned;

    @SuppressWarnings("unchecked")
    private SbwCompatibility() {
        try {
            Class<?> candidate = Class.forName("com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity");
            vehicleClass = candidate;
            Method typeGetter = candidate.getMethod("getVehicleType");
            Method wreckGetter = candidate.getMethod("isWreck");
            Method driverGetter = candidate.getMethod("getLastDriverUUID");
            Class<?> seekTool = Class.forName("com.atsuishio.superbwarfare.tools.SeekTool");
            Object predicate = seekTool.getField("IS_FRIENDLY").get(null);
            if (!(predicate instanceof BiPredicate<?, ?>)) throw new NoSuchFieldException("SeekTool.IS_FRIENDLY must be BiPredicate");
            vehicleType = typeGetter;
            wreck = wreckGetter;
            lastDriver = driverGetter;
            friendly = (BiPredicate<Entity, Entity>) predicate;
            bridgeAvailable = true;
            String version = ModList.get().getModContainerById("superbwarfare")
                    .map(container -> container.getModInfo().getVersion().toString()).orElse("unknown");
            LOGGER.info("Irux FK-2000: SBW {} adapter methods and IFF predicate resolved; gameplay compatibility still requires testing", version);
        } catch (ClassNotFoundException ignored) {
            // SBW is optional for independent development; absence never fabricates radar contacts.
            if (vehicleClass != null) warn(ignored);
        } catch (ReflectiveOperationException | LinkageError error) {
            warn(error);
        }
    }

    public static boolean isAerial(Entity target) {
        if (!target.isAlive() || target.isRemoved() || target instanceof Player) return false;
        if (target instanceof AerialTarget aerial) return aerial.isAvailableToRadar();
        if (target.getType().is(AERIAL_TARGETS)) return true;
        if (!INSTANCE.bridgeAvailable || INSTANCE.vehicleClass == null || !INSTANCE.vehicleClass.isInstance(target)) return false;
        try {
            if (Boolean.TRUE.equals(INSTANCE.wreck.invoke(target))) return false;
            Object kind = INSTANCE.vehicleType.invoke(target);
            return kind instanceof Enum<?> e && AERIAL_TYPES.contains(e.name());
        } catch (ReflectiveOperationException | LinkageError error) {
            INSTANCE.warn(error);
            return false;
        }
    }

    /** Owners, teams, passengers and SBW's IFF predicate all veto a lock. */
    public static boolean isFriendly(Entity vehicle, UUID ownerUUID, Player owner, Entity target) {
        // An offline/unknown owner cannot supply trustworthy team and SBW IFF context.
        if (ownerUUID == null || owner == null) return true;
        if (target == vehicle || target.isAlliedTo(vehicle) || vehicle.isAlliedTo(target)) return true;
        if (ownerUUID != null && ownerUUID.equals(target.getUUID())) return true;
        if (target instanceof OwnableEntity ownable && ownerUUID != null
                && ownerUUID.equals(ownable.getOwnerUUID())) return true;
        if (owner != null && (owner.isAlliedTo(target) || target.isAlliedTo(owner))) return true;
        for (Entity passenger : target.getIndirectPassengers()) {
            if (ownerUUID != null && ownerUUID.equals(passenger.getUUID())) return true;
            if (owner != null && (owner.isAlliedTo(passenger) || passenger.isAlliedTo(owner))) return true;
        }
        if (INSTANCE.vehicleClass != null && INSTANCE.vehicleClass.isInstance(target)) {
            if (!INSTANCE.bridgeAvailable || INSTANCE.friendly == null) return true;
            try {
                String driverId = String.valueOf(INSTANCE.lastDriver.invoke(target));
                if (ownerUUID != null && ownerUUID.toString().equals(driverId)) return true;
                try {
                    Player driver = target.level().getPlayerByUUID(UUID.fromString(driverId));
                    if (driver != null && owner != null && owner.isAlliedTo(driver)) return true;
                } catch (IllegalArgumentException ignored) { /* SBW uses "undefined" when unoccupied. */ }
                if (owner != null && INSTANCE.friendly != null && INSTANCE.friendly.test(owner, target)) return true;
            } catch (ReflectiveOperationException | LinkageError error) {
                INSTANCE.warn(error);
                return true; // Cannot verify IFF: do not engage a potentially friendly vehicle.
            } catch (RuntimeException error) {
                INSTANCE.warn(error);
                return true;
            }
        }
        return false;
    }

    private void warn(Throwable error) {
        if (!warned) {
            LOGGER.warn("Irux FK-2000: SBW adapter unavailable; incompatible targets fail closed", error);
            warned = true;
        }
    }
}
