/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import androidx.media3.common.MediaItem
import com.metrolist.music.db.entities.Song
import com.metrolist.music.extensions.toMediaItem

/**
 * Selects a small, deterministic Echo Brain batch from the locally persisted related-song graph.
 *
 * The scoring deliberately favors feedback already stored on-device (likes, library membership,
 * downloads, play time, and metadata affinity). Network calls are only used by MusicService to
 * fill a missing related-song cache; this class never performs I/O and never changes the queue.
 */
internal object EchoBrainQueuePlanner {
    const val DEFAULT_BATCH_SIZE = 3
    const val AUTO_TRIGGER_REMAINING_ITEMS = 12

    fun select(
        seed: Song?,
        relatedSongs: List<Song>,
        queuedIds: Set<String>,
        previouslyInjectedIds: Set<String>,
        maxItems: Int = DEFAULT_BATCH_SIZE,
    ): List<MediaItem> {
        val seedArtistIds = seed?.orderedArtists?.map { it.id }?.toSet().orEmpty()
        val seedAlbumId = seed?.song?.albumId

        return relatedSongs
            .asSequence()
            .filter { candidate ->
                candidate.id.isNotBlank() &&
                    candidate.id !in queuedIds &&
                    candidate.id !in previouslyInjectedIds
            }
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<Song> {
                    score(
                        candidate = it,
                        seedArtistIds = seedArtistIds,
                        seedAlbumId = seedAlbumId,
                    )
                }.thenBy { it.id },
            )
            .take(maxItems)
            .map(Song::toMediaItem)
            .toList()
    }

    /**
     * Keeps the first genuinely new items from MetroList's radio generator. The radio queue is
     * already resilient to sparse related pages, so it is the fallback when local relations are
     * empty or completely occupied by the current queue.
     */
    fun selectRadioItems(
        candidates: List<MediaItem>,
        queuedIds: Set<String>,
        previouslyInjectedIds: Set<String>,
        maxItems: Int = DEFAULT_BATCH_SIZE,
    ): List<MediaItem> =
        candidates
            .asSequence()
            .filter { candidate ->
                candidate.mediaId.isNotBlank() &&
                    candidate.mediaId !in queuedIds &&
                    candidate.mediaId !in previouslyInjectedIds
            }
            .distinctBy(MediaItem::mediaId)
            .take(maxItems)
            .toList()

    /**
     * Inject once at the beginning of a newly loaded mix, then refresh only when the listener is
     * approaching the end. This preserves the original queue while keeping recommendations ready.
     */
    fun shouldAutoInject(
        currentIndex: Int,
        mediaItemCount: Int,
    ): Boolean =
        currentIndex in 0 until mediaItemCount &&
            (currentIndex == 0 || mediaItemCount - currentIndex <= AUTO_TRIGGER_REMAINING_ITEMS)

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
}
