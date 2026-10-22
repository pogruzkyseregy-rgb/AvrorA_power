package com.avrora.power

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avrora.power.ui.BlinkDot
import com.avrora.power.ui.Cyan
import com.avrora.power.ui.HudBackground
import com.avrora.power.ui.HudMetric
import com.avrora.power.ui.HudPanel
import com.avrora.power.ui.HudTabBar
import com.avrora.power.ui.drawTickRing

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PowerScreen() }
    }
}

@Composable
fun PowerScreen() {
    val ctx = LocalContext.current
    val state by remember(ctx) { PowerReader.observe(ctx) }
        .collectAsState(initial = PowerReader.snapshot(ctx))
    var tab by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(state) { PowerWidget.refreshAll(ctx) }

    HudBackground {
        Column(modifier = Modifier.fillMaxSize().padding(top = 28.dp)) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> ChargePage(state)
                    else -> StorageScreen(state)
                }
            }
            HudTabBar(labels = listOf("ЗАРЯД", "ПАМЯТЬ"), selected = tab, onSelect = { tab = it })
        }
    }
}

@Composable
fun ChargePage(state: PowerState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Header(state)
        Spacer(Modifier.height(20.dp))
        ChargeDial(state)
        Spacer(Modifier.height(14.dp))
        StatusBanner(state)
        Spacer(Modifier.height(18.dp))
        Metrics(state)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun Header(s: PowerState) {
    val blink = rememberInfiniteTransition(label = "hdr")
    val alpha by blink.animateFloat(
        initialValue = 1f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "hdr-alpha",
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "AVRORA_POWER",
            color = Cyan,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp,
            fontFamily = FontFamily.Monospace,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            BlinkDot(if (s.isCharging) alpha else 0.5f)
            Spacer(Modifier.size(6.dp))
            Text(
                if (s.isCharging) "LINK" else "IDLE",
                color = Cyan.copy(alpha = 0.7f),
                fontSize = 11.sp,
                letterSpacing = 2.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
fun ChargeDial(s: PowerState) {
    val transition = rememberInfiniteTransition(label = "dial")
    val rotation by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(6000)),
        label = "rot",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )
    val animatedLevel by animateFloatAsState(
        targetValue = s.level / 100f,
        animationSpec = tween(1200),
        label = "level",
    )

    Box(
        modifier = Modifier
            .size(280.dp)
            .drawBehind {
                drawTickRing(
                    level = animatedLevel,
                    rotation = rotation,
                    charging = s.isCharging,
                    pulse = pulse,
                )
                val stroke = 10.dp.toPx()
                val r = size.minDimension / 2 - 44.dp.toPx()
                val topLeft = Offset(center.x - r, center.y - r)
                val arcSize = Size(r * 2, r * 2)
                drawArc(
                    color = Cyan.copy(alpha = 0.12f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke),
                )
                drawArc(
                    color = Cyan,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedLevel,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        PlugBurst(active = s.isCharging)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${s.level}",
                color = Cyan,
                fontSize = 68.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                when {
                    s.isFull -> "FULL"
                    s.isCharging -> "CHARGING"
                    else -> "BATTERY"
                },
                color = Cyan.copy(alpha = if (s.isCharging) pulse else 0.7f),
                fontSize = 12.sp,
                letterSpacing = 4.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

/**
 * Plays once each time the charger connects: a glow and two expanding rings.
 * progress is read inside Canvas's draw block, so the animation redraws every frame.
 */
@Composable
fun PlugBurst(active: Boolean) {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(active) {
        if (active) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(1600, easing = FastOutSlowInEasing))
        }
    }
    Canvas(Modifier.size(280.dp)) {
        val p = progress.value
        if (p < 1f) {
            val base = size.minDimension / 2
            drawCircle(
                color = Cyan.copy(alpha = (1f - p) * 0.25f),
                radius = base * 0.9f,
            )
            for (k in 0..2) {
                val q = (p - k * 0.2f).coerceIn(0f, 1f)
                if (q > 0f) {
                    drawCircle(
                        color = Cyan.copy(alpha = (1f - q) * 0.9f),
                        radius = base * (0.4f + q * 0.6f),
                        style = Stroke(3.dp.toPx()),
                    )
                }
            }
        }
    }
}

@Composable
fun StatusBanner(s: PowerState) {
    HudPanel(modifier = Modifier.fillMaxWidth()) {
        Text(
            if (s.isCharging) "▲ ПОДКЛЮЧЕНО · ${sourceText(s.source)}" else "▼ НЕ ПОДКЛЮЧЕНО",
            color = if (s.isCharging) Cyan else Cyan.copy(alpha = 0.5f),
            fontSize = 13.sp,
            letterSpacing = 1.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun Metrics(s: PowerState) {
    val ramBar = if (s.ramTotalMb > 0) s.ramUsedMb.toFloat() / s.ramTotalMb else null
    val storBar = if (s.storageTotalGb > 0) s.storageUsedGb / s.storageTotalGb else null

    val items = listOf(
        Triple("НАПРЯЖЕНИЕ", "%.2f V".format(s.voltageV), (s.voltageV - 3.0f) / 1.4f),
        Triple("ТОК", s.currentMa?.let { "$it mA" } ?: "—", s.currentMa?.let { kotlin.math.abs(it) / 3000f }),
        Triple("ТЕМПЕРАТУРА", "%.1f °C".format(s.temperatureC), (s.temperatureC - 20f) / 40f),
        Triple("ИСТОЧНИК", sourceText(s.source), null),
        Triple("ДО ПОЛНОЙ", s.minutesToFull?.let { "${it / 60}ч ${it % 60}м" } ?: "—", null),
        Triple("ЗДОРОВЬЕ", s.healthText, null),
        Triple("ОЗУ", "${s.ramUsedMb} / ${s.ramTotalMb} MB", ramBar),
        Triple("ПАМЯТЬ", "%.1f / %.1f GB".format(s.storageUsedGb, s.storageTotalGb), storBar),
        Triple("ТЕХНОЛОГИЯ", s.technology, null),
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, value, bar) ->
                    HudMetric(
                        label = label,
                        value = value,
                        bar = bar?.coerceIn(0f, 1f),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

fun sourceText(s: ChargeSource) = when (s) {
    ChargeSource.NONE -> "нет"
    ChargeSource.AC -> "сеть"
    ChargeSource.USB -> "USB"
    ChargeSource.WIRELESS -> "беспроводная"
    ChargeSource.UNKNOWN -> "неизвестно"
}
