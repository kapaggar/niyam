package org.dhamma.gong.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Nocturne design-system tokens, transcribed from the Claude Design handoff
 * (`docs/handoff/README.md`, "Design tokens").
 *
 * The appliance is a wall tablet in a dim hall — there is no light theme.
 */
object Nocturne {
    val Bg = Color(0xFF161826)
    val BgDeep = Color(0xFF0D0E17)
    val StatusBar = Color(0xFF101120)
    val NavRail = Color(0xFF13141F)
    val Surface = Color(0xFF1C1E2E)
    val SurfaceHigh = Color(0xFF232538)

    val Text = Color(0xFFE9E9ED)
    val Neutral300 = Color(0xFFB9B9C4)
    val Neutral400 = Color(0xFF9A9AA8)
    val Neutral500 = Color(0xFF7B7B8C)
    val Neutral600 = Color(0xFF5E5E6E)
    val Neutral700 = Color(0xFF414150)
    val Neutral800 = Color(0xFF2A2A38)

    val Accent = Color(0xFF9184D9)
    val Accent100 = Color(0xFFE6E2F7)
    val Accent200 = Color(0xFFCFC7EE)
    val Accent300 = Color(0xFFB5A9E4)
    val Accent700 = Color(0xFF544A8C)

    val Warning = Color(0xFFE0C07A)
    val Error = Color(0xFFE08A8A)
    val Ok = Color(0xFF7FD6A8)

    /** `color-mix(in srgb, #e9e9ed 7%, transparent)` from the handoff. */
    val Hairline = Text.copy(alpha = 0.07f)

    /** Every time, count, filename and log field is monospace. */
    val Mono = FontFamily.Monospace

    /**
     * Nothing *readable* goes below 12 sp on this device; hit targets stay >= 44 dp.
     *
     * `MIN_TEXT_SP` governs reading text — labels, values, body copy, log fields.
     * It does **not** govern the two decorative micro-styles below
     * ([Size.EYEBROW] and [Size.TAG]), which are ratified exemptions from the
     * handoff: they are all-caps, wide-tracked, low-information chrome read at
     * arm's length on a near-field control surface, never at 2 m.
     */
    const val MIN_TEXT_SP = 12f
    const val MIN_TOUCH_DP = 44

    /**
     * The handoff's type scale, as tokens. New work should reach for these
     * rather than re-typing the literal; existing call sites are deliberately
     * left alone (a mass migration is out of beta scope).
     */
    object Size {
        /** Dashboard hero clock only. The one true 2 m element. */
        val HERO = 98.sp
        /** Monospace counts and countdowns. */
        val NUMERAL = 30.sp
        val TITLE = 23.sp
        val BODY_LG = 17.sp
        val BODY = 15.sp
        val BODY_SM = 13.5.sp
        val LABEL = 12.5.sp
        val LABEL_SM = 11.5.sp

        /** Exempt from [MIN_TEXT_SP] — see the note on that constant. */
        val EYEBROW = 11.sp
        /** Exempt from [MIN_TEXT_SP] — see the note on that constant. */
        val TAG = 10.5.sp
    }

    /** Corner radii used across the shell. Existing literals are left as-is. */
    val RadiusSm = 4.dp
    val RadiusMd = 8.dp
    val RadiusLg = 12.dp
}

private val GongColors = darkColorScheme(
    primary = Nocturne.Accent,
    onPrimary = Nocturne.Accent100,
    primaryContainer = Nocturne.Accent700,
    secondary = Nocturne.Accent300,
    background = Nocturne.Bg,
    onBackground = Nocturne.Text,
    surface = Nocturne.Surface,
    onSurface = Nocturne.Text,
    surfaceVariant = Nocturne.SurfaceHigh,
    onSurfaceVariant = Nocturne.Neutral500,
    error = Nocturne.Error,
    onError = Nocturne.Bg,
    outline = Nocturne.Neutral700,
    outlineVariant = Nocturne.Neutral800,
    // M3 components that pick their own container (DropdownMenu, Tooltip,
    // DatePicker, Snackbar) read the surfaceContainer* roles, not `surface`.
    // Left unmapped they fall back to the M3 baseline browns, which is why the
    // course-type picker rendered #211F26 inside a Nocturne app.
    surfaceDim = Nocturne.BgDeep,
    surfaceBright = Nocturne.SurfaceHigh,
    surfaceContainerLowest = Nocturne.BgDeep,
    surfaceContainerLow = Nocturne.NavRail,
    surfaceContainer = Nocturne.Surface,
    surfaceContainerHigh = Nocturne.SurfaceHigh,
    surfaceContainerHighest = Nocturne.Neutral800,
    inverseSurface = Nocturne.Text,
    inverseOnSurface = Nocturne.Bg,
    scrim = Nocturne.BgDeep,
)

/** The handoff's type scale: 98 / 34 / 23 / 17 / 15 / 13.5 / 12.5 / 11.5 / 11. */
private val GongTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Nocturne.Mono,
        fontSize = 98.sp,
        lineHeight = 90.sp,
        letterSpacing = (-2.9).sp,
        fontWeight = FontWeight.Light,
    ),
    headlineMedium = TextStyle(fontSize = 23.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 17.sp),
    bodyMedium = TextStyle(fontSize = 15.sp),
    bodySmall = TextStyle(fontSize = 13.5.sp),
    labelMedium = TextStyle(fontSize = 12.5.sp),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        letterSpacing = 1.1.sp,
        fontWeight = FontWeight.Medium,
    ),
)

@Composable
fun GongTheme(
    // The appliance is always dark; the parameter exists so previews can differ.
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = GongColors,
        typography = GongTypography,
        content = content,
    )
}
