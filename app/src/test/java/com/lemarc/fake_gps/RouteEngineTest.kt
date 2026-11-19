package com.lemarc.fake_gps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteEngineTest {

    private val a = LatLon(48.0, 2.0)
    private val b = LatLon(48.0, 2.01)
    private val c = LatLon(48.01, 2.01)

    @Test
    fun distanceIsRoughlyCorrect() {
        // 0,01° de longitude à 48° N ≈ 744 m.
        assertEquals(744.0, RouteEngine.distance(a, b), 5.0)
    }

    @Test
    fun bearingEastIs90() {
        assertEquals(90f, RouteEngine.bearing(a, b), 0.5f)
    }

    @Test
    fun advanceInterpolatesAlongSegment() {
        val engine = RouteEngine(listOf(a, b), loop = false)
        val half = engine.totalLength / 2
        val pos = engine.advance(half)
        assertEquals(2.005, pos.point.lon, 1e-6)
        assertFalse(pos.finished)
    }

    @Test
    fun stopsAtEndWithoutLoop() {
        val engine = RouteEngine(listOf(a, b, c), loop = false)
        val pos = engine.advance(engine.totalLength + 1000)
        assertTrue(pos.finished)
        assertEquals(c, pos.point)
    }

    @Test
    fun twoPointLoopGoesBackAndForth() {
        val engine = RouteEngine(listOf(a, b), loop = true)
        val total = engine.totalLength
        val pos = engine.advance(total * 1.25)
        assertFalse(pos.finished)
        assertEquals(total * 0.75, engine.travelled, 1e-6)
        assertEquals(270f, pos.bearing, 0.5f)
    }

    @Test
    fun multiPointLoopWrapsAround() {
        val engine = RouteEngine(listOf(a, b, c), loop = true)
        val total = engine.totalLength
        engine.advance(total + 10)
        assertEquals(10.0, engine.travelled, 1e-6)
    }
}
