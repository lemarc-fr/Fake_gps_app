package io.github.marcbrunet92.fakegps

import android.Manifest
import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.marcbrunet92.fakegps.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
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
        binding.speed.addOnChangeListener { _, value, fromUser ->
            updateSpeedLabel(value.toInt())
            if (fromUser) MockLocationService.setSpeed(this, value.toInt())
        }
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

    private fun refreshRoute() {
        val map = binding.map
        map.overlays.removeAll(waypointMarkers)
        waypointMarkers.clear()
        waypoints.forEachIndexed { i, p ->
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
        val action = {
            if (isMockLocationApp()) {
                MockLocationService.start(
                    this,
                    points.map { it.toLatLon() },
                    move,
                    binding.speed.value.toInt(),
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
            MockMode.MOVING -> getString(R.string.status_moving, status.speedKmh)
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

    private fun updateSpeedLabel(kmh: Int) {
        binding.speedLabel.text = getString(R.string.speed_label, kmh)
    }

    private fun formatDistance(meters: Double): String =
        if (meters < 1000) {
            String.format(Locale.FRANCE, "%.0f m", meters)
        } else {
            String.format(Locale.FRANCE, "%.2f km", meters / 1000)
        }

    private fun saveState() {
        prefs.edit()
            .putString(KEY_POINTS, waypoints.joinToString(";") { "${it.latitude},${it.longitude}" })
            .putFloat(KEY_SPEED, binding.speed.value)
            .putBoolean(KEY_LOOP, binding.loop.isChecked)
            .apply()
    }

    private fun restoreState() {
        val speed = prefs.getFloat(KEY_SPEED, 5f).coerceIn(binding.speed.valueFrom, binding.speed.valueTo)
        binding.speed.value = speed
        updateSpeedLabel(speed.toInt())
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

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun GeoPoint.toLatLon() = LatLon(latitude, longitude)

    companion object {
        private const val KEY_POINTS = "points"
        private const val KEY_SPEED = "speed"
        private const val KEY_LOOP = "loop"
        private const val KEY_LAT = "lat"
        private const val KEY_LON = "lon"
        private const val KEY_ZOOM = "zoom"
    }
}
