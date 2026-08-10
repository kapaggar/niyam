package org.dhamma.gong.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GongTracksTest {

    @Test
    fun singleGongDeliversOneHitPerPlay() {
        assertEquals(1, GongTracks.hitsPerPlay("ting"))
        assertEquals(6, GongTracks.playsFor(6, "ting"))
    }

    @Test
    fun sikkimGongDeliversThreeHitsPerPlay() {
        assertEquals(3, GongTracks.hitsPerPlay("drum"))
    }

    @Test
    fun playsForDividesWithCeiling() {
        assertEquals(2, GongTracks.playsFor(6, "drum"))
        assertEquals(3, GongTracks.playsFor(7, "drum"))
        assertEquals(1, GongTracks.playsFor(1, "drum"))
        assertEquals(1, GongTracks.playsFor(3, "drum"))
        assertEquals(6, GongTracks.playsFor(16, "drum"))
    }

    @Test
    fun zeroOrNegativeRepeatsNeedNoPlays() {
        assertEquals(0, GongTracks.playsFor(0, "drum"))
        assertEquals(0, GongTracks.playsFor(-1, "ting"))
    }

    @Test
    fun unknownOrNullStemIsOneHitPerPlay() {
        // A sideloaded or future track must never be silently divided.
        assertEquals(1, GongTracks.hitsPerPlay("chime"))
        assertEquals(1, GongTracks.hitsPerPlay(null))
        assertEquals(5, GongTracks.playsFor(5, null))
    }

    @Test
    fun stemMatchingIsCaseAndWhitespaceInsensitive() {
        assertEquals(3, GongTracks.hitsPerPlay(" DRUM "))
        assertEquals("sikkim gong", GongTracks.label("Drum"))
    }

    @Test
    fun hitsAfterPlaysCapsAtRepeats() {
        // Two full plays of the Sikkim gong against repeats=5: six hits rang,
        // but the burst only ever claims what was asked for.
        assertEquals(5, GongTracks.hitsAfterPlays(2, 5, "drum"))
        assertEquals(3, GongTracks.hitsAfterPlays(1, 5, "drum"))
        assertEquals(2, GongTracks.hitsAfterPlays(2, 5, "ting"))
        assertEquals(0, GongTracks.hitsAfterPlays(0, 5, "drum"))
    }

    @Test
    fun labelsNameTheRecordingsNotTheLegacyIds() {
        assertEquals("single gong", GongTracks.label("ting"))
        assertEquals("sikkim gong", GongTracks.label("drum"))
        assertEquals("chime", GongTracks.label("chime"))
    }
}
