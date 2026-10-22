package com.avrora.power

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avrora.power.ui.Cyan
import com.avrora.power.ui.HudPanel

/** Launcher icon variants. Each is an activity-alias in the manifest; only one is enabled. */
object AppIcons {
    private val aliases = listOf(".IconEyes", ".IconAv", ".IconHud", ".IconMono")
    val labels = listOf("ГЛАЗА", "AV", "HUD", "МОНО")
    val previews = listOf(
        R.mipmap.ic_launcher,
        R.mipmap.ic_icon_av,
        R.mipmap.ic_icon_hud,
        R.mipmap.ic_icon_mono,
    )

    fun current(ctx: Context): Int =
        runCatching { ctx.getSharedPreferences("avrora", Context.MODE_PRIVATE).getInt("icon", 0) }
            .getOrDefault(0)

    fun apply(ctx: Context, i: Int) {
        val pm = ctx.packageManager
        aliases.forEachIndexed { idx, alias ->
            pm.setComponentEnabledSetting(
                ComponentName(ctx.packageName, ctx.packageName + alias),
                if (idx == i) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        runCatching {
            ctx.getSharedPreferences("avrora", Context.MODE_PRIVATE).edit().putInt("icon", i).apply()
        }
    }
}

/** Row of icon previews. Tapping one switches the launcher icon. */
@Composable
fun IconPicker() {
    val ctx = LocalContext.current
    var current by remember { mutableStateOf(AppIcons.current(ctx)) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppIcons.labels.forEachIndexed { i, label ->
            HudPanel(
                modifier = Modifier.weight(1f),
                onClick = {
                    AppIcons.apply(ctx, i)
                    current = i
                },
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        painter = painterResource(AppIcons.previews[i]),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        label,
                        color = if (i == current) Cyan else Cyan.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(
        "Иконка на рабочем столе может обновиться не сразу.",
        color = Cyan.copy(alpha = 0.4f),
        fontSize = 10.sp,
    )
}
