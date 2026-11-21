package com.lemarc.fake_gps.data.gpx

import com.lemarc.fake_gps.model.LatLon
import java.io.OutputStream
import java.util.Locale

/** Écrit un trajet au format GPX 1.1 (une trace, un segment). */
object GpxWriter {

    fun write(name: String, points: List<LatLon>, out: OutputStream) {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"Fake GPS\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        sb.append("  <metadata><name>").append(escape(name)).append("</name></metadata>\n")
        sb.append("  <trk>\n    <name>").append(escape(name)).append("</name>\n    <trkseg>\n")
        for (p in points) {
            sb.append(String.format(Locale.US, "      <trkpt lat=\"%.7f\" lon=\"%.7f\"/>\n", p.lat, p.lon))
        }
        sb.append("    </trkseg>\n  </trk>\n</gpx>\n")
        out.write(sb.toString().toByteArray(Charsets.UTF_8))
    }

    private fun escape(s: String) = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
