package org.dhamma.gong.assets

import java.io.File
import java.io.IOException

/**
 * Finds existing media files under user-supplied roots (SD cards, Downloads,
 * old app dirs) without ever walking the whole volume.
 *
 * Rules:
 * - Depth ≤ 2 only: `{root}/<filename>` and `{root}/<one-subdir>/<filename>`.
 *   Never deeper.
 * - If a subdirectory named exactly `common-general` exists at depth ≤ 2,
 *   only that directory is listed (flat) for the root.
 * - Symlinks that escape the root are ignored (canonical-path prefix check).
 * - Unreadable or missing roots are skipped silently.
 * - First hit wins per filename, in [roots] order.
 */
class StorageLocator(private val roots: List<File>) {

    /** filename → found file. */
    fun scan(filenames: Set<String>): Map<String, File> {
        val found = LinkedHashMap<String, File>()
        if (filenames.isEmpty()) return found
        for (root in roots) {
            if (found.size == filenames.size) break
            if (!root.isDirectory) continue
            val rootCanonical = canonicalOrNull(root) ?: continue
            val commonGeneral = findCommonGeneral(root, rootCanonical)
            if (commonGeneral != null) {
                collectFlat(commonGeneral, rootCanonical, filenames, found)
            } else {
                collectFlat(root, rootCanonical, filenames, found)
                for (sub in listSorted(root)) {
                    if (!sub.isDirectory) continue
                    if (!isInside(sub, rootCanonical)) continue
                    collectFlat(sub, rootCanonical, filenames, found)
                }
            }
        }
        return found
    }

    /** Matching files directly inside [dir]; no recursion. */
    private fun collectFlat(
        dir: File,
        rootCanonical: String,
        filenames: Set<String>,
        found: MutableMap<String, File>,
    ) {
        for (f in listSorted(dir)) {
            if (found.containsKey(f.name)) continue
            if (f.name !in filenames) continue
            if (!f.isFile) continue
            if (!isInside(f, rootCanonical)) continue
            found[f.name] = f
        }
    }

    /** A dir named exactly `common-general` at depth 1 or 2, or null. */
    private fun findCommonGeneral(root: File, rootCanonical: String): File? {
        val depth1 = listSorted(root).filter { it.isDirectory && isInside(it, rootCanonical) }
        depth1.firstOrNull { it.name == COMMON_GENERAL }?.let { return it }
        for (sub in depth1) {
            listSorted(sub)
                .firstOrNull { it.name == COMMON_GENERAL && it.isDirectory && isInside(it, rootCanonical) }
                ?.let { return it }
        }
        return null
    }

    /** Sorted for deterministic first-hit results; unreadable dir = empty. */
    private fun listSorted(dir: File): List<File> =
        dir.listFiles()?.sortedBy { it.name } ?: emptyList()

    /** Canonical-path prefix check so symlinks cannot escape the root. */
    private fun isInside(f: File, rootCanonical: String): Boolean {
        val path = canonicalOrNull(f) ?: return false
        return path == rootCanonical || path.startsWith(rootCanonical + File.separator)
    }

    private fun canonicalOrNull(f: File): String? =
        try {
            f.canonicalPath
        } catch (e: IOException) {
            null
        }

    private companion object {
        const val COMMON_GENERAL = "common-general"
    }
}
