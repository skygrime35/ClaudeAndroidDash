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
        Handler(Looper.getMainLooper()).postDelayed({
            val snapshot = ServiceLocator.usageRepository.read()
            for (id in mgr.getAppWidgetIds(component)) {
                mgr.updateAppWidget(id, WidgetRenderer.renderSnapshot(context, snapshot))
            }
        }, REFRESH_RENDER_DELAY_MS)
    }

    companion object {
        const val ACTION_REFRESH = "com.claudedash.widget.ACTION_REFRESH"
        private const val REFRESH_RENDER_DELAY_MS = 2_500L
    }
}
