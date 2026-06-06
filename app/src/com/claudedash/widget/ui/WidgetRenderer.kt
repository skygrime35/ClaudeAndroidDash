package com.claudedash.widget.ui

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.claudedash.widget.R
import com.claudedash.widget.UsageWidget
import com.claudedash.widget.di.ServiceLocator
import com.claudedash.widget.domain.model.UsageSnapshot

object WidgetRenderer {

    fun renderSnapshot(context: Context, snapshot: UsageSnapshot?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_usage)
        if (snapshot == null) renderEmpty(views) else renderData(views, snapshot)
        views.setOnClickPendingIntent(R.id.widget_root, refreshIntent(context))
        return views
    }

    fun renderRefreshing(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_usage)
        views.setProgressBar(R.id.block_bar, 100, 0, true)
        views.setProgressBar(R.id.week_bar, 100, 0, true)
        views.setTextViewText(R.id.block_remaining, "")
        views.setTextViewText(R.id.block_pct, "…")
        views.setTextViewText(R.id.week_remaining, "")
        views.setTextViewText(R.id.week_pct, "…")
        views.setOnClickPendingIntent(R.id.widget_root, refreshIntent(context))
        return views
    }

    private fun renderEmpty(views: RemoteViews) {
        views.setTextViewText(R.id.block_remaining, "Open Claude Code")
        views.setTextViewText(R.id.block_pct, "—")
        views.setProgressBar(R.id.block_bar, 100, 0, false)
        views.setTextViewText(R.id.week_remaining, "")
        views.setTextViewText(R.id.week_pct, "—")
        views.setProgressBar(R.id.week_bar, 100, 0, false)
    }

    private fun renderData(views: RemoteViews, snapshot: UsageSnapshot) {
        val fmt = ServiceLocator.formatter
        val fiveH = fmt.remaining(snapshot.fiveHourResetsAt)
        views.setTextViewText(R.id.block_remaining,
            if (fiveH.isNotEmpty()) "reset $fiveH" else "")
        views.setProgressBar(R.id.block_bar, 100,
            (snapshot.fiveHourPercent ?: 0.0).toInt(), false)
        views.setTextViewText(R.id.block_pct, fmt.percent(snapshot.fiveHourPercent))

        val sevenD = fmt.remaining(snapshot.sevenDayResetsAt)
        views.setTextViewText(R.id.week_remaining,
            if (sevenD.isNotEmpty()) "reset $sevenD" else "")
        views.setProgressBar(R.id.week_bar, 100,
            (snapshot.sevenDayPercent ?: 0.0).toInt(), false)
        views.setTextViewText(R.id.week_pct, fmt.percent(snapshot.sevenDayPercent))
    }

    private fun refreshIntent(context: Context): PendingIntent {
        val intent = Intent(context, UsageWidget::class.java).apply {
            action = UsageWidget.ACTION_REFRESH
        }
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
