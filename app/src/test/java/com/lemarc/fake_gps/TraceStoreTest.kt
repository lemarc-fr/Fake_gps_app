package com.lemarc.fake_gps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TraceStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val points = listOf(LatLon(48.8584, 2.2945), LatLon(48.8606, 2.3376), LatLon(48.853, 2.3499))

    @Test
    fun saveListLoadDelete() {
        val store = TraceStore(tmp.root)
        store.save("Balade <Paris> & co", points)
        assertTrue(store.exists("Balade <Paris> & co"))

        val list = store.list()
        assertEquals(1, list.size)
        assertEquals("Balade _Paris_ & co", list[0].name)

        val loaded = store.load(list[0].name)
        assertEquals(points.size, loaded.size)
        points.zip(loaded).forEach { (a, b) ->
            assertEquals(a.lat, b.lat, 1e-6)
            assertEquals(a.lon, b.lon, 1e-6)
        }

        assertTrue(store.delete(list[0].name))
        assertFalse(store.exists(list[0].name))
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun sanitizePreventsPathTraversal() {
        assertEquals("_.._etc_passwd", TraceStore.sanitize("/../etc/passwd"))
        assertEquals("trace", TraceStore.sanitize("..."))
        assertEquals("trace", TraceStore.sanitize("   "))
    }
}
