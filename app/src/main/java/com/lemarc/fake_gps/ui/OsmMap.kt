package com.lemarc.fake_gps.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lemarc.fake_gps.data.AppPreferences
import com.lemarc.fake_gps.map.MapStyle
import com.lemarc.fake_gps.map.toGeoPoint
import com.lemarc.fake_gps.map.toLatLon
import com.lemarc.fake_gps.model.EditableRoute
import com.lemarc.fake_gps.model.LatLon
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay

/** Permet à l'écran de piloter la carte (recadrage) en dehors de la composition. */
class OsmMapHandle {
    internal var renderer: RouteMapRenderer? = null

    fun zoomTo(points: List<LatLon>) {
        renderer?.zoomTo(points)
    }
}

/**
 * Carte osmdroid (vue Android classique) intégrée à Compose.
 *
 * @param initialCamera position de départ, lue une seule fois à la création de la carte.
 * @param onPause appelé à chaque mise en pause avec la caméra courante, pour la mémoriser.
 */
@Composable
fun OsmMap(
    route: EditableRoute,
    mapStyle: MapStyle,
    currentPosition: LatLon?,
    initialCamera: AppPreferences.Camera,
    handle: OsmMapHandle,
    onTap: (LatLon) -> Unit,
    onPause: (AppPreferences.Camera) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnPause by rememberUpdatedState(onPause)

    val mapView = remember {
        MapView(context).apply {
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                    currentOnTap(p.toLatLon())
                    return true
                }

                override fun longPressHelper(p: GeoPoint): Boolean = false
            }))
            controller.setZoom(initialCamera.zoom)
            controller.setCenter(initialCamera.center.toGeoPoint())
        }
    }
    val renderer = remember(mapView) { RouteMapRenderer(mapView) }

    // osmdroid doit suivre le cycle de vie de l'écran (chargement des tuiles, capteurs).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mapView) {
        handle.renderer = renderer
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> {
                    currentOnPause(AppPreferences.Camera(mapView.mapCenter.toLatLon(), mapView.zoomLevelDouble))
                    mapView.onPause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            handle.renderer = null
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map ->
            if (map.tileProvider.tileSource !== mapStyle.tileSource) map.setTileSource(mapStyle.tileSource)
            renderer.showRoute(route)
            renderer.showCurrentPosition(currentPosition)
        },
    )
}
