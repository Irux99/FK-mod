package fr.irux.fk2000.physics;

/** Independent display zoom and a sweep echo that never becomes invisible between rotations. */
public final class RadarDisplayMath {
    private RadarDisplayMath() {}
    public static int zoom(int current, boolean closer) {
        return Math.max(32, Math.min(2048, closer ? Math.max(32, current / 2) : current * 2));
    }
    public static double brightness(double bearing, double sweep) {
        if (!Double.isFinite(bearing) || !Double.isFinite(sweep)) return .4;
        double sincePass = (sweep - bearing) % (Math.PI * 2);
        if (sincePass < 0) sincePass += Math.PI * 2;
        return .4 + .6 * Math.exp(-sincePass * .85);
    }
}
