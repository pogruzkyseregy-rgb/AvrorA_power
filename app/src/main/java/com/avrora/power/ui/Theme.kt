package com.avrora.power.ui

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

/** A colour scheme the user can pick in settings. */
data class Skin(val name: String, val accent: Color, val bg: Color)

object Theme {
    val skins = listOf(
        Skin("CYAN", Color(0xFF00E5FF), Color(0xFF03060A)),
        Skin("ICE", Color(0xFF8FB8FF), Color(0xFF05060F)),
        Skin("MONO", Color(0xFFEDEDED), Color(0xFF000000)),
        Skin("CRIMSON", Color(0xFFFF3B5C), Color(0xFF0A0306)),
        Skin("AMBER", Color(0xFFFFB020), Color(0xFF080603)),
    )

    private val selected = mutableStateOf(0)

    val index: Int get() = selected.value
    val skin: Skin get() = skins[selected.value]

    fun load(ctx: Context) {
        val i = runCatching {
            ctx.getSharedPreferences("avrora", Context.MODE_PRIVATE).getInt("skin", 0)
        }.getOrDefault(0)
        selected.value = i.coerceIn(0, skins.lastIndex)
    }

    fun set(ctx: Context, i: Int) {
        selected.value = i
        runCatching {
            ctx.getSharedPreferences("avrora", Context.MODE_PRIVATE).edit().putInt("skin", i).apply()
        }
    }
}

/** Current accent and background, read from the selected skin. */
val Cyan: Color get() = Theme.skin.accent
val Bg: Color get() = Theme.skin.bg
