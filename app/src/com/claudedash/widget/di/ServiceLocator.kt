package com.claudedash.widget.di

import android.content.Context
import com.claudedash.widget.adapter.clock.RealClock
import com.claudedash.widget.adapter.refresh.TermuxRefreshTrigger
import com.claudedash.widget.adapter.repository.JsonFileUsageRepository
import com.claudedash.widget.domain.port.Clock
import com.claudedash.widget.domain.port.RefreshTrigger
import com.claudedash.widget.domain.port.UsageRepository
import com.claudedash.widget.domain.usecase.UsageFormatter

object ServiceLocator {

    val clock: Clock = RealClock()
    val usageRepository: UsageRepository = JsonFileUsageRepository()
    val formatter: UsageFormatter = UsageFormatter(clock)

    fun refreshTrigger(context: Context): RefreshTrigger =
        TermuxRefreshTrigger(context.applicationContext)
}
