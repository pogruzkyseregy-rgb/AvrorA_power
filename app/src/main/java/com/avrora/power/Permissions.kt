package com.avrora.power

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.avrora.power.ui.Cyan
import com.avrora.power.ui.HudPanel
import com.avrora.power.ui.Theme

/** On/off switch for the plug-in animation. */
@Composable
fun PlugAnimationToggle() {
    val ctx = LocalContext.current
    val on = Theme.plugAnim
    HudPanel(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        onClick = { Theme.setPlugAnim(ctx, !on) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "АНИМАЦИЯ ПОДКЛЮЧЕНИЯ",
                    color = Cyan,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "кольца и вспышка на шкале при подключении зарядки",
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
}

private data class PermState(
    val notifications: Boolean,
    val files: Boolean,
    val battery: Boolean,
    val overlay: Boolean,
    val fullScreen: Boolean,
    val phone: Boolean,
)

private fun readPerms(ctx: Context): PermState {
    val notif = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    val files = StorageTools.hasAccess()
    val pm = ctx.getSystemService(PowerManager::class.java)
    val battery = pm?.isIgnoringBatteryOptimizations(ctx.packageName) ?: false
    val overlay = Settings.canDrawOverlays(ctx)
    // Full-screen intents are a separate switch from Android 14 on; before that they are always allowed.
    val fullScreen = if (Build.VERSION.SDK_INT >= 34) {
        ctx.getSystemService(android.app.NotificationManager::class.java)?.canUseFullScreenIntent() ?: false
    } else {
        true
    }
    val phone = SignalMonitor.hasPhonePermission(ctx)
    return PermState(notif, files, battery, overlay, fullScreen, phone)
}

private fun openIntent(ctx: Context, action: String, withPackage: Boolean = false) {
    val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    intent.putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
    if (withPackage) intent.data = Uri.parse("package:${ctx.packageName}")
    runCatching { ctx.startActivity(intent) }
}

/** Status of the permissions the app needs, each with a button that fixes it. */
@Composable
fun PermissionsSection() {
    val ctx = LocalContext.current
    var perms by remember { mutableStateOf(readPerms(ctx)) }

    // Re-read statuses whenever the screen comes back (e.g. from system settings).
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) perms = readPerms(ctx)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { perms = readPerms(ctx) }

    val askPhone = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        perms = readPerms(ctx)
        SignalMonitor.ensureListening(ctx)
    }

    PermRow(
        title = "УВЕДОМЛЕНИЯ",
        hint = "оповещения о зарядке и низком заряде",
        ok = perms.notifications,
    ) {
        if (Build.VERSION.SDK_INT >= 33 && !perms.notifications) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openIntent(ctx, Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        }
    }

    PermRow(
        title = "ДОСТУП К ФАЙЛАМ",
        hint = "поиск и очистка папок, большие файлы",
        ok = perms.files,
    ) {
        openIntent(ctx, Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, withPackage = true)
    }

    PermRow(
        title = "РАБОТА В ФОНЕ",
        hint = "запись истории без остановки системой",
        ok = perms.battery,
    ) {
        openIntent(ctx, Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, withPackage = true)
    }

    PermRow(
        title = "ПОВЕРХ ДРУГИХ ПРИЛОЖЕНИЙ",
        hint = "анимация подключения поверх открытого приложения",
        ok = perms.overlay,
    ) {
        openIntent(ctx, Settings.ACTION_MANAGE_OVERLAY_PERMISSION, withPackage = true)
    }

    PermRow(
        title = "СИГНАЛ СЕТИ",
        hint = "уровень сотовой связи и график белых списков",
        ok = perms.phone,
    ) {
        if (!perms.phone) askPhone.launch(Manifest.permission.READ_PHONE_STATE)
        else openIntent(ctx, Settings.ACTION_APPLICATION_DETAILS_SETTINGS, withPackage = true)
    }

    PermRow(
        title = "ЭКРАН БЛОКИРОВКИ",
        hint = "показ анимации, когда экран выключен",
        ok = perms.fullScreen,
    ) {
        openIntent(ctx, "android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT", withPackage = true)
    }
}

@Composable
private fun PermRow(title: String, hint: String, ok: Boolean, onAction: () -> Unit) {
    HudPanel(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        onClick = onAction,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    color = Cyan,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(hint, color = Cyan.copy(alpha = 0.5f), fontSize = 10.sp)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (ok) "● ВКЛ" else "○ НАЖАТЬ",
                color = if (ok) Cyan else Cyan.copy(alpha = 0.5f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
