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
        blockedSongKeys: Set<String> = emptySet(),
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
                    canonicalSongKey(candidate) !in blockedSongKeys
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
            .distinctBy(::canonicalSongKey)
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
        blockedSongKeys: Set<String> = emptySet(),
        maxItems: Int = DEFAULT_BATCH_SIZE,
    ): List<MediaItem> =
        candidates
            .asSequence()
            .filter { candidate ->
                candidate.mediaId.isNotBlank() &&
                    candidate.mediaId !in queuedIds &&
                    candidate.mediaId !in previouslyInjectedIds &&
                    canonicalSongKey(candidate) !in blockedSongKeys
            }
            .distinctBy(::canonicalSongKey)
            .take(maxItems)
            .toList()

    /**
     * A YouTube mix can expose the same recording under distinct queue or video identifiers.
     * This key folds title and artists so Echo Brain never inserts a visible duplicate already
     * present in the listener's original mix.
     */
    fun canonicalSongKeys(items: Iterable<MediaItem>): Set<String> =
        items.mapTo(mutableSetOf(), ::canonicalSongKey)

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
}
