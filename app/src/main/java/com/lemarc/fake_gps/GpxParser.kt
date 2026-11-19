package com.lemarc.fake_gps

import org.w3c.dom.Element
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Lecture d'un fichier GPX : utilise les points de trace (trkpt) en priorité,
 * puis les points de route (rtept), puis les points de passage (wpt).
 */
object GpxParser {

    class GpxException(message: String, cause: Throwable? = null) : Exception(message, cause)

    fun parse(input: InputStream): List<LatLon> {
        val doc = try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                isExpandEntityReferences = false
                trySetFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                trySetFeature("http://xml.org/sax/features/external-general-entities", false)
                trySetFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }
            factory.newDocumentBuilder().parse(input)
        } catch (e: Exception) {
            throw GpxException("fichier GPX illisible", e)
        }

        for (tag in listOf("trkpt", "rtept", "wpt")) {
            val nodes = doc.getElementsByTagName(tag)
            val points = (0 until nodes.length).mapNotNull { i ->
                val el = nodes.item(i) as? Element ?: return@mapNotNull null
                val lat = el.getAttribute("lat").toDoubleOrNull()
                val lon = el.getAttribute("lon").toDoubleOrNull()
                if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                    LatLon(lat, lon)
                } else null
            }
            if (points.isNotEmpty()) return dedupe(points)
        }
        throw GpxException("aucun point trouvé dans le fichier")
    }

    /** Supprime les points consécutifs identiques. */
    private fun dedupe(points: List<LatLon>): List<LatLon> =
        points.filterIndexed { i, p -> i == 0 || p != points[i - 1] }

    private fun DocumentBuilderFactory.trySetFeature(name: String, value: Boolean) {
        try {
            setFeature(name, value)
        } catch (_: Exception) {
            // Fonctionnalité non prise en charge par l'analyseur de la plateforme.
        }
    }
}
