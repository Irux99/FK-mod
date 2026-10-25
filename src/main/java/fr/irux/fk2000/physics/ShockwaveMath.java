package fr.irux.fk2000.physics;

/** Bounded visual envelope, independent of Minecraft and the user's base field of view. */
public final class ShockwaveMath {
    private ShockwaveMath() {}
    public static double amplitude(double distance, double power) {
        if (!Double.isFinite(distance) || !Double.isFinite(power) || distance < 0 || power <= 0) return 0;
        double radius = Math.min(128, Math.max(24, power * 20));
        double falloff = Math.max(0, 1 - distance / radius);
        return Math.min(1, power / 4) * falloff * falloff;
    }
    public static double pulse(double age) {
        if (!Double.isFinite(age) || age < 0 || age >= 24) return 0;
        return Math.sin(age * .72) * Math.exp(-age / 6);
    }
}
