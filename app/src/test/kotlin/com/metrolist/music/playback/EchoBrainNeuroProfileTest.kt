package com.metrolist.music.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoBrainNeuroProfileTest {
    @Test
    fun `confirmed local listening orders metadata-overlapping candidates only`() {
        val profile = EchoBrainNeuroProfile()
        profile.recordConfirmedPlayback(item("seed", "Cumbia de noche", "Luna Norte"))

        val scores = profile.candidateScores(
            listOf(
                item("related", "Cumbia del amanecer", "Luna Norte"),
                item("other", "Ruido digital", "Vector Gris"),
            ),
        )

        assertTrue((scores["related"] ?: 0) > (scores["other"] ?: 0))
    }

    @Test
    fun `early skip is accepted once and does not erase a confirmed profile`() {
        val profile = EchoBrainNeuroProfile()
        profile.recordConfirmedPlayback(item("seed", "Bolero azul", "Mar Azul"))

        assertTrue(profile.recordEarlySkip(item("skipped", "Ruido digital", "Vector Gris")))
        assertTrue(profile.candidateScores(listOf(item("candidate", "Bolero de madrugada", "Mar Azul"))).isNotEmpty())
    }

    private fun item(id: String, title: String, artist: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .build(),
            )
            .build()
}
