package org.dhamma.gong.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.dhamma.gong.domain.PinCode

/**
 * Full-screen gate shown when a PIN is set and the app has not been unlocked
 * this session. Everything behind it — including the dashboard — is hidden.
 */
@Composable
fun PinLockScreen(vm: AppViewModel) {
    var entry by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (busy || entry.length < 4) return
        busy = true
        scope.launch {
            val ok = vm.verifyAndUnlock(entry)
            if (!ok) {
                wrong = true
                entry = ""
            }
            busy = false
        }
    }

    Column(
        Modifier.fillMaxSize().background(Nocturne.Bg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Eyebrow("Dhamma Gong")
        Spacer(Modifier.height(6.dp))
        Text("Enter PIN", fontSize = 23.sp, fontWeight = FontWeight.Medium, color = Nocturne.Text)
        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(maxOf(entry.length, 4)) { i ->
                Box(
                    Modifier
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(if (i < entry.length) Nocturne.Accent else Nocturne.Neutral700),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (wrong) "Wrong PIN — try again" else " ",
            fontSize = 13.sp,
            color = Nocturne.Error,
        )
        Spacer(Modifier.height(14.dp))

        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("⌫", "0", "OK"),
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (rowKeys in keys) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (key in rowKeys) {
                        KeypadButton(key, accent = key == "OK") {
                            wrong = false
                            when (key) {
                                "⌫" -> entry = entry.dropLast(1)
                                "OK" -> submit()
                                else -> if (entry.length < 8) entry += key
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadButton(label: String, accent: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .size(74.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (accent) Nocturne.Accent else Nocturne.Surface)
            .border(
                1.dp,
                if (accent) Nocturne.Accent else Nocturne.Neutral700,
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = if (label == "OK") 16.sp else 21.sp,
            fontFamily = Nocturne.Mono,
            fontWeight = FontWeight.Medium,
            color = if (accent) Nocturne.Bg else Nocturne.Text,
        )
    }
}

/**
 * Set, change, or remove the app-open PIN. Reachable only once unlocked, so
 * "reset from inside the app" always means "prove the current PIN first".
 */
@Composable
fun SecurityScreen(vm: AppViewModel) {
    val pinHash by vm.pinHash.collectAsState()
    val pinIsSet = PinCode.isSet(pinHash)

    var current by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    fun clear() {
        current = ""
        newPin = ""
        confirm = ""
    }

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ScreenTitle(
            "PIN",
            if (pinIsSet) {
                "A PIN is set. The app asks for it every time it opens."
            } else {
                "No PIN set — the app opens without asking. Set a 4–8 digit PIN " +
                    "to keep casual fingers off the schedule."
            },
        )

        SurfaceCard(modifier = Modifier.width(430.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (pinIsSet) {
                    Eyebrow("Current PIN")
                    Field(current, { current = it.digitsMax8() }, "current PIN")
                }
                Eyebrow(if (pinIsSet) "New PIN" else "PIN")
                Field(newPin, { newPin = it.digitsMax8() }, "4–8 digits")
                Eyebrow("Confirm")
                Field(confirm, { confirm = it.digitsMax8() }, "repeat it")

                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(if (pinIsSet) "Change PIN" else "Set PIN") {
                        vm.setOrChangePin(current, newPin, confirm)
                        clear()
                    }
                    if (pinIsSet) {
                        PrimaryButton("Remove PIN") {
                            vm.removePin(current)
                            clear()
                        }
                    }
                }
            }
        }
    }
}

private fun String.digitsMax8(): String = filter(Char::isDigit).take(8)
