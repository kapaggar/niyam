package org.dhamma.gong.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import org.dhamma.gong.service.AppliancePermissions
import org.dhamma.gong.service.GongService

/**
 * The only activity. It is a **client** of [GongService] — closing it must
 * change nothing about scheduling or playback (design doc §03).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GongService.start(this)
        // Optional deep-link for docs/screenshots: -e tab COURSES|SCHEDULE|LOGS|SECURITY|DASHBOARD
        val initialTab = intent?.getStringExtra(EXTRA_TAB)
            ?.let { runCatching { Tab.valueOf(it) }.getOrNull() }
        setContent {
            GongTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Nocturne.Bg) {
                    val vm: AppViewModel = viewModel()
                    RequestAppliancePermissions(vm)
                    GongApp(vm, initialTab = initialTab)
                }
            }
        }
    }

    companion object {
        const val EXTRA_TAB = "tab"
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
