package org.dhamma.gong.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Placeholder shell. Domain logic (M0) is under [org.dhamma.gong.domain].
 * Scheduler / player / full UI land in later milestones — see docs/.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PlaceholderHome()
                }
            }
        }
    }
}

@Composable
private fun PlaceholderHome() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Dhamma Gong", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Android appliance scaffold (MVP shell).\n" +
                "Domain unit tests: ActiveCourse, DohaSlots, ScheduleMaterializer, FireRules.\n" +
                "Seed: assets/seed/seed.json — schedule parity with Gong-NG.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Next: Room + seed import, player service, scheduler (see docs/ANDROID-APP-IMPLEMENTATION-PROMPT.md).",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
