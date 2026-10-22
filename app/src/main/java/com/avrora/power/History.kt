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
) {
    fun value(m: Metric): Float? = when (m) {
        Metric.LEVEL -> level.toFloat()
        Metric.VOLTAGE -> volt
        Metric.CURRENT -> cur?.toFloat()
        Metric.TEMP -> temp
        Metric.RAM -> ram
        Metric.STORAGE -> stor
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
        )

        private fun pct(used: Float, total: Float): Float =
            if (total > 0f) used / total * 100f else 0f
    }
}

/** Current value of a metric, for the live readout. */
fun Metric.live(s: PowerState): Float? = Sample.from(s).value(this)

/**
 * Keeps the last points in memory and in a small CSV file, so charts survive restarts.
 * Records only while the app is open (a background service would be needed for more).
 */
object History {
    const val STEP_MS = 30_000L
    private const val MAX = 2000

    val samples = mutableStateListOf<Sample>()
    private var file: File? = null

    fun init(ctx: Context) {
        if (file != null) return
        val f = File(ctx.filesDir, "history.csv")
        file = f
        val loaded = runCatching {
            f.readLines().takeLast(MAX).mapNotNull { parse(it) }
        }.getOrDefault(emptyList())
        samples.clear()
        samples.addAll(loaded)
    }

    fun add(ctx: Context, s: PowerState) {
        init(ctx)
        val sample = Sample.from(s)
        samples.add(sample)
        if (samples.size > MAX) {
            samples.removeAt(0)
            runCatching { file?.writeText(samples.joinToString("\n", postfix = "\n") { line(it) }) }
        } else {
            runCatching { file?.appendText(line(sample) + "\n") }
        }
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
        )
    }.getOrNull()

    private fun line(s: Sample): String =
        "${s.t},${s.level},${s.volt},${s.cur ?: ""},${s.temp},${s.ram},${s.stor}"
}
