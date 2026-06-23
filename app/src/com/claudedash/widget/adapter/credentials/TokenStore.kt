package com.claudedash.widget.adapter.credentials

import android.content.Context
import android.content.SharedPreferences

class TokenStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("claude_dash_auth", Context.MODE_PRIVATE)

    var claudeAccessToken: String?
        get() = prefs.getString("claude_access_token", null)
        set(value) = prefs.edit().putString("claude_access_token", value).apply()

    var claudeRefreshToken: String?
        get() = prefs.getString("claude_refresh_token", null)
        set(value) = prefs.edit().putString("claude_refresh_token", value).apply()

    var claudeExpiresAt: Long
        get() = prefs.getLong("claude_expires_at", 0L)
        set(value) = prefs.edit().putLong("claude_expires_at", value).apply()

    var googleAccountName: String?
        get() = prefs.getString("google_account_name", null)
        set(value) = prefs.edit().putString("google_account_name", value).apply()

    var lastUsageSnapshotJson: String?
        get() = prefs.getString("last_usage_snapshot", null)
        set(value) = prefs.edit().putString("last_usage_snapshot", value).apply()
}
