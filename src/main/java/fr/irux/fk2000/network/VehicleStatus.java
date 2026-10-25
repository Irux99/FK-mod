package fr.irux.fk2000.network;

import fr.irux.fk2000.radar.RadarContact;
import fr.irux.fk2000.radar.TargetMode;
import fr.irux.fk2000.entity.vehicle.TubeCapMode;
import fr.irux.fk2000.entity.vehicle.AutoFireMode;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Server-produced telemetry; contact coordinates need not have a client-tracked entity. */
public record VehicleStatus(int vehicleId, boolean engine, boolean radar, boolean autoAcquire,
    boolean autoFire, int missiles, float health, float maxHealth, float speed, float launcher, int targetId,
    double x, double y, double z, double radarRange, TargetMode targetMode,
    Vec3 targetCoordinates, TubeCapMode tubeCapMode, AutoFireMode autoFireMode,
    List<Integer> selectedTargetIds, List<RadarContact> contacts) {
    public VehicleStatus {
        selectedTargetIds = List.copyOf(selectedTargetIds);
        contacts = List.copyOf(contacts);
    }
}
