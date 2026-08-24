package com.metrolist.music.playback

import androidx.media3.common.MediaItem
import com.metrolist.music.db.entities.ArtistEntity
import com.metrolist.music.db.entities.Song
import com.metrolist.music.db.entities.SongEntity
import com.metrolist.music.models.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EchoBrainQueuePlannerTest {
    @Test
    fun `select prioritizes local feedback and never returns queued or injected songs`() {
        val queued = song(id = "queued", totalPlayTime = 900L)
        val previouslyInjected = song(id = "injected", liked = true)
        val listened = song(id = "listened", totalPlayTime = 200L)
        val liked = song(id = "liked", liked = true)

        val selected = EchoBrainQueuePlanner.select(
            seed = null,
            relatedSongs = listOf(queued, previouslyInjected, listened, liked),
            queuedIds = setOf("queued"),
            previouslyInjectedIds = setOf("injected"),
            maxItems = 3,
        )

        assertEquals(listOf("liked", "listened"), selected.map { it.mediaId })
    }

    @Test
    fun `select caps the batch without duplicates`() {
        val first = song(id = "first", totalPlayTime = 10L)
        val duplicate = song(id = "first", liked = true)
        val second = song(id = "second", totalPlayTime = 20L)
        val third = song(id = "third", totalPlayTime = 30L)

        val selected = EchoBrainQueuePlanner.select(
            seed = null,
            relatedSongs = listOf(first, duplicate, second, third),
            queuedIds = emptySet(),
            previouslyInjectedIds = emptySet(),
            maxItems = 2,
        )

        assertEquals(listOf("third", "second"), selected.map { it.mediaId })
    }

    @Test
    fun `select still finds the only new song for a populated mix`() {
        val active = song(id = "active")
        val queuedOne = song(id = "queued-one", liked = true)
        val queuedTwo = song(id = "queued-two", totalPlayTime = 200L)
        val fresh = song(id = "fresh", liked = true)

        val selected = EchoBrainQueuePlanner.select(
            seed = active,
            relatedSongs = listOf(queuedOne, queuedTwo, fresh),
            queuedIds = setOf("active", "queued-one", "queued-two"),
            previouslyInjectedIds = emptySet(),
            maxItems = 3,
        )

        assertEquals(listOf("fresh"), selected.map { it.mediaId })
    }

    @Test
    fun `select rejects the same recording when it has a different queue identifier`() {
        val originalMixSong = song(id = "mix-por-sus-besos", title = "POR SUS BESOS")
        val radioDuplicate = song(id = "radio-por-sus-besos", title = "Por sus Besos")
        val fresh = song(id = "fresh", title = "Una canción nueva", liked = true)

        val selected = EchoBrainQueuePlanner.select(
            seed = null,
            relatedSongs = listOf(radioDuplicate, fresh),
            queuedIds = emptySet(),
            previouslyInjectedIds = emptySet(),
            blockedSongKeys = EchoBrainQueuePlanner.canonicalSongKeys(
                listOf(mediaItem(id = originalMixSong.id, title = originalMixSong.song.title)),
            ),
        )

        assertEquals(listOf("fresh"), selected.map { it.mediaId })
    }

    @Test
    fun `radio fallback selects new tracks when all local queue entries are occupied`() {
        val selected = EchoBrainQueuePlanner.selectRadioItems(
            candidates = listOf(
                mediaItem("active"),
                mediaItem("already-in-mix"),
                mediaItem("radio-first"),
                mediaItem("radio-second"),
                mediaItem("radio-first"),
            ),
            queuedIds = setOf("active", "already-in-mix"),
            previouslyInjectedIds = emptySet(),
            maxItems = 3,
        )

        assertEquals(listOf("radio-first", "radio-second"), selected.map { it.mediaId })
    }

    @Test
    fun `radio fallback rejects a matching title with a different media id`() {
        val originalMixSong = mediaItem(id = "mix-tu-boda", title = "Tu Boda")
        val selected = EchoBrainQueuePlanner.selectRadioItems(
            candidates = listOf(
                mediaItem(id = "radio-tu-boda", title = "TU BODA"),
                mediaItem(id = "radio-fresh", title = "Una canción nueva"),
            ),
            queuedIds = emptySet(),
            previouslyInjectedIds = emptySet(),
            blockedSongKeys = EchoBrainQueuePlanner.canonicalSongKeys(listOf(originalMixSong)),
        )

        assertEquals(listOf("radio-fresh"), selected.map { it.mediaId })
    }

    @Test
    fun `strict 90 similarity accepts only anchor or reinforced local context`() {
        val seed = song(id = "seed", artistId = "anchor")
        val anchorMatch = song(id = "anchor-match", artistId = "anchor")
        val reinforcedContext = song(id = "trusted-match", artistId = "trusted", liked = true)
        val momentOnly = song(id = "moment-only", artistId = "moment")
        val unrelated = song(id = "unrelated", artistId = "other", liked = true)

        val selected = EchoBrainQueuePlanner.select(
            seed = seed,
            relatedSongs = listOf(unrelated, momentOnly, reinforcedContext, anchorMatch),
            queuedIds = emptySet(),
            previouslyInjectedIds = emptySet(),
            momentArtistIds = setOf("trusted", "moment"),
            vaultArtistIds = setOf("trusted"),
            minimumSimilarity = 90,
            allowAlternativeVersions = false,
        )

        assertEquals(listOf("trusted-match", "anchor-match"), selected.map { it.mediaId })
    }

    @Test
    fun `strict selection blocks alternative versions when disabled`() {
        val seed = song(id = "seed", artistId = "anchor")
        val liveVersion = song(id = "live", title = "Tema en vivo", artistId = "anchor")
        val mainRecording = song(id = "main", title = "Tema nuevo", artistId = "anchor")

        val selected = EchoBrainQueuePlanner.select(
            seed = seed,
            relatedSongs = listOf(liveVersion, mainRecording),
            queuedIds = emptySet(),
            previouslyInjectedIds = emptySet(),
            minimumSimilarity = 90,
            allowAlternativeVersions = false,
        )

        assertEquals(listOf("main"), selected.map { it.mediaId })
    }

    @Test
    fun `strict selection allows a related song from another release year`() {
        val seedFrom2004 = song(id = "gasolina-2004", artistId = "reggaeton-anchor", year = 2004)
        val relatedSongFrom2026 = song(
            id = "reggaeton-2026",
            artistId = "reggaeton-anchor",
            year = 2026,
        )

        val selected = EchoBrainQueuePlanner.select(
            seed = seedFrom2004,
            relatedSongs = listOf(relatedSongFrom2026),
            queuedIds = emptySet(),
            previouslyInjectedIds = emptySet(),
            minimumSimilarity = 90,
            allowAlternativeVersions = false,
        )

        assertEquals(listOf("reggaeton-2026"), selected.map { it.mediaId })
    }

    @Test
    fun `flexible 60 selection keeps graph relations across decades`() {
        val seedFrom2026 = song(id = "new-release-2026", artistId = "new-anchor", year = 2026)
        val relatedSongFrom2000 = song(id = "related-2000", artistId = "classic-related", year = 2000)

        val selected = EchoBrainQueuePlanner.select(
            seed = seedFrom2026,
            relatedSongs = listOf(relatedSongFrom2000),
            queuedIds = emptySet(),
            previouslyInjectedIds = emptySet(),
            minimumSimilarity = 60,
            allowAlternativeVersions = false,
        )

        assertEquals(listOf("related-2000"), selected.map { it.mediaId })
    }

    @Test
    fun `daily repeat guard blocks every injected song for exactly 24 hours`() {
        val now = 2_000_000_000L
        val day = 24L * 60L * 60L * 1000L
        val blocked = EchoBrainQueuePlanner.activeCooldownSongKeys(
            injectionTimestamps = mapOf(
                "nadie" to now - day + 1L,
                "another-song" to now - 60_000L,
                "expired-song" to now - day,
            ),
            nowMillis = now,
            cooldownMillis = day,
        )

        assertEquals(setOf("nadie", "another-song"), blocked)
    }

    @Test
    fun `automatic injection starts a mix and refills when the last Echo Brain item plays`() {
        assertEquals(
            true,
            EchoBrainQueuePlanner.shouldAutoInject(
                currentIndex = 0,
                mediaItemCount = 50,
                currentIsEchoBrainRecommendation = false,
                nextIsEchoBrainRecommendation = false,
                hasInjectedRecommendations = false,
            ),
        )
        assertEquals(
            false,
            EchoBrainQueuePlanner.shouldAutoInject(
                currentIndex = 2,
                mediaItemCount = 53,
                currentIsEchoBrainRecommendation = true,
                nextIsEchoBrainRecommendation = true,
                hasInjectedRecommendations = true,
            ),
        )
        assertEquals(
            true,
            EchoBrainQueuePlanner.shouldAutoInject(
                currentIndex = 3,
                mediaItemCount = 53,
                currentIsEchoBrainRecommendation = true,
                nextIsEchoBrainRecommendation = false,
                hasInjectedRecommendations = true,
            ),
        )
        assertEquals(
            true,
            EchoBrainQueuePlanner.shouldAutoInject(
                currentIndex = 39,
                mediaItemCount = 50,
                currentIsEchoBrainRecommendation = false,
                nextIsEchoBrainRecommendation = false,
                hasInjectedRecommendations = false,
            ),
        )
    }

    private fun song(
        id: String,
        title: String = id,
        liked: Boolean = false,
        totalPlayTime: Long = 0L,
        artistId: String? = null,
        year: Int? = null,
    ): Song =
        Song(
            song = SongEntity(
                id = id,
                title = title,
                liked = liked,
                totalPlayTime = totalPlayTime,
                year = year,
            ),
            artists = artistId?.let { listOf(ArtistEntity(id = it, name = it)) }.orEmpty(),
        )

    private fun mediaItem(
        id: String,
        title: String = id,
    ): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setUri(id)
            .setTag(
                MediaMetadata(
                    id = id,
                    title = title,
                    artists = emptyList(),
                    duration = 0,
                ),
            ).build()
}
