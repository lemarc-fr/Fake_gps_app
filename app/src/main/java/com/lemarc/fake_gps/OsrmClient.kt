package com.lemarc.fake_gps

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Mode de tracé : ligne droite, ou itinéraire calculé sur le réseau routier par OSRM. */
enum class RoutingProfile(val osrmPath: String?) {
    STRAIGHT(null),
    CAR("routed-car"),
    BIKE("routed-bike"),
    FOOT("routed-foot");

    companion object {
        fun fromName(name: String?): RoutingProfile = entries.firstOrNull { it.name == name } ?: STRAIGHT
    }
}

data class OsrmRoute(val points: List<LatLon>, val distanceMeters: Double, val durationSeconds: Double)

/**
 * Client du serveur OSRM public de FOSSGIS (routing.openstreetmap.de), gratuit et sans clé,
 * qui propose les profils voiture, vélo et piéton.
 */
object OsrmClient {

    private const val BASE_URL = "https://routing.openstreetmap.de"
    private const val USER_AGENT = "FakeGps-Android/1.0"

    class RoutingException(message: String) : IOException(message)

    /** Calcule l'itinéraire passant par [points] (au moins 2). Appel réseau bloquant. */
    fun route(points: List<LatLon>, profile: RoutingProfile): OsrmRoute {
        val url = buildUrl(points, profile)
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code == 429) throw RoutingException("serveur saturé, réessayez dans un instant")
            return parseResponse(body, code)
        } finally {
            connection.disconnect()
        }
    }

    fun buildUrl(points: List<LatLon>, profile: RoutingProfile): String {
        val path = requireNotNull(profile.osrmPath) { "profil sans routage" }
        require(points.size >= 2) { "au moins deux points" }
        val coords = points.joinToString(";") {
            String.format(Locale.US, "%.6f,%.6f", it.lon, it.lat)
        }
        return "$BASE_URL/$path/route/v1/driving/$coords?overview=full&geometries=polyline6&steps=false"
    }

    fun parseResponse(body: String, httpCode: Int = 200): OsrmRoute {
        val json = try {
            JSONObject(body)
        } catch (e: Exception) {
            throw RoutingException("réponse invalide du serveur (HTTP $httpCode)")
        }
        val code = json.optString("code")
        if (code != "Ok") {
            val message = when (code) {
                "NoRoute" -> "aucun itinéraire trouvé entre ces points"
                "NoSegment" -> "point trop éloigné d'une route"
                else -> json.optString("message").ifEmpty { "erreur $code (HTTP $httpCode)" }
            }
            throw RoutingException(message)
        }
        val routes = json.getJSONArray("routes")
        if (routes.length() == 0) throw RoutingException("aucun itinéraire trouvé entre ces points")
        val route = routes.getJSONObject(0)
        return OsrmRoute(
            points = decodePolyline(route.getString("geometry"), precision = 6),
            distanceMeters = route.optDouble("distance", 0.0),
            durationSeconds = route.optDouble("duration", 0.0),
        )
    }

    /** Décode une polyligne encodée (algorithme Google, précision 5 ou 6). */
    fun decodePolyline(encoded: String, precision: Int = 6): List<LatLon> {
        val factor = Math.pow(10.0, precision.toDouble())
        val result = ArrayList<LatLon>()
        var index = 0
        var lat = 0L
        var lon = 0L
        while (index < encoded.length) {
            for (axis in 0..1) {
                var shift = 0
                var value = 0L
                while (true) {
                    if (index >= encoded.length) throw RoutingException("géométrie tronquée")
                    val b = encoded[index++].code - 63
                    value = value or ((b and 0x1f).toLong() shl shift)
                    shift += 5
                    if (b < 0x20) break
                }
                val delta = if (value and 1L != 0L) (value shr 1).inv() else value shr 1
                if (axis == 0) lat += delta else lon += delta
            }
            result += LatLon(lat / factor, lon / factor)
        }
        return result
    }
}
