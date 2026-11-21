package com.lemarc.fake_gps.map

import com.lemarc.fake_gps.model.LatLon
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteGeoJsonTest {

    private val paris = LatLon(48.8584, 2.2945)
    private val lyon = LatLon(45.764, 4.8357)

    @Test
    fun `line needs two points`() {
        val json = JSONObject(RouteGeoJson.line(listOf(paris)))
        assertEquals("FeatureCollection", json.getString("type"))
        assertEquals(0, json.getJSONArray("features").length())
    }

    @Test
    fun `line lists longitude before latitude`() {
        val geometry = JSONObject(RouteGeoJson.line(listOf(paris, lyon))).getJSONObject("geometry")
        assertEquals("LineString", geometry.getString("type"))
        val first = geometry.getJSONArray("coordinates").getJSONArray(0)
        assertEquals(paris.lon, first.getDouble(0), 0.0)
        assertEquals(paris.lat, first.getDouble(1), 0.0)
    }

    @Test
    fun `points makes one feature per position`() {
        val features = JSONObject(RouteGeoJson.points(listOf(paris, lyon))).getJSONArray("features")
        assertEquals(2, features.length())
        val coords = features.getJSONObject(1).getJSONObject("geometry").getJSONArray("coordinates")
        assertEquals(lyon.lon, coords.getDouble(0), 0.0)
        assertEquals(0, JSONObject(RouteGeoJson.points(emptyList())).getJSONArray("features").length())
    }
}
