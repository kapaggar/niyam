package org.dhamma.gong.domain

/**
 * Which palette the shell paints in.
 *
 * The appliance's home is a dim meditation hall on a wall bracket, so [DARK]
 * stays the default and the shipped default must never change silently — a
 * tablet that boots white at 04:00 lights up the hall. But the same build is
 * also set up on an office desk under fluorescent light, and read at arm's
 * length there, which is where a light palette earns its place.
 *
 * [SYSTEM] defers to the device's own day/night setting so a tablet already
 * running a scheduled dark mode does not have to be told twice.
 */
enum class ThemeMode(val key: String, val label: String, val why: String) {
    DARK(
        key = "dark",
        label = "Dark",
        why = "The hall default. Nothing on screen lights the room at 04:00.",
    ),
    LIGHT(
        key = "light",
        label = "Light",
        why = "For a desk under office light, where dark-on-bright is easier to read.",
    ),
    SYSTEM(
        key = "system",
        label = "Follow device",
        why = "Tracks the tablet's own day/night setting.",
    ),
    ;

    /** Resolve to a concrete palette given what the OS currently reports. */
    fun isDark(deviceIsDark: Boolean): Boolean = when (this) {
        DARK -> true
        LIGHT -> false
        SYSTEM -> deviceIsDark
    }

    companion object {
        const val SETTING_KEY = "theme"

        val DEFAULT = DARK

        /**
         * Unknown, blank and null all resolve to [DEFAULT]. A restore from an
         * older backup, or a hand-edited row, must not leave the shell unpainted.
         */
        fun parse(raw: String?): ThemeMode =
            entries.firstOrNull { it.key == raw?.trim()?.lowercase() } ?: DEFAULT
    }
}
