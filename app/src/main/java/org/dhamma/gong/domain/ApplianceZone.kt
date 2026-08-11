package org.dhamma.gong.domain

import java.time.ZoneId

/**
 * The zone the schedule fires in.
 *
 * **Blank means "follow the device".** A tablet bought and installed at the
 * centre already knows where it is, and Android keeps that right across DST and
 * government changes without anyone touching the app. Making staff pick a zone
 * from a list was one more thing to get wrong on install day, and getting it
 * wrong moves every gong.
 *
 * The setting is kept — a centre *may* pin an explicit IANA id, which is the
 * escape hatch for a donated phone that insists it is still in another country.
 * A pinned zone always wins; only an empty setting defers to the device.
 *
 * An unparseable id also defers to the device rather than to a hardcoded
 * fallback: a corrupt row should leave the appliance on the tablet's own idea
 * of local time, which is very likely right, instead of silently moving every
 * gong to a zone nobody chose.
 */
object ApplianceZone {

    /** The stored value that means "follow the device". */
    const val FOLLOW_DEVICE = ""

    /** What the device currently reports. Re-read on every call, never cached —
     *  a tablet that crosses a timezone or gets a DST update must be believed. */
    fun deviceZone(): ZoneId = ZoneId.systemDefault()

    /**
     * @param setting the stored `timezone` value; blank = follow the device.
     */
    fun resolve(setting: String?): ZoneId {
        val id = setting?.trim().orEmpty()
        if (id.isEmpty()) return deviceZone()
        return runCatching { ZoneId.of(id) }.getOrDefault(deviceZone())
    }

    /** True when [setting] pins a zone rather than following the device. */
    fun isPinned(setting: String?): Boolean = !setting?.trim().isNullOrEmpty()
}
