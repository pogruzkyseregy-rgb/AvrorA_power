package com.avrora.power

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avrora.power.ui.Cyan
import com.avrora.power.ui.HudPanel
import com.avrora.power.ui.SegmentBar
import com.avrora.power.ui.Theme
import com.avrora.power.ui.VisualStyle

/** Visual style, colour scheme and the app certificate. Opened by tapping the AVRORA_POWER title. */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        HudPanel(modifier = Modifier.fillMaxWidth(), onClick = onBack) {
            Text(
                "‹  НАСТРОЙКИ",
                color = Cyan,
                fontSize = 13.sp,
                letterSpacing = 2.sp,
                fontFamily = FontFamily.Monospace,
            )
            Text("нажми, чтобы вернуться", color = Cyan.copy(alpha = 0.4f), fontSize = 10.sp)
        }

        Spacer(Modifier.height(16.dp))
        Text("ВИЗУАЛ", color = Cyan.copy(alpha = 0.6f), fontSize = 10.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(6.dp))

        VisualStyle.values().forEach { st ->
            HudPanel(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                style = st,
                onClick = { Theme.setStyle(ctx, st) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            st.title,
                            color = Cyan,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(st.hint, color = Cyan.copy(alpha = 0.5f), fontSize = 10.sp)
                        Spacer(Modifier.height(8.dp))
                        SegmentBar(progress = 0.7f, style = st)
                    }
                    if (st == Theme.style) {
                        Spacer(Modifier.width(8.dp))
                        Text("●", color = Cyan, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("ЦВЕТ", color = Cyan.copy(alpha = 0.6f), fontSize = 10.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(6.dp))

        Theme.skins.forEachIndexed { i, skin ->
            HudPanel(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                onClick = { Theme.set(ctx, i) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(22.dp).background(skin.accent))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        skin.name,
                        color = Cyan,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (i == Theme.index) {
                        Text("● ВЫБРАН", color = Cyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("СЕРТИФИКАТ", color = Cyan.copy(alpha = 0.6f), fontSize = 10.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(6.dp))
        Certificate(ctx)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun Certificate(ctx: Context) {
    val version = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }
        .getOrNull() ?: "?"
    val androidId = runCatching { Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) }
        .getOrNull()
    val number = androidId?.takeLast(8)?.uppercase() ?: "--------"

    HudPanel(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.av_logo),
                contentDescription = null,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "AVRORA_POWER",
                    color = Cyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    "ПЕРСОНАЛЬНЫЙ СЕРТИФИКАТ",
                    color = Cyan.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    letterSpacing = 2.sp,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        CertRow("ВЕРСИЯ", version)
        CertRow("АВТОР", "pogruzkyseregy-rgb")
        CertRow("УСТРОЙСТВО", "${Build.MANUFACTURER} ${Build.MODEL}")
        CertRow("ANDROID", Build.VERSION.RELEASE)
        CertRow("№", "AVR-$number")
    }
}

@Composable
fun CertRow(key: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            key,
            color = Cyan.copy(alpha = 0.6f),
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            fontFamily = FontFamily.Monospace,
        )
        Text(value, color = Cyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}
