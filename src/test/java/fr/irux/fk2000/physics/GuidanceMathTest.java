package fr.irux.fk2000.physics;

import org.junit.jupiter.api.Test;

import static fr.irux.fk2000.physics.GuidanceMath.*;
import static org.junit.jupiter.api.Assertions.*;

class GuidanceMathTest {
    private static final double EPS = 1.0e-8;

    @Test
    void orientationIsProgressiveAndLimitedToConfiguredAngularRate() {
        Vector direction = UP;
        Vector target = new Vector(1, 0, 0);
        double limit = Math.toRadians(5);
        for (int tick = 0; tick < 18; tick++) {
            Vector next = turnTowards(direction, target, limit);
            assertEquals(1, next.length(), EPS, "Turning must not accidentally accelerate the missile");
            double rotation = Math.acos(Math.max(-1, Math.min(1, direction.dot(next))));
            assertTrue(rotation <= limit + EPS, "Guidance must not snap to a moving target");
            direction = next;
        }
        assertEquals(target.x(), direction.x(), EPS);
        assertEquals(target.y(), direction.y(), EPS);
    }

    @Test
    void antiparallelTargetDoesNotProduceNanOrInstantTurn() {
        Vector result = turnTowards(UP, new Vector(0, -1, 0), Math.toRadians(10));
        assertEquals(1, result.length(), EPS);
        assertEquals(Math.cos(Math.toRadians(10)), result.dot(UP), EPS);
        assertTrue(Double.isFinite(result.x()));
        assertTrue(result.y() > 0, "A 180 degree reversal must take multiple ticks");
    }

    @Test
    void zeroInputKeepsAValidLaunchOrientation() {
        Vector result = turnTowards(new Vector(0, 0, 0), new Vector(0, 0, 0), 1);
        assertEquals(UP, result);
    }

    @Test
    void zeroAngularRatePreservesDirection() {
        assertEquals(UP, turnTowards(UP, new Vector(1, 0, 0), 0));
    }

    @Test
    void crossingTargetIsLedAheadRatherThanChasedAtOldPosition() {
        Vector target = new Vector(100, 0, 0);
        Vector velocity = new Vector(0, 0, 1);
        Vector intercept = intercept(new Vector(0, 0, 0), target, velocity, 2, 100);
        double flightTime = intercept.subtract(target).z();
        assertTrue(intercept.z() > 50);
        assertEquals(flightTime, intercept.length() / 2, EPS,
                "Missile and target should arrive at the computed intercept simultaneously");
    }

    @Test
    void unreachableFastTargetHasFiniteBoundedLead() {
        Vector target = new Vector(100, 10, 0);
        Vector result = intercept(new Vector(0, 0, 0), target, new Vector(10, 0, 0), 1, 20);
        assertTrue(Double.isFinite(result.x()));
        assertTrue(result.x() <= 300);
        assertEquals(10, result.y(), EPS);
    }

    @Test
    void stationaryTargetIsNotLed() {
        Vector target = new Vector(12, -4, 88);
        assertEquals(target, intercept(new Vector(0, 0, 0), target, new Vector(0, 0, 0), 2, 60));
    }

    @Test
    void proximityFuseSeesTargetPassedBetweenTicks() {
        Vector target = new Vector(5, 1, 0);
        Vector before = new Vector(0, 0, 0);
        Vector after = new Vector(10, 0, 0);
        assertTrue(target.subtract(before).length() > 2);
        assertTrue(target.subtract(after).length() > 2);
        assertEquals(1, distanceToSegment(target, before, after), EPS);
    }

    @Test
    void proximityFuseDoesNotHitPointsBeyondSweptSegment() {
        assertEquals(5, distanceToSegment(new Vector(15, 0, 0), new Vector(0, 0, 0), new Vector(10, 0, 0)), EPS);
    }

    @Test
    void stationarySweepIsSafeAndFinite() {
        assertEquals(3, distanceToSegment(new Vector(0, 3, 0), new Vector(0, 0, 0), new Vector(0, 0, 0)), EPS);
    }
}
