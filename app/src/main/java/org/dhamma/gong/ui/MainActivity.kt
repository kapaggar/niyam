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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
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
                    RequestNotifications()
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
 * The persistent notification is the appliance's health indicator; denied, the
 * service still runs and health is only visible in-app (design doc §09).
 */
@Composable
private fun RequestNotifications() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
