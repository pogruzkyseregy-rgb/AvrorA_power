package com.avrora.power

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps recording the battery history and sending alerts while the app is closed.
 * Runs as a foreground service with a quiet persistent notification.
 */
class RecorderService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            // Battery changes trigger alerts right away, not on the sampling timer.
            Alerts.check(c, PowerReader.fromIntent(c, intent))
        }
    }

    // Fires the plug animation even when the app is closed.
    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_POWER_CONNECTED) PlugFlash.play(c)
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(powerReceiver, IntentFilter(Intent.ACTION_POWER_CONNECTED))
        Alerts.ensureChannels(this)
        // Android 14+ requires the foreground service type to be passed explicitly.
        androidx.core.app.ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            Alerts.serviceNotification(this),
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        scope.launch {
            var tick = 0
            while (isActive) {
                // Retried every tick: the permission may have been granted after the service started.
                SignalMonitor.ensureListening(this@RecorderService)
                StatusOverlay.sync(this@RecorderService)
                // White-list check every other tick (about once a minute), to keep traffic small.
                if (tick % 2 == 0) SignalMonitor.probe(this@RecorderService)
                tick++
                val s = PowerReader.snapshot(this@RecorderService)
                History.append(this@RecorderService, s)
                Alerts.check(this@RecorderService, s)
                delay(History.STEP_MS)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { unregisterReceiver(batteryReceiver) }
        runCatching { unregisterReceiver(powerReceiver) }
        SignalMonitor.stop(this)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 1
    }
}
