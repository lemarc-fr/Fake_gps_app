package com.lemarc.fake_gps.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FormattersTest {

    @Test
    fun speed() {
        assertEquals("50", Formatters.speed(50.0, Locale.FRANCE))
        assertEquals("12,5", Formatters.speed(12.5, Locale.FRANCE))
        assertEquals("12.5", Formatters.speed(12.5, Locale.US))
        assertEquals("131", Formatters.speed(130.7, Locale.US))
    }

    @Test
    fun distance() {
        assertEquals("350 m", Formatters.distance(350.2, Locale.FRANCE))
        assertEquals("1,25 km", Formatters.distance(1250.0, Locale.FRANCE))
        assertEquals("1.25 km", Formatters.distance(1250.0, Locale.US))
    }
}
