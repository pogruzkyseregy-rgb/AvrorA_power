package com.avrora.power

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import com.avrora.power.ui.Theme
import com.avrora.power.ui.drawDial
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theme.load(this)
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            androidx.core.app.ActivityCompat.requestPermissions(
                this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1,
            )
        }
        // Never let the recorder take the UI down with it.
        runCatching {
            androidx.core.content.ContextCompat.startForegroundService(
                this, android.content.Intent(this, RecorderService::class.java),
            )
        }
        setContent { PowerScreen() }
    }

    override fun onResume() {
        super.onResume()
        AppState.visible = true
    }

    override fun onPause() {
        AppState.visible = false
        super.onPause()
    }
}

/** Screens: "home", "settings", or "chart:<METRIC>". */
@Composable
fun PowerScreen() {
    val ctx = LocalContext.current
    val state by remember(ctx) { PowerReader.observe(ctx) }
        .collectAsState(initial = PowerReader.snapshot(ctx))
    var screen by rememberSaveable { mutableStateOf("home") }

    // Plug animation: 0 → 1 runs once on each connection. 1 means idle.
    val plug = remember { Animatable(1f) }
    var wasCharging by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(state.isCharging) {
        val prev = wasCharging
        wasCharging = state.isCharging
        if (!state.isCharging) {
            plug.snapTo(1f)
        } else if (prev == false && Theme.plugAnim) {
            plug.snapTo(0f)
            plug.animateTo(1f, tween(1800, easing = FastOutSlowInEasing))
        }
    }

    LaunchedEffect(state) { PowerWidget.refreshAll(ctx) }
    // The background service writes history; the screen just re-reads it.
    LaunchedEffect(Unit) {
        while (true) {
            History.reload(ctx)
            delay(10_000L)
        }
    }
    BackHandler(enabled = screen != "home") { screen = "home" }

    HudBackground {
        Box(modifier = Modifier.fillMaxSize().padding(top = 28.dp)) {
            when {
                screen == "home" -> ChargePage(
                    state = state,
                    plug = plug,
                    onOpen = { key -> screen = "chart:$key" },
                    onTitle = { screen = "settings" },
                )
                screen == "settings" -> SettingsScreen(onBack = { screen = "home" })
                else -> ChartScreen(
                    metric = Metric.valueOf(screen.removePrefix("chart:")),
                    state = state,
                    onBack = { screen = "home" },
                )
            }
            LowBatteryWarning(level = state.level, charging = state.isCharging)
        }
    }
}

@Composable
fun ChargePage(
    state: PowerState,
    plug: Animatable<Float, AnimationVector1D>,
    onOpen: (String) -> Unit,
    onTitle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Header(state, onTitle)
        Spacer(Modifier.height(20.dp))
        ChargeDial(state, plug, onClick = { onOpen("LEVEL") })
        Spacer(Modifier.height(14.dp))
        StatusBanner(state)
        Spacer(Modifier.height(18.dp))
        Metrics(state, onOpen)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun Header(s: PowerState, onTitle: () -> Unit) {
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
        Row(
            modifier = Modifier.clickable(onClick = onTitle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.av_logo),
                contentDescription = null,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                "AVRORA_POWER",
                color = Cyan,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
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
fun ChargeDial(s: PowerState, plug: Animatable<Float, AnimationVector1D>, onClick: () -> Unit) {
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
            .clickable(onClick = onClick)
            .drawBehind {
                drawDial(
                    variant = Theme.dial,
                    level = animatedLevel,
                    rotation = rotation,
                    charging = s.isCharging,
                    pulse = pulse,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        PlugBurst(plug)
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
 * Plug-in animation: a glow and three expanding rings over about 1.8 s.
 * Driven by [plug], which is started from PowerScreen on each connection.
 * The value is read inside the draw block, so only drawing is repeated.
 */
@Composable
fun PlugBurst(plug: Animatable<Float, AnimationVector1D>) {
    if (!Theme.plugAnim) return
    Canvas(Modifier.size(280.dp)) {
        val p = plug.value
        if (p >= 1f) return@Canvas
        val base = size.minDimension / 2
        drawCircle(
            color = Cyan.copy(alpha = (1f - p) * 0.3f),
            radius = base * 0.9f,
        )
        for (k in 0..2) {
            val q = (p - k * 0.2f).coerceIn(0f, 1f)
            if (q > 0f) {
                drawCircle(
                    color = Cyan.copy(alpha = (1f - q) * 0.95f),
                    radius = base * (0.4f + q * 0.6f),
                    style = Stroke(4.dp.toPx()),
                )
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

private data class Cell(
    val label: String,
    val value: String,
    val bar: Float?,
    val metric: String?,
    val signal: Int? = null,
    val alert: Boolean = false,
)

@Composable
fun Metrics(s: PowerState, onOpen: (String) -> Unit) {
    val ramPct = if (s.ramTotalMb > 0) s.ramUsedMb.toFloat() / s.ramTotalMb else 0f
    val storPct = if (s.storageTotalGb > 0) s.storageUsedGb / s.storageTotalGb else 0f

    val cells = listOf(
        Cell("НАПРЯЖЕНИЕ", "%.2f V".format(s.voltageV), (s.voltageV - 3.0f) / 1.4f, "VOLTAGE"),
        Cell("ТОК", s.currentMa?.let { "$it mA" } ?: "—", s.currentMa?.let { kotlin.math.abs(it) / 3000f }, "CURRENT"),
        Cell("ТЕМПЕРАТУРА", "%.1f °C".format(s.temperatureC), (s.temperatureC - 20f) / 40f, "TEMP"),
        Cell("ИСТОЧНИК", sourceText(s.source), null, null),
        Cell("ДО ПОЛНОЙ", s.minutesToFull?.let { "${it / 60}ч ${it % 60}м" } ?: "—", null, null),
        Cell(
            "ДО РАЗРЯДА",
            if (s.isCharging) "—" else (Forecast.minutesToEmpty(History.samples)?.let { "${it / 60}ч ${it % 60}м" } ?: "—"),
            null,
            "LEVEL",
        ),
        Cell("ЗДОРОВЬЕ", s.healthText, null, null),
        Cell("ОЗУ", "${s.ramUsedMb} / ${s.ramTotalMb} MB", ramPct, "RAM"),
        Cell("ПАМЯТЬ", "%.0f / %.0f GB".format(s.storageUsedGb, s.storageTotalGb), storPct, "STORAGE"),
        Cell("ТЕХНОЛОГИЯ", s.technology, null, null),
        Cell(
            "СЕТЬ",
            SignalMonitor.level.intValue.let { if (it >= 0) "$it/4 · ${it * 25}%" else "—" },
            SignalMonitor.level.intValue.let { if (it >= 0) it / 4f else null },
            "SIGNAL",
            signal = SignalMonitor.level.intValue.takeIf { it >= 0 },
            alert = SignalMonitor.whitelist.value,
        ),
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cells.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { c ->
                    HudMetric(
                        label = c.label,
                        value = c.value,
                        bar = c.bar?.coerceIn(0f, 1f),
                        modifier = Modifier.weight(1f),
                        onClick = c.metric?.let { k -> { onOpen(k) } },
                        signal = c.signal,
                        alert = c.alert,
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
