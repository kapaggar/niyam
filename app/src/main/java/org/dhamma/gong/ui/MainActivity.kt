package org.dhamma.gong.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import org.dhamma.gong.service.GongService

/**
 * M2 shell: enough UI to prove the service and the player work on a device.
 * The full Nocturne dashboard lands in M4 (see docs/handoff/).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GongService.start(this)
        setContent {
            GongTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ServiceProbeScreen()
                }
            }
        }
    }
}

@Composable
private fun ServiceProbeScreen() {
    val service by GongService.running.collectAsState()
    val status = service?.player?.status?.collectAsState()?.value
    val scope = rememberCoroutineScope()

    val notifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            "DHAMMA GONG",
            fontSize = 11.sp,
            letterSpacing = 1.1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            if (status?.playing == true) "Ringing" else "Idle",
            fontSize = 56.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onBackground,
        )

        HealthRow("Service", if (service != null) "running" else "not bound")
        HealthRow("Audio route", status?.route ?: "—")
        HealthRow(
            "Burst",
            if (status != null && status.ofStrikes > 0) {
                "strike ${status.strike} / ${status.ofStrikes}"
            } else {
                "—"
            },
        )
        HealthRow(
            "Last play",
            listOfNotNull(
                status?.lastFile?.takeIf { it.isNotBlank() },
                status?.lastResult?.takeIf { it.isNotBlank() },
            ).joinToString(" · ").ifBlank { "—" },
        )

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { scope.launch { service?.testGong() } },
                enabled = service != null,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(50.dp),
            ) { Text("Test gong") }

            OutlinedButton(
                onClick = { scope.launch { service?.testDoha(1) } },
                enabled = service != null,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(50.dp),
            ) { Text("Test doha") }

            OutlinedButton(
                onClick = { scope.launch { service?.stopPlayback() } },
                enabled = service != null,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.height(50.dp),
            ) { Text("■ Stop") }
        }

        Text(
            "M2 probe screen — scheduler loop is M3, full dashboard is M4.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HealthRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Text(
            label,
            fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp),
        )
        Text(
            value,
            fontSize = 12.5.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
