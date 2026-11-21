package com.lemarc.fake_gps.data

import com.lemarc.fake_gps.model.LatLon
import org.junit.Assert.assertEquals
import org.junit.Test

class AppPreferencesTest {

    @Test
    fun pointsRoundTrip() {
        val points = listOf(LatLon(48.8584, 2.2945), LatLon(-33.5, -70.25))
        assertEquals(points, AppPreferences.decodePoints(AppPreferences.encodePoints(points)))
    }

    @Test
    fun decodeIgnoresGarbage() {
        assertEquals(AppPreferences.decodePoints(null), emptyList<LatLon>())
        assertEquals(AppPreferences.decodePoints(""), emptyList<LatLon>())
        assertEquals(AppPreferences.decodePoints("1.0,2.0;oops;3.0"), listOf(LatLon(1.0, 2.0)))
    }

    @Test
    fun legsRoundTrip() {
        assertEquals(listOf(1, 12, 3), AppPreferences.decodeLegs(AppPreferences.encodeLegs(listOf(1, 12, 3))))
        assertEquals(AppPreferences.decodeLegs(""), emptyList<Int>())
    }
}
