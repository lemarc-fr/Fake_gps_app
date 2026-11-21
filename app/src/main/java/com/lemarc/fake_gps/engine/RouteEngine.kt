package com.lemarc.fake_gps.engine

import com.lemarc.fake_gps.model.Geo
import com.lemarc.fake_gps.model.LatLon

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
        for (i in 1 until route.size) acc[i] = acc[i - 1] + Geo.distance(route[i - 1], route[i])
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
                while (travelled !in 0.0..total) {
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
        var brg = Geo.bearing(a, b)
        if (!forward) brg = (brg + 180f) % 360f
        return RoutePosition(p, brg, finished)
    }
}
