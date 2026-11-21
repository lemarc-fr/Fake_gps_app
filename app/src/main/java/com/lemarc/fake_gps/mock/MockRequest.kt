package com.lemarc.fake_gps.mock

import com.lemarc.fake_gps.model.LatLon

/**
 * Demande de simulation envoyée à [MockLocationService].
 * [move] = false : position fixe sur le premier point ; true : déplacement le long de [points].
 */
data class MockRequest(
    val points: List<LatLon>,
    val move: Boolean,
    val speedExpression: String,
    val loop: Boolean,
)
