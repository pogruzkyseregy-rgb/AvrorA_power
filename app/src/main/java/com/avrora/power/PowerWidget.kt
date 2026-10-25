package com.avrora.power

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.avrora.power.R

/** Home-screen widget: charge %, charging status, temperature. Tap opens the app. */
class PowerWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val s = PowerReader.snapshot(context)
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_power).apply {
                setTextViewText(R.id.w_level, "${s.level}%")
                setTextViewText(
                    R.id.w_status,
                    when {
                        s.isFull -> "FULL"
                        s.isCharging -> "CHARGING"
                        else -> "BATTERY"
                    },
                )
                setTextViewText(R.id.w_temp, "%.1f °C".format(s.temperatureC))
                // Signal bars and ping; red while white-list mode is on.
                setTextViewText(R.id.w_net, netValue())
                setTextColor(
                    R.id.w_net,
                    if (SignalMonitor.whitelist.value) 0xFFFF3B5C.toInt() else 0x9900E5FF.toInt(),
                )
                setOnClickPendingIntent(R.id.w_root, openApp)
            }
            manager.updateAppWidget(id, views)
        }
    }

    companion object {
        /** Pushes fresh values to every placed widget. Called from the app whenever data changes. */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PowerWidget::class.java))
            if (ids.isNotEmpty()) PowerWidget().onUpdate(context, manager, ids)
        }
    }
}
