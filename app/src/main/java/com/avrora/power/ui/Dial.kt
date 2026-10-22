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

/** Shapes of the charge dial, based on the reference rings. */
enum class DialVariant(val title: String, val hint: String) {
    TICKS("ШКАЛА", "тонкие деления и дуга"),
    BLOCKS("БЛОКИ", "прямоугольные блоки по кругу, как C2"),
    DOTS("ТОЧКИ", "пунктирное кольцо, как C7 и C14"),
    SECTORS("СЕКТОРЫ", "четыре сектора, как C5"),
    ARC("ДУГА", "толстая дуга с ярким концом, как C13"),
    HUD("HUD", "крупные сегменты и риски, как референс"),
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

    when (variant) {
        DialVariant.TICKS -> {
            drawTicks(lv, rotation, c, outerR, px(8f), px(2f), px(1f))
            drawCircle(Cyan.copy(alpha = 0.25f), radius = outerR - px(26f), center = c, style = Stroke(px(1f)))
            arcAt(c, mainR, -90f, 360f, Cyan.copy(alpha = 0.12f), px(10f))
            if (lv > 0f) arcAt(c, mainR, -90f, 360f * lv, Cyan, px(10f), StrokeCap.Round)
        }
        DialVariant.BLOCKS -> {
            for (i in 0 until 36) {
                val lit = i / 36f < lv
                arcAt(
                    c, mainR, -90f + i * 10f + 1.5f, 7f,
                    if (lit) Cyan else Cyan.copy(alpha = 0.12f), px(14f),
                )
            }
            drawCircle(Cyan.copy(alpha = 0.2f), radius = mainR - px(16f), center = c, style = Stroke(px(1f)))
        }
        DialVariant.DOTS -> {
            for (i in 0 until 72) {
                val lit = i / 72f < lv
                drawCircle(
                    color = if (lit) Cyan else Cyan.copy(alpha = 0.15f),
                    radius = px(2.6f),
                    center = pointAt(c, mainR, -90f + i * 5f),
                )
            }
        }
        DialVariant.SECTORS -> {
            for (q in 0 until 4) {
                val start = -90f + q * 90f + 6f
                val f = (lv * 4f - q).coerceIn(0f, 1f)
                arcAt(c, mainR, start, 78f, Cyan.copy(alpha = 0.12f), px(12f))
                if (f > 0f) arcAt(c, mainR, start, 78f * f, Cyan, px(12f))
            }
            drawCircle(Cyan.copy(alpha = 0.25f), radius = mainR - px(22f), center = c, style = Stroke(px(1f)))
            for (q in 0 until 4) {
                val deg = q * 90f - 90f
                drawLine(
                    Cyan.copy(alpha = 0.2f),
                    pointAt(c, px(30f), deg),
                    pointAt(c, mainR - px(24f), deg),
                    strokeWidth = px(1f),
                )
            }
        }
        DialVariant.ARC -> {
            arcAt(c, mainR, -90f, 360f, Cyan.copy(alpha = 0.12f), px(6f))
            if (lv > 0f) {
                arcAt(c, mainR, -90f, 360f * lv, Cyan, px(10f), StrokeCap.Round)
                drawCircle(Cyan, radius = px(9f), center = pointAt(c, mainR, -90f + 360f * lv))
            }
            drawCircle(Cyan.copy(alpha = 0.3f), radius = outerR, center = c, style = Stroke(px(1f)))
        }
        DialVariant.HUD -> {
            for (i in 0 until 16) {
                val lit = i / 16f < lv
                arcAt(
                    c, mainR + px(14f), -90f + i * 22.5f + 2f, 18f,
                    if (lit) Cyan else Cyan.copy(alpha = 0.12f), px(8f),
                )
            }
            for (i in 0 until 90) {
                val deg = -90f + i * 4f
                drawLine(
                    Cyan.copy(alpha = 0.35f),
                    pointAt(c, mainR - px(16f), deg),
                    pointAt(c, mainR - px(10f), deg),
                    strokeWidth = px(1f),
                )
            }
            if (lv > 0f) arcAt(c, mainR, -90f, 360f * lv, Cyan.copy(alpha = 0.6f), px(2f))
        }
    }

    // Shared: a rotating highlight inside the dial while charging.
    if (charging) {
        arcAt(c, mainR - px(30f), rotation, 70f, Cyan.copy(alpha = pulse), px(3f), StrokeCap.Round)
    }
}

private fun DrawScope.drawTicks(
    level: Float,
    rotation: Float,
    c: Offset,
    outerR: Float,
    tickLen: Float,
    bigWidth: Float,
    smallWidth: Float,
) {
    for (i in 0 until 60) {
        val lit = i / 60f < level
        val deg = i * 6f + rotation * 0.2f
        drawLine(
            color = if (lit) Cyan else Cyan.copy(alpha = 0.18f),
            start = pointAt(c, outerR - tickLen, deg),
            end = pointAt(c, outerR, deg),
            strokeWidth = if (i % 5 == 0) bigWidth else smallWidth,
        )
    }
}

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
