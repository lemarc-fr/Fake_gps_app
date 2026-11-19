package com.lemarc.fake_gps

import android.Manifest
import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.lemarc.fake_gps.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    private val waypoints = mutableListOf<GeoPoint>()
    private val waypointMarkers = mutableListOf<Marker>()
    private lateinit var routeLine: Polyline
    private lateinit var currentMarker: Marker

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

    private val traceStore by lazy { TraceStore(filesDir) }

    private val gpxExporter =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
            if (uri != null) exportGpx(uri)
        }

    private val gpxPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) loadGpx(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("fakegps", MODE_PRIVATE)
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyInsets()
        setupMap()
        setupControls()
        restoreState()
        observeState()
    }

    private fun applyInsets() {
        val baseMargin = (12 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(binding.panel) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = baseMargin + bars.bottom
                leftMargin = baseMargin + bars.left
                rightMargin = baseMargin + bars.right
            }
            insets
        }
    }

    private fun setupMap() {
        val map = binding.map
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        map.isTilesScaledToDpi = true

        map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                addWaypoint(p)
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean = false
        }))

        routeLine = Polyline(map).apply {
            outlinePaint.color = Color.parseColor("#1565C0")
            outlinePaint.strokeWidth = 10f
        }
        map.overlays.add(routeLine)

        currentMarker = Marker(map).apply {
            title = getString(R.string.my_position)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_current_position)
        }
    }

    private fun setupControls() {
        binding.speedInput.doAfterTextChanged {
            val text = it?.toString().orEmpty()
            try {
                SpeedExpression.parse(text)
                binding.speedLayout.error = null
                MockLocationService.setSpeed(this, text.trim())
                saveState()
            } catch (e: SpeedExpression.ParseException) {
                binding.speedLayout.error = getString(R.string.speed_invalid, e.message)
            }
        }
        binding.speedLayout.setEndIconOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.speed_help_title)
                .setMessage(R.string.speed_help)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
        binding.traces.setOnClickListener { showTracesMenu(it) }
        binding.undo.setOnClickListener {
            if (waypoints.isNotEmpty()) {
                waypoints.removeAt(waypoints.lastIndex)
                refreshRoute()
            }
        }
        binding.clear.setOnClickListener {
            waypoints.clear()
            refreshRoute()
        }
        binding.teleport.setOnClickListener {
            val target = waypoints.lastOrNull()
            if (target == null) {
                toast(getString(R.string.need_one_point))
            } else {
                startMock(listOf(target), move = false)
            }
        }
        binding.move.setOnClickListener {
            if (waypoints.size < 2) {
                toast(getString(R.string.need_two_points))
            } else {
                startMock(waypoints.toList(), move = true)
            }
        }
        binding.stop.setOnClickListener { MockLocationService.stop(this) }
    }

    private fun addWaypoint(p: GeoPoint) {
        waypoints += GeoPoint(p.latitude, p.longitude)
        refreshRoute()
    }

    private fun showTracesMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, R.string.menu_save_trace)
            menu.add(0, 2, 1, R.string.menu_my_traces)
            menu.add(0, 3, 2, R.string.menu_import_gpx)
            menu.add(0, 4, 3, R.string.menu_export_gpx)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> askTraceName()
                    2 -> showSavedTraces()
                    3 -> gpxPicker.launch(GPX_MIME_TYPES)
                    4 -> if (waypoints.isEmpty()) {
                        toast(getString(R.string.need_one_point))
                    } else {
                        gpxExporter.launch(TraceStore.sanitize(defaultTraceName()) + ".gpx")
                    }
                }
                true
            }
            show()
        }
    }

    private fun defaultTraceName(): String =
        getString(R.string.trace_default_name, SimpleDateFormat("yyyy-MM-dd HH'h'mm", Locale.FRANCE).format(Date()))

    private fun askTraceName() {
        if (waypoints.isEmpty()) {
            toast(getString(R.string.need_one_point))
            return
        }
        val input = TextInputEditText(this).apply {
            setText(defaultTraceName())
            selectAll()
            isSingleLine = true
        }
        val container = TextInputLayout(this).apply {
            hint = getString(R.string.trace_name)
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.menu_save_trace)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty().ifEmpty { defaultTraceName() }
                if (traceStore.exists(name)) {
                    MaterialAlertDialogBuilder(this)
                        .setMessage(getString(R.string.trace_exists, TraceStore.sanitize(name)))
                        .setPositiveButton(R.string.replace) { _, _ -> saveTrace(name) }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                } else {
                    saveTrace(name)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun saveTrace(name: String) {
        val points = waypoints.map { it.toLatLon() }
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { traceStore.save(name, points) } }
            result.onSuccess { toast(getString(R.string.trace_saved, TraceStore.sanitize(name))) }
                .onFailure { toast(getString(R.string.trace_error, it.message ?: it.javaClass.simpleName)) }
        }
    }

    private fun showSavedTraces() {
        lifecycleScope.launch {
            val traces = withContext(Dispatchers.IO) { traceStore.list() }
            if (traces.isEmpty()) {
                toast(getString(R.string.no_traces))
                return@launch
            }
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE)
            val labels = traces.map { "${it.name}\n${dateFormat.format(Date(it.modified))}" }.toTypedArray()
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(R.string.menu_my_traces)
                .setItems(labels) { _, which -> showTraceActions(traces[which]) }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun showTraceActions(trace: TraceStore.Trace) {
        MaterialAlertDialogBuilder(this)
            .setTitle(trace.name)
            .setPositiveButton(R.string.load) { _, _ -> loadSavedTrace(trace) }
            .setNegativeButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { traceStore.delete(trace.name) }
                    toast(getString(R.string.trace_deleted, trace.name))
                }
            }
            .setNeutralButton(R.string.cancel, null)
            .show()
    }

    private fun loadSavedTrace(trace: TraceStore.Trace) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { traceStore.load(trace.name) } }
            result.onSuccess { setRoute(it) }
                .onFailure { toast(getString(R.string.trace_error, it.message ?: it.javaClass.simpleName)) }
        }
    }

    private fun exportGpx(uri: Uri) {
        val points = waypoints.map { it.toLatLon() }
        val name = defaultTraceName()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openOutputStream(uri, "wt")?.use { GpxWriter.write(name, points, it) }
                        ?: throw java.io.IOException("fichier inaccessible")
                }
            }
            result.onSuccess { toast(getString(R.string.gpx_exported)) }
                .onFailure { toast(getString(R.string.trace_error, it.message ?: it.javaClass.simpleName)) }
        }
    }

    private fun setRoute(points: List<LatLon>) {
        waypoints.clear()
        points.mapTo(waypoints) { GeoPoint(it.lat, it.lon) }
        refreshRoute()
        zoomToRoute()
        toast(getString(R.string.gpx_loaded, points.size))
    }

    private fun loadGpx(uri: Uri) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openInputStream(uri)?.use { GpxParser.parse(it) }
                        ?: throw GpxParser.GpxException("fichier introuvable")
                }
            }
            result.onSuccess { setRoute(it) }.onFailure { e ->
                toast(getString(R.string.gpx_error, e.message ?: e.javaClass.simpleName))
            }
        }
    }

    private fun zoomToRoute() {
        val map = binding.map
        when (waypoints.size) {
            0 -> Unit
            1 -> map.controller.animateTo(waypoints[0])
            else -> map.post {
                val box = BoundingBox.fromGeoPointsSafe(waypoints)
                map.zoomToBoundingBox(box, true, (48 * resources.displayMetrics.density).toInt())
            }
        }
    }

    private fun refreshRoute() {
        val map = binding.map
        map.overlays.removeAll(waypointMarkers)
        waypointMarkers.clear()
        waypoints.forEachIndexed { i, p ->
            if (waypoints.size > MAX_MARKERS && i != 0 && i != waypoints.lastIndex) return@forEachIndexed
            waypointMarkers += Marker(map).apply {
                position = p
                title = "Point ${i + 1}"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
        }
        map.overlays.addAll(waypointMarkers)
        routeLine.setPoints(waypoints)

        val total = waypoints.zipWithNext { a, b ->
            RouteEngine.distance(a.toLatLon(), b.toLatLon())
        }.sum()
        binding.info.text = if (waypoints.isEmpty()) {
            getString(R.string.hint_tap)
        } else {
            getString(R.string.points_count, waypoints.size, formatDistance(total))
        }
        map.invalidate()
        saveState()
    }

    private fun startMock(points: List<GeoPoint>, move: Boolean) {
        val speed = currentSpeed()
        if (speed == null) {
            binding.speedLayout.error?.let { toast(it.toString()) }
            return
        }
        val action = {
            if (isMockLocationApp()) {
                MockLocationService.start(
                    this,
                    points.map { it.toLatLon() },
                    move,
                    speed,
                    binding.loop.isChecked,
                )
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

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                MockState.status.collect { render(it) }
            }
        }
    }

    private fun render(status: MockStatus) {
        binding.status.text = when (status.mode) {
            MockMode.IDLE -> getString(R.string.status_idle)
            MockMode.FIXED -> getString(R.string.status_fixed)
            MockMode.MOVING -> getString(R.string.status_moving, MockLocationService.formatSpeed(status.speedKmh))
            MockMode.ARRIVED -> getString(R.string.status_arrived)
        }
        binding.stop.isEnabled = status.mode != MockMode.IDLE

        val map = binding.map
        val pos = status.position
        if (pos != null && status.mode != MockMode.IDLE) {
            currentMarker.position = GeoPoint(pos.lat, pos.lon)
            if (!map.overlays.contains(currentMarker)) map.overlays.add(currentMarker)
        } else {
            map.overlays.remove(currentMarker)
        }
        map.invalidate()

        status.error?.let {
            MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.error_mock, it))
                .setPositiveButton(android.R.string.ok, null)
                .show()
            MockState.update(status.copy(error = null))
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

    private fun isMockLocationApp(): Boolean {
        val ops = getSystemService(AppOpsManager::class.java) ?: return false
        val mode = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), packageName)
            } else {
                @Suppress("DEPRECATION")
                ops.checkOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), packageName)
            }
        } catch (_: SecurityException) {
            AppOpsManager.MODE_ERRORED
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasNotificationPermission() =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Expression de vitesse saisie (km/h, sans maximum), ou null si invalide. */
    private fun currentSpeed(): String? =
        binding.speedInput.text?.toString()?.trim()?.takeIf { SpeedExpression.parseOrNull(it) != null }

    private fun formatDistance(meters: Double): String =
        if (meters < 1000) {
            String.format(Locale.FRANCE, "%.0f m", meters)
        } else {
            String.format(Locale.FRANCE, "%.2f km", meters / 1000)
        }

    private fun saveState() {
        val editor = prefs.edit()
            .putString(KEY_POINTS, waypoints.joinToString(";") { "${it.latitude},${it.longitude}" })
            .putBoolean(KEY_LOOP, binding.loop.isChecked)
        currentSpeed()?.let { editor.putString(KEY_SPEED_EXPR, it) }
        editor.apply()
    }

    private fun restoreState() {
        val speed = prefs.getString(KEY_SPEED_EXPR, null) ?: legacySpeed() ?: DEFAULT_SPEED
        binding.speedInput.setText(speed)
        binding.loop.isChecked = prefs.getBoolean(KEY_LOOP, false)
        binding.loop.setOnCheckedChangeListener { _, _ -> saveState() }

        prefs.getString(KEY_POINTS, null)
            ?.split(";")
            ?.mapNotNull { entry ->
                val parts = entry.split(",")
                val lat = parts.getOrNull(0)?.toDoubleOrNull()
                val lon = parts.getOrNull(1)?.toDoubleOrNull()
                if (lat != null && lon != null) GeoPoint(lat, lon) else null
            }
            ?.let { waypoints.addAll(it) }
        refreshRoute()

        binding.map.controller.setZoom(prefs.getFloat(KEY_ZOOM, 15f).toDouble())
        val center = waypoints.lastOrNull() ?: GeoPoint(
            prefs.getFloat(KEY_LAT, 48.8584f).toDouble(),
            prefs.getFloat(KEY_LON, 2.2945f).toDouble(),
        )
        binding.map.controller.setCenter(center)
    }

    override fun onResume() {
        super.onResume()
        binding.map.onResume()
    }

    override fun onPause() {
        val center = binding.map.mapCenter
        prefs.edit()
            .putFloat(KEY_LAT, center.latitude.toFloat())
            .putFloat(KEY_LON, center.longitude.toFloat())
            .putFloat(KEY_ZOOM, binding.map.zoomLevelDouble.toFloat())
            .apply()
        saveState()
        binding.map.onPause()
        super.onPause()
    }

    /** Vitesse enregistrée par les versions précédentes (Int ou Float). */
    private fun legacySpeed(): String? = when (val v = prefs.all[KEY_SPEED]) {
        is Number -> v.toInt().coerceAtLeast(1).toString()
        else -> null
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun GeoPoint.toLatLon() = LatLon(latitude, longitude)

    companion object {
        private const val KEY_POINTS = "points"
        private const val KEY_SPEED = "speed"
        private const val KEY_SPEED_EXPR = "speed_expr"
        private const val DEFAULT_SPEED = "5"
        private val GPX_MIME_TYPES =
            arrayOf("application/gpx+xml", "application/xml", "text/xml", "application/octet-stream", "*/*")
        private const val KEY_LOOP = "loop"
        private const val KEY_LAT = "lat"
        private const val KEY_LON = "lon"
        private const val KEY_ZOOM = "zoom"
        private const val MAX_MARKERS = 50
    }
}
