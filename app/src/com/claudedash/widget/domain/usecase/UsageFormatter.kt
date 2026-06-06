package com.claudedash.widget.domain.usecase

import com.claudedash.widget.domain.port.Clock

class UsageFormatter(private val clock: Clock) {

    fun percent(value: Double?): String =
        if (value == null) "—" else "%.0f%%".format(value)

    fun remaining(epochSeconds: Long?): String {
        if (epochSeconds == null) return ""
        val diff = epochSeconds - clock.nowEpochSeconds()
        if (diff <= 0) return "now"
        val days = diff / 86_400
        val hours = (diff % 86_400) / 3_600
        val minutes = (diff % 3_600) / 60
        return when {
            days > 0 -> "${days}d${hours}h"
            hours > 0 -> "${hours}h${"%02d".format(minutes)}"
            else -> "${minutes}min"
        }
    }
}
