package com.claudedash.widget.adapter.repository

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import com.claudedash.widget.adapter.credentials.TokenStore
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

data class PartialGoogleUsage(
    val gemini5hPct: Int?,
    val gemini5hReset: Long?,
    val geminiWeekPct: Int?,
    val geminiWeekReset: Long?,
    val claudeG5hPct: Int?,
    val claudeG5hReset: Long?,
    val claudeGWeekPct: Int?,
    val claudeGWeekReset: Long?
)

class GoogleQuotaRepository(private val context: Context, private val tokenStore: TokenStore) {

    fun fetch(): PartialGoogleUsage? {
        val accountName = tokenStore.googleAccountName ?: return null
        val account = Account(accountName, "com.google")
        val am = AccountManager.get(context)

        val token = try {
            val bundle = am.getAuthToken(account, "oauth2:https://www.googleapis.com/auth/cloud-platform", null, false, null, null).result
            bundle.getString(AccountManager.KEY_AUTHTOKEN)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } ?: return null

        try {
            val url = URL("https://daily-cloudcode-pa.googleapis.com/v1internal:retrieveUserQuotaSummary")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("User-Agent", "antigravity-cli")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            OutputStreamWriter(conn.outputStream).use { it.write("{}") }

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                return parseQuota(response)
            } else if (conn.responseCode == 401) {
                am.invalidateAuthToken("com.google", token)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun parseQuota(jsonStr: String): PartialGoogleUsage {
        val root = JSONObject(jsonStr)
        val summaries = root.optJSONArray("userQuotaSummaries") ?: return PartialGoogleUsage(null, null, null, null, null, null, null, null)

        var gem5hP: Int? = null; var gem5hR: Long? = null
        var gemWkP: Int? = null; var gemWkR: Long? = null
        var cla5hP: Int? = null; var cla5hR: Long? = null
        var claWkP: Int? = null; var claWkR: Long? = null

        for (i in 0 until summaries.length()) {
            val entry = summaries.getJSONObject(i)
            val limits = entry.optJSONArray("quotaLimitSummaries") ?: continue
            val models = entry.optJSONArray("modelTags") ?: continue
            var isClaude = false
            var isGemini = false
            for (j in 0 until models.length()) {
                val tag = models.getString(j)
                if (tag.lowercase().contains("claude")) isClaude = true
                if (tag.lowercase().contains("gemini")) isGemini = true
            }

            for (k in 0 until limits.length()) {
                val limit = limits.getJSONObject(k)
                val total = limit.optInt("totalQuota", 0)
                val remaining = limit.optInt("remainingQuota", 0)
                val resetStr = limit.optString("nextResetTime")
                if (total == 0) continue

                val pct = ((remaining.toDouble() / total.toDouble()) * 100).roundToInt()
                val resetEpoch = parseIso(resetStr)

                val duration = limit.optString("duration")
                val is5h = duration == "18000s"
                val isWeek = duration == "604800s"

                if (isGemini) {
                    if (is5h) { gem5hP = pct; gem5hR = resetEpoch }
                    if (isWeek) { gemWkP = pct; gemWkR = resetEpoch }
                } else if (isClaude) {
                    if (is5h) { cla5hP = pct; cla5hR = resetEpoch }
                    if (isWeek) { claWkP = pct; claWkR = resetEpoch }
                }
            }
        }

        return PartialGoogleUsage(gem5hP, gem5hR, gemWkP, gemWkR, cla5hP, cla5hR, claWkP, claWkR)
    }

    private fun parseIso(isoStr: String): Long? {
        if (isoStr.isEmpty()) return null
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            sdf.parse(isoStr)?.time?.div(1000)
        } catch (e: Exception) {
            null
        }
    }
}
