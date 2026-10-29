package com.devson.netmeter.core.data.speedtest

import com.devson.netmeter.core.domain.model.SpeedTestConfig
import com.devson.netmeter.core.domain.model.SpeedTestEvent
import kotlinx.coroutines.flow.Flow

/**
 * Interface defining the network speed test execution engine.
 * Decoupled from Android UI and usage statistics data layer to support
 * mock/fake testing and custom network clients.
 */
interface SpeedTestEngine {
    fun executeSpeedTest(config: SpeedTestConfig = SpeedTestConfig.DEFAULT): Flow<SpeedTestEvent>
}
