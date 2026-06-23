package com.claudedash.widget.adapter.refresh

import android.content.Context
import android.content.Intent
import com.claudedash.widget.domain.port.RefreshTrigger

class DirectRefreshTrigger(private val context: Context) : RefreshTrigger {
    override fun trigger(source: String) {
        val intent = Intent(context, ApiRefreshService::class.java)
        context.startService(intent)
    }
}
