package com.santhosh.lifeos

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews

class FastingWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id -> updateOne(context, appWidgetManager, id) }
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, FastingWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            ids.forEach { updateOne(context, manager, it) }
        }

        private fun updateOne(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_fasting_clock)
            val prefs = context.getSharedPreferences("lifeos_fasting", Context.MODE_PRIVATE)
            val startWall = prefs.getLong("start", 0L)
            val duration = prefs.getLong("duration", 16L * 60L * 60L * 1000L)
            val active = startWall > 0L

            views.setTextViewText(
                R.id.widget_title,
                if (active) "GRAND LINE • FASTING" else "GRAND LINE"
            )
            views.setTextViewText(
                R.id.widget_status,
                if (active) "FAST IN PROGRESS" else "Tap to open fasting"
            )

            if (active) {
                val elapsed = (System.currentTimeMillis() - startWall).coerceAtLeast(0L)
                val remaining = (duration - elapsed).coerceAtLeast(0L)
                val base = SystemClock.elapsedRealtime() + remaining
                views.setChronometer(R.id.widget_clock, base, null, true)
                views.setChronometerCountDown(R.id.widget_clock, true)
                views.setViewVisibility(R.id.widget_clock, android.view.View.VISIBLE)
                views.setTextViewText(
                    R.id.widget_status,
                    if (remaining == 0L) "TARGET REACHED • TAP TO OPEN" else "TIME REMAINING"
                )
            } else {
                views.setViewVisibility(R.id.widget_clock, android.view.View.GONE)
                views.setTextViewText(R.id.widget_status, "Tap to start or manage your fast")
            }

            val openIntent = Intent(context, MainActivity::class.java).apply {
                putExtra("open_tab", 1)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                context,
                501,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            manager.updateAppWidget(widgetId, views)
        }
    }
}