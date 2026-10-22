package com.avrora.power

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

const val LOW_LEVEL = 20

private val Amber = Color(0xFFFFB020)

/**
 * Briefly flashes a warning sign in the centre of the screen when the charge
 * drops to LOW_LEVEL while on battery. Fires on the transition, not on every frame,
 * and not if the app starts already low.
 */
@Composable
fun LowBatteryWarning(level: Int, charging: Boolean) {
    val low = !charging && level <= LOW_LEVEL
    var wasLow by remember { mutableStateOf(low) }
    val show = remember { Animatable(0f) }

    LaunchedEffect(low) {
        if (low && !wasLow) {
            show.animateTo(1f, tween(300))
            delay(2000)
            show.animateTo(0f, tween(400))
        } else if (!low) {
            show.snapTo(0f)
        }
        wasLow = low
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.alpha(show.value),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Canvas(Modifier.size(120.dp)) {
                val w = size.width
                val h = size.height
                val tri = Path().apply {
                    moveTo(w / 2f, h * 0.06f)
                    lineTo(w * 0.96f, h * 0.9f)
                    lineTo(w * 0.04f, h * 0.9f)
                    close()
                }
                drawPath(tri, Amber.copy(alpha = 0.18f))
                drawPath(tri, Amber, style = Stroke(5.dp.toPx()))
                drawRoundRect(
                    color = Amber,
                    topLeft = Offset(w * 0.455f, h * 0.34f),
                    size = Size(w * 0.09f, h * 0.32f),
                    cornerRadius = CornerRadius(w * 0.045f),
                )
                drawCircle(Amber, radius = w * 0.055f, center = Offset(w / 2f, h * 0.8f))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "НИЗКИЙ ЗАРЯД",
                color = Amber,
                fontSize = 14.sp,
                letterSpacing = 2.sp,
            )
        }
    }
}
