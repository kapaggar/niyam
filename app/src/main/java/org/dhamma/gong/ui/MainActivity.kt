package org.dhamma.gong.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.dhamma.gong.service.AppliancePermissions
import org.dhamma.gong.service.GongService

/**
 * The only activity. It is a **client** of [GongService] — closing it must
 * change nothing about scheduling or playback (design doc §03).
 */
class MainActivity : ComponentActivity() {

    /**
     * Pending `EXTRA_TAB` deep-link request, consumed and cleared by [GongApp].
     *
     * The activity is `singleTask`, so the *second* `am start` does not create a
     * new instance: it brings this one forward and delivers the intent to
     * [onNewIntent]. `getIntent()` keeps returning the original launch intent
     * until `setIntent` is called, so reading the extra in [onCreate] alone made
     * every screenshot after the first capture the Dashboard.
     *
     * A flow (rather than a plain value) so a repeat request for the tab that is
     * already showing still lands — the consumer nulls it out after applying it.
     */
    private val tabRequest = MutableStateFlow<Tab?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Scrimless bars: targetSdk 35 forces edge-to-edge anyway, and opting in
        // explicitly keeps API 29-34 identical to API 35+. The style is set again
        // below once the stored theme is known — this first call only avoids a
        // frame of default chrome before Room answers.
        applyBarStyle(dark = true)
        super.onCreate(savedInstanceState)
        GongService.start(this)
        val initialTab = parseTab(intent)
        setContent {
            val vm: AppViewModel = viewModel()
            val themeMode by vm.themeMode.collectAsStateWithLifecycle()
            val dark = themeMode.isDark(isSystemInDarkTheme())

            // Status- and nav-bar icons are drawn by the OS, outside our
            // colour scheme. Left dark on a light page they are white on white
            // — the clock and battery simply vanish.
            LaunchedEffect(dark) { applyBarStyle(dark) }

            GongTheme(themeMode) {
                Surface(modifier = Modifier.fillMaxSize(), color = Nocturne.Bg) {
                    RequestAppliancePermissions(vm)
                    GongApp(vm, initialTab = initialTab, tabRequest = tabRequest)
                }
            }
        }
    }

    private fun applyBarStyle(dark: Boolean) {
        val style = if (dark) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            // The scrim argument is what API 29-34 falls back to when it cannot
            // draw dark icons; transparent there would lose them entirely.
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.argb(0x40, 0, 0, 0))
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Without this, getIntent() would still hand back the launch intent.
        setIntent(intent)
        parseTab(intent)?.let { tabRequest.value = it }
    }

    companion object {
        const val EXTRA_TAB = "tab"

        /**
         * Optional deep-link for docs/screenshots:
         * `-e tab DASHBOARD|SCHEDULE|COURSES|LOGS|SOUNDS|SETUP`.
         *
         * Case-insensitive: `-e tab courses` used to fail [Tab.valueOf] and fall
         * back to the Dashboard without saying so.
         */
        internal fun parseTab(intent: Intent?): Tab? =
            intent?.getStringExtra(EXTRA_TAB)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { runCatching { Tab.valueOf(it.uppercase()) }.getOrNull() }
    }
}

/**
 * First-run grants for the three OS permissions that keep overnight fires
 * reliable (FABLE-REVIEW B6). Notifications use the runtime dialog; exact
 * alarms and battery exemption need system settings screens and are opened
 * from the dashboard health card when still denied. Status is re-checked on
 * every ON_RESUME so a staff grant from Settings is reflected immediately.
 */
@Composable
private fun RequestAppliancePermissions(vm: AppViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { vm.refreshPermissionStatus() }

    fun refresh() {
        vm.updatePermissionStatus(AppliancePermissions.status(context))
    }

    LaunchedEffect(Unit) {
        refresh()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
