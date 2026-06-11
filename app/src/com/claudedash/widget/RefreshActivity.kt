package com.claudedash.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.widget.RemoteViews
import com.claudedash.widget.di.ServiceLocator
import com.claudedash.widget.ui.WidgetRenderer
import java.io.File

/**
 * Lancée au clic d'un widget. Déclenche le refresh (le démon écoute le journal), affiche
 * "…", puis FINIT IMMÉDIATEMENT pour ne pas geler le téléphone (une Activity qui reste au
 * premier plan met le lanceur en pause). Le re-render avec les vraies données est fait par
 * le broadcast du script (parser/notify_widgets.sh → onReceive ACTION_UPDATE_ALL), dans un
 * process frais qui voit le JSON à jour.
 */
class RefreshActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = intent?.getStringExtra("target") ?: "claude"
        val mgr = AppWidgetManager.getInstance(this)

        // Déclenche le démon Termux via le journal qu'il surveille.
        try {
            File(TRIGGER_LOG).appendText(
                "${System.currentTimeMillis()} RefreshActivity onCreate target=$target\n")
        } catch (_: Throwable) {}
        // Best-effort : tente aussi le service Termux (sans effet s'il est bloqué).
        try { ServiceLocator.refreshTrigger(this).trigger(target) } catch (_: Throwable) {}

        // Affiche "…" puis finit aussitôt → pas de gel de l'interface.
        renderRefreshing(mgr, target)
        finish()
    }

    private fun renderRefreshing(mgr: AppWidgetManager, target: String) = when (target) {
        "gemini" -> update(mgr, GeminiUsageWidget::class.java) { WidgetRenderer.renderGeminiRefreshing(this) }
        "combined" -> update(mgr, CombinedUsageWidget::class.java) { WidgetRenderer.renderCombinedRefreshing(this) }
        else -> update(mgr, UsageWidget::class.java) { WidgetRenderer.renderRefreshing(this) }
    }

    private fun update(mgr: AppWidgetManager, cls: Class<*>, render: () -> RemoteViews) {
        val comp = ComponentName(this, cls)
        for (id in mgr.getAppWidgetIds(comp)) mgr.updateAppWidget(id, render())
    }

    companion object {
        private const val TRIGGER_LOG = "/sdcard/Download/widget_refresh.log"
    }
}
