package fr.irux.fk2000.radar;

import java.util.Locale;

/** Stable server-owned targeting modes, independent from the tube-cap launch options. */
public enum TargetMode {
    AIRCRAFT,
    SBW_VEHICLES,
    ALL_ENTITIES,
    COORDINATES;

    public TargetMode next() { return values()[(ordinal() + 1) % values().length]; }
    public String translationKey() { return "target.iruxfk2000.mode." + name().toLowerCase(Locale.ROOT); }
}
