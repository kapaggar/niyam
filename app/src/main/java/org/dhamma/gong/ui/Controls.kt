package org.dhamma.gong.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.ZonedDateTime

/**
 * Shared Nocturne controls.
 *
 * Steppers rather than sliders throughout. This is a wall tablet a server taps
 * while walking past, often without reading glasses: a −/+ pair saves
 * immediately, needs no keyboard, has no drag to mis-land, and each half
 * clears the 44 dp target. A slider would look tidier in a screenshot and be
 * worse in a hall.
 */

@Composable
fun Stepper(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    unit: String,
    step: Int = 1,
    labelWidth: Int = 64,
    valueWidth: Int = 56,
    onChange: (Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            label,
            fontSize = 12.5.sp,
            color = Nocturne.Neutral500,
            modifier = Modifier.width(labelWidth.dp),
        )
        StepButton("−", "Decrease $label", value > min) {
            onChange((value - step).coerceAtLeast(min))
        }
        Text(
            "$value$unit",
            fontSize = 15.sp,
            fontFamily = Nocturne.Mono,
            color = Nocturne.Text,
            modifier = Modifier.width(valueWidth.dp),
        )
        StepButton("+", "Increase $label", value < max) {
            onChange((value + step).coerceAtMost(max))
        }
    }
}

@Composable
fun StepButton(
    glyph: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(Nocturne.MIN_TOUCH_DP.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Nocturne.SurfaceHigh)
            .border(1.dp, Nocturne.Neutral700, RoundedCornerShape(8.dp))
            .semantics {
                contentDescription = description
                if (!enabled) disabled()
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.42f),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, fontSize = 17.sp, fontFamily = Nocturne.Mono, color = Nocturne.Text)
    }
}

/** A selectable pill. Same shape the Schedule inspector uses. */
@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .height(Nocturne.MIN_TOUCH_DP.dp)
            .widthIn(min = 40.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) Nocturne.Accent.copy(alpha = 0.22f) else Color.Transparent)
            .border(
                1.dp,
                if (selected) Nocturne.Accent else Nocturne.Neutral700,
                RoundedCornerShape(6.dp),
            )
            .semantics { contentDescription = description }
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 12.5.sp,
            color = if (selected) Nocturne.Accent200 else Nocturne.Neutral400,
        )
    }
}

/** A labelled row of chips, for a small closed set of choices. */
@Composable
fun ChipRow(
    label: String,
    labelWidth: Int = 96,
    content: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            label,
            fontSize = 12.5.sp,
            color = Nocturne.Neutral500,
            modifier = Modifier.width(labelWidth.dp),
        )
        content()
    }
}

/**
 * A clock that advances while the screen is actually being looked at.
 *
 * Bound to STARTED so a wall tablet with the display off is not recomposing
 * once a second for nobody — the appliance runs for weeks on a charger and the
 * UI is the part that should cost nothing when unattended.
 */
@Composable
fun rememberNow(intervalMs: Long = 1_000): ZonedDateTime {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, intervalMs) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = ZonedDateTime.now()
                delay(intervalMs)
            }
        }
    }
    return now
}

/**
 * A full-width callout. Used for states that must be read from across a room,
 * not tucked into a status row.
 */
@Composable
fun Banner(
    text: String,
    color: Color,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.42f), RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(text, fontSize = 13.5.sp, color = color, modifier = Modifier.weight(1f))
        if (actionLabel != null && onAction != null) {
            OutlineButton(actionLabel, color, onAction)
        }
    }
}

@Composable
fun OutlineButton(label: String, color: Color = Nocturne.Neutral300, onClick: () -> Unit) {
    Box(
        Modifier
            .height(Nocturne.MIN_TOUCH_DP.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Nocturne.Neutral700, RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 13.5.sp, color = color)
    }
}

/**
 * The one way this app explains itself.
 *
 * Everything a control does is on the control. Everything about *why Android
 * behaves that way* goes behind this badge, in three sentences or fewer. It is
 * a dialog and not a hover tip because the appliance is a tablet on a wall and
 * there is no pointer to hover with.
 *
 * The badge is drawn rather than typed: glyphs outside the platform font have
 * shipped as tofu on centre tablets before.
 */
@Composable
fun InfoDot(title: String, body: String) {
    var open by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(Nocturne.MIN_TOUCH_DP.dp)
            .semantics { contentDescription = "About $title" }
            .clickable(role = Role.Button) { open = true },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(19.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, Nocturne.Neutral600, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("i", fontSize = 12.sp, fontFamily = Nocturne.Mono, color = Nocturne.Neutral400)
        }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            containerColor = Nocturne.Surface,
            title = { Text(title, fontSize = 17.sp, color = Nocturne.Text) },
            text = { Text(body, fontSize = 13.5.sp, color = Nocturne.Neutral300) },
            confirmButton = { OutlineButton("Close", Nocturne.Neutral300) { open = false } },
        )
    }
}

/**
 * A text field that keeps a local buffer and writes it back when focus leaves
 * or the value it was seeded from changes underneath it. Saving on every
 * keystroke would write a partial IP address to the settings row the tick path
 * reads; a save button was ruled out by the design.
 */
@Composable
fun CommittingField(
    stored: String,
    placeholder: String,
    description: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onCommit: (String) -> Unit,
) {
    var typed by remember(stored) { mutableStateOf(stored) }
    var focused by remember { mutableStateOf(false) }
    Field(
        value = typed,
        onValueChange = { typed = it },
        placeholder = placeholder,
        keyboardOptions = keyboardOptions,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = description }
            // `hasFocus`, not `isFocused`: the focus target is the child
            // BasicTextField inside Field, so this node is never itself focused.
            .onFocusChanged { focus ->
                val had = focused
                focused = focus.hasFocus
                if (had && !focus.hasFocus && typed.trim() != stored) onCommit(typed)
            },
    )
}

@Composable
fun Toggle(label: String, checked: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    // The painted box stays 20 dp as designed; only the hit slop grows to the
    // 44 dp minimum, so a wall tablet tap does not miss.
    Row(
        Modifier
            .heightIn(min = Nocturne.MIN_TOUCH_DP.dp)
            .clip(RoundedCornerShape(6.dp))
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { onClick() },
            )
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (checked) Nocturne.Accent.copy(alpha = 0.26f) else Color.Transparent)
                .border(
                    1.dp,
                    if (checked) Nocturne.Accent else Nocturne.Neutral700,
                    RoundedCornerShape(4.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Text(
                    "✓",
                    fontSize = 12.sp,
                    color = Nocturne.Accent100,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
        Text(label, fontSize = 12.5.sp, color = Nocturne.Neutral300)
    }
}
