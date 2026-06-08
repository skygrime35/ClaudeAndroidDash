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
                putExtra("com.termux.RUN_COMMAND_PATH", PROOT_DISTRO_BIN)
                putExtra(
                    "com.termux.RUN_COMMAND_ARGUMENTS",
                    arrayOf("login", DISTRO, "--", "bash", API_SCRIPT)
                )
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            }
            context.startService(intent)
        } catch (_: Throwable) {
        }
    }

    companion object {
        // Claude Code (and its credentials) live inside the PRoot distro, but the
        // widget can only trigger host-Termux commands — so we log into the distro.
        private const val PROOT_DISTRO_BIN = "/data/data/com.termux/files/usr/bin/proot-distro"
        private const val DISTRO = "ubuntu"
        private const val API_SCRIPT =
            "/data/data/com.termux/files/home/Projects/ClaudeAndroidDash/parser/claude_usage_api.sh"
    }
}
