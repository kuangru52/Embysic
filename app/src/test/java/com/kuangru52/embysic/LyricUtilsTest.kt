package com.kuangru52.embysic

import org.junit.Assert.*
import org.junit.Test

class LyricUtilsTest {

    @Test
    fun testCleanString() {
        assertEquals("songtitle", LyricUtils.cleanString("Song Title (Live)"))
        assertEquals("songtitle", LyricUtils.cleanString("Song Title [Remix]"))
        assertEquals("你好123", LyricUtils.cleanString("你好! 123..."))
        assertEquals("featartist", LyricUtils.cleanString("Feat. Artist"))
    }

    @Test
    fun testFindBestMatch_PerfectMatch() {
        val targetTitle = "Perfect"
        val targetArtist = "Artist"
        val targetDuration = 200000L

        val songs = listOf(
            NeteaseSearchSong(1, "Perfect", listOf(NeteaseArtist(1, "Artist", null)), null, 200000L),
            NeteaseSearchSong(2, "Other", listOf(NeteaseArtist(1, "Artist", null)), null, 200000L)
        )

        val match = LyricUtils.findBestMatch(songs, targetTitle, targetArtist, targetDuration)
        assertEquals(1L, match?.id)
    }

    @Test
    fun testFindBestMatch_LiveMismatch() {
        val targetTitle = "Song Name"
        val targetArtist = "Artist"
        val targetDuration = 200000L

        val songs = listOf(
            NeteaseSearchSong(1, "Song Name (Live)", listOf(NeteaseArtist(1, "Artist", null)), null, 200000L),
            NeteaseSearchSong(2, "Song Name", listOf(NeteaseArtist(1, "Artist", null)), null, 195000L) // Closer to original but duration off
        )

        val match = LyricUtils.findBestMatch(songs, targetTitle, targetArtist, targetDuration)
        // Song 2 should be preferred because Song 1 is "Live" while target is not.
        assertEquals(2L, match?.id)
    }

    @Test
    fun testFindBestMatch_DurationPenalty() {
        val targetTitle = "Song"
        val targetArtist = "Artist"
        val targetDuration = 200000L

        val songs = listOf(
            NeteaseSearchSong(1, "Song", listOf(NeteaseArtist(1, "Artist", null)), null, 210000L), // 10s diff = 100 penalty
            NeteaseSearchSong(2, "Song", listOf(NeteaseArtist(1, "Artist", null)), null, 205000L)  // 5s diff = 50 penalty
        )

        val match = LyricUtils.findBestMatch(songs, targetTitle, targetArtist, targetDuration)
        assertEquals(2L, match?.id)
    }

    @Test
    fun testFindBestMatch_HeavyMetadata() {
        // Target from Emby might have heavy metadata
        val targetTitle = "Song Title (Official Video) [Remastered]"
        val targetArtist = "Famous Artist"
        val targetDuration = 180000L

        val songs = listOf(
            NeteaseSearchSong(1, "Song Title", listOf(NeteaseArtist(1, "Famous Artist", null)), null, 180000L),
            NeteaseSearchSong(2, "Song Title (Live)", listOf(NeteaseArtist(1, "Famous Artist", null)), null, 180000L)
        )

        val match = LyricUtils.findBestMatch(songs, targetTitle, targetArtist, targetDuration)
        assertEquals(1L, match?.id)
    }
}
