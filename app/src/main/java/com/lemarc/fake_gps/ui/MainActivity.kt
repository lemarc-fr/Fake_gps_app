package com.lemarc.fake_gps.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lemarc.fake_gps.R
import com.lemarc.fake_gps.data.AppPreferences
import com.lemarc.fake_gps.data.TraceStore
import com.lemarc.fake_gps.data.routing.RoutingProfile
import com.lemarc.fake_gps.databinding.ActivityMainBinding
import com.lemarc.fake_gps.format.Formatters
import com.lemarc.fake_gps.map.MapStyle
import com.lemarc.fake_gps.map.toGeoPoint
import com.lemarc.fake_gps.map.toLatLon
import com.lemarc.fake_gps.mock.MockError
import com.lemarc.fake_gps.mock.MockLocationAccess
import com.lemarc.fake_gps.mock.MockLocationService
import com.lemarc.fake_gps.mock.MockMode
import com.lemarc.fake_gps.mock.MockRequest
import com.lemarc.fake_gps.mock.MockState
import com.lemarc.fake_gps.mock.MockStatus
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.MapEventsOverlay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Écran principal : relie les vues à [MainViewModel] (trajet, réglages, traces)
 * et à [MockLocationService] (simulation). Ne contient pas de logique métier.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mapRenderer: RouteMapRenderer
    private val viewModel: MainViewModel by viewModels()
    private var appliedMapStyle: MapStyle? = null

    /** Action à lancer une fois les permissions accordées. */
    private var pendingAction: (() -> Unit)? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val action = pendingAction
            pendingAction = null
            if (hasLocationPermission()) {
                action?.invoke()
            } else {
                toast(getString(R.string.permission_denied))
            }
        }

    private val gpxExporter =
        registerForActivityResult(ActivityResultContracts.CreateDocument(GPX_MIME_TYPE)) { uri ->
            if (uri != null) viewModel.exportGpx(uri, defaultTraceName())
        }

    private val gpxPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) viewModel.importGpx(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(this, getSharedPreferences(OSMDROID_PREFS, MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyInsets()
        setupMap()
        setupControls()
        observe()
    }

    override fun onResume() {
        super.onResume()
        binding.map.onResume()
    }

    override fun onPause() {
        val map = binding.map
        viewModel.saveCamera(AppPreferences.Camera(map.mapCenter.toLatLon(), map.zoomLevelDouble))
        map.onPause()
        super.onPause()
    }

    // --- Mise en place ---

    /** Décale les éléments posés sur la carte pour qu'ils ne passent pas sous les barres système. */
    private fun applyInsets() {
        val baseMargin = resources.getDimensionPixelSize(R.dimen.overlay_margin)
        ViewCompat.setOnApplyWindowInsetsListener(binding.panel) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = baseMargin + bars.bottom
                leftMargin = baseMargin + bars.left
                rightMargin = baseMargin + bars.right
            }
            insets
        }
        for (view in listOf(binding.mapStyle, binding.attribution)) {
            ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    topMargin = baseMargin + bars.top
                    leftMargin = baseMargin + bars.left
                    rightMargin = baseMargin + bars.right
                }
                insets
            }
        }
    }

    private fun setupMap() {
        val map = binding.map
        map.setMultiTouchControls(true)
        map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        map.isTilesScaledToDpi = true
        map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                viewModel.addPoint(p.toLatLon())
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean = false
        }))
        mapRenderer = RouteMapRenderer(map)

        val camera = viewModel.camera
        map.controller.setZoom(camera.zoom)
        map.controller.setCenter(camera.center.toGeoPoint())
    }

    private fun setupControls() {
        val state = viewModel.state.value

        binding.speedInput.setText(viewModel.speedExpression)
        binding.speedInput.doAfterTextChanged { text ->
            val expression = text?.toString().orEmpty()
            val error = viewModel.setSpeedExpression(expression)
            binding.speedLayout.error = error?.let { getString(R.string.speed_invalid, it) }
            if (error == null) MockLocationService.setSpeed(this, expression.trim())
        }
        binding.speedLayout.setEndIconOnClickListener { showSpeedHelp() }
        binding.speedLayout.setStartIconOnClickListener {
            SpeedPresetsDialog.show(this) { preset ->
                binding.speedInput.setText(preset.expression)
                binding.speedInput.setSelection(preset.expression.length)
            }
        }

        binding.loop.isChecked = state.loop
        binding.loop.setOnCheckedChangeListener { _, checked -> viewModel.setLoop(checked) }

        binding.routingGroup.check(ROUTING_BUTTONS.getValue(state.routingProfile))
        binding.routingGroup.addOnButtonCheckedListener { _, id, checked ->
            if (checked) viewModel.setRoutingProfile(ROUTING_BUTTONS.entries.first { it.value == id }.key)
        }

        binding.mapStyle.setOnClickListener { showMapStyles() }
        binding.traces.setOnClickListener { showTracesMenu(it) }
        binding.undo.setOnClickListener { viewModel.undo() }
        binding.clear.setOnClickListener { viewModel.clear() }
        binding.teleport.setOnClickListener { viewModel.teleportRequest()?.let(::startMock) }
        binding.move.setOnClickListener { viewModel.moveRequest()?.let(::startMock) }
        binding.stop.setOnClickListener { MockLocationService.stop(this) }
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch { viewModel.events.collect(::handleEvent) }
                launch { MockState.status.collect(::renderMockStatus) }
                launch { MockState.errors.collect(::showMockError) }
            }
        }
    }

    // --- Affichage ---

    private fun render(state: MainViewModel.UiState) {
        if (state.mapStyle != appliedMapStyle) {
            appliedMapStyle = state.mapStyle
            binding.map.setTileSource(state.mapStyle.tileSource)
            binding.attribution.text = state.mapStyle.tileSource.copyrightNotice
        }
        mapRenderer.showRoute(state.route)
        binding.info.text = when {
            state.isRouting -> getString(R.string.routing_computing)
            !state.route.isEmpty -> resources.getQuantityString(
                R.plurals.points_count,
                state.route.tappedCount,
                state.route.tappedCount,
                Formatters.distance(state.route.lengthMeters),
            )
            state.routingProfile == RoutingProfile.STRAIGHT -> getString(R.string.hint_tap)
            else -> getString(R.string.hint_tap_routed)
        }
    }

    private fun renderMockStatus(status: MockStatus) {
        binding.status.text = when (status.mode) {
            MockMode.IDLE -> getString(R.string.status_idle)
            MockMode.FIXED -> getString(R.string.status_fixed)
            MockMode.MOVING -> getString(R.string.status_moving, Formatters.speed(status.speedKmh))
            MockMode.ARRIVED -> getString(R.string.status_arrived)
        }
        binding.stop.isEnabled = status.mode != MockMode.IDLE
        mapRenderer.showCurrentPosition(status.position.takeIf { status.mode != MockMode.IDLE })
    }

    private fun handleEvent(event: MainEvent) {
        when (event) {
            is MainEvent.Info -> toast(getString(event.messageRes, *event.args.toTypedArray()))
            is MainEvent.Failure ->
                toast(getString(event.messageRes, ErrorMessages.describe(this, event.error)))
            is MainEvent.RouteLoaded -> {
                mapRenderer.zoomTo(viewModel.state.value.route.points)
                toast(resources.getQuantityString(R.plurals.gpx_loaded, event.pointCount, event.pointCount))
            }
        }
    }

    private fun showMockError(error: MockError) {
        MaterialAlertDialogBuilder(this)
            .setMessage(getString(R.string.error_mock, ErrorMessages.describe(this, error)))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // --- Dialogues et menus ---

    private fun showSpeedHelp() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.speed_help_title)
            .setMessage(R.string.speed_help)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showMapStyles() {
        val styles = MapStyle.entries
        val labels = styles.map { getString(it.labelRes) }.toTypedArray()
        val current = styles.indexOf(viewModel.state.value.mapStyle)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.map_style)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                viewModel.setMapStyle(styles[which])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showTracesMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            inflate(R.menu.menu_traces)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_save_trace -> askTraceName()
                    R.id.action_my_traces -> showSavedTraces()
                    R.id.action_import_gpx -> gpxPicker.launch(GPX_OPEN_MIME_TYPES)
                    R.id.action_export_gpx -> exportGpx()
                    else -> return@setOnMenuItemClickListener false
                }
                true
            }
            show()
        }
    }

    private fun askTraceName() {
        if (viewModel.state.value.route.isEmpty) {
            toast(getString(R.string.need_one_point))
            return
        }
        TraceDialogs.askName(this, defaultTraceName(), viewModel::traceExists, viewModel::saveTrace)
    }

    private fun showSavedTraces() {
        lifecycleScope.launch {
            val traces = viewModel.savedTraces()
            if (traces.isEmpty()) {
                toast(getString(R.string.no_traces))
            } else {
                TraceDialogs.showList(this@MainActivity, traces, viewModel::loadTrace, viewModel::deleteTrace)
            }
        }
    }

    private fun exportGpx() {
        if (viewModel.state.value.route.isEmpty) {
            toast(getString(R.string.need_one_point))
        } else {
            gpxExporter.launch(TraceStore.sanitize(defaultTraceName()) + GPX_EXTENSION)
        }
    }

    private fun defaultTraceName(): String =
        getString(R.string.trace_default_name, SimpleDateFormat(TRACE_DATE_PATTERN, Locale.ROOT).format(Date()))

    // --- Simulation et permissions ---

    private fun startMock(request: MockRequest) {
        val action = {
            if (MockLocationAccess.isSelectedMockApp(this)) {
                MockLocationService.start(this, request)
            } else {
                showMockSettingsDialog()
            }
        }
        if (hasLocationPermission() && hasNotificationPermission()) {
            action()
        } else {
            pendingAction = action
            val perms = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms += Manifest.permission.POST_NOTIFICATIONS
            }
            permissionLauncher.launch(perms.toTypedArray())
        }
    }

    private fun showMockSettingsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.mock_title)
            .setMessage(R.string.mock_message)
            .setPositiveButton(R.string.mock_open_settings) { _, _ ->
                try {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                } catch (_: ActivityNotFoundException) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasNotificationPermission() =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private companion object {
        const val OSMDROID_PREFS = "osmdroid"
        const val GPX_MIME_TYPE = "application/gpx+xml"
        const val GPX_EXTENSION = ".gpx"

        /** Types acceptés à l'import : beaucoup d'applis exportent le GPX sans type MIME précis. */
        val GPX_OPEN_MIME_TYPES =
            arrayOf(GPX_MIME_TYPE, "application/xml", "text/xml", "application/octet-stream", "*/*")

        /** Date du nom de trace par défaut ; sert aussi de nom de fichier, donc sans « / » ni « : ». */
        const val TRACE_DATE_PATTERN = "yyyy-MM-dd HH'h'mm"

        val ROUTING_BUTTONS = mapOf(
            RoutingProfile.STRAIGHT to R.id.routingStraight,
            RoutingProfile.CAR to R.id.routingCar,
            RoutingProfile.BIKE to R.id.routingBike,
            RoutingProfile.FOOT to R.id.routingFoot,
        )
    }
}
