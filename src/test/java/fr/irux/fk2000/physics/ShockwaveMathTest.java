package fr.irux.fk2000.physics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ShockwaveMathTest {
    @Test void pressureFadesWithDistanceAndRejectsNonFiniteInput() {
        assertEquals(1, ShockwaveMath.amplitude(0, 4));
        assertTrue(ShockwaveMath.amplitude(10, 4) > ShockwaveMath.amplitude(40, 4));
        assertEquals(0, ShockwaveMath.amplitude(128, 100));
        assertEquals(0, ShockwaveMath.amplitude(Double.NaN, 4));
        assertEquals(0, ShockwaveMath.amplitude(0, Double.POSITIVE_INFINITY));
    }
    @Test void fieldOfViewPulseIsFiniteBoundedAndEnds() {
        for (double age = 0; age < 24; age += .05) {
            double multiplier = 1 - .1 * ShockwaveMath.pulse(age);
            assertTrue(multiplier >= .9 && multiplier <= 1.1);
        }
        assertEquals(0, ShockwaveMath.pulse(-1));
        assertEquals(0, ShockwaveMath.pulse(24));
        assertEquals(0, ShockwaveMath.pulse(Double.NaN));
    }
}
