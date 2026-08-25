package com.metrolist.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class EchoBrainInjectionGateTest {
    @Test
    fun `same seed is acquired by one concurrent cycle and can run again after release`() {
        val gate = EchoBrainInjectionGate()
        val workers = 8
        val ready = CountDownLatch(workers)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(workers)

        try {
            val results = (0 until workers).map {
                executor.submit<Boolean> {
                    ready.countDown()
                    check(start.await(5, TimeUnit.SECONDS))
                    gate.tryAcquire("seed")
                }
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            assertEquals(1, results.count { it.get(5, TimeUnit.SECONDS) })
            assertFalse(gate.tryAcquire("seed"))

            gate.release("seed")
            assertTrue(gate.tryAcquire("seed"))
        } finally {
            executor.shutdownNow()
        }
    }
}
