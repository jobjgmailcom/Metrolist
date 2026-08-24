/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import androidx.media3.common.MediaItem
import com.metrolist.music.db.entities.Song
import com.metrolist.music.extensions.metadata
import com.metrolist.music.extensions.toMediaItem
import java.text.Normalizer

/**
 * Selects a small, deterministic Echo Brain batch from the locally persisted related-song graph.
 *
 * Selection is intentionally conservative: a batch may contain fewer than three songs when no
 * candidate reaches the listener's minimum affinity. This is preferable to injecting unrelated
 * music merely to fill a queue slot.
 */
internal object EchoBrainQueuePlanner {
    const val DEFAULT_BATCH_SIZE = 3
    const val AUTO_TRIGGER_REMAINING_ITEMS = 12

    fun select(
        seed: Song?,
        relatedSongs: List<Song>,
        queuedIds: Set<String>,
        previouslyInjectedIds: Set<String>,
        blockedSongKeys: Set<String> = emptySet(),
        momentArtistIds: Set<String> = emptySet(),
        vaultArtistIds: Set<String> = emptySet(),
        minimumSimilarity: Int = 0,
        allowAlternativeVersions: Boolean = true,
        maxItems: Int = DEFAULT_BATCH_SIZE,
    ): List<MediaItem> {
        val seedArtistIds = seed?.orderedArtists?.map { it.id }?.toSet().orEmpty()
        val seedAlbumId = seed?.song?.albumId

        return relatedSongs
            .asSequence()
            .filter { candidate ->
                candidate.id.isNotBlank() &&
                    candidate.id !in queuedIds &&
                    candidate.id !in previouslyInjectedIds &&
                    canonicalSongKey(candidate) !in blockedSongKeys &&
                    (allowAlternativeVersions || !isAlternativeVersion(candidate.song.title)) &&
                    similarityScore(
                        candidate,
                        seedArtistIds,
                        seedAlbumId,
                        momentArtistIds,
                        vaultArtistIds,
                    ) >= minimumSimilarity
            }
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<Song> {
                    similarityScore(
                        it,
                        seedArtistIds,
                        seedAlbumId,
                        momentArtistIds,
                        vaultArtistIds,
                    )
                }
                    .thenByDescending { score(it, seedArtistIds, seedAlbumId) }
                    .thenBy { it.id },
            )
            .distinctBy(::canonicalSongKey)
            .distinctBy(::primaryArtistKey)
            .take(maxItems)
            .map(Song::toMediaItem)
            .toList()
    }

    /**
     * Keeps the first safe items from MetroList's radio generator. Radio offers the initial
     * relationship signal but must still meet the configured similarity threshold.
     */
    fun selectRadioItems(
        candidates: List<MediaItem>,
        queuedIds: Set<String>,
        previouslyInjectedIds: Set<String>,
        blockedSongKeys: Set<String> = emptySet(),
        seed: MediaItem? = null,
        momentArtistIds: Set<String> = emptySet(),
        vaultArtistIds: Set<String> = emptySet(),
        minimumSimilarity: Int = 0,
        allowAlternativeVersions: Boolean = true,
        maxItems: Int = DEFAULT_BATCH_SIZE,
    ): List<MediaItem> {
        val seedMetadata = seed?.metadata
        val seedArtists = seedMetadata?.artists?.mapNotNull { it.id }?.toSet().orEmpty()
        val seedAlbum = normalize(seedMetadata?.album?.id.orEmpty())

        return candidates
            .asSequence()
            .filter { candidate ->
                candidate.mediaId.isNotBlank() &&
                    candidate.mediaId !in queuedIds &&
                    candidate.mediaId !in previouslyInjectedIds &&
                    canonicalSongKey(candidate) !in blockedSongKeys &&
                    (allowAlternativeVersions || !isAlternativeVersion(candidate.metadata?.title.orEmpty())) &&
                    radioSimilarityScore(
                        candidate,
                        seedArtists,
                        seedAlbum,
                        momentArtistIds,
                        vaultArtistIds,
                    ) >= minimumSimilarity
            }
            .sortedByDescending {
                radioSimilarityScore(
                    it,
                    seedArtists,
                    seedAlbum,
                    momentArtistIds,
                    vaultArtistIds,
                )
            }
            .distinctBy(::canonicalSongKey)
            .distinctBy(::primaryArtistKey)
            .take(maxItems)
            .toList()
    }

    /**
     * A YouTube mix can expose the same recording under distinct queue or video identifiers.
     * This key folds title and artists so Echo Brain never inserts a visible duplicate already
     * present in the listener's original mix.
     */
    fun canonicalSongKeys(items: Iterable<MediaItem>): Set<String> =
        items.mapTo(mutableSetOf(), ::canonicalSongKey)

    /** Returns songs that remain unavailable until their configured repeat cooldown expires. */
    fun activeCooldownSongKeys(
        injectionTimestamps: Map<String, Long>,
        nowMillis: Long,
        cooldownMillis: Long,
    ): Set<String> =
        injectionTimestamps
            .filter { (_, injectedAtMillis) -> nowMillis - injectedAtMillis < cooldownMillis }
            .keys

    private fun similarityScore(
        candidate: Song,
        seedArtistIds: Set<String>,
        seedAlbumId: String?,
        momentArtistIds: Set<String>,
        vaultArtistIds: Set<String>,
    ): Int {
        val song = candidate.song
        val artists = candidate.orderedArtists.map { it.id }.toSet()
        var score = 60 // The persisted related-song graph is the baseline relation signal.
        if (artists.any { it in seedArtistIds }) score += 30 // Ancla: la pista activa.
        if (artists.any { it in momentArtistIds }) score += 15 // Momento: la sesión reciente.
        if (artists.any { it in vaultArtistIds }) score += 10 // Bóveda: gustos locales.
        if (seedAlbumId != null && song.albumId == seedAlbumId) score += 10
        if (song.liked || song.inLibrary != null || song.isDownloaded) score += 5
        return score.coerceAtMost(100)
    }

    private fun radioSimilarityScore(
        candidate: MediaItem,
        seedArtists: Set<String>,
        seedAlbum: String,
        momentArtistIds: Set<String>,
        vaultArtistIds: Set<String>,
    ): Int {
        val metadata = candidate.metadata
        val artists = metadata?.artists?.mapNotNull { it.id }?.toSet().orEmpty()
        var score = 60 // Radio is the baseline relation signal.
        if (artists.any { it in seedArtists }) score += 30
        if (artists.any { it in momentArtistIds }) score += 15
        if (artists.any { it in vaultArtistIds }) score += 10
        if (seedAlbum.isNotBlank() && normalize(metadata?.album?.id.orEmpty()) == seedAlbum) score += 10
        return score.coerceAtMost(100)
    }

    private fun canonicalSongKey(song: Song): String =
        canonicalSongKey(
            title = song.song.title,
            artistNames = song.orderedArtists.map { it.name },
            fallbackId = song.id,
        )

    private fun canonicalSongKey(mediaItem: MediaItem): String {
        val metadata = mediaItem.metadata
        return canonicalSongKey(
            title = metadata?.title ?: mediaItem.mediaMetadata.title?.toString().orEmpty(),
            artistNames = metadata?.artists?.map { it.name }
                ?: listOfNotNull(mediaItem.mediaMetadata.artist?.toString()),
            fallbackId = mediaItem.mediaId,
        )
    }

    private fun primaryArtistKey(song: Song): String =
        normalize(song.orderedArtists.firstOrNull()?.name.orEmpty()).ifBlank { canonicalSongKey(song) }

    private fun primaryArtistKey(mediaItem: MediaItem): String =
        normalize(mediaItem.metadata?.artists?.firstOrNull()?.name.orEmpty())
            .ifBlank { canonicalSongKey(mediaItem) }

    private fun canonicalSongKey(
        title: String,
        artistNames: List<String>,
        fallbackId: String,
    ): String {
        val normalizedTitle = normalize(title)
        val normalizedArtists = artistNames.map(::normalize).filter(String::isNotBlank).sorted()
        return if (normalizedTitle.isBlank() && normalizedArtists.isEmpty()) {
            "id:${normalize(fallbackId)}"
        } else {
            "$normalizedTitle|${normalizedArtists.joinToString(",")}"
        }
    }

    private fun isAlternativeVersion(title: String): Boolean {
        val normalizedTitle = normalize(title)
        return AlternativeVersionMarkers.any(normalizedTitle::contains)
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .lowercase()
            .filter(Char::isLetterOrDigit)

    /**
     * Starts Echo Brain for a fresh mix, retries a mix that has not received recommendations near
     * its end, and refills exactly when playback reaches the last item of an injected batch.
     */
    fun shouldAutoInject(
        currentIndex: Int,
        mediaItemCount: Int,
        currentIsEchoBrainRecommendation: Boolean,
        nextIsEchoBrainRecommendation: Boolean,
        hasInjectedRecommendations: Boolean,
    ): Boolean =
        currentIndex in 0 until mediaItemCount &&
            (currentIndex == 0 ||
                (currentIsEchoBrainRecommendation && !nextIsEchoBrainRecommendation) ||
                (!hasInjectedRecommendations &&
                    mediaItemCount - currentIndex <= AUTO_TRIGGER_REMAINING_ITEMS))

    private fun score(
        candidate: Song,
        seedArtistIds: Set<String>,
        seedAlbumId: String?,
    ): Long {
        val song = candidate.song
        val commonArtists = candidate.orderedArtists.count { it.id in seedArtistIds }

        return buildLong {
            if (song.liked) add(1_000_000_000L)
            if (song.inLibrary != null) add(500_000_000L)
            if (song.isDownloaded) add(75_000_000L)
            if (seedAlbumId != null && song.albumId == seedAlbumId) add(150_000_000L)
            add(commonArtists * 100_000_000L)
            add(song.totalPlayTime.coerceIn(0L, 100_000_000L))
        }
    }

    private inline fun buildLong(block: LongAccumulator.() -> Unit): Long =
        LongAccumulator().apply(block).value

    private class LongAccumulator {
        var value: Long = 0L
            private set

        fun add(amount: Long) {
            value += amount
        }
    }

    private val AlternativeVersionMarkers =
        listOf(
            "remix",
            "live",
            "envivo",
            "acoustic",
            "acustica",
            "acustico",
            "cover",
            "instrumental",
            "session",
            "version",
            "karaoke",
            "slowed",
            "spedup",
        )
}
