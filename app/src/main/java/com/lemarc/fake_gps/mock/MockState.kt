package com.lemarc.fake_gps.mock

import com.lemarc.fake_gps.model.LatLon
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

enum class MockMode { IDLE, FIXED, MOVING, ARRIVED }

data class MockStatus(
    val mode: MockMode = MockMode.IDLE,
    val position: LatLon? = null,
    val speedKmh: Double = 0.0,
    val speedExpression: String = "",
)

/** Échec de la simulation ; le texte affiché est choisi par l'interface selon [reason]. */
data class MockError(val reason: Reason, val detail: String? = null) {
    enum class Reason {
        /** Android refuse l'accès : l'appli n'est pas l'application de position fictive. */
        NOT_ALLOWED,
        /** Aucun fournisseur de position n'a pu être remplacé. */
        NO_PROVIDER,
    }
}

/**
 * État de la simulation, publié par [MockLocationService] et lu par l'interface.
 *
 * Sens unique : seul le service écrit ([update], [reportError]) ; l'interface ne fait que lire
 * [status] et consommer [errors]. Chaque erreur n'est livrée qu'une fois.
 */
object MockState {
    private val _status = MutableStateFlow(MockStatus())
    val status: StateFlow<MockStatus> = _status.asStateFlow()

    private val _errors = Channel<MockError>(Channel.BUFFERED)
    val errors: Flow<MockError> = _errors.receiveAsFlow()

    internal fun update(status: MockStatus) {
        _status.value = status
    }

    internal fun reportError(error: MockError) {
        _errors.trySend(error)
    }
}
