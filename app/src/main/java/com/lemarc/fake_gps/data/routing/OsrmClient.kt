package com.lemarc.fake_gps.data.routing

import com.lemarc.fake_gps.model.LatLon
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class OsrmRoute(val points: List<LatLon>, val distanceMeters: Double, val durationSeconds: Double)

/**
 * Client du serveur OSRM public de FOSSGIS (routing.openstreetmap.de), gratuit et sans clé,
 * qui propose les profils voiture, vélo et piéton.
 */
object OsrmClient {

    private const val BASE_URL = "https://routing.openstreetmap.de"
    private const val USER_AGENT = "FakeGps-Android/1.0"

    /**
     * Échec du calcul d'itinéraire. Le texte affiché est choisi par l'interface selon [reason] ;
     * [serverMessage] contient le message brut du serveur pour [Reason.SERVER_ERROR].
     */
    class RoutingException(
        val reason: Reason,
        val serverMessage: String? = null,
    ) : IOException(serverMessage ?: reason.name) {
        enum class Reason {
            /** HTTP 429 : trop de requêtes. */
            SERVER_BUSY,
            /** La réponse n'est pas du JSON exploitable. */
            INVALID_RESPONSE,
            /** Aucun itinéraire entre les points. */
            NO_ROUTE,
            /** Un point est trop loin de toute route. */
            NO_SEGMENT,
            /** Géométrie encodée incomplète. */
            TRUNCATED_GEOMETRY,
            /** Autre erreur renvoyée par le serveur (voir [serverMessage]). */
            SERVER_ERROR,
        }
    }

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val HTTP_TOO_MANY_REQUESTS = 429

    /** Calcule l'itinéraire passant par [points] (au moins 2). Appel réseau bloquant. */
    fun route(points: List<LatLon>, profile: RoutingProfile): OsrmRoute {
        val url = buildUrl(points, profile)
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code == HTTP_TOO_MANY_REQUESTS) throw RoutingException(RoutingException.Reason.SERVER_BUSY)
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
            throw RoutingException(RoutingException.Reason.INVALID_RESPONSE, "HTTP $httpCode")
        }
        val code = json.optString("code")
        if (code != "Ok") {
            throw when (code) {
                "NoRoute" -> RoutingException(RoutingException.Reason.NO_ROUTE)
                "NoSegment" -> RoutingException(RoutingException.Reason.NO_SEGMENT)
                else -> RoutingException(
                    RoutingException.Reason.SERVER_ERROR,
                    json.optString("message").ifEmpty { "$code (HTTP $httpCode)" },
                )
            }
        }
        val routes = json.getJSONArray("routes")
        if (routes.length() == 0) throw RoutingException(RoutingException.Reason.NO_ROUTE)
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
                    if (index >= encoded.length) throw RoutingException(RoutingException.Reason.TRUNCATED_GEOMETRY)
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
