package com.avrora.power

import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avrora.power.ui.Cyan
import com.avrora.power.ui.HudButton
import com.avrora.power.ui.HudPanel

/** Turns our network icon over the status bar on or off, and adjusts its position. */
@Composable
fun StatusOverlaySection() {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(StatusOverlay.isEnabled(ctx)) }
    var offset by remember { mutableIntStateOf(StatusOverlay.offsetDp(ctx)) }
    var width by remember { mutableIntStateOf(StatusOverlay.widthDp(ctx)) }
    val canDraw = Settings.canDrawOverlays(ctx)

    HudPanel(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        onClick = {
            on = !on
            StatusOverlay.setEnabled(ctx, on)
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "ЗНАЧОК СЕТИ В СТАТУС-БАРЕ",
                    color = Cyan,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (canDraw) "наш значок поверх стокового, с пингом"
                    else "нужно разрешение «Поверх других приложений»",
                    color = Cyan.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (on) "● ВКЛ" else "○ ВЫКЛ",
                color = if (on) Cyan else Cyan.copy(alpha = 0.5f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }

    if (on) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            HudButton(
                "‹ ВЛЕВО",
                onClick = { offset += 4; StatusOverlay.setOffset(ctx, offset) },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            HudButton(
                "ВПРАВО ›",
                onClick = { offset = (offset - 4).coerceAtLeast(0); StatusOverlay.setOffset(ctx, offset) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            HudButton(
                "− ШИРИНА",
                onClick = { width -= 8; StatusOverlay.setWidth(ctx, width) },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            HudButton(
                "+ ШИРИНА",
                onClick = { width += 8; StatusOverlay.setWidth(ctx, width) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
