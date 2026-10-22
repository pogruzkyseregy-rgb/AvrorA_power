package com.avrora.power.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Combined charge dials: several rings layered in one, based on the reference shapes. */
enum class DialVariant(val title: String, val hint: String) {
    HYBRID("HUD+", "блоки по кругу, точки, тонкие риски и дуга"),
    TECH("ТЕХ", "сектора, блоки и внутренняя шкала"),
    ORBIT("ОРБИТА", "точечная орбита, дуга и вращающиеся маркеры"),
}

/**
 * Draws the charge dial. Sizes are designed for a 280 dp canvas and scale with it,
 * so the same code also draws the small previews in settings.
 */
fun DrawScope.drawDial(
    variant: DialVariant,
    level: Float,
    rotation: Float,
    charging: Boolean,
    pulse: Float,
) {
    val k = size.minDimension / 280.dp.toPx()
    fun px(d: Float): Float = d.dp.toPx() * k

    val c = Offset(size.width / 2, size.height / 2)
    val lv = level.coerceIn(0f, 1f)
    val outerR = size.minDimension / 2 - px(4f)
    val mainR = size.minDimension / 2 - px(44f)
    val dim = 0.12f

    when (variant) {
        DialVariant.HYBRID -> {
            // Outer HUD blocks, lit by level.
            for (i in 0 until 16) {
                val lit = i / 16f < lv
                arcAt(c, outerR - px(6f), -90f + i * 22.5f + 2f, 18f, litColor(lit, dim), px(6f))
            }
            // Fine risks between the blocks and the main ring.
            for (i in 0 until 120) {
                val deg = -90f + i * 3f
                drawLine(
                    Cyan.copy(alpha = 0.3f),
                    pointAt(c, outerR - px(18f), deg),
                    pointAt(c, outerR - px(14f), deg),
                    strokeWidth = px(1f),
                )
            }
            // Dotted inner ring.
            for (i in 0 until 48) {
                val lit = i / 48f < lv
                drawCircle(
                    color = litColor(lit, 0.15f),
                    radius = px(1.8f),
                    center = pointAt(c, mainR - px(4f), -90f + i * 7.5f),
                )
            }
            // Main thick arc with a bright head.
            val arcR = mainR - px(12f)
            arcAt(c, arcR, -90f, 360f, Cyan.copy(alpha = dim), px(8f))
            if (lv > 0f) {
                arcAt(c, arcR, -90f, 360f * lv, Cyan, px(8f), StrokeCap.Round)
                drawCircle(Cyan, radius = px(7f), center = pointAt(c, arcR, -90f + 360f * lv))
            }
            if (charging) {
                arcAt(c, arcR, rotation, 50f, Cyan.copy(alpha = pulse), px(3f), StrokeCap.Round)
            }
        }
        DialVariant.TECH -> {
            // Four thick sectors, each filled by its quarter of the level.
            val secR = mainR + px(10f)
            for (q in 0 until 4) {
                val start = -90f + q * 90f + 5f
                val f = (lv * 4f - q).coerceIn(0f, 1f)
                arcAt(c, secR, start, 80f, Cyan.copy(alpha = dim), px(10f))
                if (f > 0f) arcAt(c, secR, start, 80f * f, Cyan, px(10f))
            }
            // Fine scale inside the sectors: longer tick every fifth.
            for (i in 0 until 60) {
                val lit = i / 60f < lv
                val deg = -90f + i * 6f
                drawLine(
                    litColor(lit, 0.2f),
                    pointAt(c, mainR - px(6f), deg),
                    pointAt(c, mainR - px(16f), deg),
                    strokeWidth = if (i % 5 == 0) px(2f) else px(1f),
                )
            }
            // Inner ring of small blocks.
            for (i in 0 until 24) {
                val lit = i / 24f < lv
                arcAt(c, mainR - px(30f), -90f + i * 15f + 2f, 8f, litColor(lit, dim), px(6f))
            }
            // Crosshair spokes.
            for (q in 0 until 4) {
                val deg = q * 90f - 90f
                drawLine(
                    Cyan.copy(alpha = 0.15f),
                    pointAt(c, px(30f), deg),
                    pointAt(c, mainR - px(40f), deg),
                    strokeWidth = px(1f),
                )
            }
            if (charging) {
                arcAt(c, secR, rotation, 40f, Cyan.copy(alpha = pulse), px(3f), StrokeCap.Round)
            }
        }
        DialVariant.ORBIT -> {
            // Outer orbit of dots, lit by level.
            for (i in 0 until 72) {
                val lit = i / 72f < lv
                drawCircle(
                    color = litColor(lit, 0.2f),
                    radius = px(2f),
                    center = pointAt(c, outerR - px(6f), -90f + i * 5f),
                )
            }
            // Main arc with a head.
            arcAt(c, mainR, -90f, 360f, Cyan.copy(alpha = dim), px(6f))
            if (lv > 0f) {
                arcAt(c, mainR, -90f, 360f * lv, Cyan, px(6f), StrokeCap.Round)
                drawCircle(Cyan, radius = px(6f), center = pointAt(c, mainR, -90f + 360f * lv))
            }
            // Inner markers that rotate continuously; they pulse while charging.
            val markerAlpha = if (charging) pulse else 0.5f
            for (i in 0 until 6) {
                arcAt(c, mainR - px(26f), rotation + i * 60f, 30f, Cyan.copy(alpha = markerAlpha), px(2f))
            }
        }
    }
}

private fun litColor(lit: Boolean, dimAlpha: Float): Color =
    if (lit) Cyan else Cyan.copy(alpha = dimAlpha)

private fun DrawScope.arcAt(
    c: Offset,
    r: Float,
    start: Float,
    sweep: Float,
    color: Color,
    width: Float,
    cap: StrokeCap = StrokeCap.Butt,
) {
    if (r <= 0f) return
    drawArc(
        color = color,
        startAngle = start,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset(c.x - r, c.y - r),
        size = Size(r * 2, r * 2),
        style = Stroke(width, cap = cap),
    )
}

private fun pointAt(c: Offset, r: Float, deg: Float): Offset {
    val a = deg * Math.PI.toFloat() / 180f
    return Offset(c.x + cos(a) * r, c.y + sin(a) * r)
}
