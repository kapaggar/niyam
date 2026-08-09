package org.dhamma.gong.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * The appliance shell: a persistent left nav rail and a content pane.
 * Nav state is the only routing — there is no back stack to get lost in
 * (design handoff, "Interactions & behaviour").
 */
enum class Tab(
    val label: String,
    val glyph: String,
    val requiresPin: Boolean,
    val enabled: Boolean = true,
) {
    DASHBOARD("Dashboard", "◉", requiresPin = false),
    SCHEDULE("Schedule", "▦", requiresPin = true),
    COURSES("Courses", "▤", requiresPin = true),
    LOGS("Logs", "≡", requiresPin = false),
    SECURITY("PIN", "⚿", requiresPin = false),

    // Specified in design doc §08 but not yet designed — drawn locked.
    SOUNDS("Sounds", "♪", requiresPin = true, enabled = false),
    AUDIO_OUT("Audio out", "⊳", requiresPin = true, enabled = false),
    TIME("Time", "◷", requiresPin = true, enabled = false),
    NETWORK("Network", "⌁", requiresPin = true, enabled = false),
    SETUP("Setup", "✓", requiresPin = true, enabled = false),
}

@Composable
fun GongApp(vm: AppViewModel) {
    var tab by remember { mutableStateOf(Tab.DASHBOARD) }
    val toast by vm.toast.collectAsState()
    val pinHash by vm.pinHash.collectAsState()
    val unlocked by vm.unlocked.collectAsState()

    Box(
        Modifier
            .fillMaxSize()
            .background(Nocturne.Bg),
    ) {
        when {
            // Not yet read from the DB: show nothing rather than flash the
            // dashboard at someone who should be seeing the lock.
            pinHash == null -> Unit

            org.dhamma.gong.domain.PinCode.isSet(pinHash) && !unlocked -> PinLockScreen(vm)

            else -> Row(Modifier.fillMaxSize()) {
                NavRail(current = tab, onSelect = { tab = it })
                Box(Modifier.fillMaxSize()) {
                    when (tab) {
                        Tab.DASHBOARD -> DashboardScreen(vm)
                        Tab.COURSES -> CoursesScreen(vm)
                        Tab.SCHEDULE -> ScheduleScreen(vm)
                        Tab.LOGS -> LogsScreen(vm)
                        Tab.SECURITY -> SecurityScreen(vm)
                        else -> LockedScreen(tab)
                    }
                }
            }
        }

        // Toast: centred 38 dp from the top, amber, auto-dismissed after 2.6 s.
        AnimatedVisibility(
            visible = toast != null,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 38.dp),
            enter = slideInVertically { -it } + fadeIn(),
            exit = fadeOut(),
        ) {
            Toast(toast.orEmpty())
        }
        LaunchedEffect(toast) {
            if (toast != null) {
                delay(2_600)
                vm.clearToast()
            }
        }
    }
}

@Composable
private fun NavRail(current: Tab, onSelect: (Tab) -> Unit) {
    Column(
        Modifier
            .width(186.dp)
            .fillMaxHeight()
            .background(Nocturne.NavRail)
            .padding(vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "DHAMMA GONG",
            fontSize = 11.sp,
            letterSpacing = 1.1.sp,
            fontWeight = FontWeight.Medium,
            color = Nocturne.Neutral500,
            modifier = Modifier.padding(start = 20.dp, bottom = 18.dp),
        )
        for (t in Tab.entries) {
            NavItem(
                tab = t,
                selected = t == current,
                onClick = { if (t.enabled) onSelect(t) },
            )
        }
    }
}

@Composable
private fun NavItem(tab: Tab, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(Nocturne.MIN_TOUCH_DP.dp)
            .padding(horizontal = 10.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Nocturne.Accent.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(enabled = tab.enabled, onClick = onClick)
            .padding(horizontal = 10.dp)
            // Locked items are inert at 42 % (design handoff).
            .alpha(if (tab.enabled) 1f else 0.42f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(tab.glyph, fontSize = 15.sp, color = if (selected) Nocturne.Accent200 else Nocturne.Neutral500)
        Text(
            tab.label,
            fontSize = 13.5.sp,
            color = if (selected) Nocturne.Accent100 else Nocturne.Neutral400,
            modifier = Modifier.weight(1f),
        )
        if (!tab.enabled) Text("🔒", fontSize = 11.sp)
    }
}

@Composable
private fun Toast(message: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Nocturne.Warning.copy(alpha = 0.16f))
            .border(1.dp, Nocturne.Warning.copy(alpha = 0.40f), RoundedCornerShape(8.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(message, fontSize = 13.5.sp, color = Nocturne.Warning)
    }
}

@Composable
private fun LockedScreen(tab: Tab) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenTitle(tab.label)
        Text(
            "Specified in the design doc but not yet designed. " +
                "See android/PROGRESS.md for the milestone that lands it.",
            fontSize = 13.5.sp,
            color = Nocturne.Neutral500,
        )
    }
}

// ---------------------------------------------------------------- shared

@Composable
fun ScreenTitle(text: String, subtitle: String? = null) {
    Column {
        Text(text, fontSize = 23.sp, fontWeight = FontWeight.Medium, color = Nocturne.Text)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, fontSize = 13.5.sp, color = Nocturne.Neutral500)
        }
    }
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        fontSize = 11.sp,
        letterSpacing = 1.1.sp,
        fontWeight = FontWeight.Medium,
        color = Nocturne.Neutral500,
        modifier = modifier,
    )
}

@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Nocturne.Surface)
            .padding(padding),
        content = content,
    )
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Nocturne.Hairline))
}

/** The health-row dot: 7 dp, colour-coded. */
@Composable
fun Dot(color: Color) {
    Box(
        Modifier
            .size(7.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(color),
    )
}

@Composable
fun Tag(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.40f), RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(text, fontSize = 10.5.sp, letterSpacing = 0.6.sp, color = color)
    }
}
