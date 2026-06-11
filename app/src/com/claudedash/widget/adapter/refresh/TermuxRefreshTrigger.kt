package com.claudedash.widget.adapter.refresh

import android.content.Context
import android.content.Intent
import android.os.Build
import com.claudedash.widget.domain.port.RefreshTrigger

class TermuxRefreshTrigger(private val context: Context) : RefreshTrigger {

    override fun trigger(target: String) {
        val script = when (target) {
            "gemini" -> GEMINI_SCRIPT
            "combined" -> COMBINED_SCRIPT
            else -> CLAUDE_SCRIPT
        }
        // Plan B (fiable sur Android 16) : déposer un fichier-déclencheur qu'un démon
        // Termux surveille. Ne dépend pas de RunCommandService (bloqué par Android 16).
        // Le target est encodé dans le NOM du fichier : à travers FUSE, seuls le nom et
        // le mtime se propagent au PRoot de façon fiable, pas le contenu.
        try {
            java.io.File(REFRESH_PREFIX + target).writeText(System.currentTimeMillis().toString())
        } catch (_: Throwable) {}
        // Plan A (best effort) : démarrage direct du service Termux, si jamais autorisé.
        try {
            val intent = Intent().apply {
                setClassName("com.termux", "com.termux.app.RunCommandService")
                action = "com.termux.RUN_COMMAND"
                // Les scripts vivent dans le distro PRoot "ubuntu" (agy, credentials Claude,
                // curl/jq y sont). RunCommandService s'exécute en Termux natif, donc on entre
                // d'abord dans le distro — exactement comme le wrapper du cron.
                putExtra("com.termux.RUN_COMMAND_PATH", PROOT_DISTRO)
                putExtra(
                    "com.termux.RUN_COMMAND_ARGUMENTS",
                    arrayOf("login", DISTRO, "--", "bash", script)
                )
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            }
            // Termux RunCommandService se met lui-même en foreground (notification) :
            // sur API 26+ il DOIT être démarré via startForegroundService, sinon Android
            // refuse le démarrage et le service ne tourne jamais.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (_: Throwable) {
        }
    }

    companion object {
        private const val PROOT_DISTRO = "/data/data/com.termux/files/usr/bin/proot-distro"
        private const val DISTRO = "ubuntu"
        private const val REFRESH_PREFIX = "/sdcard/Download/.cd_refresh_"
        private const val CLAUDE_SCRIPT =
            "/data/data/com.termux/files/home/Projects/ClaudeAndroidDash/parser/claude_usage_api.sh"
        private const val GEMINI_SCRIPT =
            "/data/data/com.termux/files/home/Projects/ClaudeAndroidDash/parser/gemini_usage_api.sh"
        private const val COMBINED_SCRIPT =
            "/data/data/com.termux/files/home/Projects/ClaudeAndroidDash/parser/refresh_all.sh"
    }
}
