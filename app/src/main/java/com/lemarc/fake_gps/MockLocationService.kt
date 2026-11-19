package com.lemarc.fake_gps

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
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
    private var speedKmh = 5
    private var lastTick = 0L
    private var moving = false

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                shutdown()
                return START_NOT_STICKY
            }
            ACTION_SET_SPEED -> {
                speedKmh = intent.getIntExtra(EXTRA_SPEED, speedKmh)
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
                speedKmh = intent.getIntExtra(EXTRA_SPEED, speedKmh)
                engine = RouteEngine(points, intent.getBooleanExtra(EXTRA_LOOP, false))

                if (!setupProviders()) return START_NOT_STICKY
                lastTick = SystemClock.elapsedRealtime()
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

    @Suppress("DEPRECATION", "WrongConstant")
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
                locationManager.addTestProvider(
                    name,
                    name == LocationManager.NETWORK_PROVIDER,
                    name == LocationManager.GPS_PROVIDER,
                    false, false, true, true, true,
                    Criteria.POWER_LOW, Criteria.ACCURACY_FINE,
                )
                locationManager.setTestProviderEnabled(name, true)
                providers += name
            } catch (e: SecurityException) {
                fail(e.message ?: e.javaClass.simpleName)
                return false
            } catch (e: IllegalArgumentException) {
                // Certains appareils refusent le fournisseur « fused » : on l'ignore.
            }
        }
        if (providers.isEmpty()) {
            fail("aucun fournisseur disponible")
            return false
        }
        return true
    }

    private fun tick() {
        val eng = engine ?: return
        val now = SystemClock.elapsedRealtime()
        val dt = (now - lastTick) / 1000.0
        lastTick = now

        val pos = if (moving) eng.advance(speedKmh / 3.6 * dt) else eng.current()
        val speedMs = if (moving && !pos.finished) (speedKmh / 3.6).toFloat() else 0f
        if (!pushLocation(pos, speedMs)) return

        val mode = when {
            !moving -> MockMode.FIXED
            pos.finished -> MockMode.ARRIVED
            else -> MockMode.MOVING
        }
        MockState.update(MockStatus(mode, pos.point, speedKmh))
    }

    private fun pushLocation(pos: RoutePosition, speedMs: Float): Boolean {
        // Légère imprécision pour que la position paraisse plus naturelle.
        val accuracy = 3f + Random.nextFloat() * 2f
        for (name in providers) {
            val loc = Location(name).apply {
                latitude = pos.point.lat
                longitude = pos.point.lon
                altitude = 35.0
                this.accuracy = accuracy
                speed = speedMs
                bearing = pos.bearing
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    verticalAccuracyMeters = 5f
                    speedAccuracyMetersPerSecond = 0.5f
                    bearingAccuracyDegrees = 5f
                }
            }
            try {
                locationManager.setTestProviderLocation(name, loc)
            } catch (e: SecurityException) {
                fail(e.message ?: e.javaClass.simpleName)
                return false
            } catch (_: IllegalArgumentException) {
                // Fournisseur retiré entre-temps.
            }
        }
        return true
    }

    private fun publishState() {
        val current = MockState.status.value
        MockState.update(current.copy(speedKmh = speedKmh))
    }

    private fun fail(message: String) {
        MockState.update(MockStatus(MockMode.IDLE, error = message))
        shutdown(keepError = true)
    }

    private fun shutdown(keepError: Boolean = false) {
        handler.removeCallbacks(ticker)
        for (name in providers) {
            try {
                locationManager.setTestProviderEnabled(name, false)
                locationManager.removeTestProvider(name)
            } catch (_: Exception) {
            }
        }
        providers.clear()
        engine = null
        if (!keepError) MockState.update(MockStatus())
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        if (providers.isNotEmpty()) {
            for (name in providers) {
                try {
                    locationManager.setTestProviderEnabled(name, false)
                    locationManager.removeTestProvider(name)
                } catch (_: Exception) {
                }
            }
            providers.clear()
            MockState.update(MockStatus())
        }
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
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
            getString(R.string.status_moving, speedKmh)
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

        const val ACTION_START = "com.lemarc.fake_gps.START"
        const val ACTION_STOP = "com.lemarc.fake_gps.STOP"
        const val ACTION_SET_SPEED = "com.lemarc.fake_gps.SET_SPEED"
        const val EXTRA_LATS = "lats"
        const val EXTRA_LONS = "lons"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_LOOP = "loop"
        const val EXTRA_MOVE = "move"

        fun start(context: Context, points: List<LatLon>, move: Boolean, speedKmh: Int, loop: Boolean) {
            val intent = Intent(context, MockLocationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_LATS, points.map { it.lat }.toDoubleArray())
                putExtra(EXTRA_LONS, points.map { it.lon }.toDoubleArray())
                putExtra(EXTRA_MOVE, move)
                putExtra(EXTRA_SPEED, speedKmh)
                putExtra(EXTRA_LOOP, loop)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun setSpeed(context: Context, speedKmh: Int) {
            if (MockState.status.value.mode == MockMode.IDLE) return
            context.startService(
                Intent(context, MockLocationService::class.java)
                    .setAction(ACTION_SET_SPEED)
                    .putExtra(EXTRA_SPEED, speedKmh),
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, MockLocationService::class.java).setAction(ACTION_STOP))
        }
    }
}
