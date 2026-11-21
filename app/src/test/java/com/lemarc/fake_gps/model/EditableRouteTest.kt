package com.lemarc.fake_gps.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditableRouteTest {

    private val a = LatLon(48.0, 2.0)
    private val b = LatLon(48.1, 2.1)
    private val c = LatLon(48.2, 2.2)
    private val d = LatLon(48.3, 2.3)

    @Test
    fun undoRemovesWholeLeg() {
        val route = EditableRoute.EMPTY.plusLeg(listOf(a)).plusLeg(listOf(b, c, d))
        assertEquals(2, route.tappedCount)
        assertEquals(listOf(0, 3), route.anchorIndices)

        val undone = route.minusLastLeg()
        assertEquals(listOf(a), undone.points)
        assertTrue(undone.minusLastLeg().isEmpty)
        assertTrue(EditableRoute.EMPTY.minusLastLeg().isEmpty)
    }

    @Test
    fun inconsistentLegSizesFallBackToSinglePoints() {
        assertEquals(listOf(1, 1, 1), EditableRoute.of(listOf(a, b, c), listOf(2)).legSizes)
        assertEquals(listOf(1, 1), EditableRoute.of(listOf(a, b), null).legSizes)
        assertEquals(listOf(1, 1), EditableRoute.of(listOf(a, b), listOf(2, 0)).legSizes)
        assertEquals(listOf(1, 2), EditableRoute.of(listOf(a, b, c), listOf(1, 2)).legSizes)
    }

    @Test
    fun emptyLegIsIgnored() {
        assertEquals(EditableRoute.EMPTY, EditableRoute.EMPTY.plusLeg(emptyList()))
    }
}
