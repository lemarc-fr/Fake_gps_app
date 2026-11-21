package com.lemarc.fake_gps.ui

import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import com.lemarc.fake_gps.R
import com.lemarc.fake_gps.map.toGeoPoint
import com.lemarc.fake_gps.model.EditableRoute
import com.lemarc.fake_gps.model.LatLon
import com.lemarc.fake_gps.ui.theme.RouteLine
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/** Dessine sur la carte osmdroid le trajet, les points touchés et la position simulée. */
class RouteMapRenderer(private val map: MapView) {

    private val context = map.context

    private val routeLine = Polyline(map).apply {
        outlinePaint.color = RouteLine.toArgb()
        outlinePaint.strokeWidth = context.resources.getDimension(R.dimen.route_line_width)
    }

    private val currentMarker = Marker(map).apply {
        title = context.getString(R.string.my_position)
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        icon = ContextCompat.getDrawable(context, R.drawable.ic_current_position)
    }

    private val waypointMarkers = mutableListOf<Marker>()
    private var shownRoute: EditableRoute? = null

    init {
        map.overlays.add(routeLine)
    }

    fun showRoute(route: EditableRoute) {
        if (route == shownRoute) return
        shownRoute = route

        map.overlays.removeAll(waypointMarkers)
        waypointMarkers.clear()
        // Marqueurs sur les points touchés, pas sur chaque point du tracé routier ; au-delà de
        // MAX_MARKERS (GPX importé), seulement le départ et l'arrivée.
        val anchors = route.anchorIndices
        val shown = if (anchors.size > MAX_MARKERS) listOf(0, route.points.lastIndex).distinct() else anchors
        shown.forEachIndexed { n, i ->
            waypointMarkers += Marker(map).apply {
                position = route.points[i].toGeoPoint()
                title = context.getString(R.string.waypoint_title, n + 1)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
        }
        map.overlays.addAll(waypointMarkers)
        routeLine.setPoints(route.points.map { it.toGeoPoint() })
        map.invalidate()
    }

    /** Affiche le marqueur de position simulée, ou le retire si [position] est null. */
    fun showCurrentPosition(position: LatLon?) {
        if (position != null) {
            currentMarker.position = position.toGeoPoint()
            if (!map.overlays.contains(currentMarker)) map.overlays.add(currentMarker)
        } else {
            map.overlays.remove(currentMarker)
        }
        map.invalidate()
    }

    fun zoomTo(points: List<LatLon>) {
        when (points.size) {
            0 -> Unit
            1 -> map.controller.animateTo(points[0].toGeoPoint())
            else -> map.post {
                val box = BoundingBox.fromGeoPointsSafe(points.map { it.toGeoPoint() })
                val padding = context.resources.getDimensionPixelSize(R.dimen.route_zoom_padding)
                map.zoomToBoundingBox(box, true, padding)
            }
        }
    }

    private companion object {
        const val MAX_MARKERS = 50
    }
}
