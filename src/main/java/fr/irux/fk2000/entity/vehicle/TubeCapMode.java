package fr.irux.fk2000.entity.vehicle;

/** Mechanical cover fitted to the six launch tubes. */
public enum TubeCapMode {
    EJECT,
    HINGED,
    NONE;

    public TubeCapMode next() { return values()[(ordinal() + 1) % values().length]; }
}
