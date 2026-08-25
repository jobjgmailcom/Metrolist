/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

/** Pure recovery rules shared by stream resolution and tests; no account state is retained here. */
internal object PlaybackRecoveryPolicy {
    const val STREAM_EXPIRY_SAFETY_MILLIS = 60_000L
    const val FAILED_CLIENT_BACKOFF_MILLIS = 10 * 60_000L

    fun isRecoverableStreamStatus(statusCode: Int?): Boolean =
        statusCode in setOf(403, 404, 410, 416)

    fun canReuseResolvedStream(
        expiresAtMillis: Long,
        nowMillis: Long,
    ): Boolean = expiresAtMillis > nowMillis + STREAM_EXPIRY_SAFETY_MILLIS

    fun shouldRecoverTransitionStall(
        isBuffering: Boolean,
        playWhenReady: Boolean,
        positionMs: Long,
        initialPositionLimitMs: Long,
    ): Boolean = isBuffering && playWhenReady && positionMs < initialPositionLimitMs

    fun shouldRecoverNoAudioStart(
        isReady: Boolean,
        isPlaying: Boolean,
        playWhenReady: Boolean,
        positionMs: Long,
        initialPositionLimitMs: Long,
    ): Boolean = isReady && !isPlaying && playWhenReady && positionMs < initialPositionLimitMs
}
