package com.avrora.power.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.delay
import kotlin.random.Random
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Warning colour for white-list mode and other alerts. */
val AlertRed = Color(0xFFFF3B5C)

/** Rectangle with the top-left and bottom-right corners cut diagonally. */
private fun chamfer(w: Float, h: Float, cut: Float) = Path().apply {
    moveTo(cut, 0f)
    lineTo(w, 0f)
    lineTo(w, h - cut)
    lineTo(w - cut, h)
    lineTo(0f, h)
    lineTo(0f, cut)
    close()
}

/** Dark background with a faint cyan grid. */
@Composable
fun HudBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .drawBehind {
                val step = 24.dp.toPx()
                val line = Cyan.copy(alpha = 0.05f)
                var x = 0f
                while (x <= size.width) {
                    drawLine(line, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    x += step
                }
                var y = 0f
                while (y <= size.height) {
                    drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    y += step
                }
            },
    ) { content() }
}

/** Panel whose frame is drawn in the chosen visual style. */
@Composable
fun HudPanel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    style: VisualStyle = Theme.style,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .drawBehind { drawPanel(style) }
            .padding(14.dp),
        content = content,
    )
}

private fun DrawScope.drawPanel(style: VisualStyle) {
    val w = size.width
    val h = size.height
    val thin = 1.dp.toPx()
    when (style) {
        VisualStyle.CHAMFER -> {
            val cut = 12.dp.toPx()
            val path = chamfer(w, h, cut)
            drawPath(path, Cyan.copy(alpha = 0.07f))
            drawPath(path, Cyan.copy(alpha = 0.45f), style = Stroke(thin))
            val accent = 3.dp.toPx()
            drawLine(Cyan, Offset(0f, cut), Offset(cut, 0f), strokeWidth = accent)
            drawLine(Cyan, Offset(w, h - cut), Offset(w - cut, h), strokeWidth = accent)
            drawLine(Cyan, Offset(cut, 0f), Offset(cut + 16.dp.toPx(), 0f), strokeWidth = accent)
        }
        VisualStyle.BRACKET -> {
            val len = 18.dp.toPx()
            val t = 2.dp.toPx()
            val c = Cyan.copy(alpha = 0.9f)
            drawRect(Cyan.copy(alpha = 0.04f))
            drawLine(c, Offset(0f, 0f), Offset(len, 0f), strokeWidth = t)
            drawLine(c, Offset(0f, 0f), Offset(0f, len), strokeWidth = t)
            drawLine(c, Offset(w, 0f), Offset(w - len, 0f), strokeWidth = t)
            drawLine(c, Offset(w, 0f), Offset(w, len), strokeWidth = t)
            drawLine(c, Offset(0f, h), Offset(len, h), strokeWidth = t)
            drawLine(c, Offset(0f, h), Offset(0f, h - len), strokeWidth = t)
            drawLine(c, Offset(w, h), Offset(w - len, h), strokeWidth = t)
            drawLine(c, Offset(w, h), Offset(w, h - len), strokeWidth = t)
        }
        VisualStyle.GLASS -> {
            val r = CornerRadius(16.dp.toPx())
            drawRoundRect(Cyan.copy(alpha = 0.08f), cornerRadius = r)
            drawRoundRect(Cyan.copy(alpha = 0.35f), cornerRadius = r, style = Stroke(thin))
        }
        VisualStyle.STRIPE -> {
            val cut = 6.dp.toPx()
            drawPath(chamfer(w, h, cut), Cyan.copy(alpha = 0.05f))
            drawPath(chamfer(w, h, cut), Cyan.copy(alpha = 0.4f), style = Stroke(thin))
            val sw = 6.dp.toPx()
            clipRect(0f, 0f, sw, h) {
                var x = -h
                while (x < sw + h) {
                    drawLine(Cyan.copy(alpha = 0.7f), Offset(x, h), Offset(x + h, 0f), strokeWidth = 2f)
                    x += 8.dp.toPx()
                }
            }
        }
        VisualStyle.RING -> {
            val r = CornerRadius(8.dp.toPx())
            drawRoundRect(Cyan.copy(alpha = 0.04f), cornerRadius = r)
            drawRoundRect(Cyan.copy(alpha = 0.3f), cornerRadius = r, style = Stroke(thin))
            drawCircle(Cyan, radius = 3.dp.toPx(), center = Offset(w - 12.dp.toPx(), 12.dp.toPx()))
        }
    }
}

/** Progress bar drawn in the chosen style: segments, smooth pill, striped, or a ring. */
@Composable
fun SegmentBar(
    progress: Float,
    segments: Int = 20,
    modifier: Modifier = Modifier,
    style: VisualStyle = Theme.style,
) {
    val p = progress.coerceIn(0f, 1f)
    when (style) {
        VisualStyle.CHAMFER, VisualStyle.BRACKET -> {
            val lit = (p * segments).toInt()
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                repeat(segments) { i ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(8.dp)
                            .background(if (i < lit) Cyan else Cyan.copy(alpha = 0.12f)),
                    )
                }
            }
        }
        VisualStyle.GLASS -> Canvas(modifier.fillMaxWidth().height(8.dp)) {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(Cyan.copy(alpha = 0.15f), cornerRadius = r)
            drawRoundRect(Cyan, size = Size(size.width * p, size.height), cornerRadius = r)
        }
        VisualStyle.STRIPE -> Canvas(modifier.fillMaxWidth().height(8.dp)) {
            drawRect(Cyan.copy(alpha = 0.15f))
            clipRect(0f, 0f, size.width * p, size.height) {
                var x = 0f
                while (x < size.width) {
                    drawLine(Cyan, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 3f)
                    x += 6.dp.toPx()
                }
            }
        }
        VisualStyle.RING -> Canvas(modifier.size(44.dp)) {
            val stroke = 5.dp.toPx()
            val r = size.minDimension / 2 - stroke
            val tl = Offset(center.x - r, center.y - r)
            val sz = Size(r * 2, r * 2)
            drawArc(Cyan.copy(alpha = 0.15f), -90f, 360f, false, tl, sz, style = Stroke(stroke))
            drawArc(
                Cyan, -90f, 360f * p, false, tl, sz,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}

/**
 * Label + value cell used in the metrics grid.
 * [signal] adds a bar icon (0–4). [alert] switches the cell to red and makes it glitch.
 */
@Composable
fun HudMetric(
    label: String,
    value: String,
    bar: Float? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    signal: Int? = null,
    alert: Boolean = false,
) {
    val tint = if (alert) AlertRed else Cyan
    val jitter = remember { mutableFloatStateOf(0f) }
    val flicker = remember { mutableFloatStateOf(1f) }
    LaunchedEffect(alert) {
        if (!alert) {
            jitter.floatValue = 0f
            flicker.floatValue = 1f
            return@LaunchedEffect
        }
        while (true) {
            jitter.floatValue = Random.nextFloat() * 6f - 3f
            flicker.floatValue = if (Random.nextFloat() < 0.25f) 0.3f else 1f
            delay(Random.nextLong(60, 180))
        }
    }

    HudPanel(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (signal != null) {
                SignalIcon(
                    bars = signal,
                    color = tint,
                    modifier = Modifier.offset(x = jitter.floatValue.dp).alpha(flicker.floatValue),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(label, color = tint.copy(alpha = 0.6f), fontSize = 10.sp, letterSpacing = 2.sp)
        }
        Spacer8()
        Text(
            value,
            color = tint.copy(alpha = flicker.floatValue),
            fontSize = 17.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.offset(x = jitter.floatValue.dp),
        )
        if (bar != null) {
            Spacer8()
            SegmentBar(bar)
        }
    }
}

/** Four signal bars; lit ones are [bars] high. */
@Composable
private fun SignalIcon(bars: Int, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 14.dp, height = 12.dp)) {
        val w = size.width / 4f
        for (i in 0 until 4) {
            val h = size.height * (i + 1) / 4f
            drawRect(
                color = if (i < bars) color else color.copy(alpha = 0.18f),
                topLeft = Offset(i * w + 1.dp.toPx(), size.height - h),
                size = Size(w - 2.dp.toPx(), h),
            )
        }
    }
}

/** Blinking dot used in headers. */
@Composable
fun BlinkDot(alpha: Float) {
    Canvas(Modifier.size(8.dp)) {
        drawCircle(Cyan.copy(alpha = alpha))
    }
}

/** Clickable chamfered button. `selected` fills it brighter. */
@Composable
fun HudButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    accent: Color = Cyan,
) {
    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .drawBehind {
                val cut = 8.dp.toPx()
                val path = chamfer(size.width, size.height, cut)
                drawPath(path, accent.copy(alpha = if (selected) 0.25f else 0.07f))
                drawPath(path, accent.copy(alpha = if (selected) 1f else 0.5f), style = Stroke(width = 1.dp.toPx()))
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = accent.copy(alpha = if (selected) 1f else 0.8f),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun Spacer8() {
    Box(Modifier.height(8.dp))
}
