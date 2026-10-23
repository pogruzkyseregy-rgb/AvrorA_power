package com.avrora.power

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.avrora.power.ui.Theme
import kotlinx.coroutines.delay

/** True while our own UI is on screen; then the in-app animation is used instead. */
object AppState {
    @Volatile var visible = false
}

/**
 * Plays the plug-in animation outside the app.
 * Screen on: a system overlay drawn on top of whatever app is open.
 * Screen off or locked: a full-screen notification that wakes the screen and shows the animation.
 */
object PlugFlash {
    private const val CHANNEL = "plug_anim"
    private const val NOTIFICATION_ID = 5
    private val main = Handler(Looper.getMainLooper())

    fun play(ctx: Context) {
        if (AppState.visible) return
        Theme.load(ctx)
        if (!Theme.plugAnim) return

        val interactive = ctx.getSystemService(PowerManager::class.java)?.isInteractive ?: true
        val locked = ctx.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked ?: false
        if (interactive && !locked && android.provider.Settings.canDrawOverlays(ctx)) {
            showOverlay(ctx)
        } else {
            fullScreen(ctx)
        }
    }

    private fun showOverlay(ctx: Context) {
        main.post {
            val wm = ctx.getSystemService(WindowManager::class.java) ?: return@post
            val view = PlugOverlayView(ctx, Theme.skin.accent.toArgb())
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            )
            if (runCatching { wm.addView(view, params) }.isFailure) return@post

            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1800
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    view.progress = it.animatedValue as Float
                    view.invalidate()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        runCatching { wm.removeView(view) }
                    }
                })
                start()
            }
        }
    }

    private fun fullScreen(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Подключение зарядки", NotificationManager.IMPORTANCE_HIGH)
        )
        val intent = Intent(ctx, PlugFlashActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(
            ctx, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("Зарядка подключена")
            .setContentText("AvrorA_power")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setFullScreenIntent(pi, true)
            .setAutoCancel(true)
            .setTimeoutAfter(4000)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, n) }
    }
}

private fun withAlpha(color: Int, alpha: Float): Int =
    (color and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24)

/** Same rings as the in-app animation, drawn by a plain view for the system overlay. */
private class PlugOverlayView(ctx: Context, private val accent: Int) : View(ctx) {
    var progress = 0f
    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * density
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        val p = progress
        if (p <= 0f || p >= 1f) return
        val cx = width / 2f
        val cy = height / 2f
        val base = 140f * density
        fill.color = withAlpha(accent, (1f - p) * 0.3f)
        canvas.drawCircle(cx, cy, base * 0.9f, fill)
        for (k in 0..2) {
            val q = (p - k * 0.2f).coerceIn(0f, 1f)
            if (q > 0f) {
                line.color = withAlpha(accent, (1f - q) * 0.95f)
                canvas.drawCircle(cx, cy, base * (0.4f + q * 0.6f), line)
            }
        }
    }
}

private fun DrawScope.drawPlugRings(p: Float, accent: Color) {
    if (p >= 1f) return
    val base = size.minDimension / 2
    drawCircle(color = accent.copy(alpha = (1f - p) * 0.3f), radius = base * 0.9f)
    for (k in 0..2) {
        val q = (p - k * 0.2f).coerceIn(0f, 1f)
        if (q > 0f) {
            drawCircle(
                color = accent.copy(alpha = (1f - q) * 0.95f),
                radius = base * (0.4f + q * 0.6f),
                style = Stroke(4.dp.toPx()),
            )
        }
    }
}

/** Shown over the lock screen when the screen was off; plays once and closes itself. */
class PlugFlashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theme.load(this)
        setContent {
            val anim = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                anim.animateTo(1f, tween(1800, easing = FastOutSlowInEasing))
                delay(500)
                finish()
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x66000000))
                    .clickable { finish() },
                contentAlignment = Alignment.Center,
            ) {
                val accent = Theme.skin.accent
                Canvas(Modifier.size(280.dp)) { drawPlugRings(anim.value, accent) }
            }
        }
    }
}
