package com.avrora.power

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import java.io.File

/** Parameters that can be charted. */
enum class Metric(val title: String, val fixedMin: Float?, val fixedMax: Float?) {
    LEVEL("ЗАРЯД", 0f, 100f),
    VOLTAGE("НАПРЯЖЕНИЕ", null, null),
    CURRENT("ТОК", null, null),
    TEMP("ТЕМПЕРАТУРА", null, null),
    RAM("ОЗУ", 0f, 100f),
    STORAGE("ПАМЯТЬ", 0f, 100f),
    SIGNAL("СИГНАЛ", 0f, 100f),
    PING("ПИНГ", 0f, null),
}

/** One recorded point in time. */
data class Sample(
    val t: Long,
    val level: Int,
    val volt: Float,
    val cur: Int?,
    val temp: Float,
    val ram: Float,
    val stor: Float,
    val chg: Boolean = false,
    /** Signal bars 0–4, or -1 when unknown. */
    val sig: Int = -1,
    /** True while the mobile network was limited to a white list. */
    val wl: Boolean = false,
    /** Ping in ms, or -1 when unknown. */
    val ping: Int = -1,
) {
    fun value(m: Metric): Float? = when (m) {
        Metric.LEVEL -> level.toFloat()
        Metric.VOLTAGE -> volt
        Metric.CURRENT -> cur?.toFloat()
        Metric.TEMP -> temp
        Metric.RAM -> ram
        Metric.STORAGE -> stor
        Metric.SIGNAL -> if (sig >= 0) sig * 25f else null
        Metric.PING -> if (ping >= 0) ping.toFloat() else null
    }

    companion object {
        fun from(s: PowerState): Sample = Sample(
            t = System.currentTimeMillis(),
            level = s.level,
            volt = s.voltageV,
            cur = s.currentMa,
            temp = s.temperatureC,
            ram = pct(s.ramUsedMb.toFloat(), s.ramTotalMb.toFloat()),
            stor = pct(s.storageUsedGb, s.storageTotalGb),
            chg = s.isCharging,
            sig = SignalMonitor.level.intValue,
            wl = SignalMonitor.whitelist.value,
            ping = SignalMonitor.pingMs.intValue,
        )

        private fun pct(used: Float, total: Float): Float =
            if (total > 0f) used / total * 100f else 0f
    }
}

/** Current value of a metric, for the live readout. */
fun Metric.live(s: PowerState): Float? = Sample.from(s).value(this)

/**
 * History lives in a small CSV file. The background service is the only writer;
 * the screen reloads the file to show it. This keeps charts complete while the app is closed.
 */
object History {
    const val STEP_MS = 30_000L
    /** History is kept for two weeks, then old rows are dropped. */
    private const val KEEP_MS = 14L * 24 * 3600_000L
    /** Most rows loaded into memory for the charts and the forecast. */
    private const val UI_MAX = 40_000
    private var lastTrim = 0L

    val samples = mutableStateListOf<Sample>()

    private fun file(ctx: Context) = File(ctx.filesDir, "history.csv")

    @Synchronized
    fun append(ctx: Context, s: PowerState) {
        val f = file(ctx)
        val now = System.currentTimeMillis()
        runCatching {
            // Drop rows older than the retention period, at most once an hour.
            if (now - lastTrim > 3600_000L) {
                lastTrim = now
                val cutoff = now - KEEP_MS
                if (f.exists()) {
                    val keep = f.readLines().filter { l ->
                        (l.substringBefore(',').toLongOrNull() ?: 0L) >= cutoff
                    }
                    f.writeText(keep.joinToString("\n", postfix = if (keep.isEmpty()) "" else "\n"))
                }
            }
            f.appendText(line(Sample.from(s)) + "\n")
        }
    }

    @Synchronized
    fun reload(ctx: Context) {
        val loaded = runCatching {
            file(ctx).readLines().takeLast(UI_MAX).mapNotNull { parse(it) }
        }.getOrDefault(emptyList())
        samples.clear()
        samples.addAll(loaded)
    }

    private fun parse(l: String): Sample? = runCatching {
        val p = l.split(",")
        Sample(
            t = p[0].toLong(),
            level = p[1].toInt(),
            volt = p[2].toFloat(),
            cur = p[3].takeIf { it.isNotEmpty() }?.toInt(),
            temp = p[4].toFloat(),
            ram = p[5].toFloat(),
            stor = p[6].toFloat(),
            chg = p.getOrNull(7) == "1",
            // Older rows have no signal columns; they load with defaults.
            sig = p.getOrNull(8)?.toIntOrNull() ?: -1,
            wl = p.getOrNull(9) == "1",
            ping = p.getOrNull(10)?.toIntOrNull() ?: -1,
        )
    }.getOrNull()

    private fun line(s: Sample): String =
        "${s.t},${s.level},${s.volt},${s.cur ?: ""},${s.temp},${s.ram},${s.stor}," +
            "${if (s.chg) 1 else 0},${s.sig},${if (s.wl) 1 else 0},${s.ping}"
}

/** Forecast of time left on battery, from the slope of recent discharge. */
object Forecast {
    private const val WINDOW_MS = 30 * 60_000L

    /** Minutes until 0%, or null if there is not enough discharge data yet. */
    fun minutesToEmpty(samples: List<Sample>, now: Long = System.currentTimeMillis()): Long? {
        val pts = samples.filter { !it.chg && now - it.t <= WINDOW_MS }
        if (pts.size < 4) return null
        val t0 = pts.first().t
        if (pts.last().t - t0 < 10 * 60_000L) return null

        val xs = pts.map { (it.t - t0) / 60_000.0 }
        val ys = pts.map { it.level.toDouble() }
        val mx = xs.average()
        val my = ys.average()
        val num = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) }
        val den = xs.sumOf { (it - mx) * (it - mx) }
        if (den == 0.0) return null

        val slope = num / den // percent per minute, negative while discharging
        if (slope >= -0.005) return null
        return (pts.last().level / -slope).toLong()
    }
}
