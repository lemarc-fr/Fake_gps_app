package com.lemarc.fake_gps.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.lemarc.fake_gps.data.routing.RoutingProfile
import com.lemarc.fake_gps.map.MapStyle
import com.lemarc.fake_gps.model.EditableRoute
import com.lemarc.fake_gps.model.LatLon

/**
 * Réglages et état mémorisés entre deux lancements (trajet, vitesse, carte…).
 * Seul endroit qui connaît les clés et le format de stockage dans les SharedPreferences.
 */
class AppPreferences(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE))

    /** Position de la caméra de la carte. */
    data class Camera(val center: LatLon, val zoom: Double)

    var route: EditableRoute
        get() = EditableRoute.of(
            decodePoints(prefs.getString(KEY_POINTS, null)),
            prefs.getString(KEY_LEGS, null)?.let(::decodeLegs),
        )
        set(value) = prefs.edit {
            putString(KEY_POINTS, encodePoints(value.points))
            putString(KEY_LEGS, encodeLegs(value.legSizes))
        }

    /** Expression de vitesse v(t) (dernière valeur valide saisie). */
    var speedExpression: String
        get() = prefs.getString(KEY_SPEED_EXPR, null) ?: legacySpeed() ?: DEFAULT_SPEED
        set(value) = prefs.edit { putString(KEY_SPEED_EXPR, value) }

    var loop: Boolean
        get() = prefs.getBoolean(KEY_LOOP, false)
        set(value) = prefs.edit { putBoolean(KEY_LOOP, value) }

    var routingProfile: RoutingProfile
        get() = RoutingProfile.fromName(prefs.getString(KEY_PROFILE, null))
        set(value) = prefs.edit { putString(KEY_PROFILE, value.name) }

    var mapStyle: MapStyle
        get() = MapStyle.fromName(prefs.getString(KEY_MAP_STYLE, null))
        set(value) = prefs.edit { putString(KEY_MAP_STYLE, value.name) }

    var camera: Camera
        get() = Camera(
            center = LatLon(
                prefs.getFloat(KEY_LAT, DEFAULT_CENTER.lat.toFloat()).toDouble(),
                prefs.getFloat(KEY_LON, DEFAULT_CENTER.lon.toFloat()).toDouble(),
            ),
            zoom = prefs.getFloat(KEY_ZOOM, DEFAULT_ZOOM.toFloat()).toDouble(),
        )
        set(value) = prefs.edit {
            putFloat(KEY_LAT, value.center.lat.toFloat())
            putFloat(KEY_LON, value.center.lon.toFloat())
            putFloat(KEY_ZOOM, value.zoom.toFloat())
        }

    /** Vitesse enregistrée par les premières versions (nombre entier ou flottant, clé « speed »). */
    private fun legacySpeed(): String? = when (val v = prefs.all[KEY_LEGACY_SPEED]) {
        is Number -> v.toInt().coerceAtLeast(1).toString()
        else -> null
    }

    companion object {
        private const val FILE_NAME = "fakegps"

        const val DEFAULT_SPEED = "5"
        /** Centre affiché au premier lancement (tour Eiffel). */
        val DEFAULT_CENTER = LatLon(48.8584, 2.2945)
        const val DEFAULT_ZOOM = 15.0

        private const val KEY_POINTS = "points"
        private const val KEY_LEGS = "legs"
        private const val KEY_LEGACY_SPEED = "speed"
        private const val KEY_SPEED_EXPR = "speed_expr"
        private const val KEY_LOOP = "loop"
        private const val KEY_PROFILE = "routing_profile"
        private const val KEY_MAP_STYLE = "map_style"
        private const val KEY_LAT = "lat"
        private const val KEY_LON = "lon"
        private const val KEY_ZOOM = "zoom"

        /** Format historique, conservé pour relire les données existantes : « lat,lon;lat,lon ». */
        internal fun encodePoints(points: List<LatLon>): String =
            points.joinToString(";") { "${it.lat},${it.lon}" }

        internal fun decodePoints(raw: String?): List<LatLon> =
            raw.orEmpty().split(";").mapNotNull { entry ->
                val parts = entry.split(",")
                val lat = parts.getOrNull(0)?.toDoubleOrNull()
                val lon = parts.getOrNull(1)?.toDoubleOrNull()
                if (lat != null && lon != null) LatLon(lat, lon) else null
            }

        internal fun encodeLegs(legSizes: List<Int>): String = legSizes.joinToString(",")

        internal fun decodeLegs(raw: String): List<Int> =
            raw.split(",").mapNotNull { it.toIntOrNull() }
    }
}
