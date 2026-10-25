package fr.irux.fk2000.entity.missile;

/** Serialized in ordinal order; append new states rather than reordering existing values. */
public enum FlightState {
    IDLE, PREPARING, EJECTING, ORIENTING, BOOSTING, GUIDING, DETONATED;

    public boolean motorActive() { return this == BOOSTING || this == GUIDING; }

    public static FlightState fromId(int id) {
        return id >= 0 && id < values().length ? values()[id] : IDLE;
    }
}
