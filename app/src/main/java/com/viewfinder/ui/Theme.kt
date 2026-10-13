package com.viewfinder.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.viewfinder.R

/** Light (white mat, black text) or dark (black mat, white text) look; read as Compose state. */
object Palette {
    var dark by mutableStateOf(false)
}

private fun pick(light: Long, dark: Long) = Color(if (Palette.dark) dark else light)

val Ink: Color get() = pick(0xFF0A0A0A, 0xFFF2F2F2)
val Mat: Color get() = pick(0xFFFFFFFF, 0xFF0B0B0B)
val Muted: Color get() = pick(0xFF6E6E6E, 0xFF8C8C8C)
val Hairline: Color get() = pick(0xFFDADADA, 0xFF2E2E2E)
val Selected: Color get() = pick(0xFFF0F0F0, 0xFF1C1C1C)
val Warn: Color get() = pick(0xFFD7261E, 0xFFFF5A4E)
val HatchFill: Color get() = pick(0xFFEDEDED, 0xFF161616)
val HatchLine: Color get() = pick(0xFFBDBDBD, 0xFF3A3A3A)

/** Frame guide colors, assigned in the order guides were added. Drawn on the camera image. */
val GuideColors = listOf(
    Color(0xFFFFC247), Color(0xFF3FD0FF), Color(0xFFFF5FA8),
    Color(0xFF8BE04E), Color(0xFFB48CFF), Color(0xFFFF8A3D),
)

fun guideColor(index: Int) = GuideColors[index % GuideColors.size]

@OptIn(ExperimentalTextApi::class)
private fun inter(weight: Int) = Font(
    R.font.inter,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Inter = FontFamily(inter(400), inter(500), inter(600), inter(700))

private fun TextStyle.inter() = copy(fontFamily = Inter)

private val InterTypography = Typography().run {
    Typography(
        displayLarge = displayLarge.inter(), displayMedium = displayMedium.inter(), displaySmall = displaySmall.inter(),
        headlineLarge = headlineLarge.inter(), headlineMedium = headlineMedium.inter(), headlineSmall = headlineSmall.inter(),
        titleLarge = titleLarge.inter(), titleMedium = titleMedium.inter(), titleSmall = titleSmall.inter(),
        bodyLarge = bodyLarge.inter(), bodyMedium = bodyMedium.inter(), bodySmall = bodySmall.inter(),
        labelLarge = labelLarge.inter(), labelMedium = labelMedium.inter(), labelSmall = labelSmall.inter(),
    )
}

@Composable
fun ViewfinderTheme(content: @Composable () -> Unit) {
    val scheme = if (Palette.dark) {
        darkColorScheme(
            primary = Ink, onPrimary = Mat, background = Mat, onBackground = Ink, surface = Mat, onSurface = Ink,
            surfaceContainer = Mat, surfaceContainerLow = Mat, surfaceContainerHigh = Mat, outline = Ink,
            onSurfaceVariant = Muted,
        )
    } else {
        lightColorScheme(
            primary = Ink, onPrimary = Mat, background = Mat, onBackground = Ink, surface = Mat, onSurface = Ink,
            surfaceContainer = Mat, surfaceContainerLow = Mat, surfaceContainerHigh = Mat, outline = Ink,
            onSurfaceVariant = Muted,
        )
    }
    MaterialTheme(colorScheme = scheme, typography = InterTypography, content = content)
}
