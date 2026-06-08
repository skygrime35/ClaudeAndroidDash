package com.claudedash.widget.adapter.repository

import android.os.Environment
import com.claudedash.widget.domain.model.UsageSnapshot
import com.claudedash.widget.domain.port.UsageRepository
import org.json.JSONObject
import java.io.File

class JsonFileUsageRepository : UsageRepository {

    override fun read(): UsageSnapshot? {
        val file = jsonFile()
        if (!file.exists()) return null
        return try {
            val root = JSONObject(file.readText())
            when (root.optString("source", "")) {
                "statusline", "api" -> parseStatusline(root)
                else -> parseLegacy(root)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun parseStatusline(root: JSONObject): UsageSnapshot {
        val fh = root.optJSONObject("five_hour")
        val sd = root.optJSONObject("seven_day")
        return UsageSnapshot(
            updatedAt = root.optString("updated_at", ""),
            source = "statusline",
            model = root.optString("model", "").takeIf { it.isNotEmpty() },
            contextPercent = root.optDoubleOrNull("context_pct"),
            sessionCostUsd = root.optDouble("session_cost_usd", 0.0),
            fiveHourPercent = fh?.optDoubleOrNull("used_pct"),
            fiveHourResetsAt = fh?.optLong("resets_at", 0L)?.takeIf { it > 0 },
            sevenDayPercent = sd?.optDoubleOrNull("used_pct"),
            sevenDayResetsAt = sd?.optLong("resets_at", 0L)?.takeIf { it > 0 }
        )
    }

    private fun parseLegacy(root: JSONObject): UsageSnapshot {
        val block = root.optJSONObject("block_5h")
        val week = root.optJSONObject("week")
        val total = week?.optJSONObject("all_models")
        return UsageSnapshot(
            updatedAt = root.optString("updated_at", ""),
            source = "legacy",
            model = null,
            contextPercent = null,
            sessionCostUsd = total?.optDouble("cost_usd", 0.0) ?: 0.0,
            fiveHourPercent = block?.optDoubleOrNull("sonnet_pct_used"),
            fiveHourResetsAt = null,
            sevenDayPercent = total?.optDoubleOrNull("pct_used"),
            sevenDayResetsAt = null
        )
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return try { getDouble(key) } catch (_: Throwable) { null }
    }

    companion object {
        private const val FILENAME = "claude_usage.json"

        fun jsonFile(): File = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            FILENAME
        )
    }
}
