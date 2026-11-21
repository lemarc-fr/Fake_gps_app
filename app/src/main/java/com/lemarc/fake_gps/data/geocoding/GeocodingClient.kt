package com.lemarc.fake_gps.data.geocoding

import com.lemarc.fake_gps.model.Geo
import com.lemarc.fake_gps.model.LatLon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Résultat de recherche : [name] en titre, [detail] (code postal, ville, pays) en sous-titre.
 * [city] et [country] servent à reconnaître un même endroit renvoyé sous plusieurs formes.
 */
data class Place(
    val name: String,
    val detail: String,
    val position: LatLon,
    val city: String = "",
    val country: String = "",
)

/** Adresse IGN avec son score de correspondance (0 à 1). */
data class ScoredPlace(val place: Place, val score: Double)

/**
 * Recherche d'adresses et de lieux, gratuite et sans clé, en interrogeant deux services en parallèle :
 * - Photon (OpenStreetMap, monde entier) : lieux connus, gares, adresses à l'étranger ;
 * - l'IGN (Base Adresse Nationale) : adresses françaises au numéro près.
 *
 * Seul l'index des adresses de l'IGN est utilisé : ses « lieux » mélangent les monuments et
 * des lieux-dits homonymes, et le point de référence écrase leur score.
 */
object GeocodingClient {

    private const val IGN_URL = "https://data.geopf.fr/geocodage/search"
    private const val PHOTON_URL = "https://photon.komoot.io/api/"
    private const val USER_AGENT = "FakeGps-Android/1.0"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 10_000
    private const val LIMIT_PER_SERVICE = 6
    private const val MAX_RESULTS = 8

    /** Adresse IGN assez sûre pour passer avant les résultats de Photon. */
    private const val IGN_STRONG_SCORE = 0.8

    /** En dessous, l'adresse IGN ne ressemble plus à la saisie. */
    private const val IGN_MIN_SCORE = 0.5

    /**
     * Deux résultats de même nom à moins de cette distance désignent le même endroit : une ville
     * revient sous plusieurs formes (mairie, centre, limites administratives) à quelques centaines de mètres.
     */
    private const val SAME_PLACE_METERS = 1_000.0

    /** Les adresses IGN sont toutes en France ; Photon renvoie le nom du pays dans la langue demandée (fr). */
    private const val FRANCE = "France"

    /** Objets OpenStreetMap qui sont eux-mêmes une localité (ville, village, limites administratives). */
    private val AREA_KEYS = setOf("place", "boundary")

    /**
     * Cherche [query]. [near] (centre de la carte) fait remonter les résultats proches.
     * Lève [IOException] seulement si les deux services échouent.
     */
    suspend fun search(query: String, near: LatLon?): List<Place> = coroutineScope {
        val ign = async(Dispatchers.IO) { runCatching { parseIgn(get(ignUrl(query, near))) } }
        val photon = async(Dispatchers.IO) { runCatching { parsePhoton(get(photonUrl(query, near))) } }
        val ignResult = ign.await()
        val photonResult = photon.await()
        photonResult.exceptionOrNull()?.let { if (ignResult.isFailure) throw it }
        merge(ignResult.getOrDefault(emptyList()), photonResult.getOrDefault(emptyList()))
    }

    /**
     * Ordre affiché : adresses IGN sûres, puis Photon, puis adresses IGN moins sûres ;
     * un même endroit trouvé par les deux services n'apparaît qu'une fois.
     */
    fun merge(ign: List<ScoredPlace>, photon: List<Place>): List<Place> {
        val strong = ign.filter { it.score >= IGN_STRONG_SCORE }.map { it.place }
        val weak = ign.filter { it.score >= IGN_MIN_SCORE && it.score < IGN_STRONG_SCORE }.map { it.place }
        val merged = ArrayList<Place>()
        val seen = ArrayList<Place>()
        for (place in strong + photon + weak) {
            // Comparé à tout ce qui précède, même écarté : les doublons s'enchaînent de proche en proche.
            if (seen.none { it.isSameAs(place) }) merged += place
            seen += place
        }
        return merged.take(MAX_RESULTS)
    }

    /** Le point de référence n'est envoyé à l'IGN que si la carte montre la France métropolitaine. */
    fun ignUrl(query: String, near: LatLon?): String =
        "$IGN_URL?q=${encode(query)}&index=address&limit=$LIMIT_PER_SERVICE" +
            near?.takeIf { it.isInMetropolitanFrance() }.biasParams()

    fun photonUrl(query: String, near: LatLon?): String =
        "$PHOTON_URL?q=${encode(query)}&limit=$LIMIT_PER_SERVICE&lang=fr" + near.biasParams()

    /** Adresses de la Base Adresse Nationale, avec leur score. */
    fun parseIgn(body: String): List<ScoredPlace> {
        val features = features(body)
        return (0 until features.length()).mapNotNull { i ->
            val feature = features.getJSONObject(i)
            val p = feature.getJSONObject("properties")
            val position = feature.position() ?: return@mapNotNull null
            val name = p.optString("name").ifBlank { return@mapNotNull null }
            val city = p.optString("city")
            ScoredPlace(
                Place(name, joinWords(p.optString("postcode"), city), position, city, FRANCE),
                p.optDouble("score", 0.0),
            )
        }
    }

    /** Résultats Photon (OpenStreetMap). */
    fun parsePhoton(body: String): List<Place> {
        val features = features(body)
        return (0 until features.length()).mapNotNull { i ->
            val feature = features.getJSONObject(i)
            val p = feature.getJSONObject("properties")
            val position = feature.position() ?: return@mapNotNull null
            val street = joinWords(p.optString("housenumber"), p.optString("street"))
            val name = p.optString("name").ifEmpty { street }.ifBlank { return@mapNotNull null }
            // Une ville (ou ses limites administratives) n'a pas de champ « city » : c'est elle-même.
            val isArea = p.optString("osm_key") in AREA_KEYS
            val city = p.optString("city").ifEmpty { if (isArea) name else "" }
            val country = p.optString("country")
            val shownCity = city.takeIf { it != name }.orEmpty()
            val detail = listOf(joinWords(p.optString("postcode"), shownCity), country)
                .filter { it.isNotBlank() }
                .joinToString(", ")
            Place(name, detail, position, city, country)
        }
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun features(body: String): JSONArray = try {
        JSONObject(body).getJSONArray("features")
    } catch (e: Exception) {
        throw IOException("Réponse de géocodage illisible", e)
    }

    /** Point GeoJSON : coordonnées dans l'ordre longitude, latitude. */
    private fun JSONObject.position(): LatLon? {
        val coords = optJSONObject("geometry")?.optJSONArray("coordinates") ?: return null
        if (coords.length() < 2) return null
        return LatLon(lat = coords.getDouble(1), lon = coords.getDouble(0))
    }

    /**
     * Même nom, et même ville du même pays ou tout proche. Une ville revient sous plusieurs formes
     * (centre, limites administratives, gare homonyme) parfois à plusieurs kilomètres les unes des autres.
     */
    private fun Place.isSameAs(other: Place): Boolean {
        if (!name.equals(other.name, ignoreCase = true)) return false
        val sameCity = city.isNotEmpty() && city.equals(other.city, ignoreCase = true) &&
            country.equals(other.country, ignoreCase = true)
        return sameCity || detail == other.detail || Geo.distance(position, other.position) < SAME_PLACE_METERS
    }

    private fun LatLon.isInMetropolitanFrance() = lat in 41.0..51.5 && lon in -5.5..10.0

    private fun joinWords(vararg parts: String) = parts.filter { it.isNotBlank() }.joinToString(" ")

    private fun LatLon?.biasParams(): String =
        if (this == null) "" else String.format(Locale.US, "&lat=%.5f&lon=%.5f", lat, lon)

    private fun encode(text: String): String = URLEncoder.encode(text.trim(), "UTF-8")
}
