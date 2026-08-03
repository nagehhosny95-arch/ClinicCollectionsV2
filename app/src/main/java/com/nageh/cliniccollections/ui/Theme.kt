package com.nageh.cliniccollections.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nageh.cliniccollections.CollectionState
import com.nageh.cliniccollections.stateLabel

// --------------------------------------------------------------------------
// Palette. Emerald is taken from the app icon, gold is the icon's accent
// muted down so it reads as a highlight rather than decoration.
// --------------------------------------------------------------------------

val Emerald = Color(0xFF0B6B4F)
val EmeraldDeep = Color(0xFF064E3B)
val EmeraldInk = Color(0xFF01351F)
val EmeraldSoft = Color(0xFFE6F1EC)

val Gold = Color(0xFFC9A227)
val GoldSoft = Color(0xFFFBF3DC)

val AppBackground = Color(0xFFF6F8F7)
val AppSurface = Color(0xFFFFFFFF)
val AppOutline = Color(0xFFE2E8E5)
val TextPrimary = Color(0xFF12211C)
val TextMuted = Color(0xFF5C6B65)

val OkGreen = Color(0xFF17724F)
val OkSoft = Color(0xFFE6F4EC)
val WarnOrange = Color(0xFFA75F00)
val WarnSoft = Color(0xFFFCEEDA)
val DangerRed = Color(0xFFA9251D)
val DangerSoft = Color(0xFFFAE6E4)

/** Restrained WhatsApp green: recognisable, but not the neon brand colour. */
val WhatsAppGreen = Color(0xFF1E7A4C)

/** 4 / 8 / 12 / 16 / 24 dp rhythm used everywhere. */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
}

private val ClinicColorScheme = lightColorScheme(
    primary = Emerald,
    onPrimary = Color.White,
    primaryContainer = EmeraldSoft,
    onPrimaryContainer = EmeraldDeep,
    secondary = EmeraldDeep,
    onSecondary = Color.White,
    secondaryContainer = EmeraldSoft,
    onSecondaryContainer = EmeraldDeep,
    tertiary = Gold,
    onTertiary = EmeraldInk,
    tertiaryContainer = GoldSoft,
    onTertiaryContainer = Color(0xFF4A3B00),
    background = AppBackground,
    onBackground = TextPrimary,
    surface = AppSurface,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFEFF3F1),
    onSurfaceVariant = TextMuted,
    outline = Color(0xFFB9C4BF),
    outlineVariant = AppOutline,
    error = DangerRed,
    onError = Color.White,
    errorContainer = DangerSoft,
    onErrorContainer = Color(0xFF4A100C)
)

/** Strong but not oversized headings, and nothing below 12sp. */
private val ClinicTypography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 13.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp)
)

private val ClinicShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun ClinicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ClinicColorScheme,
        typography = ClinicTypography,
        shapes = ClinicShapes,
        content = content
    )
}

/**
 * Card colours per status. These map one to one onto the rules in
 * [com.nageh.cliniccollections.collectionState] and are not allowed to drift:
 * green for paid or on track, orange for a collection that is due, red for a
 * payment past its due date.
 */
data class StateStyle(
    val container: Color,
    val accent: Color,
    val onContainer: Color,
    val label: String
)

fun styleFor(state: CollectionState): StateStyle = when (state) {
    CollectionState.PAID -> StateStyle(OkSoft, OkGreen, Color(0xFF0C3A27), stateLabel(state))
    CollectionState.NORMAL -> StateStyle(OkSoft, OkGreen, Color(0xFF0C3A27), stateLabel(state))
    CollectionState.COLLECTION_DUE -> StateStyle(WarnSoft, WarnOrange, Color(0xFF4A2B00), stateLabel(state))
    CollectionState.PAYMENT_OVERDUE -> StateStyle(DangerSoft, DangerRed, Color(0xFF4A100C), stateLabel(state))
}
