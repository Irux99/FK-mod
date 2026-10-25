package fr.irux.fk2000.network;

import fr.irux.fk2000.radar.RadarContact;
import java.util.List;

/** Server-produced telemetry; contact coordinates need not have a client-tracked entity. */
public record VehicleStatus(int vehicleId, boolean engine, boolean radar, boolean autoAcquire,
    boolean autoFire, int missiles, float speed, float launcher, int targetId,
    double x, double y, double z, double radarRange, List<RadarContact> contacts) {
    public VehicleStatus { contacts = List.copyOf(contacts); }
}
