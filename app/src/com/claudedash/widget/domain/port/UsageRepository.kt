package com.claudedash.widget.domain.port

import com.claudedash.widget.domain.model.UsageSnapshot

interface UsageRepository {
    fun read(): UsageSnapshot?
}
