package fr.irux.fk2000.physics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class RadarDisplayMathTest {
    @Test void echoBrightensAtSweepThenFadesWithoutDisappearing() {
        assertEquals(1, RadarDisplayMath.brightness(1, 1));
        assertTrue(RadarDisplayMath.brightness(1, 1.2) > RadarDisplayMath.brightness(1, 4));
        for (double angle = -20; angle <= 20; angle += .03)
            assertTrue(RadarDisplayMath.brightness(0, angle) >= .4);
        assertEquals(RadarDisplayMath.brightness(0, .5), RadarDisplayMath.brightness(Math.PI * 2, .5), 1e-9);
    }
    @Test void viewportZoomHasIndependentLimits() {
        assertEquals(32, RadarDisplayMath.zoom(32, true));
        assertEquals(2048, RadarDisplayMath.zoom(2048, false));
        assertEquals(80, RadarDisplayMath.zoom(160, true));
        assertEquals(320, RadarDisplayMath.zoom(160, false));
    }
}
