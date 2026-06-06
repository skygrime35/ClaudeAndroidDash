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
    val sevenDayResetsAt: Long?
)
