package com.claudedash.widget.adapter.clock

import com.claudedash.widget.domain.port.Clock

class RealClock : Clock {
    override fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1000L
}
