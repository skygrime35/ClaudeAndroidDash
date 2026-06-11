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

class GeminiUsageWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val snapshot = ServiceLocator.usageRepository.read()
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(
                id,
                WidgetRenderer.renderGeminiSnapshot(context, snapshot)
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return

        if (action == ACTION_REFRESH) {
            ServiceLocator.refreshTrigger(context).trigger("gemini")

            val mgr = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, GeminiUsageWidget::class.java)
            for (id in mgr.getAppWidgetIds(component)) {
                mgr.updateAppWidget(id, WidgetRenderer.renderGeminiRefreshing(context))
            }
            val handler = Handler(Looper.getMainLooper())
            val rerender = Runnable {
                val snapshot = ServiceLocator.usageRepository.read()
                for (id in mgr.getAppWidgetIds(component)) {
                    mgr.updateAppWidget(id, WidgetRenderer.renderGeminiSnapshot(context, snapshot))
                }
            }
            for (delay in REFRESH_RENDER_DELAYS_MS) handler.postDelayed(rerender, delay)
        } else if (action == ACTION_UPDATE_ALL) {
            val mgr = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, GeminiUsageWidget::class.java)
            val snapshot = ServiceLocator.usageRepository.read()
            for (id in mgr.getAppWidgetIds(component)) {
                mgr.updateAppWidget(id, WidgetRenderer.renderGeminiSnapshot(context, snapshot))
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.claudedash.widget.ACTION_REFRESH_GEMINI"
        const val ACTION_UPDATE_ALL = "com.claudedash.widget.ACTION_UPDATE_ALL"
        // Gemini lance agy (CLI lourde) via proot-distro : ~25s. On re-render à 12s puis 26s
        // (le broadcast final du script reste la garantie principale de mise à jour).
        private val REFRESH_RENDER_DELAYS_MS = longArrayOf(12_000L, 26_000L)
    }
}
