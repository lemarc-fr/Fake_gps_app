package io.github.marcbrunet92.fakegps

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

data class RoutePosition(
    val point: LatLon,
    /** Cap en degrés (0 = nord, sens horaire). */
    val bearing: Float,
    /** true lorsque la fin du trajet est atteinte (sans boucle). */
    val finished: Boolean,
)

/**
 * Fait avancer une position le long d'une polyligne à vitesse donnée.
 * Logique pure (sans dépendance Android) pour pouvoir être testée unitairement.
 */
class RouteEngine(points: List<LatLon>, private val loop: Boolean) {

    private val route: List<LatLon> = if (loop && points.size > 2 && points.first() != points.last()) {
        points + points.first()
    } else {
        points
    }

    private val cumulative: DoubleArray = DoubleArray(route.size).also { acc ->
        for (i in 1 until route.size) acc[i] = acc[i - 1] + distance(route[i - 1], route[i])
    }

    val totalLength: Double get() = cumulative.lastOrNull() ?: 0.0

    /** Distance parcourue depuis le début, en mètres. */
    var travelled: Double = 0.0
        private set

    private var forward = true

    init {
        require(points.isNotEmpty()) { "Le trajet doit contenir au moins un point" }
    }

    /** Avance de [meters] et renvoie la nouvelle position. */
    fun advance(meters: Double): RoutePosition {
        val total = totalLength
        if (total <= 0.0) return RoutePosition(route.first(), 0f, finished = !loop)

        var finished = false
        if (forward) travelled += meters else travelled -= meters

        if (loop) {
            if (route.size == 2) {
                // Aller-retour entre deux points.
                while (travelled > total || travelled < 0) {
                    if (travelled > total) {
                        travelled = 2 * total - travelled; forward = false
                    } else {
                        travelled = -travelled; forward = true
                    }
                }
            } else {
                travelled %= total
                if (travelled < 0) travelled += total
            }
        } else if (travelled >= total) {
            travelled = total
            finished = true
        }

        return positionAt(travelled, finished)
    }

    fun current(): RoutePosition = positionAt(travelled, finished = !loop && travelled >= totalLength)

    private fun positionAt(d: Double, finished: Boolean): RoutePosition {
        if (route.size == 1) return RoutePosition(route[0], 0f, finished)
        var i = 1
        while (i < route.size - 1 && cumulative[i] < d) i++
        val a = route[i - 1]
        val b = route[i]
        val segLen = cumulative[i] - cumulative[i - 1]
        val t = if (segLen > 0) ((d - cumulative[i - 1]) / segLen).coerceIn(0.0, 1.0) else 0.0
        val p = LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
        var brg = bearing(a, b)
        if (!forward) brg = (brg + 180f) % 360f
        return RoutePosition(p, brg, finished)
    }

    companion object {
        private const val EARTH_RADIUS = 6_371_000.0

        fun distance(a: LatLon, b: LatLon): Double {
            val dLat = Math.toRadians(b.lat - a.lat)
            val dLon = Math.toRadians(b.lon - a.lon)
            val h = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
            return 2 * EARTH_RADIUS * atan2(sqrt(h), sqrt(1 - h))
        }

        fun bearing(a: LatLon, b: LatLon): Float {
            val lat1 = Math.toRadians(a.lat)
            val lat2 = Math.toRadians(b.lat)
            val dLon = Math.toRadians(b.lon - a.lon)
            val y = sin(dLon) * cos(lat2)
            val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
            return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
        }
    }
}
