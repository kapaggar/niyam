package org.dhamma.gong.domain

/**
 * What the bundled gong recordings actually contain.
 *
 * The stems `ting` and `drum` are legacy ids from the Pi daemon and are
 * load-bearing: they live in seeded settings (`gong_track`), in schedule rows
 * (`track`), and in the "—" inherit chain. Renaming them would strand every
 * existing database (`insertMissing` never rewrites a seeded setting), and an
 * unresolvable `gong_track` is a missed gong. So the ids stay, the audio and
 * the labels change:
 *
 *   - `ting` → Single_Gong recording: **one** ring per play.
 *   - `drum` → Sikkim gong recording: **three** hits per play.
 *
 * A schedule row's `repeats` counts audible HITS, not plays of the file —
 * `repeats: 6` must sound six times on either track. [playsFor] does the
 * division, so the player plays the Sikkim file twice, not six times.
 */
object GongTracks {
    const val SINGLE = "ting"
    const val SIKKIM = "drum"

    /** How many gong hits one full play of the recording delivers. */
    fun hitsPerPlay(stem: String?): Int =
        if (SIKKIM.equals(stem?.trim(), ignoreCase = true)) 3 else 1

    /** Staff-facing name; the stem itself is an id, not a description. */
    fun label(stem: String): String = when {
        SINGLE.equals(stem.trim(), ignoreCase = true) -> "single gong"
        SIKKIM.equals(stem.trim(), ignoreCase = true) -> "sikkim gong"
        else -> stem
    }

    /** Plays of the file needed to deliver [repeats] hits (ceiling division). */
    fun playsFor(repeats: Int, stem: String?): Int {
        if (repeats <= 0) return 0
        val h = hitsPerPlay(stem)
        return (repeats + h - 1) / h
    }

    /** Hits delivered once [plays] full plays have finished, capped at [repeats]. */
    fun hitsAfterPlays(plays: Int, repeats: Int, stem: String?): Int =
        minOf(plays.coerceAtLeast(0) * hitsPerPlay(stem), repeats)
}
