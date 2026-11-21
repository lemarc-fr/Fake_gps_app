package com.lemarc.fake_gps.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Calculs géographiques sur la sphère terrestre (formules de haversine). */
object Geo {

    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Distance orthodromique entre [a] et [b], en mètres. */
    fun distance(a: LatLon, b: LatLon): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(h), sqrt(1 - h))
    }

    /** Cap initial de [a] vers [b], en degrés (0 = nord, sens horaire). */
    fun bearing(a: LatLon, b: LatLon): Float {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
    }

    /** Longueur totale de la polyligne [points], en mètres. */
    fun length(points: List<LatLon>): Double =
        points.zipWithNext { a, b -> distance(a, b) }.sum()
}
