package org.dhamma.gong.domain

/**
 * Pure classification of a sideloaded doha pack — filename → slot.
 *
 * No Android imports on purpose: SAF (`DocumentsContract`, tree URIs, persisted
 * permissions) lives in the UI layer, and everything that could get a slot
 * *wrong* lives here, where the JVM tests can reach it.
 *
 * Two rules this file exists to protect (design doc "Auto-mapping"):
 *   - never guess — an unparseable prefix or a contested slot is reported,
 *     not resolved by coin flip;
 *   - never displace staff — auto-map may only write a slot that is empty or
 *     already `auto`.
 */
object DohaPackMapper {

    /** Source keys as stored in `media_slots.source`. Kept as plain strings so
     *  `domain/` need not see the Room entity. */
    const val SOURCE_AUTO = "auto"

    /** The one extension staff may use in v1 — a finite list keeps "why didn't
     *  my file appear" answerable. */
    const val AUDIO_EXT = ".mp3"

    /** The subdirectory name we will descend into, exactly one level. */
    const val DOHA_DIR = "doha"

    /** A file seen in a scanned directory. [uri] is opaque here. */
    data class ScannedFile(val name: String, val uri: String)

    /** An immediate directory listing. Deeper levels are modelled, not scanned. */
    data class DirNode(
        val name: String,
        val files: List<ScannedFile> = emptyList(),
        val dirs: List<DirNode> = emptyList(),
    )

    /**
     * Which files a picked tree actually offers.
     *
     * @param viaDohaChild true when we descended into the single `doha/` child.
     */
    data class ScanTarget(
        val files: List<ScannedFile>,
        val viaDohaChild: Boolean,
    )

    /** A file that parsed to a slot already held by `manual` or `bundled`. */
    data class Skipped(val file: ScannedFile, val slot: Int, val heldBy: String)

    /** Two or more files claiming one slot. Nothing is auto-assigned. */
    data class Conflict(val slot: Int, val files: List<ScannedFile>)

    data class Mapping(
        /** Slots auto-map is allowed to write, with the file that won them. */
        val assigned: Map<Int, ScannedFile> = emptyMap(),
        val skipped: List<Skipped> = emptyList(),
        val conflicts: List<Conflict> = emptyList(),
        val unassigned: List<ScannedFile> = emptyList(),
    ) {
        val isEmpty: Boolean
            get() = assigned.isEmpty() && skipped.isEmpty() &&
                conflicts.isEmpty() && unassigned.isEmpty()
    }

    /** `D01…D11` at the very start of the name, case-insensitive, exactly two digits. */
    private val PREFIX = Regex("^[dD](\\d{2})(?![0-9])")

    fun isAudio(name: String): Boolean = name.endsWith(AUDIO_EXT, ignoreCase = true)

    /**
     * @return slot 1..11, or null when the name does not begin with a parseable
     *   `Dnn` token or the number is outside the slot range. Slot 0 and slot 12
     *   are *rejected*, not clamped.
     */
    fun parseSlot(filename: String): Int? {
        val n = PREFIX.find(filename)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        return if (n in DohaSlots.SLOTS) n else null
    }

    /**
     * Staff must pick the folder that *directly contains* `D01…D11`.
     *
     * One concession, no more: when the picked tree holds no matching audio at
     * the top level and its immediate children contain exactly one directory
     * named `doha` (case-insensitive), scan that child's immediate children.
     * Never deeper — anything else is a wrong pick and gets the empty state.
     */
    fun resolveScanTarget(root: DirNode): ScanTarget {
        val top = root.files.filter { isAudio(it.name) }
        if (top.isNotEmpty()) return ScanTarget(top, viaDohaChild = false)

        val dohaChildren = root.dirs.filter { it.name.equals(DOHA_DIR, ignoreCase = true) }
        if (dohaChildren.size != 1) return ScanTarget(emptyList(), viaDohaChild = false)

        // Only the child's *immediate* files. Its own subdirectories are not
        // followed, so a two-level-deep pack resolves to nothing.
        val nested = dohaChildren.first().files.filter { isAudio(it.name) }
        return ScanTarget(nested, viaDohaChild = nested.isNotEmpty())
    }

    /**
     * Classify a scanned listing against what the slots already hold.
     *
     * @param heldSources slot → current `media_slots.source`, for mapped slots only.
     *   A slot held by anything other than `auto` is protected: a file claiming
     *   it is reported [Skipped], never applied.
     */
    fun classify(
        files: List<ScannedFile>,
        heldSources: Map<Int, String> = emptyMap(),
    ): Mapping {
        val ordered = files.sortedBy { it.name.lowercase() }
        val unassigned = mutableListOf<ScannedFile>()
        val claims = linkedMapOf<Int, MutableList<ScannedFile>>()

        for (f in ordered) {
            val slot = if (isAudio(f.name)) parseSlot(f.name) else null
            if (slot == null) {
                unassigned += f
            } else {
                claims.getOrPut(slot) { mutableListOf() } += f
            }
        }

        val assigned = linkedMapOf<Int, ScannedFile>()
        val skipped = mutableListOf<Skipped>()
        val conflicts = mutableListOf<Conflict>()

        for ((slot, claimants) in claims.entries.sortedBy { it.key }) {
            if (claimants.size > 1) {
                // Silently picking one would be a coin flip on which recording
                // plays at 04:30. Report both; staff resolve it manually.
                conflicts += Conflict(slot, claimants.toList())
                continue
            }
            val file = claimants.first()
            val holder = heldSources[slot]
            if (holder != null && !holder.equals(SOURCE_AUTO, ignoreCase = true)) {
                skipped += Skipped(file, slot, holder)
            } else {
                assigned[slot] = file
            }
        }

        return Mapping(assigned, skipped, conflicts, unassigned)
    }
}
