package com.avrora.power

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.graphics.toArgb
import com.avrora.power.ui.AlertRed
import com.avrora.power.ui.Theme

/**
 * Our own network icon drawn over the status bar, on top of the system one.
 * The system icon is hidden under a black patch. Its position is set by the user
 * (offset from the right edge and width), because it differs between phones.
 */
object StatusOverlay {
    private var view: StatusView? = null
    private val main = Handler(Looper.getMainLooper())

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("avrora", Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean("statusOverlay", false)
    fun offsetDp(ctx: Context): Int = prefs(ctx).getInt("statusOffset", 90)
    fun widthDp(ctx: Context): Int = prefs(ctx).getInt("statusWidth", 110)

    fun setEnabled(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean("statusOverlay", on).apply()
        sync(ctx)
    }

    fun setOffset(ctx: Context, dp: Int) {
        prefs(ctx).edit().putInt("statusOffset", dp.coerceIn(0, 400)).apply()
        reposition(ctx)
    }

    fun setWidth(ctx: Context, dp: Int) {
        prefs(ctx).edit().putInt("statusWidth", dp.coerceIn(40, 300)).apply()
        reposition(ctx)
    }

    /** Shows or hides the overlay to match the setting and the permission. Call from any thread. */
    fun sync(ctx: Context) {
        val app = ctx.applicationContext
        main.post {
            Theme.load(app)
            if (!isEnabled(app) || !Settings.canDrawOverlays(app)) {
                hide(app)
            } else if (view == null) {
                show(app)
            }
        }
    }

    private fun reposition(ctx: Context) {
        val app = ctx.applicationContext
        main.post {
            hide(app)
            if (isEnabled(app) && Settings.canDrawOverlays(app)) show(app)
        }
    }

    private fun statusBarHeight(ctx: Context): Int {
        val res = ctx.resources
        val id = res.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) res.getDimensionPixelSize(id) else (24 * res.displayMetrics.density).toInt()
    }

    private fun show(ctx: Context) {
        val wm = ctx.getSystemService(WindowManager::class.java) ?: return
        val density = ctx.resources.displayMetrics.density
        val v = StatusView(ctx)
        val params = WindowManager.LayoutParams(
            (widthDp(ctx) * density).toInt(),
            statusBarHeight(ctx),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (offsetDp(ctx) * density).toInt()
            y = 0
        }
        if (runCatching { wm.addView(v, params) }.isSuccess) view = v
    }

    private fun hide(ctx: Context) {
        val v = view ?: return
        val wm = ctx.getSystemService(WindowManager::class.java)
        runCatching { wm?.removeView(v) }
        view = null
    }
}

/** Draws the black patch, four bars and the ping. Refreshes once a second. */
private class StatusView(ctx: Context) : View(ctx) {
    private val density = resources.displayMetrics.density
    private val patch = Paint().apply { color = AndroidColor.BLACK }
    private val lit = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f * density
        typeface = Typeface.MONOSPACE
    }
    private val refresh = object : Runnable {
        override fun run() {
            invalidate()
            postDelayed(this, 1000L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(refresh)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(refresh)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        // Hide the system icon underneath.
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), patch)

        val accent = Theme.skin.accent.toArgb()
        val color = if (SignalMonitor.whitelist.value) AlertRed.toArgb() else accent
        lit.color = color
        dim.color = (color and 0x00FFFFFF) or (0x33 shl 24)
        label.color = color

        val sig = SignalMonitor.level.intValue
        val barW = 3f * density
        val gap = 2f * density
        val baseY = height * 0.8f
        val maxH = height * 0.6f
        val x0 = 4f * density
        for (i in 0 until 4) {
            val h = maxH * (i + 1) / 4f
            val left = x0 + i * (barW + gap)
            canvas.drawRect(left, baseY - h, left + barW, baseY, if (i < sig) lit else dim)
        }

        val ping = SignalMonitor.pingMs.intValue
        val text = if (ping >= 0) "$ping ms" else "—"
        canvas.drawText(text, x0 + 4 * (barW + gap) + 6f * density, baseY, label)
    }
}
