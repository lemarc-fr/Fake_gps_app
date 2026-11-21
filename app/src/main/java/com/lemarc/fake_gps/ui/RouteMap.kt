package com.lemarc.fake_gps.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lemarc.fake_gps.data.AppPreferences
import com.lemarc.fake_gps.map.MapStyle
import com.lemarc.fake_gps.map.RouteGeoJson
import com.lemarc.fake_gps.model.EditableRoute
import com.lemarc.fake_gps.model.LatLon
import com.lemarc.fake_gps.ui.theme.CurrentPosition
import com.lemarc.fake_gps.ui.theme.RouteLine
import org.maplibre.compose.camera.CameraFit
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.RasterLayer
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.sources.rememberRasterTileSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.DpPadding
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Position

/** Permet à l'écran de recadrer la carte en dehors de la composition. */
class RouteMapHandle {
    internal var mapState: MapState? = null

    /** Centre la carte sur [points], ou l'ajuste pour les montrer tous. */
    suspend fun zoomTo(points: List<LatLon>) {
        val state = mapState ?: return
        when (points.size) {
            0 -> Unit
            1 -> state.animateCamera(CameraUpdate(center = points[0].toPosition()))
            else -> state.animateCameraToBounds(
                BoundingBox(
                    west = points.minOf { it.lon },
                    south = points.minOf { it.lat },
                    east = points.maxOf { it.lon },
                    north = points.maxOf { it.lat },
                ),
                CameraFit(fitPadding = ROUTE_FIT_PADDING),
            )
        }
    }
}

/**
 * Carte MapLibre : fond raster [mapStyle], trajet, points touchés et position simulée.
 *
 * @param initialCamera position de départ, lue une seule fois à la création de la carte.
 * @param onPause appelé à chaque mise en pause avec la caméra courante, pour la mémoriser.
 */
@Composable
fun RouteMap(
    route: EditableRoute,
    mapStyle: MapStyle,
    currentPosition: LatLon?,
    initialCamera: AppPreferences.Camera,
    handle: RouteMapHandle,
    onTap: (LatLon) -> Unit,
    onPause: (AppPreferences.Camera) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnPause by rememberUpdatedState(onPause)

    // Style vide : toutes les sources et couches sont déclarées ici, dans l'ordre d'affichage.
    val mapState = rememberMapState(
        baseStyle = BaseStyle.Empty,
        initialCameraPosition = remember {
            CameraPosition(center = initialCamera.center.toPosition(), zoom = initialCamera.zoom)
        },
    ) {
        BaseMapLayer(mapStyle)
        RouteLayers(route)
        CurrentPositionLayer(currentPosition)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mapState) {
        handle.mapState = mapState
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                val camera = mapState.cameraPosition
                currentOnPause(AppPreferences.Camera(camera.center.toLatLon(), camera.zoom))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            handle.mapState = null
        }
    }

    val interactions = remember {
        MapInteractions {
            callbacks {
                click {
                    onEvent { event ->
                        event.position?.let { currentOnTap(it.toLatLon()) }
                        ClickResult.Pass
                    }
                }
            }
        }
    }

    MaplibreMap(
        modifier = modifier,
        state = mapState,
        interactions = interactions,
        // L'attribution est affichée par l'écran, hors du panneau de commandes qui couvre le bas.
        overlay = {},
    )
}

@Composable
@MaplibreComposable
private fun BaseMapLayer(style: MapStyle) {
    key(style) {
        val tiles = rememberRasterTileSource(style.tiles, tileSize = MapStyle.TILE_SIZE) {
            maxZoom = style.maxZoom
        }
        RasterLayer(id = "basemap", source = tiles)
    }
}

@Composable
@MaplibreComposable
private fun RouteLayers(route: EditableRoute) {
    val line = rememberGeoJsonSource(GeoJsonData.JsonString(RouteGeoJson.line(route.points)))
    LineLayer(
        id = "route",
        source = line,
        color = const(RouteLine),
        width = const(4.dp),
        cap = const(LineCap.Round),
        join = const(LineJoin.Round),
    )

    // Marqueurs sur les points touchés, pas sur chaque point du tracé routier ; au-delà de
    // MAX_MARKERS (GPX importé), seulement le départ et l'arrivée.
    val anchors = route.anchorIndices
    val shown = if (anchors.size > MAX_MARKERS) listOf(0, route.points.lastIndex).distinct() else anchors
    val waypoints = rememberGeoJsonSource(GeoJsonData.JsonString(RouteGeoJson.points(shown.map(route.points::get))))
    CircleLayer(
        id = "waypoints",
        source = waypoints,
        color = const(Color.White),
        radius = const(6.dp),
        strokeColor = const(RouteLine),
        strokeWidth = const(3.dp),
    )
}

@Composable
@MaplibreComposable
private fun CurrentPositionLayer(position: LatLon?) {
    val source = rememberGeoJsonSource(GeoJsonData.JsonString(RouteGeoJson.points(listOfNotNull(position))))
    CircleLayer(
        id = "current-position",
        source = source,
        color = const(CurrentPosition),
        radius = const(7.dp),
        strokeColor = const(Color.White),
        strokeWidth = const(4.dp),
    )
}

private const val MAX_MARKERS = 50

private val ROUTE_FIT_PADDING = DpPadding(48.dp, 48.dp, 48.dp, 48.dp)

private fun LatLon.toPosition() = Position(longitude = lon, latitude = lat)

/** Les clics peuvent tomber sur une autre « copie » du monde : longitude ramenée dans [-180, 180[. */
private fun Position.toLatLon() = LatLon(latitude, (longitude + 180.0).mod(360.0) - 180.0)
