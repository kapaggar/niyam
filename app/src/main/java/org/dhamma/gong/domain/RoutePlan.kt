package org.dhamma.gong.domain

/**
 * Which output the appliance will actually use, and what Audio out should say
 * about it.
 *
 * The rule is one line of the design doc and it is not up for negotiation: a
 * gong from the wrong speaker beats no gong (§06, §10). A preferred route that
 * is not plugged in when the alarm fires falls back to the built-in speaker and
 * *says so* in the log — it never becomes silence, and it never quietly claims
 * to have played somewhere it did not.
 *
 * Keys, not device ids, are what get persisted. A Bluetooth amp that reconnects
 * with a new id is still "the Bluetooth route" to staff, and a settings row
 * holding a stale integer would strand the appliance on the speaker forever.
 */
object RoutePlan {

    const val SPEAKER = "speaker"
    const val BLUETOOTH = "bluetooth"
    const val USB = "usb"

    /** Reserved for the v1.1 wired amplifier; never offered in v1. */
    const val WIRED_AMP = "wired_amp"

    /** Display order. The one that is always there comes first. */
    val ORDER = listOf(SPEAKER, BLUETOOTH, USB, WIRED_AMP)

    /**
     * @param key what will actually be used
     * @param requested what was asked for
     * @param fellBack true when [key] is the speaker only because [requested]
     *   was not there to be used
     */
    data class Choice(
        val key: String,
        val requested: String,
        val fellBack: Boolean,
    )

    /**
     * Resolve a stored preference against what is plugged in right now.
     *
     * The speaker never checks availability: it is built into the device, and a
     * probe that failed to list it must not be allowed to conclude the
     * appliance has no output at all.
     */
    fun choose(availableKeys: Collection<String>, preferredKey: String?): Choice {
        val want = preferredKey?.trim().orEmpty().ifBlank { SPEAKER }
        if (want == SPEAKER) return Choice(SPEAKER, SPEAKER, fellBack = false)
        return if (availableKeys.contains(want)) {
            Choice(want, want, fellBack = false)
        } else {
            Choice(SPEAKER, want, fellBack = true)
        }
    }

    /** One line of the picker. */
    data class Row(
        val key: String,
        /** Present on the device right now. */
        val available: Boolean,
        /** The stored `audio_route` preference. */
        val selected: Boolean,
        /** This route last rendered a fire successfully (`state.route_last_ok`). */
        val lastOk: Boolean,
    )

    /**
     * What the picker draws.
     *
     * A selected route that has vanished still gets a row. Dropping it would
     * leave the screen showing the speaker ticked while the settings row says
     * bluetooth — staff would have no way to see, let alone undo, the choice
     * that is quietly falling back every morning.
     */
    fun rows(
        availableKeys: Collection<String>,
        preferredKey: String?,
        lastOkKey: String? = null,
    ): List<Row> {
        val selected = preferredKey?.trim().orEmpty().ifBlank { SPEAKER }
        val present = availableKeys.toMutableSet().apply { add(SPEAKER) }
        val keys = (present + selected).distinct().sortedWith(
            compareBy(
                { ORDER.indexOf(it).takeIf { i -> i >= 0 } ?: ORDER.size },
                { it },
            ),
        )
        return keys.map { key ->
            Row(
                key = key,
                available = present.contains(key),
                selected = key == selected,
                lastOk = key == lastOkKey?.trim().orEmpty().ifBlank { null },
            )
        }
    }

    /**
     * A name for a route with no device behind it — an unplugged USB DAC, or a
     * Bluetooth amp that is switched off. When the device *is* present the
     * caller has its real product name and should use that instead.
     */
    fun genericLabel(key: String): String = when (key) {
        SPEAKER -> "Built-in speaker"
        BLUETOOTH -> "Bluetooth"
        USB -> "USB audio"
        WIRED_AMP -> "Wired amp"
        else -> key
    }
}
