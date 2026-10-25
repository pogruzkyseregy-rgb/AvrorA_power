package com.avrora.power

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Notifications for charger connect / disconnect, low charge and full charge.
 * Fires on transitions only. The last known state is kept in preferences,
 * so a restart does not repeat old events.
 */
object Alerts {
    private const val ALERT_CHANNEL = "power_alerts"
    const val SERVICE_CHANNEL = "power_service"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("alerts", Context.MODE_PRIVATE)

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL, "Батарея", NotificationManager.IMPORTANCE_DEFAULT)
        )
        nm.createNotificationChannel(
            NotificationChannel(SERVICE_CHANNEL, "Запись заряда", NotificationManager.IMPORTANCE_MIN)
        )
    }

    /** Persistent notification required by the foreground service. */
    fun serviceNotification(ctx: Context): Notification =
        NotificationCompat.Builder(ctx, SERVICE_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
            .setContentTitle("AvrorA_power")
            .setContentText("Запись заряда активна")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

    fun check(ctx: Context, s: PowerState) {
        val p = prefs(ctx)
        val first = !p.contains("init")
        val lastCharging = p.getBoolean("charging", false)
        val lastLow = p.getBoolean("low", false)
        val lastFull = p.getBoolean("full", false)

        val charging = s.isCharging
        val low = !charging && s.level <= LOW_LEVEL
        val full = charging && (s.isFull || s.level >= 100)

        if (!first) {
            if (charging && !lastCharging) {
                notify(ctx, 1, "Зарядка подключена", "${sourceText(s.source)} · ${s.level}%")
            }
            if (!charging && lastCharging) {
                notify(ctx, 2, "Зарядка отключена", "Заряд ${s.level}%")
            }
            if (low && !lastLow) {
                notify(ctx, 3, "Низкий заряд", "Осталось ${s.level}%. Лучше подключить зарядку.")
            }
            if (full && !lastFull) {
                notify(ctx, 4, "Батарея заряжена", "100%. Можно отключить зарядку.")
            }
        }

        p.edit()
            .putBoolean("init", true)
            .putBoolean("charging", charging)
            .putBoolean("low", low)
            .putBoolean("full", full)
            .apply()
    }

    /** Posts a notification on the alerts channel. Used by other monitors (e.g. white lists). */
    fun post(ctx: Context, id: Int, title: String, text: String) = notify(ctx, id, title, text)

    private fun notify(ctx: Context, id: Int, title: String, text: String) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val n = NotificationCompat.Builder(ctx, ALERT_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(id, n) }
    }
}
