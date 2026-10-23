package com.avrora.power.ui

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

/** A colour scheme the user can pick in settings. */
data class Skin(val name: String, val accent: Color, val bg: Color)

/** How panels, bars and the dial are drawn. Each one is taken from a reference style. */
enum class VisualStyle(val title: String, val hint: String) {
    CHAMFER("СРЕЗ", "срезанные углы, как у рамок с референса"),
    BRACKET("СКОБКИ", "угловые скобки, как в прицеле"),
    GLASS("СТЕКЛО", "скруглённые плашки и мягкое свечение"),
    STRIPE("ПОЛОСЫ", "диагональная штриховка на шкалах"),
    RING("КОЛЬЦА", "кольцевые шкалы из сегментов"),
}

object Theme {
    val skins = listOf(
        Skin("CYAN", Color(0xFF00E5FF), Color(0xFF03060A)),
        Skin("ICE", Color(0xFF8FB8FF), Color(0xFF05060F)),
        Skin("MONO", Color(0xFFEDEDED), Color(0xFF000000)),
        Skin("CRIMSON", Color(0xFFFF3B5C), Color(0xFF0A0306)),
        Skin("AMBER", Color(0xFFFFB020), Color(0xFF080603)),
    )

    private val selected = mutableStateOf(0)
    private val selectedStyle = mutableStateOf(VisualStyle.CHAMFER)
    private val selectedDial = mutableStateOf(DialVariant.HYBRID)
    private val plugOn = mutableStateOf(true)

    val index: Int get() = selected.value
    val skin: Skin get() = skins[selected.value]
    val style: VisualStyle get() = selectedStyle.value
    val dial: DialVariant get() = selectedDial.value
    val plugAnim: Boolean get() = plugOn.value

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("avrora", Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        runCatching {
            val p = prefs(ctx)
            plugOn.value = p.getBoolean("plugAnim", true)
            selected.value = p.getInt("skin", 0).coerceIn(0, skins.lastIndex)
            selectedDial.value = runCatching {
                DialVariant.valueOf(p.getString("dial", "HYBRID") ?: "HYBRID")
            }.getOrDefault(DialVariant.HYBRID)
            selectedStyle.value = runCatching {
                VisualStyle.valueOf(p.getString("visual", "CHAMFER") ?: "CHAMFER")
            }.getOrDefault(VisualStyle.CHAMFER)
        }
    }

    fun set(ctx: Context, i: Int) {
        selected.value = i
        runCatching { prefs(ctx).edit().putInt("skin", i).apply() }
    }

    fun setDial(ctx: Context, v: DialVariant) {
        selectedDial.value = v
        runCatching { prefs(ctx).edit().putString("dial", v.name).apply() }
    }

    fun setPlugAnim(ctx: Context, on: Boolean) {
        plugOn.value = on
        runCatching { prefs(ctx).edit().putBoolean("plugAnim", on).apply() }
    }

    fun setStyle(ctx: Context, s: VisualStyle) {
        selectedStyle.value = s
        runCatching { prefs(ctx).edit().putString("visual", s.name).apply() }
    }
}

/** Current accent and background, read from the selected skin. */
val Cyan: Color get() = Theme.skin.accent
val Bg: Color get() = Theme.skin.bg
