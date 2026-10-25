package com.avrora.power

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avrora.power.ui.AlertRed
import com.avrora.power.ui.Cyan
import com.avrora.power.ui.HudButton
import com.avrora.power.ui.HudPanel
import kotlinx.coroutines.launch

/** White-list mode switch, a button to check now, and what each site answered. */
@Composable
fun NetworkSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val manual = SignalMonitor.manual.intValue
    val modeText = when (manual) {
        1 -> "ВКЛЮЧЕНЫ ВРУЧНУЮ"
        0 -> "ВЫКЛЮЧЕНЫ ВРУЧНУЮ"
        else -> "АВТО"
    }

    HudPanel(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        onClick = { SignalMonitor.cycleManual(ctx) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "РЕЖИМ БЕЛЫХ СПИСКОВ",
                    color = Cyan,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "$modeText · нажми, чтобы переключить",
                    color = Cyan.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                )
            }
        }
    }

    HudButton(
        "ПРОВЕРИТЬ СЕЙЧАС",
        onClick = { scope.launch { SignalMonitor.probe(ctx) } },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )

    Spacer(Modifier.height(8.dp))
    Text("ЧТО ОТКРЫВАЕТСЯ", color = Cyan.copy(alpha = 0.6f), fontSize = 10.sp, letterSpacing = 2.sp)
    Spacer(Modifier.height(4.dp))

    val results = SignalMonitor.lastResults.value
    if (results.isEmpty()) {
        Text("проверки ещё не было", color = Cyan.copy(alpha = 0.5f), fontSize = 11.sp)
    }
    results.forEach { p ->
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text(
                p.url.removePrefix("https://").substringBefore("/"),
                color = Cyan,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
            Text(
                when {
                    p.ms != null -> "${p.ms} мс"
                    !p.dns -> "адрес не найден"
                    p.timeout -> "таймаут"
                    else -> "отказ"
                },
                color = if (p.ms != null) Cyan else AlertRed,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
