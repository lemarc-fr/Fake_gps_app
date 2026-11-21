package com.lemarc.fake_gps.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.lemarc.fake_gps.R
import com.lemarc.fake_gps.data.TraceStore
import com.lemarc.fake_gps.data.routing.RoutingProfile
import com.lemarc.fake_gps.format.Formatters
import com.lemarc.fake_gps.mock.MockError
import com.lemarc.fake_gps.mock.MockLocationAccess
import com.lemarc.fake_gps.mock.MockLocationService
import com.lemarc.fake_gps.mock.MockMode
import com.lemarc.fake_gps.mock.MockRequest
import com.lemarc.fake_gps.mock.MockState
import com.lemarc.fake_gps.mock.MockStatus
import com.lemarc.fake_gps.ui.theme.AttributionBackground
import com.lemarc.fake_gps.ui.theme.AttributionText
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Boîte de dialogue affichée au-dessus de l'écran (une seule à la fois). */
private sealed interface MainDialog {
    data object SpeedHelp : MainDialog
    data object SpeedPresets : MainDialog
    data object MapStyles : MainDialog
    data class SaveTrace(val defaultName: String) : MainDialog
    data class ReplaceTrace(val name: String) : MainDialog
    data class TraceList(val traces: List<TraceStore.Trace>) : MainDialog
    data class TraceActions(val trace: TraceStore.Trace) : MainDialog
    data object MockSettings : MainDialog
    data class MockFailure(val error: MockError) : MainDialog
}

/**
 * Écran principal : carte plein écran, panneau de commandes en bas, boîtes de dialogue.
 * Relie [MainViewModel] (trajet, réglages, traces) et [MockLocationService] (simulation).
 */
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mockStatus by MockState.status.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<MainDialog?>(null) }
    val mapHandle = remember { OsmMapHandle() }
    val scope = rememberCoroutineScope()
    var speedText by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(viewModel.speedExpression))
    }

    // Événements ponctuels : messages du ViewModel et erreurs du service.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch {
                viewModel.events.collect { event ->
                    when (event) {
                        is MainEvent.Info -> context.toast(resources.getString(event.messageRes, *event.args.toTypedArray()))
                        is MainEvent.Failure -> context.toast(
                            resources.getString(event.messageRes, ErrorMessages.describe(resources, event.error)),
                        )
                        is MainEvent.RouteLoaded -> {
                            mapHandle.zoomTo(viewModel.state.value.route.points)
                            context.toast(
                                resources.getQuantityString(R.plurals.gpx_loaded, event.pointCount, event.pointCount),
                            )
                        }
                    }
                }
            }
            launch { MockState.errors.collect { dialog = MainDialog.MockFailure(it) } }
        }
    }

    // Démarrage de la simulation, après les permissions si besoin.
    var pendingRequest by remember { mutableStateOf<MockRequest?>(null) }
    fun launchMock(request: MockRequest) {
        if (MockLocationAccess.isSelectedMockApp(context)) {
            MockLocationService.start(context, request)
        } else {
            dialog = MainDialog.MockSettings
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val request = pendingRequest
        pendingRequest = null
        if (context.hasLocationPermission()) {
            request?.let(::launchMock)
        } else {
            context.toast(resources.getString(R.string.permission_denied))
        }
    }
    fun startMock(request: MockRequest) {
        if (context.hasLocationPermission() && context.hasNotificationPermission()) {
            launchMock(request)
        } else {
            pendingRequest = request
            permissionLauncher.launch(requiredPermissions())
        }
    }

    val gpxExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(GPX_MIME_TYPE)) { uri ->
        if (uri != null) viewModel.exportGpx(uri, resources.defaultTraceName())
    }
    val gpxPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importGpx(uri)
    }

    fun setSpeedText(value: TextFieldValue) {
        speedText = value
        if (viewModel.setSpeedExpression(value.text) == null) {
            MockLocationService.setSpeed(context, value.text.trim())
        }
    }

    fun requireRoute(action: () -> Unit) {
        if (state.route.isEmpty) context.toast(resources.getString(R.string.need_one_point)) else action()
    }

    val actions = ControlPanelActions(
        onSpeedTextChange = ::setSpeedText,
        onShowSpeedPresets = { dialog = MainDialog.SpeedPresets },
        onShowSpeedHelp = { dialog = MainDialog.SpeedHelp },
        onLoopChange = viewModel::setLoop,
        onRoutingProfileChange = viewModel::setRoutingProfile,
        onSaveTrace = { requireRoute { dialog = MainDialog.SaveTrace(resources.defaultTraceName()) } },
        onShowTraces = {
            scope.launch {
                val traces = viewModel.savedTraces()
                if (traces.isEmpty()) {
                    context.toast(resources.getString(R.string.no_traces))
                } else {
                    dialog = MainDialog.TraceList(traces)
                }
            }
        },
        onImportGpx = { gpxPicker.launch(GPX_OPEN_MIME_TYPES) },
        onExportGpx = { requireRoute { gpxExporter.launch(TraceStore.sanitize(resources.defaultTraceName()) + GPX_EXTENSION) } },
        onUndo = viewModel::undo,
        onClear = viewModel::clear,
        onTeleport = { viewModel.teleportRequest()?.let(::startMock) },
        onMove = { viewModel.moveRequest()?.let(::startMock) },
        onStop = { MockLocationService.stop(context) },
    )

    Box(Modifier.fillMaxSize()) {
        OsmMap(
            route = state.route,
            mapStyle = state.mapStyle,
            currentPosition = mockStatus.position.takeIf { mockStatus.mode != MockMode.IDLE },
            initialCamera = viewModel.camera,
            handle = mapHandle,
            onTap = viewModel::addPoint,
            onPause = viewModel::saveCamera,
            modifier = Modifier.fillMaxSize(),
        )

        val overlay = Modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(12.dp)
        Text(
            text = state.mapStyle.tileSource.copyrightNotice.orEmpty(),
            color = AttributionText,
            fontSize = 10.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .then(overlay)
                .background(AttributionBackground)
                .padding(horizontal = 4.dp),
        )
        SmallFloatingActionButton(
            onClick = { dialog = MainDialog.MapStyles },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .then(overlay),
        ) {
            Icon(painterResource(R.drawable.ic_layers), stringResource(R.string.map_style))
        }

        ControlPanel(
            status = mockStatus.label(),
            info = when {
                state.isRouting -> stringResource(R.string.routing_computing)
                !state.route.isEmpty -> pluralStringResource(
                    R.plurals.points_count,
                    state.route.tappedCount,
                    state.route.tappedCount,
                    Formatters.distance(state.route.lengthMeters),
                )
                state.routingProfile == RoutingProfile.STRAIGHT -> stringResource(R.string.hint_tap)
                else -> stringResource(R.string.hint_tap_routed)
            },
            speedText = speedText,
            speedError = state.speedError,
            loop = state.loop,
            routingProfile = state.routingProfile,
            stopEnabled = mockStatus.mode != MockMode.IDLE,
            actions = actions,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(overlay)
                .widthIn(max = PANEL_MAX_WIDTH)
                .fillMaxWidth(),
        )
    }

    val close = { dialog = null }
    when (val current = dialog) {
        null -> Unit
        MainDialog.SpeedHelp -> SpeedHelpDialog(onDismiss = close)
        MainDialog.SpeedPresets -> SpeedPresetsDialog(
            onSelect = { preset ->
                setSpeedText(TextFieldValue(preset.expression, TextRange(preset.expression.length)))
                close()
            },
            onDismiss = close,
        )
        MainDialog.MapStyles -> MapStyleDialog(
            current = state.mapStyle,
            onSelect = {
                viewModel.setMapStyle(it)
                close()
            },
            onDismiss = close,
        )
        is MainDialog.SaveTrace -> SaveTraceDialog(
            defaultName = current.defaultName,
            onSave = { name ->
                if (viewModel.traceExists(name)) {
                    dialog = MainDialog.ReplaceTrace(name)
                } else {
                    viewModel.saveTrace(name)
                    close()
                }
            },
            onDismiss = close,
        )
        is MainDialog.ReplaceTrace -> ReplaceTraceDialog(
            name = current.name,
            onReplace = {
                viewModel.saveTrace(current.name)
                close()
            },
            onDismiss = close,
        )
        is MainDialog.TraceList -> TraceListDialog(
            traces = current.traces,
            onSelect = { dialog = MainDialog.TraceActions(it) },
            onDismiss = close,
        )
        is MainDialog.TraceActions -> TraceActionsDialog(
            trace = current.trace,
            onLoad = {
                viewModel.loadTrace(current.trace)
                close()
            },
            onDelete = {
                viewModel.deleteTrace(current.trace)
                close()
            },
            onDismiss = close,
        )
        MainDialog.MockSettings -> MockSettingsDialog(
            onOpenSettings = {
                context.openDeveloperSettings()
                close()
            },
            onDismiss = close,
        )
        is MainDialog.MockFailure -> MessageDialog(
            message = stringResource(R.string.error_mock, ErrorMessages.describe(resources, current.error)),
            onDismiss = close,
        )
    }
}

@Composable
private fun MockStatus.label(): String = when (mode) {
    MockMode.IDLE -> stringResource(R.string.status_idle)
    MockMode.FIXED -> stringResource(R.string.status_fixed)
    MockMode.MOVING -> stringResource(R.string.status_moving, Formatters.speed(speedKmh))
    MockMode.ARRIVED -> stringResource(R.string.status_arrived)
}

/** Largeur maximale du panneau : en paysage ou sur tablette, la carte reste visible sur les côtés. */
private val PANEL_MAX_WIDTH = 560.dp

private const val GPX_MIME_TYPE = "application/gpx+xml"
private const val GPX_EXTENSION = ".gpx"

/** Types acceptés à l'import : beaucoup d'applis exportent le GPX sans type MIME précis. */
private val GPX_OPEN_MIME_TYPES =
    arrayOf(GPX_MIME_TYPE, "application/xml", "text/xml", "application/octet-stream", "*/*")

/** Date du nom de trace par défaut ; sert aussi de nom de fichier, donc sans « / » ni « : ». */
private const val TRACE_DATE_PATTERN = "yyyy-MM-dd HH'h'mm"

private fun Resources.defaultTraceName(): String =
    getString(R.string.trace_default_name, SimpleDateFormat(TRACE_DATE_PATTERN, Locale.ROOT).format(Date()))

private fun requiredPermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

private fun Context.hasLocationPermission() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun Context.hasNotificationPermission() =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

private fun Context.openDeveloperSettings() {
    try {
        startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
    } catch (_: ActivityNotFoundException) {
        startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}

private fun Context.toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
