package org.dhamma.gong.domain

import java.time.ZoneId

/**
 * The appliance's timezone, from the `timezone` setting — never the device
 * default. A centre tablet must gong in the centre's zone even if the phone
 * thinks it has travelled (FABLE-REVIEW B1).
 *
 * Pi parity: the Pi daemon's config.py defaults `timezone = "Asia/Kolkata"`.
 */
object ApplianceZone {

    const val DEFAULT_ID = "Asia/Kolkata"

    val DEFAULT: ZoneId = ZoneId.of(DEFAULT_ID)

    /** Blank, null, or unparseable settings fall back to [DEFAULT] — a corrupt
     *  row must degrade to IST, not crash the loop or drift to UTC. */
    fun resolve(setting: String?): ZoneId {
        val id = setting?.trim().orEmpty()
        if (id.isEmpty()) return DEFAULT
        return runCatching { ZoneId.of(id) }.getOrDefault(DEFAULT)
    }
}
