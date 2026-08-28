/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import java.text.Normalizer

/**
 * Parses the listener-owned Echo Brain artist list entirely on-device.
 *
 * The stored representation preserves the first readable spelling of every artist, while matching
 * uses a folded key so accents, whitespace and letter casing cannot create duplicate allowances.
 */
internal object EchoBrainArtistWhitelist {
    const val MAX_ARTISTS = 20_000

    fun parse(serializedArtists: String): List<String> {
        if (serializedArtists.isBlank()) return emptyList()

        val artists = linkedMapOf<String, String>()
        serializedArtists
            .split(Regex("[\\n,;]+"))
            .forEach { rawArtist ->
                val displayName = rawArtist.replace(Regex("\\s+"), " ").trim()
                val key = normalize(displayName)
                if (key.isNotBlank() && artists.size < MAX_ARTISTS) {
                    artists.putIfAbsent(key, displayName)
                }
            }
        return artists.values.toList()
    }

    fun serialize(rawArtists: String): String = parse(rawArtists).joinToString(separator = "\n")

    fun keys(serializedArtists: String): Set<String> = parse(serializedArtists).mapTo(mutableSetOf(), ::normalize)

    fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .lowercase()
            .filter(Char::isLetterOrDigit)
}
