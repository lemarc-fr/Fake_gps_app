package com.lemarc.fake_gps.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lemarc.fake_gps.R
import com.lemarc.fake_gps.data.AppPreferences
import com.lemarc.fake_gps.data.TraceStore
import com.lemarc.fake_gps.data.gpx.GpxParser
import com.lemarc.fake_gps.data.gpx.GpxWriter
import com.lemarc.fake_gps.data.routing.OsrmClient
import com.lemarc.fake_gps.data.routing.RoutingProfile
import com.lemarc.fake_gps.engine.SpeedExpression
import com.lemarc.fake_gps.map.MapStyle
import com.lemarc.fake_gps.mock.MockRequest
import com.lemarc.fake_gps.model.EditableRoute
import com.lemarc.fake_gps.model.Geo
import com.lemarc.fake_gps.model.LatLon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * État et actions de l'écran principal : trajet en cours d'édition, réglages, traces.
 *
 * Survit aux rotations ; chaque modification est aussi mémorisée dans [AppPreferences].
 * Ne connaît ni les vues ni les textes : l'écran affiche [state] et traduit les [events].
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    data class UiState(
        val route: EditableRoute,
        val routingProfile: RoutingProfile,
        val mapStyle: MapStyle,
        val loop: Boolean,
        /** Un itinéraire OSRM est en cours de calcul. */
        val isRouting: Boolean = false,
        /** Erreur de syntaxe de l'expression de vitesse saisie, ou null si elle est valide. */
        val speedError: String? = null,
    )

    private val prefs = AppPreferences(application)
    private val traceStore = TraceStore(application.filesDir)
    private val contentResolver get() = getApplication<Application>().contentResolver

    private val _state = MutableStateFlow(
        UiState(prefs.route, prefs.routingProfile, prefs.mapStyle, prefs.loop),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = Channel<MainEvent>(Channel.BUFFERED)
    val events: Flow<MainEvent> = _events.receiveAsFlow()

    /** Dernière expression de vitesse valide (celle envoyée au service). */
    var speedExpression: String = prefs.speedExpression
        private set

    /** Caméra de la carte ; au lancement, centrée sur le dernier point du trajet s'il y en a un. */
    var camera: AppPreferences.Camera = prefs.camera.let { saved ->
        _state.value.route.points.lastOrNull()?.let { saved.copy(center = it) } ?: saved
    }
        private set

    private var routingJob: Job? = null

    // --- Édition du trajet ---

    /** Ajoute le point touché : directement en ligne droite, ou via un itinéraire OSRM. */
    fun addPoint(point: LatLon) {
        val current = _state.value
        val from = current.route.points.lastOrNull()
        val profile = current.routingProfile
        if (profile == RoutingProfile.STRAIGHT || from == null) {
            setRoute(current.route.plusLeg(listOf(point)))
            return
        }
        if (routingJob?.isActive == true) {
            send(MainEvent.Info(R.string.routing_busy))
            return
        }
        _state.update { it.copy(isRouting = true) }
        routingJob = viewModelScope.launch {
            try {
                val route = withContext(Dispatchers.IO) { OsrmClient.route(listOf(from, point), profile) }
                // Le tracé OSRM part du point de route le plus proche : on évite de dupliquer le point de départ.
                val leg = route.points.dropWhile { Geo.distance(it, from) < SAME_POINT_THRESHOLD_M }
                setRoute(_state.value.route.plusLeg(leg))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                send(MainEvent.Failure(R.string.routing_error, e))
            } finally {
                _state.update { it.copy(isRouting = false) }
            }
        }
    }

    /** Annule le calcul d'itinéraire en cours, sinon retire le dernier tronçon. */
    fun undo() {
        if (routingJob?.isActive == true) {
            routingJob?.cancel()
        } else {
            setRoute(_state.value.route.minusLastLeg())
        }
    }

    fun clear() {
        routingJob?.cancel()
        setRoute(EditableRoute.EMPTY)
    }

    private fun setRoute(route: EditableRoute) {
        _state.update { it.copy(route = route) }
        prefs.route = route
    }

    /** Remplace tout le trajet (trace chargée ou GPX importé) et recadre la carte. */
    private fun replaceRoute(points: List<LatLon>) {
        routingJob?.cancel()
        setRoute(EditableRoute.of(points))
        send(MainEvent.RouteLoaded(points.size))
    }

    // --- Réglages ---

    fun setRoutingProfile(profile: RoutingProfile) {
        prefs.routingProfile = profile
        _state.update { it.copy(routingProfile = profile) }
    }

    fun setMapStyle(style: MapStyle) {
        prefs.mapStyle = style
        _state.update { it.copy(mapStyle = style) }
    }

    fun setLoop(loop: Boolean) {
        prefs.loop = loop
        _state.update { it.copy(loop = loop) }
    }

    /**
     * Valide l'expression saisie et la mémorise si elle est correcte.
     * @return le message d'erreur de syntaxe, ou null si l'expression est valide.
     */
    fun setSpeedExpression(text: String): String? {
        val expression = text.trim()
        val error = try {
            SpeedExpression.parse(expression)
            speedExpression = expression
            prefs.speedExpression = expression
            null
        } catch (e: SpeedExpression.ParseException) {
            e.message.orEmpty()
        }
        _state.update { it.copy(speedError = error) }
        return error
    }

    fun saveCamera(camera: AppPreferences.Camera) {
        this.camera = camera
        prefs.camera = camera
    }

    // --- Simulation ---

    /** Demande « Téléporter » sur le dernier point, ou null (avec un message) si impossible. */
    fun teleportRequest(): MockRequest? {
        val target = _state.value.route.points.lastOrNull()
        if (target == null) {
            send(MainEvent.Info(R.string.need_one_point))
            return null
        }
        return mockRequest(listOf(target), move = false)
    }

    /** Demande « Se déplacer » le long du trajet, ou null (avec un message) si impossible. */
    fun moveRequest(): MockRequest? {
        val points = _state.value.route.points
        if (points.size < 2) {
            send(MainEvent.Info(R.string.need_two_points))
            return null
        }
        return mockRequest(points, move = true)
    }

    private fun mockRequest(points: List<LatLon>, move: Boolean): MockRequest? {
        _state.value.speedError?.let {
            send(MainEvent.Info(R.string.speed_invalid, listOf(it)))
            return null
        }
        return MockRequest(points, move, speedExpression, _state.value.loop)
    }

    // --- Traces et GPX ---

    fun traceExists(name: String): Boolean = traceStore.exists(name)

    suspend fun savedTraces(): List<TraceStore.Trace> = withContext(Dispatchers.IO) { traceStore.list() }

    fun saveTrace(name: String) {
        val points = _state.value.route.points
        launchIo(R.string.trace_error, action = { traceStore.save(name, points) }) {
            send(MainEvent.Info(R.string.trace_saved, listOf(TraceStore.sanitize(name))))
        }
    }

    fun loadTrace(trace: TraceStore.Trace) {
        launchIo(R.string.trace_error, action = { traceStore.load(trace.name) }, onSuccess = ::replaceRoute)
    }

    fun deleteTrace(trace: TraceStore.Trace) {
        launchIo(R.string.trace_error, action = { traceStore.delete(trace.name) }) {
            send(MainEvent.Info(R.string.trace_deleted, listOf(trace.name)))
        }
    }

    fun importGpx(uri: Uri) {
        launchIo(R.string.gpx_error, action = {
            contentResolver.openInputStream(uri)?.use { GpxParser.parse(it) }
                ?: throw GpxParser.GpxException(GpxParser.GpxException.Reason.NOT_FOUND)
        }, onSuccess = ::replaceRoute)
    }

    fun exportGpx(uri: Uri, name: String) {
        val points = _state.value.route.points
        launchIo(R.string.trace_error, action = {
            contentResolver.openOutputStream(uri, "wt")?.use { GpxWriter.write(name, points, it) }
                ?: throw IOException("openOutputStream($uri) a renvoyé null")
        }) {
            send(MainEvent.Info(R.string.gpx_exported))
        }
    }

    /** Exécute [action] sur un thread d'E/S ; en cas d'échec, envoie [MainEvent.Failure] avec [errorRes]. */
    private fun <T> launchIo(errorRes: Int, action: () -> T, onSuccess: (T) -> Unit) {
        viewModelScope.launch {
            try {
                onSuccess(withContext(Dispatchers.IO) { action() })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                send(MainEvent.Failure(errorRes, e))
            }
        }
    }

    private fun send(event: MainEvent) {
        _events.trySend(event)
    }

    private companion object {
        /** En dessous de cette distance, deux points sont considérés comme identiques. */
        const val SAME_POINT_THRESHOLD_M = 1.0
    }
}
