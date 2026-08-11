package org.dhamma.gong.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.dhamma.gong.domain.PinCode

private const val BACKSPACE = "⌫"
private const val CONFIRM = "OK"

/**
 * Full-screen gate shown when a PIN is set and the app has not been unlocked
 * this session. Everything behind it — including the dashboard — is hidden.
 */
@Composable
fun PinLockScreen(vm: AppViewModel) {
    // `remember`, deliberately NOT `rememberSaveable`: a saveable buffer would
    // write the typed plaintext PIN into the saved-instance-state Bundle, which
    // Android may persist to disk and hand back after process death. Losing a
    // half-typed PIN on rotation is the cheaper trade. Do not "fix" this.
    var entry by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (busy || entry.length < 4) return
        busy = true
        scope.launch {
            // verifyAndUnlock applies the wrong-PIN backoff itself, so `busy`
            // stays true (and the keypad inert) for the whole penalty window.
            val ok = vm.verifyAndUnlock(entry)
            if (!ok) {
                wrong = true
                entry = ""
            }
            busy = false
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Nocturne.Bg)) {
        // Landscape phones give us ~360–400 dp of height; the full-size keypad
        // alone needs 326 dp, so the OK row used to be clipped off-screen with
        // no way to submit. Scroll always, and shrink the keys when short.
        val viewportHeight = maxHeight
        val compact = viewportHeight < 520.dp
        val keySize = if (compact) 56.dp else 74.dp
        val keyGap = if (compact) 8.dp else 10.dp

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // `Arrangement.Center` is a no-op inside a scrolling column, so the
            // inner column claims at least the viewport height and centres in
            // that instead — centred when it fits, scrollable when it does not.
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = viewportHeight)
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Eyebrow("Dhamma Gong")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Enter PIN",
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Medium,
                    color = Nocturne.Text,
                )
                Spacer(Modifier.height(if (compact) 12.dp else 18.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.semantics(mergeDescendants = true) {
                        contentDescription = when (entry.length) {
                            0 -> "No digits entered"
                            1 -> "1 digit entered"
                            else -> "${entry.length} digits entered"
                        }
                    },
                ) {
                    repeat(maxOf(entry.length, 4)) { i ->
                        Box(
                            Modifier
                                .size(13.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i < entry.length) Nocturne.Accent else Nocturne.Neutral700,
                                ),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        busy -> "Checking…"
                        wrong -> "Wrong PIN — try again"
                        else -> " "
                    },
                    fontSize = 13.sp,
                    color = if (busy) Nocturne.Neutral500 else Nocturne.Error,
                )
                Spacer(Modifier.height(if (compact) 10.dp else 14.dp))

                val keys = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf(BACKSPACE, "0", CONFIRM),
                )
                Column(verticalArrangement = Arrangement.spacedBy(keyGap)) {
                    for (rowKeys in keys) {
                        Row(horizontalArrangement = Arrangement.spacedBy(keyGap)) {
                            for (key in rowKeys) {
                                KeypadButton(
                                    label = key,
                                    description = keyDescription(key),
                                    size = keySize,
                                    accent = key == CONFIRM,
                                ) {
                                    if (busy) return@KeypadButton
                                    wrong = false
                                    when (key) {
                                        BACKSPACE -> entry = entry.dropLast(1)
                                        CONFIRM -> submit()
                                        else -> if (entry.length < 8) entry += key
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** TalkBack reads glyphs like "⌫" as raw characters, so name every key. */
private fun keyDescription(key: String): String = when (key) {
    BACKSPACE -> "Delete last digit"
    CONFIRM -> "Unlock"
    else -> "Digit $key"
}

@Composable
private fun KeypadButton(
    label: String,
    description: String,
    size: androidx.compose.ui.unit.Dp,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(if (accent) Nocturne.Accent else Nocturne.Surface)
            .border(
                1.dp,
                if (accent) Nocturne.Accent else Nocturne.Neutral700,
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .semantics {
                this.contentDescription = description
                this.role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = if (label == CONFIRM) 16.sp else 21.sp,
            fontFamily = Nocturne.Mono,
            fontWeight = FontWeight.Medium,
            color = if (accent) Nocturne.Bg else Nocturne.Text,
        )
    }
}

/**
 * Set, change, or remove the app-open PIN.
 *
 * Lives on **Setup** rather than its own nav entry: it is an install-day
 * decision made once by the same person working through the OS grants, not
 * something staff visit. Reachable only once unlocked, so "reset from inside
 * the app" always means "prove the current PIN first".
 */
@Composable
fun SecurityCard(vm: AppViewModel) {
    val pinHash by vm.pinHash.collectAsState()
    val pinIsSet = PinCode.isSet(pinHash)

    // `remember`, deliberately NOT `rememberSaveable`: a saveable buffer would
    // write the typed plaintext PIN into the saved-instance-state Bundle, which
    // Android may persist to disk and hand back after process death. Do not
    // "fix" this to survive rotation.
    var current by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    fun clear() {
        current = ""
        newPin = ""
        confirm = ""
    }

    SurfaceCard(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Eyebrow("PIN")
            if (pinIsSet) Tag("SET", Nocturne.Ok) else Tag("NOT SET", Nocturne.Warning)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (pinIsSet) {
                "The app asks for it every time it opens."
            } else {
                "The app opens without asking. Set a 4–8 digit PIN to keep " +
                    "casual fingers off the schedule."
            },
            fontSize = 12.5.sp,
            color = Nocturne.Neutral500,
        )
        Spacer(Modifier.height(14.dp))

        Box(Modifier.width(430.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (pinIsSet) {
                    Eyebrow("Current PIN")
                    PinField(current, { current = it.digitsMax8() }, "current PIN")
                }
                Eyebrow(if (pinIsSet) "New PIN" else "PIN")
                PinField(newPin, { newPin = it.digitsMax8() }, "4–8 digits")
                Eyebrow("Confirm")
                PinField(confirm, { confirm = it.digitsMax8() }, "repeat it")

                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(if (pinIsSet) "Change PIN" else "Set PIN") {
                        vm.setOrChangePin(current, newPin, confirm)
                        clear()
                    }
                    if (pinIsSet) {
                        RemovePinButton {
                            vm.removePin(current)
                            clear()
                        }
                    }
                }
            }
        }
    }
}

/**
 * A [Field] that never shows the PIN. The shared field is a plain
 * `BasicTextField`, and on a hall tablet anyone standing nearby can read a PIN
 * being typed — so this local copy masks the digits and asks for the numeric
 * password IME.
 */
@Composable
private fun PinField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(Nocturne.MIN_TOUCH_DP.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Nocturne.SurfaceHigh)
            .border(1.dp, Nocturne.Neutral700, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(placeholder, fontSize = 13.5.sp, color = Nocturne.Neutral600, maxLines = 1)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation('•'),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            textStyle = TextStyle(
                fontSize = 13.5.sp,
                color = Nocturne.Text,
                fontFamily = Nocturne.Mono,
                letterSpacing = 3.sp,
            ),
            cursorBrush = SolidColor(Nocturne.Accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Two-tap remove, matching the courses table's delete idiom: the first tap
 * arms, the second within 3 s removes. Disabling the gate by mis-tapping a
 * button that sits next to "Change PIN" — and only learning from a toast that
 * has already faded — is not a recoverable mistake on a shared tablet.
 */
@Composable
private fun RemovePinButton(onConfirm: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(3_000)
            armed = false
        }
    }
    Box(
        Modifier
            .height(Nocturne.MIN_TOUCH_DP.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Nocturne.Error.copy(alpha = if (armed) 0.30f else 0.12f))
            .border(
                1.dp,
                Nocturne.Error.copy(alpha = if (armed) 0.85f else 0.34f),
                RoundedCornerShape(8.dp),
            )
            .clickable {
                if (armed) {
                    armed = false
                    onConfirm()
                } else {
                    armed = true
                }
            }
            .semantics {
                contentDescription =
                    if (armed) "Confirm: remove PIN" else "Remove PIN, needs a second tap"
                role = Role.Button
            }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (armed) "Tap again to remove" else "Remove PIN",
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            color = Nocturne.Error,
        )
    }
}

private fun String.digitsMax8(): String = filter(Char::isDigit).take(8)
