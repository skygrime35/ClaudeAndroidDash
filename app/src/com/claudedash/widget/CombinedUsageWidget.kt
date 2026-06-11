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

class CombinedUsageWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val snapshot = ServiceLocator.usageRepository.read()
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(
                id,
                WidgetRenderer.renderCombinedSnapshot(context, snapshot)
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return

        if (action == ACTION_REFRESH) {
            ServiceLocator.refreshTrigger(context).trigger("combined")

            val mgr = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, CombinedUsageWidget::class.java)
            for (id in mgr.getAppWidgetIds(component)) {
                mgr.updateAppWidget(id, WidgetRenderer.renderCombinedRefreshing(context))
            }
            val handler = Handler(Looper.getMainLooper())
            val rerender = Runnable {
                val snapshot = ServiceLocator.usageRepository.read()
                for (id in mgr.getAppWidgetIds(component)) {
                    mgr.updateAppWidget(id, WidgetRenderer.renderCombinedSnapshot(context, snapshot))
                }
            }
            for (delay in REFRESH_RENDER_DELAYS_MS) handler.postDelayed(rerender, delay)
        } else if (action == ACTION_UPDATE_ALL) {
            val mgr = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, CombinedUsageWidget::class.java)
            val snapshot = ServiceLocator.usageRepository.read()
            for (id in mgr.getAppWidgetIds(component)) {
                mgr.updateAppWidget(id, WidgetRenderer.renderCombinedSnapshot(context, snapshot))
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.claudedash.widget.ACTION_REFRESH_COMBINED"
        const val ACTION_UPDATE_ALL = "com.claudedash.widget.ACTION_UPDATE_ALL"
        // Combined refresh runs both Claude (1-2s) and Gemini (~11s) in parallel.
        // Render at 4s (for Claude fast-update), 8s, and 12s (for Gemini).
        private val REFRESH_RENDER_DELAYS_MS = longArrayOf(4_000L, 8_000L, 12_000L)
    }
}
