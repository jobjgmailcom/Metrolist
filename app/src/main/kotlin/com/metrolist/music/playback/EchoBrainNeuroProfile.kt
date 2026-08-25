/*
 * Adapted from Flow Android Client, commit 3fcc95c32b0f80bedb55e5bf2f6571f2c9bda483.
 * Original Copyright (C) 2025-2026 Flow | A-EDev.
 * https://github.com/A-EDev/Flow
 *
 * This file is licensed under GNU GPL v3.0 or later. MetroList changes:
 * - limits the profile to music metadata available locally;
 * - keeps the profile behind Echo Brain's existing hard eligibility filters;
 * - does not fetch content, replace queues, or send telemetry.
 */

package com.metrolist.music.playback

import androidx.media3.common.MediaItem
import org.json.JSONObject
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A compact local preference vector used only as a final Echo Brain ordering signal. */
internal data class EchoBrainContentVector(
    val topics: Map<String, Double> = emptyMap(),
    val duration: Double = 0.5,
    val pacing: Double = 0.5,
    val complexity: Double = 0.5,
    val isLive: Double = 0.0,
)

/**
 * Stateful local profile with FlowNeuro's separation between orchestration, profile state and
 * pure vector math. It deliberately cannot make a blocked candidate eligible.
 */
internal class EchoBrainNeuroProfile {
    private var profile = EchoBrainContentVector()
    private val confirmedMediaIds = LinkedHashSet<String>()
    private val skippedMediaIds = LinkedHashSet<String>()

    @Synchronized
    fun restore(serialized: String) {
        val restored = runCatching {
            val root = JSONObject(serialized)
            val topics = root.optJSONObject("topics") ?: JSONObject()
            buildMap {
                val keys = topics.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = topics.optDouble(key, Double.NaN)
                    if (value.isFinite() && value > 0.0) put(key, value.coerceIn(0.0, 1.0))
                }
            }
        }.getOrDefault(emptyMap())
        profile = EchoBrainContentVector(topics = trimTopics(restored))
        confirmedMediaIds.clear()
        skippedMediaIds.clear()
    }

    @Synchronized
    fun serialize(): String =
        JSONObject().apply {
            put("schema", SCHEMA_VERSION)
            put("topics", JSONObject(profile.topics))
        }.toString()

    /** Records an existing confirmation event exactly once per service session. */
    @Synchronized
    fun recordConfirmedPlayback(mediaItem: MediaItem): Boolean {
        val mediaId = mediaItem.mediaId.takeIf(String::isNotBlank) ?: return false
        if (!confirmedMediaIds.add(mediaId)) return false
        trimIds(confirmedMediaIds)
        profile = EchoBrainFlowVectorMath.adjustVector(profile, vectorFor(mediaItem), POSITIVE_RATE)
        return true
    }

    /** Records an existing early-skip event exactly once per service session. */
    @Synchronized
    fun recordEarlySkip(mediaItem: MediaItem): Boolean {
        val mediaId = mediaItem.mediaId.takeIf(String::isNotBlank) ?: return false
        if (!skippedMediaIds.add(mediaId)) return false
        trimIds(skippedMediaIds)
        profile = EchoBrainFlowVectorMath.adjustVector(profile, vectorFor(mediaItem), NEGATIVE_RATE)
        return true
    }

    /**
     * Returns an optional ordering signal. Empty means no learned profile, so existing ordering is
     * unchanged. Scores are keyed by Media3 id because all eligibility checks happen beforehand.
     */
    @Synchronized
    fun candidateScores(candidates: List<MediaItem>): Map<String, Int> {
        if (profile.topics.isEmpty()) return emptyMap()
        return candidates
            .asSequence()
            .filter { it.mediaId.isNotBlank() }
            .associate { candidate ->
                candidate.mediaId to (
                    EchoBrainFlowVectorMath.calculateCosineSimilarity(profile, vectorFor(candidate)) * 100.0
                    ).roundToInt().coerceIn(0, 100)
            }
    }

    private fun vectorFor(mediaItem: MediaItem): EchoBrainContentVector {
        val metadata = mediaItem.mediaMetadata
        val normalizedText = sequenceOf(metadata.title, metadata.artist, metadata.albumTitle)
            .filterNotNull()
            .joinToString(" ")
            .let(::normalize)
        val tokens = normalizedText
            .split(Regex("[^a-z0-9]+"))
            .asSequence()
            .filter { it.length >= 2 && it !in STOP_WORDS }
            .groupingBy { it }
            .eachCount()
        val maximum = tokens.values.maxOrNull()?.toDouble() ?: 1.0
        return EchoBrainContentVector(
            topics = tokens.mapValues { (_, count) -> (count / maximum).coerceIn(0.0, 1.0) },
        )
    }

    private fun trimTopics(topics: Map<String, Double>): Map<String, Double> =
        topics.entries
            .asSequence()
            .filter { it.value >= EchoBrainFlowVectorMath.TOPIC_PRUNE_THRESHOLD }
            .sortedByDescending { it.value }
            .take(MAX_TOPICS)
            .associate { it.key to it.value }

    private fun trimIds(ids: LinkedHashSet<String>) {
        while (ids.size > SESSION_SIGNAL_LIMIT) ids.remove(ids.first())
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .lowercase()

    private companion object {
        const val SCHEMA_VERSION = 1
        const val MAX_TOPICS = 128
        const val SESSION_SIGNAL_LIMIT = 256
        const val POSITIVE_RATE = 0.12
        const val NEGATIVE_RATE = -0.12
        val STOP_WORDS = setOf("the", "and", "feat", "official", "video", "music", "audio")
    }
}

/**
 * Pure vector functions adapted from Flow's NeuroVectorMath. Keeping this component stateless
 * makes the profile order signal independently testable and prevents it from changing policy.
 */
internal object EchoBrainFlowVectorMath {
    const val TOPIC_SIMILARITY_WEIGHT = 0.70
    const val DURATION_SIMILARITY_WEIGHT = 0.10
    const val PACING_SIMILARITY_WEIGHT = 0.10
    const val COMPLEXITY_SIMILARITY_WEIGHT = 0.10
    const val TOPIC_PRUNE_THRESHOLD = 0.03
    private const val SCALAR_ONLY_DAMP = 0.3
    private const val ESTABLISHED_TOPIC_THRESHOLD = 0.30
    private const val DEVELOPING_TOPIC_THRESHOLD = 0.10
    private const val ESTABLISHED_DECAY_RATE = 0.998
    private const val DEVELOPING_DECAY_RATE = 0.993
    private const val EMERGING_DECAY_RATE = 0.97
    private const val NEGATIVE_PROPORTIONAL_EXPONENT = 1.5
    private const val NEGATIVE_FLOOR_FACTOR = 0.3
    private const val NEGATIVE_SCALAR_PROPORTIONAL = 0.3
    private const val NEGATIVE_SCALAR_FLOOR = 0.1

    fun calculateCosineSimilarity(user: EchoBrainContentVector, content: EchoBrainContentVector): Double {
        val (smallMap, largeMap) = if (user.topics.size <= content.topics.size) {
            user.topics to content.topics
        } else {
            content.topics to user.topics
        }
        val scalarScore = (1.0 - abs(user.duration - content.duration)) * DURATION_SIMILARITY_WEIGHT +
            (1.0 - abs(user.pacing - content.pacing)) * PACING_SIMILARITY_WEIGHT +
            (1.0 - abs(user.complexity - content.complexity)) * COMPLEXITY_SIMILARITY_WEIGHT
        if (smallMap.isEmpty()) return scalarScore * SCALAR_ONLY_DAMP

        var dotProduct = 0.0
        var hasIntersection = false
        smallMap.forEach { (key, value) ->
            largeMap[key]?.let {
                dotProduct += value * it
                hasIntersection = true
            }
        }
        if (!hasIntersection) return scalarScore * SCALAR_ONLY_DAMP
        val magnitudeA = sqrt(user.topics.values.sumOf { it * it })
        val magnitudeB = sqrt(content.topics.values.sumOf { it * it })
        val topicSimilarity = if (magnitudeA > 0.0 && magnitudeB > 0.0) {
            dotProduct / (magnitudeA * magnitudeB)
        } else {
            0.0
        }
        return (topicSimilarity * TOPIC_SIMILARITY_WEIGHT) + scalarScore
    }

    fun adjustVector(
        current: EchoBrainContentVector,
        target: EchoBrainContentVector,
        baseRate: Double,
    ): EchoBrainContentVector {
        val topics = current.topics.toMutableMap()
        val isNegative = baseRate < 0.0
        target.topics.forEach { (key, targetValue) ->
            val currentValue = topics[key] ?: 0.0
            val delta = if (isNegative) {
                minOf(
                    currentValue * currentValue.pow(NEGATIVE_PROPORTIONAL_EXPONENT) * baseRate,
                    baseRate * NEGATIVE_FLOOR_FACTOR,
                )
            } else {
                val saturation = (1.0 - currentValue).pow(2)
                val coldTopicDamping = 0.5 + 0.5 * (currentValue / 0.20).coerceAtMost(1.0)
                (targetValue - currentValue) * baseRate * saturation * coldTopicDamping
            }
            topics[key] = (currentValue + delta).coerceIn(0.0, 1.0)
        }
        val iterator = topics.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (baseRate > 0.0 && entry.key !in target.topics) {
                val decay = when {
                    entry.value >= ESTABLISHED_TOPIC_THRESHOLD -> ESTABLISHED_DECAY_RATE
                    entry.value >= DEVELOPING_TOPIC_THRESHOLD -> DEVELOPING_DECAY_RATE
                    else -> EMERGING_DECAY_RATE
                }
                entry.setValue(entry.value * decay)
            }
            if (entry.key !in target.topics && entry.value < TOPIC_PRUNE_THRESHOLD) iterator.remove()
        }

        fun updateScalar(currentValue: Double, targetValue: Double): Double =
            if (isNegative) {
                currentValue + minOf(
                    currentValue * baseRate * NEGATIVE_SCALAR_PROPORTIONAL,
                    baseRate * NEGATIVE_SCALAR_FLOOR,
                )
            } else {
                currentValue + (targetValue - currentValue) * baseRate * (1.0 - currentValue).pow(2)
            }.coerceIn(0.0, 1.0)

        return current.copy(
            topics = topics,
            duration = updateScalar(current.duration, target.duration),
            pacing = updateScalar(current.pacing, target.pacing),
            complexity = updateScalar(current.complexity, target.complexity),
            isLive = updateScalar(current.isLive, target.isLive),
        )
    }
}
