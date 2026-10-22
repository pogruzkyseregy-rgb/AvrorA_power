package com.avrora.power

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.awaitClose
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

enum class ChargeSource { NONE, AC, USB, WIRELESS, UNKNOWN }

data class PowerState(
    val level: Int = 0,                 // %
    val voltageV: Float = 0f,           // V
    val currentMa: Int? = null,         // mA, + charging / - discharging
    val temperatureC: Float = 0f,       // °C
    val isCharging: Boolean = false,
    val isFull: Boolean = false,
    val source: ChargeSource = ChargeSource.NONE,
    val minutesToFull: Long? = null,    // only while charging
    val healthText: String = "—",
    val technology: String = "—",
    val ramUsedMb: Long = 0,
    val ramTotalMb: Long = 0,
    val storageUsedGb: Float = 0f,
    val storageTotalGb: Float = 0f,
)

object PowerReader {

    fun snapshot(ctx: Context): PowerState {
        val intent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return fromIntent(ctx, intent)
    }

    fun fromIntent(ctx: Context, intent: Intent?): PowerState {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else 0

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        val full = status == BatteryManager.BATTERY_STATUS_FULL

        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val source = when (plugged) {
            0 -> ChargeSource.NONE
            BatteryManager.BATTERY_PLUGGED_AC -> ChargeSource.AC
            BatteryManager.BATTERY_PLUGGED_USB -> ChargeSource.USB
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> ChargeSource.WIRELESS
            else -> ChargeSource.UNKNOWN
        }

        val voltage = (intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000f
        val temp = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f

        // µA -> mA; sign convention is device-dependent, so normalise to + = charging
        val currentUa = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentMa = if (currentUa == Int.MIN_VALUE || currentUa == 0) null else currentUa / 1000

        val minutesToFull = if (charging && !full && Build.VERSION.SDK_INT >= 28) {
            val ms = bm.computeChargeTimeRemaining()
            if (ms > 0) ms / 60_000 else null
        } else null

        val health = when (intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Хорошее"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Перегрев"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Сел"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Перенапряжение"
            BatteryManager.BATTERY_HEALTH_COLD -> "Холод"
            else -> "Неизвестно"
        }
        val tech = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "—"

        // RAM
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val totalMb = mi.totalMem / 1_048_576
        val usedMb = (mi.totalMem - mi.availMem) / 1_048_576

        // Storage
        val stat = StatFs(Environment.getDataDirectory().path)
        val totalGb = stat.totalBytes / 1_073_741_824f
        val usedGb = (stat.totalBytes - stat.availableBytes) / 1_073_741_824f

        return PowerState(
            level = percent,
            voltageV = voltage,
            currentMa = currentMa,
            temperatureC = temp,
            isCharging = charging,
            isFull = full,
            source = source,
            minutesToFull = minutesToFull,
            healthText = health,
            technology = tech,
            ramUsedMb = usedMb,
            ramTotalMb = totalMb,
            storageUsedGb = usedGb,
            storageTotalGb = totalGb,
        )
    }

    /** Emits a new PowerState on every battery broadcast. */
    fun observe(ctx: Context): Flow<PowerState> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                trySend(fromIntent(c, intent))
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ctx.registerReceiver(receiver, filter)
        trySend(snapshot(ctx))
        awaitClose { ctx.unregisterReceiver(receiver) }
    }.distinctUntilChanged()
}
