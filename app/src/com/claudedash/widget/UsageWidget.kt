package com.claudedash.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.claudedash.widget.di.ServiceLocator
import com.claudedash.widget.ui.WidgetRenderer

class UsageWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val snapshot = ServiceLocator.usageRepository.read()
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(
                id,
                WidgetRenderer.renderSnapshot(context, snapshot)
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_REFRESH) return

        ServiceLocator.refreshTrigger(context).trigger()

        val mgr = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, UsageWidget::class.java)
        for (id in mgr.getAppWidgetIds(component)) {
            mgr.updateAppWidget(id, WidgetRenderer.renderRefreshing(context))
        }
        val handler = Handler(Looper.getMainLooper())
        val rerender = Runnable {
            val snapshot = ServiceLocator.usageRepository.read()
            for (id in mgr.getAppWidgetIds(component)) {
                mgr.updateAppWidget(id, WidgetRenderer.renderSnapshot(context, snapshot))
            }
        }
        for (delay in REFRESH_RENDER_DELAYS_MS) handler.postDelayed(rerender, delay)
    }

    companion object {
        const val ACTION_REFRESH = "com.claudedash.widget.ACTION_REFRESH"
        // The API refresh runs inside the PRoot distro: warm ~3s, cold start ~8s.
        // Re-render twice so a warm hit feels snappy and a cold start still lands.
        private val REFRESH_RENDER_DELAYS_MS = longArrayOf(3_500L, 8_000L)
    }
}
