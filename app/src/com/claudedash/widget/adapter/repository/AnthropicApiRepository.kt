package com.claudedash.widget.adapter.repository

import com.claudedash.widget.adapter.credentials.TokenStore
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

data class PartialClaudeUsage(
    val updatedAt: String,
    val source: String,
    val fiveHourPercent: Double,
    val fiveHourResetsAt: Long,
    val sevenDayPercent: Double,
    val sevenDayResetsAt: Long
)

class AnthropicApiRepository(private val tokenStore: TokenStore) {

    fun fetch(): PartialClaudeUsage? {
        // claudeAccessToken now stores the API key (sk-ant-api03-...)
        val apiKey = tokenStore.claudeAccessToken?.takeIf { it.startsWith("sk-ant-api") }
            ?: return null

        try {
            val url = URL("https://api.anthropic.com/v1/messages")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("x-api-key", apiKey)
            conn.setRequestProperty("anthropic-version", "2023-06-01")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.doOutput = true

            // Minimal request — we only care about the rate-limit headers in the response
            val body = """{"model":"claude-haiku-4-5","max_tokens":1,"messages":[{"role":"user","content":"hi"}]}"""
            OutputStreamWriter(conn.outputStream).use { it.write(body) }

            val code = conn.responseCode
            if (code != 200) {
                android.util.Log.e("AnthropicRepo", "HTTP $code: ${conn.errorStream?.bufferedReader()?.readText()}")
                return null
            }

            // The unified rate-limit headers are the same ones Claude Code reads
            val fhU = conn.getHeaderField("anthropic-ratelimit-unified-5h-utilization")?.toDoubleOrNull() ?: 0.0
            val fhR = conn.getHeaderField("anthropic-ratelimit-unified-5h-reset")?.toLongOrNull() ?: 0L
            val sdU = conn.getHeaderField("anthropic-ratelimit-unified-7d-utilization")?.toDoubleOrNull() ?: 0.0
            val sdR = conn.getHeaderField("anthropic-ratelimit-unified-7d-reset")?.toLongOrNull() ?: 0L

            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }

            return PartialClaudeUsage(
                updatedAt = sdf.format(Date()),
                source = "api",
                fiveHourPercent = (fhU * 100).roundToInt().toDouble(),
                fiveHourResetsAt = fhR,
                sevenDayPercent = (sdU * 100).roundToInt().toDouble(),
                sevenDayResetsAt = sdR
            )
        } catch (e: Exception) {
            android.util.Log.e("AnthropicRepo", "fetch error", e)
            return null
        }
    }
}
