package com.lemarc.fake_gps.mock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.lemarc.fake_gps.R
import com.lemarc.fake_gps.engine.RouteEngine
import com.lemarc.fake_gps.engine.RoutePosition
import com.lemarc.fake_gps.engine.SpeedExpression
import com.lemarc.fake_gps.format.Formatters
import com.lemarc.fake_gps.model.LatLon
import com.lemarc.fake_gps.ui.MainActivity
import kotlin.random.Random

/**
 * Service de premier plan qui injecte des positions fictives dans les
 * fournisseurs GPS / réseau / fused d'Android.
 */
class MockLocationService : Service() {

    private lateinit var locationManager: LocationManager
    private val handler = Handler(Looper.getMainLooper())
    private val providers = mutableListOf<String>()

    private var engine: RouteEngine? = null
    private var speedExpr: SpeedExpression = SpeedExpression.parse(DEFAULT_SPEED)
    private var speedKmh = 0.0
    private var lastTick = 0L
    private var moveStart = 0L
    private var moving = false
    private var ticksSinceNotif = 0

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                shutdown()
                return START_NOT_STICKY
            }
            ACTION_SET_SPEED -> {
                intent.getStringExtra(EXTRA_SPEED)?.let(SpeedExpression::parseOrNull)?.let { speedExpr = it }
                publishState()
                updateNotification()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                startInForeground()
                val lats = intent.getDoubleArrayExtra(EXTRA_LATS)
                val lons = intent.getDoubleArrayExtra(EXTRA_LONS)
                if (lats == null || lons == null || lats.isEmpty() || lats.size != lons.size) {
                    shutdown()
                    return START_NOT_STICKY
                }
                val points = lats.indices.map { LatLon(lats[it], lons[it]) }
                moving = intent.getBooleanExtra(EXTRA_MOVE, false) && points.size >= 2
                intent.getStringExtra(EXTRA_SPEED)?.let(SpeedExpression::parseOrNull)?.let { speedExpr = it }
                engine = RouteEngine(points, intent.getBooleanExtra(EXTRA_LOOP, false))

                if (!setupProviders()) return START_NOT_STICKY
                lastTick = SystemClock.elapsedRealtime()
                moveStart = lastTick
                handler.removeCallbacks(ticker)
                handler.post(ticker)
                updateNotification()
            }
        }
        return START_NOT_STICKY
    }

    private fun startInForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
    }

    private fun setupProviders(): Boolean {
        if (providers.isNotEmpty()) return true
        val candidates = buildList {
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
        }
        for (name in candidates) {
            try {
                try {
                    locationManager.removeTestProvider(name)
                } catch (_: IllegalArgumentException) {
                    // Pas encore enregistré.
                }
                addTestProvider(name)
                locationManager.setTestProviderEnabled(name, true)
                providers += name
            } catch (e: SecurityException) {
                fail(MockError(MockError.Reason.NOT_ALLOWED, e.message))
                return false
            } catch (_: IllegalArgumentException) {
                // Certains appareils refusent le fournisseur « fused » : on l'ignore.
            }
        }
        if (providers.isEmpty()) {
            fail(MockError(MockError.Reason.NO_PROVIDER))
            return false
        }
        return true
    }

    /** Déclare [name] comme fournisseur de test : précis, peu gourmand, avec altitude, vitesse et cap. */
    private fun addTestProvider(name: String) {
        val requiresNetwork = name == LocationManager.NETWORK_PROVIDER
        val requiresSatellite = name == LocationManager.GPS_PROVIDER
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val properties = ProviderProperties.Builder()
                .setHasNetworkRequirement(requiresNetwork)
                .setHasSatelliteRequirement(requiresSatellite)
                .setHasAltitudeSupport(true)
                .setHasSpeedSupport(true)
                .setHasBearingSupport(true)
                .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
                .setAccuracy(ProviderProperties.ACCURACY_FINE)
                .build()
            locationManager.addTestProvider(name, properties)
        } else {
            // Avant Android 12, seule la surcharge à base de constantes Criteria existe.
            @Suppress("DEPRECATION", "WrongConstant")
            locationManager.addTestProvider(
                name, requiresNetwork, requiresSatellite,
                false, false, true, true, true,
                android.location.Criteria.POWER_LOW, android.location.Criteria.ACCURACY_FINE,
            )
        }
    }

    private fun tick() {
        val eng = engine ?: return
        val now = SystemClock.elapsedRealtime()
        val dt = (now - lastTick) / 1000.0
        lastTick = now

        val t = (now - moveStart) / 1000.0
        speedKmh = if (moving) speedExpr.speedKmh(t, eng.travelled) else 0.0
        val pos = if (moving) eng.advance(speedKmh / 3.6 * dt) else eng.current()
        val speedMs = if (moving && !pos.finished) (speedKmh / 3.6).toFloat() else 0f
        if (!pushLocation(pos, speedMs)) return

        val mode = when {
            !moving -> MockMode.FIXED
            pos.finished -> MockMode.ARRIVED
            else -> MockMode.MOVING
        }
        MockState.update(MockStatus(mode, pos.point, speedKmh, speedExpr.source))
        if (!speedExpr.isConstant && ++ticksSinceNotif >= NOTIF_EVERY_TICKS) {
            ticksSinceNotif = 0
            updateNotification()
        }
    }

    private fun pushLocation(pos: RoutePosition, speedMs: Float): Boolean {
        // Légère imprécision pour que la position paraisse plus naturelle.
        val accuracy = MIN_ACCURACY_M + Random.nextFloat() * ACCURACY_JITTER_M
        for (name in providers) {
            val loc = Location(name).apply {
                latitude = pos.point.lat
                longitude = pos.point.lon
                altitude = ALTITUDE_M
                this.accuracy = accuracy
                speed = speedMs
                bearing = pos.bearing
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                verticalAccuracyMeters = VERTICAL_ACCURACY_M
                speedAccuracyMetersPerSecond = SPEED_ACCURACY_MS
                bearingAccuracyDegrees = BEARING_ACCURACY_DEG
            }
            try {
                locationManager.setTestProviderLocation(name, loc)
            } catch (e: SecurityException) {
                fail(MockError(MockError.Reason.NOT_ALLOWED, e.message))
                return false
            } catch (_: IllegalArgumentException) {
                // Fournisseur retiré entre-temps.
            }
        }
        return true
    }

    private fun publishState() {
        val current = MockState.status.value
        MockState.update(current.copy(speedKmh = speedKmh, speedExpression = speedExpr.source))
    }

    private fun fail(error: MockError) {
        MockState.reportError(error)
        shutdown()
    }

    /** Arrête la simulation et le service. */
    private fun shutdown() {
        releaseProviders()
        engine = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Arrête le minuteur, retire les fournisseurs de test et repasse l'état à IDLE. */
    private fun releaseProviders() {
        handler.removeCallbacks(ticker)
        for (name in providers) {
            try {
                locationManager.setTestProviderEnabled(name, false)
                locationManager.removeTestProvider(name)
            } catch (_: Exception) {
                // Fournisseur déjà retiré, ou permission retirée entre-temps : rien à nettoyer.
            }
        }
        providers.clear()
        MockState.update(MockStatus())
    }

    override fun onDestroy() {
        releaseProviders()
        super.onDestroy()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, MockLocationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = if (moving) {
            getString(R.string.status_moving, Formatters.speed(speedKmh))
        } else {
            getString(R.string.status_fixed)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.btn_stop), stopIntent)
            .build()
    }

    private fun updateNotification() {
        if (providers.isEmpty()) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    companion object {
        private const val CHANNEL_ID = "mock_location"
        private const val NOTIFICATION_ID = 42
        private const val UPDATE_INTERVAL_MS = 1000L
        private const val NOTIF_EVERY_TICKS = 5
        private const val DEFAULT_SPEED = "5"

        // Valeurs renseignées dans chaque position simulée.
        private const val ALTITUDE_M = 35.0
        private const val MIN_ACCURACY_M = 3f
        private const val ACCURACY_JITTER_M = 2f
        private const val VERTICAL_ACCURACY_M = 5f
        private const val SPEED_ACCURACY_MS = 0.5f
        private const val BEARING_ACCURACY_DEG = 5f

        private const val ACTION_START = "com.lemarc.fake_gps.START"
        private const val ACTION_STOP = "com.lemarc.fake_gps.STOP"
        private const val ACTION_SET_SPEED = "com.lemarc.fake_gps.SET_SPEED"
        private const val EXTRA_LATS = "lats"
        private const val EXTRA_LONS = "lons"
        private const val EXTRA_SPEED = "speed"
        private const val EXTRA_LOOP = "loop"
        private const val EXTRA_MOVE = "move"

        fun start(context: Context, request: MockRequest) {
            val intent = Intent(context, MockLocationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_LATS, request.points.map { it.lat }.toDoubleArray())
                putExtra(EXTRA_LONS, request.points.map { it.lon }.toDoubleArray())
                putExtra(EXTRA_MOVE, request.move)
                putExtra(EXTRA_SPEED, request.speedExpression)
                putExtra(EXTRA_LOOP, request.loop)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun setSpeed(context: Context, speed: String) {
            if (MockState.status.value.mode == MockMode.IDLE) return
            context.startService(
                Intent(context, MockLocationService::class.java)
                    .setAction(ACTION_SET_SPEED)
                    .putExtra(EXTRA_SPEED, speed),
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, MockLocationService::class.java).setAction(ACTION_STOP))
        }
    }
}
