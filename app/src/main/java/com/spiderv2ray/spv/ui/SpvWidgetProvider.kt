package com.spiderv2ray.spv.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.spiderv2ray.spv.MainActivity
import com.spiderv2ray.spv.R
import com.spiderv2ray.spv.core.SpvVpnService
import com.spiderv2ray.spv.data.ConfigRepository

class SpvWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_WIDGET_UPDATE = "com.spiderv2ray.spv.WIDGET_UPDATE"
        const val ACTION_WIDGET_TOGGLE = "com.spiderv2ray.spv.WIDGET_TOGGLE"

        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, SpvWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                val intent = Intent(context, SpvWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_WIDGET_UPDATE, AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                val mgr = AppWidgetManager.getInstance(context)
                val ids = mgr.getAppWidgetIds(ComponentName(context, SpvWidgetProvider::class.java))
                onUpdate(context, mgr, ids)
            }
            ACTION_WIDGET_TOGGLE -> {
                val serviceIntent = Intent(context, SpvVpnService::class.java).apply {
                    action = SpvVpnService.ACTION_TOGGLE
                }
                try {
                    context.startService(serviceIntent)
                } catch (e: Exception) {
                    // fallback open app
                    val open = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(open)
                }
                // schedule a refresh after a short delay
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    updateAll(context)
                }, 800)
            }
        }
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_spv)
        val connected = SpvVpnService.isRunning
        val repo = ConfigRepository(context)
        val active = repo.getActive()

        views.setTextViewText(
            R.id.tvWidgetStatus,
            if (connected) "SPV • متصل" else "SPV • قطع"
        )
        views.setTextViewText(
            R.id.btnWidgetToggle,
            if (connected) "قطع" else "وصل"
        )


        // Open app
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPi = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetRoot, openPi)

        // Toggle button
        val toggleIntent = Intent(context, SpvWidgetProvider::class.java).apply {
            action = ACTION_WIDGET_TOGGLE
        }
        val togglePi = PendingIntent.getBroadcast(
            context, 1, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btnWidgetToggle, togglePi)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
