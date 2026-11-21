package com.lemarc.fake_gps.data.geocoding

import com.lemarc.fake_gps.model.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class GeocodingClientTest {

    /** Réponse réelle de l'IGN (index des adresses) pour « 8 bd du port amiens ». */
    private val ign = """
        {"type":"FeatureCollection","features":[
          {"type":"Feature","geometry":{"type":"Point","coordinates":[2.29008,49.89744]},
           "properties":{"label":"8 Boulevard du Port 80000 Amiens","score":0.60,
           "name":"8 Boulevard du Port","postcode":"80000","city":"Amiens","_type":"address"}},
          {"type":"Feature","geometry":{"type":"Point","coordinates":[2.30658,49.89526]},
           "properties":{"name":"8 Port d'Amont","postcode":"80000","city":"Amiens","score":0.54,"_type":"address"}}
        ]}
    """.trimIndent()

    /** Réponse réelle de Photon pour « tour eiffel » : le monument, puis un arrêt de bus homonyme. */
    private val photon = """
        {"type":"FeatureCollection","features":[
          {"type":"Feature","properties":{"name":"Tour Eiffel","housenumber":"5","street":"Avenue Anatole France",
           "city":"Paris","country":"France","postcode":"75007","osm_value":"tower"},
           "geometry":{"type":"Point","coordinates":[2.29448,48.85826]}},
          {"type":"Feature","properties":{"name":"Tour Eiffel","street":"Quai Jacques Chirac","city":"Paris",
           "country":"France","osm_value":"bus_stop"},
           "geometry":{"type":"Point","coordinates":[2.29380,48.85900]}},
          {"type":"Feature","properties":{"housenumber":"8","street":"Boulevard du Port","city":"Amiens","country":"France"},
           "geometry":{"type":"Point","coordinates":[2.29010,49.89745]}}
        ]}
    """.trimIndent()

    private val eiffel = LatLon(48.85826, 2.29448)

    @Test
    fun `ign reads addresses with their score, longitude first`() {
        val places = GeocodingClient.parseIgn(ign)
        assertEquals(
            ScoredPlace(Place("8 Boulevard du Port", "80000 Amiens", LatLon(49.89744, 2.29008), "Amiens", "France"), 0.60),
            places[0],
        )
        assertEquals(2, places.size)
    }

    @Test
    fun `photon names places, or builds the name from the street`() {
        val places = GeocodingClient.parsePhoton(photon)
        assertEquals(Place("Tour Eiffel", "75007 Paris, France", eiffel, "Paris", "France"), places[0])
        assertEquals("8 Boulevard du Port", places[2].name)
        assertEquals("Amiens, France", places[2].detail)
    }

    @Test
    fun `merge puts strong ign addresses first, weak ones after photon`() {
        val strong = ScoredPlace(Place("Rue de Rivoli", "75001 Paris", LatLon(48.86, 2.34)), 0.97)
        val weak = ScoredPlace(Place("Rue de la Tour Eiffel", "59390 Lys-lez-Lannoy", LatLon(50.66, 3.21)), 0.63)
        val junk = ScoredPlace(Place("Rue Jack London", "92230 Gennevilliers", LatLon(48.93, 2.29)), 0.34)
        val monument = Place("Tour Eiffel", "75007 Paris, France", eiffel)

        val merged = GeocodingClient.merge(listOf(weak, strong, junk), listOf(monument))

        assertEquals(listOf(strong.place, monument, weak.place), merged)
    }

    @Test
    fun `merge keeps one entry for the same place found twice`() {
        val ignPlace = ScoredPlace(Place("8 Boulevard du Port", "80000 Amiens", LatLon(49.89744, 2.29008)), 0.9)
        val merged = GeocodingClient.merge(listOf(ignPlace), GeocodingClient.parsePhoton(photon))
        assertEquals(1, merged.count { it.name == "8 Boulevard du Port" })
        assertEquals("80000 Amiens", merged[0].detail)
        // Même nom mais 100 m plus loin et autre objet (arrêt de bus) : doublon aussi.
        assertEquals(1, merged.count { it.name == "Tour Eiffel" })
    }

    @Test
    fun `merge drops a city returned several times`() {
        // Photon pour « amiens » : la ville, ses limites (à 7 km), la gare homonyme, puis deux fois Amiens (Australie).
        val photon = GeocodingClient.parsePhoton(
            """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","properties":{"osm_key":"place","name":"Amiens","postcode":"80000","country":"France"},
               "geometry":{"type":"Point","coordinates":[2.29569,49.89417]}},
              {"type":"Feature","properties":{"osm_key":"boundary","name":"Amiens","country":"France"},
               "geometry":{"type":"Point","coordinates":[2.25181,49.96107]}},
              {"type":"Feature","properties":{"osm_key":"railway","name":"Amiens","city":"Amiens","postcode":"80000","country":"France"},
               "geometry":{"type":"Point","coordinates":[2.30819,49.89055]}},
              {"type":"Feature","properties":{"osm_key":"boundary","name":"Amiens","country":"Australie"},
               "geometry":{"type":"Point","coordinates":[151.81731,-28.59934]}},
              {"type":"Feature","properties":{"osm_key":"place","name":"Amiens","country":"Australie"},
               "geometry":{"type":"Point","coordinates":[151.81218,-28.59397]}}
            ]}
            """.trimIndent(),
        )
        val mairie = ScoredPlace(Place("Amiens", "80000 Amiens", LatLon(49.8942, 2.2957), "Amiens", "France"), 0.96)

        val merged = GeocodingClient.merge(listOf(mairie), photon)

        assertEquals(listOf("80000 Amiens", "Australie"), merged.map { it.detail })
    }

    @Test
    fun `ign gets the bias point only in metropolitan france`() {
        val url = GeocodingClient.ignUrl(" 8 bd du Port, Amiens ", LatLon(49.9, 2.3))
        assertTrue(url, url.contains("q=8+bd+du+Port%2C+Amiens&"))
        assertTrue(url, url.endsWith("&lat=49.90000&lon=2.30000"))
        assertFalse(GeocodingClient.ignUrl("x", LatLon(40.71, -74.0)).contains("lat="))
        assertTrue(GeocodingClient.photonUrl("x", LatLon(40.71, -74.0)).contains("&lat=40.71000"))
    }

    @Test(expected = IOException::class)
    fun `unreadable response is an io error`() {
        GeocodingClient.parsePhoton("<html>")
    }
}
