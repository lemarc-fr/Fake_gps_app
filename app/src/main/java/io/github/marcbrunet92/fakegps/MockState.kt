package io.github.marcbrunet92.fakegps

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class MockMode { IDLE, FIXED, MOVING, ARRIVED }

data class MockStatus(
    val mode: MockMode = MockMode.IDLE,
    val position: LatLon? = null,
    val speedKmh: Int = 0,
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
