package com.lemarc.fake_gps.map

import com.lemarc.fake_gps.model.LatLon

/** Sérialise le trajet en GeoJSON (ordre des coordonnées : longitude, latitude) pour MapLibre. */
object RouteGeoJson {

    private const val EMPTY = """{"type":"FeatureCollection","features":[]}"""

    /** Ligne du trajet ; collection vide tant qu'il y a moins de deux points. */
    fun line(points: List<LatLon>): String {
        if (points.size < 2) return EMPTY
        return feature("""{"type":"LineString","coordinates":[${points.joinToString(",") { it.position() }}]}""")
    }

    /** Un point GeoJSON par position. */
    fun points(points: List<LatLon>): String =
        """{"type":"FeatureCollection","features":[${points.joinToString(",") { point(it) }}]}"""

    private fun point(p: LatLon) = feature("""{"type":"Point","coordinates":${p.position()}}""")

    private fun feature(geometry: String) = """{"type":"Feature","properties":{},"geometry":$geometry}"""

    private fun LatLon.position() = "[$lon,$lat]"
}
