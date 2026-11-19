package com.lemarc.fake_gps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class SpeedPresetsTest {

    @Test
    fun allPresetsParseAndProduceValidSpeeds() {
        for (preset in SpeedPresets.ALL) {
            val expr = SpeedExpression.parse(preset.expression)
            val values = SpeedPresets.sample(expr)
            assertTrue(preset.name, values.all { it >= 0 && !it.isNaN() })
        }
    }

    @Test
    fun periodicSignals() {
        fun f(e: String, x: Double) = SpeedExpression.parse(e).evaluate(x)
        assertEquals(1.0, f("square(t)", PI / 2), 1e-9)
        assertEquals(-1.0, f("square(t)", 3 * PI / 2), 1e-9)
        assertEquals(1.0, f("tri(t)", PI / 2), 1e-9)
        assertEquals(0.0, f("tri(t)", 0.0), 1e-9)
        assertEquals(-1.0, f("tri(t)", -PI / 2), 1e-9)
        assertEquals(0.0, f("saw(t)", 0.0), 1e-9)
        assertEquals(0.5, f("saw(t)", PI / 2), 1e-9)
        assertEquals(0.0, f("saw(t)", 2 * PI), 1e-9)
    }

    @Test
    fun stopAndGoPreset() {
        val e = SpeedExpression.parse("if(mod(t; 90) < 30; 0; 50)")
        assertEquals(0.0, e.speedKmh(10.0), 1e-9)
        assertEquals(50.0, e.speedKmh(45.0), 1e-9)
        assertEquals(0.0, e.speedKmh(95.0), 1e-9)
    }
}
