package org.dhamma.gong.domain

/**
 * How many destinations the shell offers.
 *
 * Two different people use this appliance. A centre server sets a course up in
 * the morning and wants the five screens that run it. A technician wires the
 * amplifier relay, pins the timezone and maps doha slots once, and never comes
 * back. Showing the technician's screens to the server every day is what made
 * the rail eleven items long.
 *
 * [SIMPLE] is the shipped default because the server is the daily user, and
 * because a screen nobody in the hall needs is a screen somebody in the hall
 * can break. Switching modes only changes what renders — it never edits, clears
 * or migrates a setting, so a centre configured in [ADVANCED] keeps every one
 * of those values while showing the short rail.
 */
enum class UiMode(val key: String, val label: String, val why: String) {
    SIMPLE(
        key = "simple",
        label = "Simple",
        why = "The five screens a course needs. Amp power and network live in Setup.",
    ),
    ADVANCED(
        key = "advanced",
        label = "Advanced",
        why = "Adds sounds, audio out, the full amp page and the timezone pin.",
    ),
    ;

    companion object {
        const val SETTING_KEY = "ui_mode"

        val DEFAULT = SIMPLE

        /**
         * Unknown, blank and null all resolve to [DEFAULT]. A restore from a
         * backup written before this release carries no `ui_mode` row, and an
         * appliance that answered "no mode at all" would render an empty rail.
         */
        fun parse(raw: String?): UiMode =
            entries.firstOrNull { it.key == raw?.trim()?.lowercase() } ?: DEFAULT
    }
}
