package com.claudedash.widget.adapter.repository

import android.os.Environment
import com.claudedash.widget.domain.model.UsageSnapshot
import com.claudedash.widget.domain.port.UsageRepository
import org.json.JSONObject
import java.io.File

/**
 * Lit le fichier unique /sdcard/Download/usage.json :
 *   { "claude": { updated_at, source, model, context_pct, session_cost_usd,
 *                 five_hour:{used_pct,resets_at}, seven_day:{used_pct,resets_at} },
 *     "google": { updated_at, credits, gemini_5h_pct/reset, gemini_week_pct/reset,
 *                 claude_5h_pct/reset, claude_week_pct/reset } }
 */
class JsonFileUsageRepository : UsageRepository {

    override fun read(): UsageSnapshot? {
        val file = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "usage.json"
        )
        if (!file.exists()) return null
        val root = try { JSONObject(file.readText()) } catch (_: Throwable) { return null }

        val claude = root.optJSONObject("claude")
        val google = root.optJSONObject("google")
        if (claude == null && google == null) return null

        // --- Anthropic (Claude) : 5h / 7j ---
        val fh = claude?.optJSONObject("five_hour")
        val sd = claude?.optJSONObject("seven_day")

        // --- Google : crédits + Gemini/Claude 5h/semaine ---
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
            geminiCredits = google?.optIntOrNull("credits"),
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
