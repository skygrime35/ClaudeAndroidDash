package com.claudedash.widget.adapter.refresh

import android.content.Context
import android.content.Intent
import com.claudedash.widget.domain.port.RefreshTrigger

class TermuxRefreshTrigger(private val context: Context) : RefreshTrigger {

    override fun trigger() {
        try {
            val intent = Intent().apply {
                setClassName("com.termux", "com.termux.app.RunCommandService")
                action = "com.termux.RUN_COMMAND"
                putExtra("com.termux.RUN_COMMAND_PATH", PYTHON_BIN)
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf(PARSER_SCRIPT))
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            }
            context.startService(intent)
        } catch (_: Throwable) {
        }
    }

    companion object {
        private const val PYTHON_BIN = "/data/data/com.termux/files/usr/bin/python3"
        private const val PARSER_SCRIPT =
            "/data/data/com.termux/files/home/Projects/ClaudeAndroidDash/parser/claude_usage.py"
    }
}
