package fr.irux.fk2000.entity.vehicle;

/** The owner either engages one selected contact or assigns one round to each queued contact. */
public enum AutoFireMode {
    SALVO,
    ONE_PER_TARGET;

    public AutoFireMode next() { return values()[(ordinal() + 1) % values().length]; }
}
