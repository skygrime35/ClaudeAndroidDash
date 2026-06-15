package com.claudedash.widget.domain.model

data class UsageSnapshot(
    val updatedAt: String,
    val source: String,
    val model: String?,
    val contextPercent: Double?,
    val sessionCostUsd: Double,
    val fiveHourPercent: Double?,
    val fiveHourResetsAt: Long?,
    val sevenDayPercent: Double?,
    val sevenDayResetsAt: Long?,
    val geminiUpdatedAt: String?,
    // Quota Google via l'API Code Assist (retrieveUserQuotaSummary) : % RESTANT par fenêtre.
    // Gemini (modèles Google) et Claude/3p (Claude & GPT inclus dans l'abo Google).
    val gemini5hPct: Int? = null,
    val gemini5hReset: Long? = null,
    val geminiWeekPct: Int? = null,
    val geminiWeekReset: Long? = null,
    val claudeG5hPct: Int? = null,
    val claudeG5hReset: Long? = null,
    val claudeGWeekPct: Int? = null,
    val claudeGWeekReset: Long? = null
)
