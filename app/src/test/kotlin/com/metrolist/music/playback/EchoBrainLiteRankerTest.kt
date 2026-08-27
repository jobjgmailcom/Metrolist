package com.metrolist.music.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class EchoBrainLiteRankerTest {
    @Test
    fun `ranking only reorders the existing eligible candidates`() {
        val reordered = EchoBrainLiteRanker.reorderIds(
            candidates = listOf("first", "second", "third"),
            scores = mapOf("second" to 90, "first" to 70),
        )

        assertEquals(listOf("second", "first", "third"), reordered)
    }
}
