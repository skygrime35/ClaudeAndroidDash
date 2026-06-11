package com.claudedash.widget.ui

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.claudedash.widget.R
import com.claudedash.widget.UsageWidget
import com.claudedash.widget.GeminiUsageWidget
import com.claudedash.widget.CombinedUsageWidget
import com.claudedash.widget.RefreshActivity
import com.claudedash.widget.di.ServiceLocator
import com.claudedash.widget.domain.model.UsageSnapshot

object WidgetRenderer {

    // Les 4 lignes de quota Google : Gemini 5h, Gemini semaine, Claude 5h, Claude semaine.
    private val GOOGLE_BARS = intArrayOf(R.id.gem5_bar, R.id.gemw_bar, R.id.clg5_bar, R.id.clgw_bar)
    private val GOOGLE_VALS = intArrayOf(R.id.gem5_val, R.id.gemw_val, R.id.clg5_val, R.id.clgw_val)
    private val GOOGLE_SUBS = intArrayOf(R.id.gem5_sub, R.id.gemw_sub, R.id.clg5_sub, R.id.clgw_sub)

    // --- Claude Widget ---

    fun renderSnapshot(context: Context, snapshot: UsageSnapshot?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_usage)
        if (hasClaude(snapshot)) renderClaudeData(views, snapshot!!) else renderClaudeEmpty(views)
        views.setOnClickPendingIntent(R.id.widget_root, refreshIntent(context))
        return views
    }

    private fun hasClaude(s: UsageSnapshot?): Boolean =
        s != null && (s.fiveHourPercent != null || s.sevenDayPercent != null)

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

    private fun renderClaudeEmpty(views: RemoteViews) {
        views.setTextViewText(R.id.block_remaining, "Open Claude Code")
        views.setTextViewText(R.id.block_pct, "—")
        views.setProgressBar(R.id.block_bar, 100, 0, false)
        views.setTextViewText(R.id.week_remaining, "")
        views.setTextViewText(R.id.week_pct, "—")
        views.setProgressBar(R.id.week_bar, 100, 0, false)
    }

    private fun renderClaudeData(views: RemoteViews, snapshot: UsageSnapshot) {
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
        val intent = Intent(context, RefreshActivity::class.java).apply {
            putExtra("target", "claude")
        }
        return PendingIntent.getActivity(
            context, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }


    // --- Gemini Widget ---

    fun renderGeminiSnapshot(context: Context, snapshot: UsageSnapshot?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_gemini)
        if (hasGoogle(snapshot)) renderGeminiData(views, snapshot!!) else renderGeminiEmpty(views)
        views.setOnClickPendingIntent(R.id.widget_root, refreshGeminiIntent(context))
        return views
    }

    fun renderGeminiRefreshing(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_gemini)
        refreshingGoogle(views)
        views.setOnClickPendingIntent(R.id.widget_root, refreshGeminiIntent(context))
        return views
    }

    private fun refreshingGoogle(views: RemoteViews) {
        views.setProgressBar(R.id.gcred_bar, 1000, 0, true)
        views.setTextViewText(R.id.gcred_val, "…")
        views.setTextViewText(R.id.gcred_sub, "")
        for (bar in GOOGLE_BARS) views.setProgressBar(bar, 100, 0, true)
        for (v in GOOGLE_VALS) views.setTextViewText(v, "…")
        for (s in GOOGLE_SUBS) views.setTextViewText(s, "")
    }

    private fun hasGoogle(s: UsageSnapshot?): Boolean =
        s != null && (s.gemini5hPct != null || s.geminiWeekPct != null ||
            s.claudeG5hPct != null || s.claudeGWeekPct != null)

    private fun renderGeminiEmpty(views: RemoteViews) {
        views.setProgressBar(R.id.gcred_bar, 1000, 0, false)
        views.setTextViewText(R.id.gcred_val, "—")
        views.setTextViewText(R.id.gcred_sub, "")
        for (bar in GOOGLE_BARS) views.setProgressBar(bar, 100, 0, false)
        for (v in GOOGLE_VALS) views.setTextViewText(v, "—")
        for (s in GOOGLE_SUBS) views.setTextViewText(s, "")
    }

    private fun renderGeminiData(views: RemoteViews, s: UsageSnapshot) {
        // Ligne Crédits IA (en haut, échelle ~1000)
        val credits = s.geminiCredits
        views.setTextViewText(R.id.gcred_val, if (credits != null) "$credits" else "—")
        views.setProgressBar(R.id.gcred_bar, 1000, credits ?: 0, false)
        views.setTextViewText(R.id.gcred_sub, if (credits != null) "crédits IA" else "")
        googleLine(views, R.id.gem5_bar, R.id.gem5_val, R.id.gem5_sub, s.gemini5hPct, s.gemini5hReset)
        googleLine(views, R.id.gemw_bar, R.id.gemw_val, R.id.gemw_sub, s.geminiWeekPct, s.geminiWeekReset)
        googleLine(views, R.id.clg5_bar, R.id.clg5_val, R.id.clg5_sub, s.claudeG5hPct, s.claudeG5hReset)
        googleLine(views, R.id.clgw_bar, R.id.clgw_val, R.id.clgw_sub, s.claudeGWeekPct, s.claudeGWeekReset)
    }

    // % RESTANT : la barre se remplit de ce qu'il reste, + heure de reset en sous-texte.
    private fun googleLine(views: RemoteViews, barId: Int, valId: Int, subId: Int,
                           pct: Int?, resetEpoch: Long?) {
        views.setTextViewText(valId, if (pct != null) "$pct%" else "—")
        views.setProgressBar(barId, 100, pct ?: 0, false)
        val r = ServiceLocator.formatter.remaining(resetEpoch)
        views.setTextViewText(subId, if (r.isNotEmpty()) "reset $r" else "")
    }

    private fun refreshGeminiIntent(context: Context): PendingIntent {
        val intent = Intent(context, RefreshActivity::class.java).apply {
            putExtra("target", "gemini")
        }
        return PendingIntent.getActivity(
            context, 2, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }


    // --- Combined Widget ---

    fun renderCombinedSnapshot(context: Context, snapshot: UsageSnapshot?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_combined)
        
        // Render Claude side
        if (hasClaude(snapshot)) renderClaudeData(views, snapshot!!) else renderClaudeEmpty(views)

        // Render Gemini side
        if (hasGoogle(snapshot)) renderGeminiData(views, snapshot!!) else renderGeminiEmpty(views)

        views.setOnClickPendingIntent(R.id.widget_root, refreshCombinedIntent(context))
        return views
    }

    fun renderCombinedRefreshing(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_combined)
        
        // Claude side refreshing
        views.setProgressBar(R.id.block_bar, 100, 0, true)
        views.setProgressBar(R.id.week_bar, 100, 0, true)
        views.setTextViewText(R.id.block_remaining, "")
        views.setTextViewText(R.id.block_pct, "…")
        views.setTextViewText(R.id.week_remaining, "")
        views.setTextViewText(R.id.week_pct, "…")

        // Gemini side refreshing
        refreshingGoogle(views)

        views.setOnClickPendingIntent(R.id.widget_root, refreshCombinedIntent(context))
        return views
    }

    private fun refreshCombinedIntent(context: Context): PendingIntent {
        val intent = Intent(context, RefreshActivity::class.java).apply {
            putExtra("target", "combined")
        }
        return PendingIntent.getActivity(
            context, 3, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
