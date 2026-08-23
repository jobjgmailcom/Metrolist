package com.metrolist.music.playback

import com.metrolist.music.db.entities.Song
import com.metrolist.music.db.entities.SongEntity
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

    private fun song(
        id: String,
        liked: Boolean = false,
        totalPlayTime: Long = 0L,
    ): Song =
        Song(
            song = SongEntity(
                id = id,
                title = id,
                liked = liked,
                totalPlayTime = totalPlayTime,
            ),
            artists = emptyList(),
        )
}
