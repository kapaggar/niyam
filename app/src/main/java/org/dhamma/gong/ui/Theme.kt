package org.dhamma.gong.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.dhamma.gong.domain.ThemeMode

/**
 * One palette's worth of Nocturne tokens.
 *
 * The ramps are **semantic, not literal**: `neutral300` always means "closest
 * to the text colour" and `neutral800` always means "closest to the
 * background", so in [LightPalette] the greys run dark-to-light where
 * [DarkPalette] runs light-to-dark. The same holds for `accent100..accent700`:
 * 100 is always the highest-contrast accent ink, 700 always the container fill.
 *
 * That inversion is the whole trick. Because the meaning of each token is
 * fixed, every existing `Nocturne.Neutral500` call site stays correct in both
 * palettes without being edited.
 */
data class Palette(
    val isDark: Boolean,
    val bg: Color,
    val bgDeep: Color,
    val statusBar: Color,
    val navRail: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val text: Color,
    val neutral300: Color,
    val neutral400: Color,
    val neutral500: Color,
    val neutral600: Color,
    val neutral700: Color,
    val neutral800: Color,
    val accent: Color,
    val accent100: Color,
    val accent200: Color,
    val accent300: Color,
    val accent700: Color,
    val warning: Color,
    val error: Color,
    val ok: Color,
    /** Hairlines are a wash of [text]; light needs a touch more to register. */
    val hairlineAlpha: Float,
)

/** The shipped default, transcribed from the Claude Design handoff. */
private val DarkPalette = Palette(
    isDark = true,
    bg = Color(0xFF161826),
    bgDeep = Color(0xFF0D0E17),
    statusBar = Color(0xFF101120),
    navRail = Color(0xFF13141F),
    surface = Color(0xFF1C1E2E),
    surfaceHigh = Color(0xFF232538),
    text = Color(0xFFE9E9ED),
    neutral300 = Color(0xFFB9B9C4),
    neutral400 = Color(0xFF9A9AA8),
    neutral500 = Color(0xFF7B7B8C),
    neutral600 = Color(0xFF5E5E6E),
    neutral700 = Color(0xFF414150),
    neutral800 = Color(0xFF2A2A38),
    accent = Color(0xFF9184D9),
    accent100 = Color(0xFFE6E2F7),
    accent200 = Color(0xFFCFC7EE),
    accent300 = Color(0xFFB5A9E4),
    accent700 = Color(0xFF544A8C),
    warning = Color(0xFFE0C07A),
    error = Color(0xFFE08A8A),
    ok = Color(0xFF7FD6A8),
    hairlineAlpha = 0.07f,
)

/**
 * Daylight Nocturne — the same layout in ink-on-paper.
 *
 * Every value here is chosen against its own background for at least 4.5:1,
 * because the screens were laid out for a 2 m read and a washed-out light
 * theme would quietly undo that. Cards stay pure white so the elevation
 * ordering (page < card < raised) survives the flip.
 */
private val LightPalette = Palette(
    isDark = false,
    bg = Color(0xFFF2F2F6),
    bgDeep = Color(0xFFE3E4EC),
    statusBar = Color(0xFFE9E9F0),
    navRail = Color(0xFFEAEAF1),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFF7F7FB),
    text = Color(0xFF1A1B26),
    neutral300 = Color(0xFF3B3C4A),
    neutral400 = Color(0xFF54556A),
    neutral500 = Color(0xFF6B6C80),
    neutral600 = Color(0xFF74758A),
    neutral700 = Color(0xFFC6C7D2),
    neutral800 = Color(0xFFE0E1E9),
    accent = Color(0xFF5B4FBE),
    accent100 = Color(0xFF2B2273),
    accent200 = Color(0xFF3D3391),
    accent300 = Color(0xFF4C40A8),
    accent700 = Color(0xFFD6D1F2),
    warning = Color(0xFF8A6410),
    error = Color(0xFFB3261E),
    ok = Color(0xFF17663F),
    hairlineAlpha = 0.10f,
)

/**
 * Nocturne design-system tokens, transcribed from the Claude Design handoff
 * (`docs/handoff/README.md`, "Design tokens").
 *
 * Every colour here is a **getter over the active [Palette]**, not a constant.
 * That is deliberate: fifteen screens read `Nocturne.Bg`, `Nocturne.Text` and
 * friends directly by name, and threading a CompositionLocal through all of
 * them to add a light theme would have been a thousand-line diff with a
 * thousand chances to miss one and leave a dark smear on a white page. Reading
 * through a single snapshot-backed slot keeps the call sites untouched and
 * makes a missed one impossible.
 *
 * The slot is written only by [GongTheme], before any child composes, and only
 * when the palette actually changes — see the note there.
 */
object Nocturne {
    private val slot = mutableStateOf(DarkPalette)

    /** The palette in force. Set through [GongTheme]. */
    val palette: Palette get() = slot.value

    internal fun apply(next: Palette) {
        if (slot.value != next) slot.value = next
    }

    /** True when the dark palette is in force — for the rare asset that must branch. */
    val isDark: Boolean get() = palette.isDark

    val Bg: Color get() = palette.bg
    val BgDeep: Color get() = palette.bgDeep
    val StatusBar: Color get() = palette.statusBar
    val NavRail: Color get() = palette.navRail
    val Surface: Color get() = palette.surface
    val SurfaceHigh: Color get() = palette.surfaceHigh

    val Text: Color get() = palette.text
    val Neutral300: Color get() = palette.neutral300
    val Neutral400: Color get() = palette.neutral400
    val Neutral500: Color get() = palette.neutral500
    val Neutral600: Color get() = palette.neutral600
    val Neutral700: Color get() = palette.neutral700
    val Neutral800: Color get() = palette.neutral800

    val Accent: Color get() = palette.accent
    val Accent100: Color get() = palette.accent100
    val Accent200: Color get() = palette.accent200
    val Accent300: Color get() = palette.accent300
    val Accent700: Color get() = palette.accent700

    val Warning: Color get() = palette.warning
    val Error: Color get() = palette.error
    val Ok: Color get() = palette.ok

    /** `color-mix(in srgb, #e9e9ed 7%, transparent)` from the handoff. */
    val Hairline: Color get() = palette.text.copy(alpha = palette.hairlineAlpha)

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

/**
 * M3 roles for a palette.
 *
 * The `surfaceContainer*` roles are mapped explicitly because the components
 * that pick their own container (DropdownMenu, Tooltip, DatePicker, Snackbar)
 * read those, not `surface`. Left unmapped they fall back to the M3 baseline
 * browns, which is why the course-type picker once rendered #211F26 inside a
 * Nocturne app.
 */
private fun schemeFor(p: Palette) = if (p.isDark) {
    darkColorScheme(
        primary = p.accent,
        onPrimary = p.accent100,
        primaryContainer = p.accent700,
        secondary = p.accent300,
        background = p.bg,
        onBackground = p.text,
        surface = p.surface,
        onSurface = p.text,
        surfaceVariant = p.surfaceHigh,
        onSurfaceVariant = p.neutral500,
        error = p.error,
        onError = p.bg,
        outline = p.neutral700,
        outlineVariant = p.neutral800,
        surfaceDim = p.bgDeep,
        surfaceBright = p.surfaceHigh,
        surfaceContainerLowest = p.bgDeep,
        surfaceContainerLow = p.navRail,
        surfaceContainer = p.surface,
        surfaceContainerHigh = p.surfaceHigh,
        surfaceContainerHighest = p.neutral800,
        inverseSurface = p.text,
        inverseOnSurface = p.bg,
        scrim = p.bgDeep,
    )
} else {
    lightColorScheme(
        primary = p.accent,
        // Light flips what "on primary" means: the accent is now a mid-tone
        // fill, so its label has to be near-white rather than near-black.
        onPrimary = p.bg,
        primaryContainer = p.accent700,
        onPrimaryContainer = p.accent100,
        secondary = p.accent300,
        background = p.bg,
        onBackground = p.text,
        surface = p.surface,
        onSurface = p.text,
        surfaceVariant = p.surfaceHigh,
        onSurfaceVariant = p.neutral500,
        error = p.error,
        onError = p.surface,
        outline = p.neutral700,
        outlineVariant = p.neutral800,
        surfaceDim = p.bgDeep,
        surfaceBright = p.surface,
        surfaceContainerLowest = p.surface,
        surfaceContainerLow = p.surfaceHigh,
        surfaceContainer = p.surface,
        surfaceContainerHigh = p.surfaceHigh,
        surfaceContainerHighest = p.neutral800,
        inverseSurface = p.text,
        inverseOnSurface = p.bg,
        scrim = p.neutral600,
    )
}

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

/**
 * @param mode which palette to paint in. Defaults to [ThemeMode.DEFAULT] so a
 *   preview, a test host or anything that forgets to pass one still gets the
 *   appliance's shipped look rather than whatever the emulator is set to.
 */
@Composable
fun GongTheme(
    mode: ThemeMode = ThemeMode.DEFAULT,
    content: @Composable () -> Unit,
) {
    val palette = if (mode.isDark(isSystemInDarkTheme())) DarkPalette else LightPalette

    // Written here, in the parent, so it lands before any child reads it — the
    // first frame is already correct and there is no flash of the wrong theme.
    // Safe against recomposition loops because GongTheme itself never reads the
    // slot, and the write is a no-op when the palette has not changed.
    Nocturne.apply(palette)

    MaterialTheme(
        colorScheme = schemeFor(palette),
        typography = GongTypography,
        content = content,
    )
}
