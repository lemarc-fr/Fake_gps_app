package com.lemarc.fake_gps.data.routing

import com.lemarc.fake_gps.model.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OsrmClientTest {

    @Test
    fun decodesGoogleExamplePolyline() {
        // Exemple de la documentation Google (précision 5).
        val points = OsrmClient.decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@", precision = 5)
        assertEquals(3, points.size)
        assertEquals(38.5, points[0].lat, 1e-6)
        assertEquals(-120.2, points[0].lon, 1e-6)
        assertEquals(40.7, points[1].lat, 1e-6)
        assertEquals(-120.95, points[1].lon, 1e-6)
        assertEquals(43.252, points[2].lat, 1e-6)
        assertEquals(-126.453, points[2].lon, 1e-6)
    }

    @Test
    fun buildsUrlWithLonLatOrder() {
        val url = OsrmClient.buildUrl(
            listOf(LatLon(48.8584, 2.2945), LatLon(48.8606, 2.3376)),
            RoutingProfile.FOOT,
        )
        assertEquals(
            "https://routing.openstreetmap.de/routed-foot/route/v1/driving/" +
                "2.294500,48.858400;2.337600,48.860600?overview=full&geometries=polyline6&steps=false",
            url,
        )
    }

    @Test
    fun parsesOkResponse() {
        val route = OsrmClient.parseResponse(
            """{"code":"Ok","routes":[{"geometry":"_izlhA~rlgdF_{geC~ywl@","distance":1234.5,"duration":60}]}""",
        )
        assertEquals(2, route.points.size)
        assertEquals(38.5, route.points[0].lat, 1e-6)
        assertEquals(-120.2, route.points[0].lon, 1e-6)
        assertEquals(1234.5, route.distanceMeters, 1e-9)
    }

    @Test
    fun reportsNoRoute() {
        val e = assertThrows(OsrmClient.RoutingException::class.java) {
            OsrmClient.parseResponse("""{"code":"NoRoute","message":"Impossible route"}""")
        }
        assertEquals(OsrmClient.RoutingException.Reason.NO_ROUTE, e.reason)
    }
}
