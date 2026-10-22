package com.avrora.power

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.avrora.power.ui.Bg
import com.avrora.power.ui.Cyan
import com.avrora.power.ui.HudButton
import com.avrora.power.ui.HudPanel
import com.avrora.power.ui.SegmentBar
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One line in the storage lists. */
data class Entry(val name: String, val path: String, val bytes: Long, val isDir: Boolean)

/** Storage helpers. Work only with files the user can see; the app never touches system folders. */
object StorageTools {
    fun root(): File = Environment.getExternalStorageDirectory()

    fun hasAccess(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

    fun sizeOf(f: File): Long =
        if (f.isFile) f.length()
        else f.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** Top-level folders of shared storage with their sizes, biggest first. */
    fun topLevel(): List<Entry> =
        (root().listFiles() ?: emptyArray())
            .map { Entry(it.name, it.absolutePath, sizeOf(it), it.isDirectory) }
            .sortedByDescending { it.bytes }

    /** Files of at least `minBytes`, skipping Android/ (system-managed). */
    fun bigFiles(minBytes: Long = 50L * 1024 * 1024, limit: Int = 50): List<Entry> =
        root().walkTopDown()
            .onEnter { it.name != "Android" }
            .filter { it.isFile && it.length() >= minBytes }
            .sortedByDescending { it.length() }
            .take(limit)
            .map { Entry(it.name, it.absolutePath, it.length(), false) }
            .toList()
}

fun fmtBytes(b: Long): String = when {
    b >= 1L shl 30 -> "%.1f GB".format(b / 1073741824.0)
    b >= 1L shl 20 -> "%.0f MB".format(b / 1048576.0)
    else -> "%.0f KB".format(b / 1024.0)
}

@Composable
fun StorageScreen(state: PowerState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var access by remember { mutableStateOf(StorageTools.hasAccess()) }
    var busy by remember { mutableStateOf(false) }
    var top by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var big by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var pending by remember { mutableStateOf<Entry?>(null) }
    var status by remember { mutableStateOf("Нажми «СКАНИРОВАТЬ»") }
    var cache by remember { mutableStateOf(StorageTools.sizeOf(ctx.cacheDir)) }

    fun scan() {
        scope.launch {
            busy = true
            status = "Сканирую..."
            val result = withContext(Dispatchers.IO) {
                runCatching { StorageTools.topLevel() to StorageTools.bigFiles() }
                    .getOrElse { emptyList<Entry>() to emptyList<Entry>() }
            }
            top = result.first
            big = result.second
            busy = false
            status = "Готово"
        }
    }

    fun delete(e: Entry) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { File(e.path).deleteRecursively() }.getOrDefault(false)
            }
            if (ok) {
                big = big.filterNot { it.path == e.path }
                cache = StorageTools.sizeOf(ctx.cacheDir)
                status = "Удалено: ${e.name}"
            } else {
                status = "Не удалось удалить: ${e.name}"
            }
            pending = null
        }
    }

    fun clearCache() {
        ctx.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        cache = StorageTools.sizeOf(ctx.cacheDir)
        status = "Кэш приложения очищен"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        HudPanel(modifier = Modifier.fillMaxWidth()) {
            Text("ОЗУ", color = Cyan.copy(alpha = 0.6f), fontSize = 10.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "${state.ramUsedMb} / ${state.ramTotalMb} MB",
                color = Cyan,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            SegmentBar(if (state.ramTotalMb > 0) state.ramUsedMb.toFloat() / state.ramTotalMb else 0f)
            Spacer(Modifier.height(6.dp))
            Text(
                "Android не даёт смотреть и закрывать память чужих приложений, поэтому показана общая.",
                color = Cyan.copy(alpha = 0.5f),
                fontSize = 10.sp,
            )
        }

        Spacer(Modifier.height(10.dp))

        HudPanel(modifier = Modifier.fillMaxWidth()) {
            Text("ХРАНИЛИЩЕ", color = Cyan.copy(alpha = 0.6f), fontSize = 10.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "%.1f / %.1f GB".format(state.storageUsedGb, state.storageTotalGb),
                color = Cyan,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            SegmentBar(if (state.storageTotalGb > 0) state.storageUsedGb / state.storageTotalGb else 0f)
        }

        Spacer(Modifier.height(14.dp))

        if (!access) {
            HudButton(
                text = "ДАТЬ ДОСТУП К ФАЙЛАМ",
                onClick = {
                    ctx.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:${ctx.packageName}"),
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                selected = true,
            )
            Spacer(Modifier.height(8.dp))
            HudButton(
                text = "ПРОВЕРИТЬ ДОСТУП",
                onClick = { access = StorageTools.hasAccess() },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HudButton(
                text = "СКАНИРОВАТЬ",
                onClick = { if (!busy) scan() },
                modifier = Modifier.weight(1f),
                selected = true,
            )
            HudButton(
                text = "КЭШ · ${fmtBytes(cache)}",
                onClick = { clearCache() },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            status,
            color = Cyan.copy(alpha = 0.7f),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )

        if (top.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("ПАПКИ", color = Cyan.copy(alpha = 0.6f), fontSize = 10.sp, letterSpacing = 2.sp)
            top.take(12).forEach { e ->
                FolderRow(e.name, fmtBytes(e.bytes))
            }
        }

        if (big.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                "БОЛЬШИЕ ФАЙЛЫ (от 50 MB) · нажми, чтобы удалить",
                color = Cyan.copy(alpha = 0.6f),
                fontSize = 10.sp,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(6.dp))
            big.forEach { e ->
                HudButton(
                    text = "${e.name}  ·  ${fmtBytes(e.bytes)}",
                    onClick = { pending = e },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    accent = Color(0xFFFF5577),
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    pending?.let { e ->
        AlertDialog(
            onDismissRequest = { pending = null },
            containerColor = Bg,
            title = {
                Text("Удалить файл?", color = Cyan, fontFamily = FontFamily.Monospace)
            },
            text = {
                Text(
                    "${e.name}\n${fmtBytes(e.bytes)}\n\nФайл удалится без возможности восстановить.",
                    color = Cyan.copy(alpha = 0.85f),
                )
            },
            confirmButton = {
                HudButton(
                    text = "УДАЛИТЬ",
                    onClick = { delete(e) },
                    accent = Color(0xFFFF5577),
                    selected = true,
                )
            },
            dismissButton = {
                HudButton(text = "ОТМЕНА", onClick = { pending = null })
            },
        )
    }
}

@Composable
fun FolderRow(name: String, size: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            name,
            color = Cyan,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
        Text(
            size,
            color = Cyan.copy(alpha = 0.7f),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}
