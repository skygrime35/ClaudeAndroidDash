package com.claudedash.widget.adapter.refresh

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.claudedash.widget.UsageWidget
import com.claudedash.widget.adapter.credentials.TokenStore
import com.claudedash.widget.adapter.repository.AnthropicApiRepository
import com.claudedash.widget.adapter.repository.GoogleQuotaRepository
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ApiRefreshService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Thread {
            try {
                val tokenStore = TokenStore(this)
                val anthropicRepo = AnthropicApiRepository(tokenStore)
                val googleRepo = GoogleQuotaRepository(this, tokenStore)

                val claudeData = anthropicRepo.fetch()
                val googleData = googleRepo.fetch()

                if (claudeData != null || googleData != null) {
                    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                    val now = sdf.format(Date())

                    val json = JSONObject()

                    if (claudeData != null) {
                        val claudeObj = JSONObject()
                        claudeObj.put("updated_at", claudeData.updatedAt)
                        claudeObj.put("source", claudeData.source)
                        claudeObj.put("model", "claude-haiku (api)")
                        claudeObj.put("context_pct", 0.0)
                        claudeObj.put("session_cost_usd", 0.0)

                        val fh = JSONObject()
                        fh.put("used_pct", claudeData.fiveHourPercent)
                        fh.put("resets_at", claudeData.fiveHourResetsAt)
                        claudeObj.put("five_hour", fh)

                        val sd = JSONObject()
                        sd.put("used_pct", claudeData.sevenDayPercent)
                        sd.put("resets_at", claudeData.sevenDayResetsAt)
                        claudeObj.put("seven_day", sd)

                        json.put("claude", claudeObj)
                    }

                    if (googleData != null) {
                        val googleObj = JSONObject()
                        googleObj.put("updated_at", now)
                        googleData.gemini5hPct?.let { googleObj.put("gemini_5h_pct", it) }
                        googleData.gemini5hReset?.let { googleObj.put("gemini_5h_reset", it) }
                        googleData.geminiWeekPct?.let { googleObj.put("gemini_week_pct", it) }
                        googleData.geminiWeekReset?.let { googleObj.put("gemini_week_reset", it) }
                        googleData.claudeG5hPct?.let { googleObj.put("claude_5h_pct", it) }
                        googleData.claudeG5hReset?.let { googleObj.put("claude_5h_reset", it) }
                        googleData.claudeGWeekPct?.let { googleObj.put("claude_week_pct", it) }
                        googleData.claudeGWeekReset?.let { googleObj.put("claude_week_reset", it) }
                        json.put("google", googleObj)
                    }

                    val file = java.io.File("/data/data/com.claudedash.widget/files/usage.json")
                    file.parentFile?.mkdirs()
                    file.writeText(json.toString())

                    sendBroadcast(Intent(this@ApiRefreshService, UsageWidget::class.java).apply { action = UsageWidget.ACTION_UPDATE_ALL })
                    sendBroadcast(Intent(this@ApiRefreshService, com.claudedash.widget.GeminiUsageWidget::class.java).apply { action = UsageWidget.ACTION_UPDATE_ALL })
                    sendBroadcast(Intent(this@ApiRefreshService, com.claudedash.widget.CombinedUsageWidget::class.java).apply { action = UsageWidget.ACTION_UPDATE_ALL })
                } else {
                    sendBroadcast(Intent(this@ApiRefreshService, UsageWidget::class.java).apply { action = UsageWidget.ACTION_UPDATE_ALL })
                    sendBroadcast(Intent(this@ApiRefreshService, com.claudedash.widget.GeminiUsageWidget::class.java).apply { action = UsageWidget.ACTION_UPDATE_ALL })
                    sendBroadcast(Intent(this@ApiRefreshService, com.claudedash.widget.CombinedUsageWidget::class.java).apply { action = UsageWidget.ACTION_UPDATE_ALL })
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                stopSelf(startId)
            }
        }.start()

        return START_NOT_STICKY
    }
}
