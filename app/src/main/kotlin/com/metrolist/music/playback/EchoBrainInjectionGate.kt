package com.metrolist.music.playback

import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps Echo Brain from starting two recommendation cycles for the same seed at once.
 * It owns no recommendation rules and stores no playback history.
 */
internal class EchoBrainInjectionGate {
    private val inFlightSeeds = ConcurrentHashMap.newKeySet<String>()

    fun tryAcquire(seedMediaId: String): Boolean =
        seedMediaId.isNotBlank() && inFlightSeeds.add(seedMediaId)

    fun release(seedMediaId: String) {
        inFlightSeeds.remove(seedMediaId)
    }

    fun clear() {
        inFlightSeeds.clear()
    }
}
