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
    fun `strict selection accepts a persisted direct relation from another regional artist`() {
        val seed = song(
            id = "la-culpa",
            title = "La Culpa No Tengo Yo",
            artistId = "temerarios",
        )
        val directRelatedSong = song(
            id = "necesito-decirte",
            title = "Necesito Decirte",
            artistId = "conjunto-primavera",
        )

        val selected = EchoBrainQueuePlanner.select(
            seed = seed,
            relatedSongs = listOf(directRelatedSong),
            queuedIds = setOf(seed.song.id),
            previouslyInjectedIds = emptySet(),
            minimumSimilarity = 90,
            allowAlternativeVersions = false,
            maxItems = 3,
        )

        assertEquals(listOf("necesito-decirte"), selected.map { it.mediaId })
    }

    @Test
    fun `artist whitelist permits only selected Echo Brain candidates and preserves queue exclusions`() {
        val seed = song(id = "la-culpa", artistId = "temerarios")
        val allowed = song(id = "primavera", artistId = "conjunto-primavera")
        val excluded = song(id = "ajena", artistId = "baltimora")
        val queuedOriginal = song(id = "original", artistId = "conjunto-primavera")

        val selected = EchoBrainQueuePlanner.select(
            seed = seed,
            relatedSongs = listOf(allowed, excluded, queuedOriginal),
            queuedIds = setOf(seed.id, queuedOriginal.id),
            previouslyInjectedIds = emptySet(),
            minimumSimilarity = 90,
            allowedArtistKeys = setOf("conjuntoprimavera"),
            limitToAllowedArtists = true,
        )

        assertEquals(listOf("primavera"), selected.map { it.mediaId })
    }

    @Test
    fun `artist whitelist also limits radio fallback candidates`() {
        val seed = mediaItem(id = "seed", artistId = "temerarios")
        val selected = EchoBrainQueuePlanner.selectRadioItems(
            seed = seed,
            candidates = listOf(
                mediaItem(id = "allowed", artistId = "temerarios"),
                mediaItem(id = "outside", artistId = "other-artist"),
            ),
            queuedIds = emptySet(),
            previouslyInjectedIds = emptySet(),
            minimumSimilarity = 90,
            allowedArtistKeys = setOf("temerarios"),
            limitToAllowedArtists = true,
        )

        assertEquals(listOf("allowed"), selected.map { it.mediaId })
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
    fun `strict radio rejects unrelated top results even when the mix is long`() {
        val seed = mediaItem("la-culpa", "La Culpa No Tengo Yo", "temerarios")
        val selected = EchoBrainQueuePlanner.selectRadioItems(
            candidates = listOf(
                mediaItem("tarzan-boy", "Tarzan Boy", "baltimora"),
                mediaItem("rivers", "Rivers of Babylon", "boney-m"),
                mediaItem("brother-louie", "Brother Louie", "modern-talking"),
            ),
            queuedIds = setOf("la-culpa"),
            previouslyInjectedIds = emptySet(),
            blockedArtistKeys = setOf("temerarios"),
            seed = seed,
            minimumSimilarity = 90,
            allowAlternativeVersions = false,
            maxItems = 3,
        )

        assertEquals(emptyList<String>(), selected.map { it.mediaId })
    }

    @Test
    fun `strict radio accepts only explicit anchor evidence`() {
        val seed = mediaItem("la-culpa", "La Culpa No Tengo Yo", "temerarios")
        val selected = EchoBrainQueuePlanner.selectRadioItems(
            candidates = listOf(
                mediaItem("unrelated", "Tarzan Boy", "baltimora"),
                mediaItem("same-artist", "Una canción relacionada", "temerarios"),
            ),
            queuedIds = emptySet(),
            previouslyInjectedIds = emptySet(),
            seed = seed,
            minimumSimilarity = 90,
            maxItems = 3,
        )

        assertEquals(listOf("same-artist"), selected.map { it.mediaId })
    }

    @Test
    fun `radio applies every hard exclusion before using its relation signal`() {
        val original = mediaItem("original", "Already in the Mix", "original-artist")
        val selected = EchoBrainQueuePlanner.selectRadioItems(
            candidates = listOf(
                mediaItem("queued", "Queued Track", "safe-artist"),
                mediaItem("previous", "Previous Track", "safe-artist"),
                mediaItem("duplicate-title", "ALREADY IN THE MIX", "original-artist"),
                mediaItem("blocked-artist", "Blocked Artist Track", "blocked-artist"),
                mediaItem("live-version", "Related Track (Live)", "live-artist"),
                mediaItem("safe", "Related Main Recording", "safe-artist"),
            ),
            queuedIds = setOf("queued"),
            previouslyInjectedIds = setOf("previous"),
            blockedSongKeys = EchoBrainQueuePlanner.canonicalSongKeys(listOf(original)),
            blockedArtistKeys = setOf("blockedartist"),
            minimumSimilarity = 60,
            allowAlternativeVersions = false,
            maxItems = 3,
        )

        assertEquals(listOf("safe"), selected.map { it.mediaId })
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
    fun `radio fallback blocks remaster and radio edit variants of an existing recording`() {
        val originalMixSong = mediaItem(
            id = "mix-im-not-alone",
            title = "I'm Not Alone",
            artistId = "calvin-harris",
        )
        val selected = EchoBrainQueuePlanner.selectRadioItems(
            candidates = listOf(
                mediaItem("radio-edit", "I'm Not Alone (Radio Edit)", "calvin-harris"),
                mediaItem("remaster", "I'm Not Alone (2009 Remaster)", "calvin-harris"),
                mediaItem("fresh", "Related track", "related-artist"),
            ),
            queuedIds = emptySet(),
            previouslyInjectedIds = emptySet(),
            blockedSongKeys = EchoBrainQueuePlanner.canonicalSongKeys(listOf(originalMixSong)),
            allowAlternativeVersions = true,
        )

        assertEquals(listOf("fresh"), selected.map { it.mediaId })
    }

    @Test
    fun `artist diversity excludes a recent Echo Brain artist before selecting`() {
        val repeatedArtist = song(id = "same-artist", artistId = "calvin-harris")
        val relatedArtist = song(id = "related-artist", artistId = "david-guetta")

        val selected = EchoBrainQueuePlanner.select(
            seed = song(id = "seed", artistId = "anchor"),
            relatedSongs = listOf(repeatedArtist, relatedArtist),
            queuedIds = emptySet(),
            previouslyInjectedIds = emptySet(),
            blockedArtistKeys = setOf("calvinharris"),
            minimumSimilarity = 60,
        )

        assertEquals(listOf("related-artist"), selected.map { it.mediaId })
    }

    @Test
    fun `expanded artist window avoids a four artist circle at every similarity threshold`() {
        val outsideCircle = song(id = "outside-circle", artistId = "outside")
        val fourArtistCircle = listOf(
            song(id = "circle-one", artistId = "artist-one"),
            song(id = "circle-two", artistId = "artist-two"),
            song(id = "circle-three", artistId = "artist-three"),
            song(id = "circle-four", artistId = "artist-four"),
        )
        val blockedCircle = setOf("artistone", "artisttwo", "artistthree", "artistfour")

        listOf(90, 80, 70, 60).forEach { threshold ->
            val selected = EchoBrainQueuePlanner.select(
                seed = song(id = "seed", artistId = "outside"),
                relatedSongs = fourArtistCircle + outsideCircle,
                queuedIds = emptySet(),
                previouslyInjectedIds = emptySet(),
                blockedArtistKeys = blockedCircle,
                minimumSimilarity = threshold,
                maxItems = 1,
            )

            assertEquals(listOf("outside-circle"), selected.map { it.mediaId })
        }
    }

    @Test
    fun `sequence feedback prefers a successful valid transition but never bypasses filters`() {
        val preferred = song(id = "preferred", title = "Preferred", artistId = "preferred-artist")
        val safeAlternative = song(id = "safe", title = "Safe", artistId = "safe-artist")
        val feedback = mapOf("preferred|preferredartist" to 2)

        val selected = EchoBrainQueuePlanner.select(
            seed = null,
            relatedSongs = listOf(safeAlternative, preferred),
            queuedIds = emptySet(),
            previouslyInjectedIds = emptySet(),
            sequenceFeedbackScores = feedback,
            minimumSimilarity = 60,
            maxItems = 1,
        )
        val selectedWithPreferredArtistBlocked = EchoBrainQueuePlanner.select(
            seed = null,
            relatedSongs = listOf(safeAlternative, preferred),
            queuedIds = emptySet(),
            previouslyInjectedIds = emptySet(),
            blockedArtistKeys = setOf("preferredartist"),
            sequenceFeedbackScores = feedback,
            minimumSimilarity = 60,
            maxItems = 1,
        )

        assertEquals(listOf("preferred"), selected.map { it.mediaId })
        assertEquals(listOf("safe"), selectedWithPreferredArtistBlocked.map { it.mediaId })
    }

    @Test
    fun `neuro profile only orders radio candidates that already pass strict similarity`() {
        val seed = mediaItem(id = "seed", artistId = "anchor")
        val safeAnchorMatch = mediaItem(id = "safe", artistId = "anchor")
        val profiledAnchorMatch = mediaItem(id = "profiled", artistId = "anchor")
        val unrelatedProfiled = mediaItem(id = "unrelated", artistId = "other")

        val ordered = EchoBrainQueuePlanner.selectRadioItems(
            seed = seed,
            candidates = listOf(safeAnchorMatch, profiledAnchorMatch),
            queuedIds = emptySet(),
            previouslyInjectedIds = emptySet(),
            neuroProfileScores = mapOf("profiled" to 100),
            minimumSimilarity = 90,
            maxItems = 1,
        )
        val blockedByThreshold = EchoBrainQueuePlanner.selectRadioItems(
            seed = seed,
            candidates = listOf(unrelatedProfiled),
            queuedIds = emptySet(),
            previouslyInjectedIds = emptySet(),
            neuroProfileScores = mapOf("unrelated" to 100),
            minimumSimilarity = 90,
            maxItems = 1,
        )

        assertEquals(listOf("profiled"), ordered.map { it.mediaId })
        assertEquals(emptyList<String>(), blockedByThreshold.map { it.mediaId })
    }

    @Test
    fun `strict 90 similarity trusts a direct relationship and strengthens local context`() {
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

        assertEquals(listOf("trusted-match", "anchor-match", "moment-only"), selected.map { it.mediaId })
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
    fun `early skip is a session signal only before twenty percent of the active song`() {
        assertEquals(true, EchoBrainQueuePlanner.isEarlySkip(positionMillis = 11_999L, durationMillis = 60_000L))
        assertEquals(false, EchoBrainQueuePlanner.isEarlySkip(positionMillis = 12_000L, durationMillis = 60_000L))
        assertEquals(false, EchoBrainQueuePlanner.isEarlySkip(positionMillis = 5_000L, durationMillis = -1L))
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
        assertEquals(
            true,
            EchoBrainQueuePlanner.shouldAutoInject(
                currentIndex = 4,
                mediaItemCount = 53,
                currentIsEchoBrainRecommendation = false,
                nextIsEchoBrainRecommendation = false,
                hasInjectedRecommendations = true,
                dominantMode = true,
            ),
        )
        assertEquals(
            false,
            EchoBrainQueuePlanner.shouldAutoInject(
                currentIndex = 4,
                mediaItemCount = 53,
                currentIsEchoBrainRecommendation = false,
                nextIsEchoBrainRecommendation = false,
                hasInjectedRecommendations = true,
                dominantMode = false,
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
        artistId: String? = null,
    ): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setUri(id)
            .setTag(
                MediaMetadata(
                    id = id,
                    title = title,
                    artists = artistId?.let { listOf(MediaMetadata.Artist(id = it, name = it)) }.orEmpty(),
                    duration = 0,
                ),
            ).build()
    @Test
    fun `live and remix filter remains active when alternative versions are allowed`() {
        val seed = song(id = "seed", artistId = "anchor")
        val live = song(id = "live", title = "Tema (Live)", artistId = "related-live")
        val enVivo = song(id = "en-vivo", title = "Tema en vivo", artistId = "related-en-vivo")
        val remix = song(id = "remix", title = "Tema Remix", artistId = "related-remix")
        val main = song(id = "main", title = "Tema principal", artistId = "related-main")

        val selected = EchoBrainQueuePlanner.select(
            seed = seed,
            relatedSongs = listOf(live, enVivo, remix, main),
            queuedIds = emptySet(),
            previouslyInjectedIds = emptySet(),
            minimumSimilarity = 60,
            allowAlternativeVersions = true,
            excludeLiveRemix = true,
        )

        assertEquals(listOf("main"), selected.map { it.mediaId })
    }
}
