package com.lemarc.fake_gps.data.gpx

import com.lemarc.fake_gps.model.LatLon
import org.junit.Assert.assertEquals
import org.junit.Test

class GpxParserTest {

    private fun parse(xml: String) = GpxParser.parse(xml.byteInputStream())

    @Test
    fun readsTrackPoints() {
        val points = parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1">
              <wpt lat="1.0" lon="1.0"/>
              <trk><trkseg>
                <trkpt lat="48.8584" lon="2.2945"><ele>35</ele></trkpt>
                <trkpt lat="48.8584" lon="2.2945"/>
                <trkpt lat="48.8606" lon="2.3376"/>
              </trkseg></trk>
            </gpx>
            """.trimIndent(),
        )
        assertEquals(listOf(LatLon(48.8584, 2.2945), LatLon(48.8606, 2.3376)), points)
    }

    @Test
    fun fallsBackToRoutePoints() {
        val points = parse(
            """<gpx><rte><rtept lat="45.0" lon="5.0"/><rtept lat="45.1" lon="5.1"/></rte></gpx>""",
        )
        assertEquals(2, points.size)
    }

    @Test(expected = GpxParser.GpxException::class)
    fun failsWhenEmpty() {
        parse("<gpx></gpx>")
    }

    @Test(expected = GpxParser.GpxException::class)
    fun failsOnInvalidXml() {
        parse("pas du xml")
    }
}
