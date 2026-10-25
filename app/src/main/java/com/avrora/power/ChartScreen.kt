package com.avrora.power

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avrora.power.ui.AlertRed
import com.avrora.power.ui.Cyan
import com.avrora.power.ui.HudPanel
import com.avrora.power.ui.LineChart
import java.text.SimpleDateFormat
import java.util.Locale

fun fmtValue(m: Metric, v: Float): String = when (m) {
    Metric.LEVEL, Metric.RAM, Metric.STORAGE, Metric.SIGNAL -> "%.0f%%".format(v)
    Metric.VOLTAGE -> "%.2f V".format(v)
    Metric.CURRENT -> "%.0f mA".format(v)
    Metric.TEMP -> "%.1f °C".format(v)
}

/** Chart of one metric over time, with min / average / max. Storage also shows the cleanup tools. */
@Composable
fun ChartScreen(metric: Metric, state: PowerState, onBack: () -> Unit) {
    val samples = History.samples
    // Points with a value, plus whether each one was taken during white-list mode.
    val points = samples.mapNotNull { s -> s.value(metric)?.let { it to s.wl } }
    val values = points.map { it.first }
    val flags = points.map { it.second }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val live = metric.live(state)
    val avg = if (values.isEmpty()) null else values.average().toFloat()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        HudPanel(modifier = Modifier.fillMaxWidth(), onClick = onBack) {
            Text(
                "‹  ${metric.title}",
                color = Cyan,
                fontSize = 13.sp,
                letterSpacing = 2.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                live?.let { fmtValue(metric, it) } ?: "—",
                color = Cyan,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Text("нажми, чтобы вернуться", color = Cyan.copy(alpha = 0.4f), fontSize = 10.sp)
        }

        Spacer(Modifier.height(12.dp))

        HudPanel(modifier = Modifier.fillMaxWidth()) {
            Text(
                "ИСТОРИЯ · ${values.size} ТОЧЕК",
                color = Cyan.copy(alpha = 0.6f),
                fontSize = 10.sp,
                letterSpacing = 2.sp,
            )
            if (metric == Metric.SIGNAL) {
                Text(
                    "красным — время в режиме белых списков",
                    color = AlertRed.copy(alpha = 0.8f),
                    fontSize = 10.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                Column(
                    modifier = Modifier.fillMaxHeight().padding(end = 6.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End,
                ) {
                    val hi = metric.fixedMax ?: values.maxOrNull()
                    val lo = metric.fixedMin ?: values.minOrNull()
                    Text(
                        hi?.let { fmtValue(metric, it) } ?: "",
                        color = Cyan.copy(alpha = 0.6f),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        lo?.let { fmtValue(metric, it) } ?: "",
                        color = Cyan.copy(alpha = 0.6f),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                LineChart(
                    values = values,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    fixedMin = metric.fixedMin,
                    fixedMax = metric.fixedMax,
                    highlight = if (metric == Metric.SIGNAL) flags else null,
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    samples.firstOrNull()?.let { timeFmt.format(it.t) } ?: "",
                    color = Cyan.copy(alpha = 0.5f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "сейчас",
                    color = Cyan.copy(alpha = 0.5f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        HudPanel(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCell("МИН", values.minOrNull()?.let { fmtValue(metric, it) } ?: "—", Modifier.weight(1f))
                StatCell("СРЕДНЕЕ", avg?.let { fmtValue(metric, it) } ?: "—", Modifier.weight(1f))
                StatCell("МАКС", values.maxOrNull()?.let { fmtValue(metric, it) } ?: "—", Modifier.weight(1f))
            }
        }

        if (metric == Metric.STORAGE) {
            Spacer(Modifier.height(14.dp))
            StorageSection()
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Cyan.copy(alpha = 0.6f), fontSize = 9.sp, letterSpacing = 1.sp)
        Text(
            value,
            color = Cyan,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
