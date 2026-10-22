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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin


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

/** Panel with a chamfered outline and bright accents on the cut edges. */
@Composable
fun HudPanel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .drawBehind {
                val cut = 12.dp.toPx()
                val path = chamfer(size.width, size.height, cut)
                drawPath(path, Cyan.copy(alpha = 0.07f))
                drawPath(path, Cyan.copy(alpha = 0.45f), style = Stroke(width = 1.dp.toPx()))
                val accent = 3.dp.toPx()
                drawLine(Cyan, Offset(0f, cut), Offset(cut, 0f), strokeWidth = accent)
                drawLine(Cyan, Offset(size.width, size.height - cut), Offset(size.width - cut, size.height), strokeWidth = accent)
                drawLine(Cyan, Offset(cut, 0f), Offset(cut + 16.dp.toPx(), 0f), strokeWidth = accent)
            }
            .padding(14.dp),
        content = content,
    )
}

/** Segmented bar: progress in 0..1 lights up segments. */
@Composable
fun SegmentBar(progress: Float, segments: Int = 20, modifier: Modifier = Modifier) {
    val lit = (progress.coerceIn(0f, 1f) * segments).toInt()
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

/** Label + value cell used in the metrics grid. */
@Composable
fun HudMetric(
    label: String,
    value: String,
    bar: Float? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    HudPanel(modifier = modifier, onClick = onClick) {
        Text(label, color = Cyan.copy(alpha = 0.6f), fontSize = 10.sp, letterSpacing = 2.sp)
        Spacer8()
        Text(
            value,
            color = Cyan,
            fontSize = 17.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
        )
        if (bar != null) {
            Spacer8()
            SegmentBar(bar)
        }
    }
}

/** Dial ticks light up to `level`; inner ring with a spinning arc while charging. */
fun DrawScope.drawTickRing(level: Float, rotation: Float, charging: Boolean, pulse: Float) {
    val c = Offset(size.width / 2, size.height / 2)
    val outer = size.minDimension / 2 - 4.dp.toPx()
    val tickLen = 8.dp.toPx()

    for (i in 0 until 60) {
        val a = (i * 6f + rotation * 0.2f) * Math.PI.toFloat() / 180f
        val lit = i.toFloat() / 60f < level
        val r1 = outer - tickLen
        val p1 = Offset(c.x + cos(a) * r1, c.y + sin(a) * r1)
        val p2 = Offset(c.x + cos(a) * outer, c.y + sin(a) * outer)
        drawLine(
            color = if (lit) Cyan else Cyan.copy(alpha = 0.18f),
            start = p1,
            end = p2,
            strokeWidth = if (i % 5 == 0) 2.dp.toPx() else 1.dp.toPx(),
        )
    }

    val innerR = outer - 26.dp.toPx()
    drawCircle(Cyan.copy(alpha = 0.25f), radius = innerR, center = c, style = Stroke(1.dp.toPx()))

    if (charging) {
        drawArc(
            color = Cyan.copy(alpha = pulse),
            startAngle = rotation,
            sweepAngle = 70f,
            useCenter = false,
            topLeft = Offset(c.x - innerR, c.y - innerR),
            size = Size(innerR * 2, innerR * 2),
            style = Stroke(3.dp.toPx()),
        )
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

/** Bottom tab bar made of HudButtons. */
@Composable
fun HudTabBar(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        labels.forEachIndexed { i, label ->
            HudButton(
                text = label,
                onClick = { onSelect(i) },
                modifier = Modifier.weight(1f),
                selected = i == selected,
            )
        }
    }
}

@Composable
private fun Spacer8() {
    Box(Modifier.height(8.dp))
}
