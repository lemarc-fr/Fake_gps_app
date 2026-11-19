package com.lemarc.fake_gps

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class MockMode { IDLE, FIXED, MOVING, ARRIVED }

data class MockStatus(
    val mode: MockMode = MockMode.IDLE,
    val position: LatLon? = null,
    val speedKmh: Double = 0.0,
    val speedExpression: String = "",
    val error: String? = null,
)

/** État partagé entre le service et l'interface. */
object MockState {
    private val _status = MutableStateFlow(MockStatus())
    val status: StateFlow<MockStatus> = _status

    fun update(status: MockStatus) {
        _status.value = status
    }
}
