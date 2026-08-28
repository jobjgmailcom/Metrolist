package com.metrolist.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoBrainArtistWhitelistTest {
    @Test
    fun `parser accepts separators folds accents and removes duplicates`() {
        val artists = EchoBrainArtistWhitelist.parse(
            "Los Temerarios\nConjunto Primavera; los temerarios,  Los Acosta  ",
        )

        assertEquals(listOf("Los Temerarios", "Conjunto Primavera", "Los Acosta"), artists)
        assertEquals(
            setOf("lostemerarios", "conjuntoprimavera", "losacosta"),
            EchoBrainArtistWhitelist.keys(artists.joinToString("\n")),
        )
    }

    @Test
    fun `parser keeps only the first twenty thousand valid artists`() {
        val pasted = (1..20_005).joinToString("\n") { "Artista $it" }
        val artists = EchoBrainArtistWhitelist.parse(pasted)

        assertEquals(EchoBrainArtistWhitelist.MAX_ARTISTS, artists.size)
        assertEquals("Artista 1", artists.first())
        assertEquals("Artista 20000", artists.last())
        assertTrue(EchoBrainArtistWhitelist.serialize(pasted).isNotBlank())
    }
}
