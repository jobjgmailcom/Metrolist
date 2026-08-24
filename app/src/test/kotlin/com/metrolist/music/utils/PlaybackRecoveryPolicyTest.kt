package com.metrolist.music.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackRecoveryPolicyTest {
    @Test
    fun `only expected stream rejections trigger client fallback`() {
        listOf(403, 404, 410, 416).forEach { status ->
            assertEquals(true, PlaybackRecoveryPolicy.isRecoverableStreamStatus(status))
        }
        listOf(null, 200, 401, 429, 500).forEach { status ->
            assertEquals(false, PlaybackRecoveryPolicy.isRecoverableStreamStatus(status))
        }
    }

    @Test
    fun `resolved stream is reused only before its safety window`() {
        val now = 1_000_000L

        assertEquals(true, PlaybackRecoveryPolicy.canReuseResolvedStream(now + 60_001L, now))
        assertEquals(false, PlaybackRecoveryPolicy.canReuseResolvedStream(now + 60_000L, now))
        assertEquals(false, PlaybackRecoveryPolicy.canReuseResolvedStream(now + 59_999L, now))
    }
}
