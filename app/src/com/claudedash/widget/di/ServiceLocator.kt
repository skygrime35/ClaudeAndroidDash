package com.claudedash.widget.di

import android.content.Context
import com.claudedash.widget.adapter.clock.RealClock
import com.claudedash.widget.adapter.refresh.DirectRefreshTrigger
import com.claudedash.widget.domain.model.UsageSnapshot
import com.claudedash.widget.domain.port.Clock
import com.claudedash.widget.domain.port.RefreshTrigger
import com.claudedash.widget.domain.port.UsageRepository
import com.claudedash.widget.domain.usecase.UsageFormatter
import org.json.JSONObject
import java.io.File

object ServiceLocator {

    val clock: Clock = RealClock()
    val formatter: UsageFormatter = UsageFormatter(clock)

    val usageRepository: UsageRepository = object : UsageRepository {
        override fun read(): UsageSnapshot? {
            val file = File("/data/data/com.claudedash.widget/files/usage.json")
            if (!file.exists()) return null
            val root = try { JSONObject(file.readText()) } catch (_: Throwable) { return null }

            val claude = root.optJSONObject("claude")
            val google = root.optJSONObject("google")
            if (claude == null && google == null) return null

            val fh = claude?.optJSONObject("five_hour")
            val sd = claude?.optJSONObject("seven_day")

            return UsageSnapshot(
                updatedAt = claude?.optString("updated_at", "")?.takeIf { it.isNotEmpty() }
                    ?: google?.optString("updated_at", "") ?: "",
                source = claude?.optString("source", "api") ?: "api",
                model = claude?.optString("model", "")?.takeIf { it.isNotEmpty() },
                contextPercent = claude?.optDoubleOrNull("context_pct"),
                sessionCostUsd = claude?.optDouble("session_cost_usd", 0.0) ?: 0.0,
                fiveHourPercent = fh?.optDoubleOrNull("used_pct"),
                fiveHourResetsAt = fh?.optLong("resets_at", 0L)?.takeIf { it > 0 },
                sevenDayPercent = sd?.optDoubleOrNull("used_pct"),
                sevenDayResetsAt = sd?.optLong("resets_at", 0L)?.takeIf { it > 0 },
                geminiUpdatedAt = google?.optString("updated_at", null),
                gemini5hPct = google?.optIntOrNull("gemini_5h_pct"),
                gemini5hReset = google?.optLongOrNull("gemini_5h_reset"),
                geminiWeekPct = google?.optIntOrNull("gemini_week_pct"),
                geminiWeekReset = google?.optLongOrNull("gemini_week_reset"),
                claudeG5hPct = google?.optIntOrNull("claude_5h_pct"),
                claudeG5hReset = google?.optLongOrNull("claude_5h_reset"),
                claudeGWeekPct = google?.optIntOrNull("claude_week_pct"),
                claudeGWeekReset = google?.optLongOrNull("claude_week_reset")
            )
        }

        private fun JSONObject.optDoubleOrNull(key: String): Double? {
            if (!has(key) || isNull(key)) return null
            return try { getDouble(key) } catch (_: Throwable) { null }
        }

        private fun JSONObject.optIntOrNull(key: String): Int? {
            if (!has(key) || isNull(key)) return null
            return try { getInt(key) } catch (_: Throwable) { null }
        }

        private fun JSONObject.optLongOrNull(key: String): Long? {
            if (!has(key) || isNull(key)) return null
            return try { getLong(key) } catch (_: Throwable) { null }
        }
    }

    fun refreshTrigger(context: Context): RefreshTrigger = DirectRefreshTrigger(context)
}
