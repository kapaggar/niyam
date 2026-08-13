package org.dhamma.gong.assets

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.dhamma.gong.BuildConfig

/**
 * Process-wide [AudioAssetManager], lazily built on first use. This is the
 * only place the pipeline meets Android: app storage, connectivity,
 * `BuildConfig.MEDIA_PASSPHRASE`, and the legacy storage roots for scans.
 *
 * Both the [org.dhamma.gong.service.GongService] and the UI call [get]; the
 * manager's own IO scope outlives either, matching the appliance model where
 * a download must survive the activity closing.
 */
object AudioAssets {

    private const val MANIFEST_ASSET = "doha_manifest.json"
    private const val AUDIO_DIR = "audio"

    @Volatile
    private var instance: AudioAssetManager? = null

    /** Safe from any thread; always binds to the application context. */
    fun get(context: Context): AudioAssetManager =
        instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

    private fun build(app: Context): AudioAssetManager {
        val catalog = AssetCatalog.parse(
            app.assets.open(MANIFEST_ASSET).bufferedReader().use { it.readText() },
        )
        val root = File(app.getExternalFilesDir(null) ?: app.filesDir, AUDIO_DIR)
        return AudioAssetManager(
            assetCatalog = catalog,
            store = AssetStore(root),
            downloader = CdnDownloader(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            // A fresh CharArray per call; the manager wipes it after use and
            // the value never reaches a log, toast, or exception message.
            passphrase = { BuildConfig.MEDIA_PASSPHRASE.toCharArray() },
            online = { isOnline(app) },
            metered = { isMetered(app) },
            legacyRoots = {
                listOf(
                    Environment.getExternalStorageDirectory(),
                    File("/sdcard"),
                    File("/storage/emulated/0"),
                )
            },
        )
    }

    private fun connectivity(app: Context): ConnectivityManager? =
        app.getSystemService(ConnectivityManager::class.java)

    /** Internet-capable network present right now; unknown = false. */
    private fun isOnline(app: Context): Boolean {
        val cm = connectivity(app) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Metered or unknown = true — a ~45 MB doha deserves caution. */
    private fun isMetered(app: Context): Boolean {
        val cm = connectivity(app) ?: return true
        return cm.isActiveNetworkMetered
    }
}
