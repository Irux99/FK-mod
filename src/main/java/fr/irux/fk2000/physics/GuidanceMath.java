package fr.irux.fk2000.physics;

/** Side-independent flight mathematics; distances are blocks and time is ticks. */
public final class GuidanceMath {
    private static final double EPSILON = 1.0e-10;
    public static final Vector UP = new Vector(0, 1, 0);

    private GuidanceMath() {}

    public record Vector(double x, double y, double z) {
        public Vector add(Vector other) { return new Vector(x + other.x, y + other.y, z + other.z); }
        public Vector subtract(Vector other) { return new Vector(x - other.x, y - other.y, z - other.z); }
        public Vector scale(double value) { return new Vector(x * value, y * value, z * value); }
        public double dot(Vector other) { return x * other.x + y * other.y + z * other.z; }
        public Vector cross(Vector other) {
            return new Vector(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x);
        }
        public double length() { return Math.sqrt(dot(this)); }
        public Vector normalizedOr(Vector fallback) {
            double length = length();
            return length < EPSILON ? fallback : scale(1.0 / length);
        }
    }

    /** A radial exhaust direction and its normalized share of the requested lateral impulse. */
    public record ThrusterJet(Vector direction, double strength) {}

    /**
     * Four nozzles around the rear body. Exhaust points away from the requested
     * correction, so its reaction force pushes the missile toward that correction.
     * Opposite nozzles never fire together; a diagonal correction uses two sides.
     */
    public static ThrusterJet[] reactionJets(Vector forward, Vector requestedCorrection) {
        Vector axis = forward.normalizedOr(UP);
        Vector reference = Math.abs(axis.dot(UP)) > .9 ? new Vector(0, 0, 1) : UP;
        Vector radialX = axis.cross(reference).normalizedOr(new Vector(1, 0, 0));
        Vector radialY = radialX.cross(axis).normalizedOr(new Vector(0, 0, 1));
        Vector lateral = requestedCorrection.subtract(axis.scale(requestedCorrection.dot(axis)));
        Vector impulse = lateral.length() < EPSILON ? new Vector(0, 0, 0) : lateral.normalizedOr(UP);
        Vector[] directions = {radialX, radialX.scale(-1), radialY, radialY.scale(-1)};
        ThrusterJet[] jets = new ThrusterJet[directions.length];
        for (int i = 0; i < directions.length; i++)
            jets[i] = new ThrusterJet(directions[i], Math.max(0, -impulse.dot(directions[i])));
        return jets;
    }

    /** Rotate along the shortest arc, including an explicit stable axis for opposite vectors. */
    public static Vector turnTowards(Vector current, Vector wanted, double maximumRadians) {
        Vector from = current.normalizedOr(UP);
        Vector to = wanted.normalizedOr(from);
        double dot = Math.max(-1, Math.min(1, from.dot(to)));
        double angle = Math.acos(dot);
        double step = Math.min(angle, Math.max(0, maximumRadians));
        if (step < EPSILON) return from;
        if (angle <= maximumRadians) return to;
        Vector axis = from.cross(to);
        if (axis.length() < EPSILON) {
            Vector basis = Math.abs(from.x()) < 0.8 ? new Vector(1, 0, 0) : new Vector(0, 0, 1);
            axis = from.cross(basis);
        }
        axis = axis.normalizedOr(new Vector(1, 0, 0));
        // Rodrigues' formula keeps speed/direction unit length without linear-lerp artifacts.
        return from.scale(Math.cos(step)).add(axis.cross(from).scale(Math.sin(step)))
                .add(axis.scale(axis.dot(from) * (1 - Math.cos(step)))).normalizedOr(from);
    }

    /** Earliest positive constant-speed intercept, bounded when no physical intercept exists. */
    public static Vector intercept(Vector missilePosition, Vector targetPosition, Vector targetVelocity,
                                   double missileSpeed, double maximumLeadTicks) {
        Vector relative = targetPosition.subtract(missilePosition);
        double speed = Math.max(0.01, missileSpeed);
        double a = targetVelocity.dot(targetVelocity) - speed * speed;
        double b = 2 * relative.dot(targetVelocity);
        double c = relative.dot(relative);
        double time = Math.sqrt(c) / speed;
        if (Math.abs(a) < EPSILON) {
            if (Math.abs(b) > EPSILON && -c / b > 0) time = -c / b;
        } else {
            double discriminant = b * b - 4 * a * c;
            if (discriminant >= 0) {
                double first = (-b - Math.sqrt(discriminant)) / (2 * a);
                double second = (-b + Math.sqrt(discriminant)) / (2 * a);
                if (first > 0 && second > 0) time = Math.min(first, second);
                else if (first > 0) time = first;
                else if (second > 0) time = second;
            }
        }
        time = Math.min(Math.max(0, time), Math.max(0, maximumLeadTicks));
        return targetPosition.add(targetVelocity.scale(time));
    }

    /** Distance to a swept segment, preventing a fast missile from skipping its proximity fuse. */
    public static double distanceToSegment(Vector point, Vector start, Vector end) {
        Vector segment = end.subtract(start);
        double squareLength = segment.dot(segment);
        if (squareLength < EPSILON) return point.subtract(start).length();
        double fraction = Math.max(0, Math.min(1, point.subtract(start).dot(segment) / squareLength));
        return point.subtract(start.add(segment.scale(fraction))).length();
    }
}
