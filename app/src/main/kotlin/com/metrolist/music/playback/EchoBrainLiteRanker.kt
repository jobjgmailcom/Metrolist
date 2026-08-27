/*
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.channels.FileChannel
import java.text.Normalizer
import kotlin.math.roundToInt

/**
 * Small, CPU-only on-device ordering signal for Echo Brain.
 *
 * This ranker deliberately receives candidates only *after* EchoBrainQueuePlanner has applied
 * similarity, duplicate, cooldown, alternate-version and diversity rules. Therefore a model
 * score can never make an ineligible song eligible, resolve media URLs, query the network or
 * change the listener's existing queue.
 */
internal class EchoBrainLiteRanker(context: Context) {
    private val appContext = context.applicationContext
    private val interpreter: Interpreter? by lazy {
        runCatching {
            appContext.assets.openFd(MODEL_FILE).use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                    val mapped = channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        descriptor.startOffset,
                        descriptor.declaredLength,
                    )
                    Interpreter(mapped, Interpreter.Options().setNumThreads(1))
                }
            }
        }.getOrNull()
    }

    @Synchronized
    fun reorderEligible(
        seed: MediaItem?,
        candidates: List<MediaItem>,
        neuroProfileScores: Map<String, Int>,
    ): List<MediaItem> {
        if (seed == null || candidates.size < 2) return candidates
        val runtime = interpreter ?: return candidates
        val scores = buildMap {
            candidates.forEachIndexed { index, candidate ->
                val mediaId = candidate.mediaId.takeIf(String::isNotBlank) ?: return@forEachIndexed
                val output = Array(1) { FloatArray(1) }
                runCatching {
                    runtime.run(
                        arrayOf(features(seed, candidate, index, candidates.size, neuroProfileScores[mediaId])),
                        output,
                    )
                }.onSuccess {
                    put(mediaId, (output[0][0] * 100f).roundToInt().coerceIn(0, 100))
                }
            }
        }
        return reorder(candidates, scores)
    }

    private fun features(
        seed: MediaItem,
        candidate: MediaItem,
        index: Int,
        size: Int,
        neuroScore: Int?,
    ): FloatArray {
        val seedMetadata = seed.mediaMetadata
        val candidateMetadata = candidate.mediaMetadata
        val denominator = (size - 1).coerceAtLeast(1).toFloat()
        return floatArrayOf(
            (1f - index / denominator).coerceIn(0f, 1f),
            ((neuroScore ?: DEFAULT_NEUTRAL_NEURO_SCORE) / 100f).coerceIn(0f, 1f),
            sharesArtist(seedMetadata, candidateMetadata),
            sharesAlbum(seedMetadata, candidateMetadata),
            titleOverlap(seedMetadata.title?.toString(), candidateMetadata.title?.toString()),
            if (neuroScore == null) 0f else 1f,
        )
    }

    private fun sharesArtist(seed: MediaMetadata, candidate: MediaMetadata): Float {
        val seedArtists = seed.artist?.toString()?.let(::tokens).orEmpty()
        val candidateArtists = candidate.artist?.toString()?.let(::tokens).orEmpty()
        return if (seedArtists.intersect(candidateArtists).isNotEmpty()) 1f else 0f
    }

    private fun sharesAlbum(seed: MediaMetadata, candidate: MediaMetadata): Float =
        if (normalize(seed.albumTitle?.toString()) == normalize(candidate.albumTitle?.toString()) &&
            seed.albumTitle?.isNotBlank() == true
        ) 1f else 0f

    private fun titleOverlap(seed: String?, candidate: String?): Float {
        val seedTokens = tokens(seed)
        if (seedTokens.isEmpty()) return 0f
        return (seedTokens.intersect(tokens(candidate)).size.toFloat() / seedTokens.size).coerceIn(0f, 1f)
    }

    private fun tokens(value: String?): Set<String> =
        normalize(value)
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length > 1 && it !in STOP_WORDS }
            .toSet()

    private fun normalize(value: String?): String =
        Normalizer.normalize(value.orEmpty(), Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .lowercase()

    companion object {
        const val MODEL_FILE = "echo_brain_metadata_ranker.tflite"
        const val DEFAULT_NEUTRAL_NEURO_SCORE = 50
        val STOP_WORDS = setOf("the", "and", "feat", "official", "video", "music", "audio")

        internal fun reorder(candidates: List<MediaItem>, scores: Map<String, Int>): List<MediaItem> =
            reorderById(candidates, MediaItem::mediaId, scores)

        internal fun reorderIds(candidates: List<String>, scores: Map<String, Int>): List<String> =
            reorderById(candidates, { it }, scores)

        private fun <T> reorderById(
            candidates: List<T>,
            identifier: (T) -> String,
            scores: Map<String, Int>,
        ): List<T> =
            candidates.withIndex()
            .sortedWith(
                    compareByDescending<IndexedValue<T>> { scores[identifier(it.value)] ?: Int.MIN_VALUE }
                        .thenBy { it.index },
                )
                .map(IndexedValue<T>::value)
    }
}
