package fr.irux.fk2000.radar;

import java.util.UUID;

/** Immutable, server-produced radar observation; positions never load chunks. */
public record RadarContact(int entityId, UUID uuid, String name, double x, double y,
                           double z, double distance) {
}
