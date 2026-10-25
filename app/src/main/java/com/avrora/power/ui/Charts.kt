package com.avrora.power.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Simple line chart with a gradient fill and a dot on the latest value. */
@Composable
fun LineChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    fixedMin: Float? = null,
    fixedMax: Float? = null,
    /** Same length as values. Segments whose point is flagged are drawn in red. */
    highlight: List<Boolean>? = null,
) {
    val lo = fixedMin ?: values.minOrNull() ?: 0f
    val hi0 = fixedMax ?: values.maxOrNull() ?: 1f
    val hi = if (hi0 - lo < 0.001f) lo + 1f else hi0

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        for (i in 0..4) {
            val y = h * i / 4f
            drawLine(Cyan.copy(alpha = 0.12f), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }

        if (values.size < 2) return@Canvas

        val step = w / (values.size - 1)
        val pts = values.mapIndexed { i, v ->
            Offset(i * step, h - ((v - lo) / (hi - lo)) * h)
        }

        val line = Path()
        pts.forEachIndexed { i, p ->
            if (i == 0) line.moveTo(p.x, p.y) else line.lineTo(p.x, p.y)
        }

        val fill = Path()
        fill.moveTo(pts.first().x, h)
        pts.forEach { fill.lineTo(it.x, it.y) }
        fill.lineTo(pts.last().x, h)
        fill.close()

        drawPath(fill, Brush.verticalGradient(listOf(Cyan.copy(alpha = 0.35f), Cyan.copy(alpha = 0f))))
        drawPath(line, Cyan, style = Stroke(2.dp.toPx()))

        if (highlight != null && highlight.size == values.size) {
            for (i in 1 until pts.size) {
                if (highlight[i] || highlight[i - 1]) {
                    drawLine(AlertRed, pts[i - 1], pts[i], strokeWidth = 2.dp.toPx())
                }
            }
        }
        drawCircle(Cyan, radius = 4.dp.toPx(), center = pts.last())
    }
}
