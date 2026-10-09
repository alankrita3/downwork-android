package com.raviga.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.raviga.app.R

/**
 * Bricolage Grotesque for anything with a voice (titles, headings, the big figures, what the client
 * said); Figtree for everything else. Both OFL, bundled as the same static instances iOS uses
 * (Bricolage opsz 48 Bold, 24 SemiBold, 32 Medium).
 */
val Display = FontFamily(
    Font(R.font.bricolage_bold, FontWeight.Bold),
    Font(R.font.bricolage_semibold, FontWeight.SemiBold),
    Font(R.font.bricolage_medium, FontWeight.Medium),
)
val Sans = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
    Font(R.font.figtree_bold, FontWeight.Bold),
)

private val trim = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)
private val noPadding = PlatformTextStyle(includeFontPadding = false)

private fun style(family: FontFamily, weight: FontWeight, size: Int, line: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
    lineHeightStyle = trim,
    platformStyle = noPadding,
)

/** Named styles from the design doc's type scale, in sp so font scaling works everywhere. */
object RaType {
    /** Welcome headline. */
    val hero = style(Display, FontWeight.Bold, 38, 42, -1.0)
    /** The quote figure, the credit balance. */
    val display = style(Display, FontWeight.Bold, 48, 52, -1.4)
    /** Screen titles, project and brief titles. */
    val title = style(Display, FontWeight.Bold, 32, 37, -0.7)
    /** Brief section headings, tile titles. */
    val heading = style(Display, FontWeight.SemiBold, 21, 26, -0.2)
    /** Live transcript on Capture. */
    val dictation = style(Display, FontWeight.Medium, 26, 34)
    /** Capture's hint and speech messages, where the transcript will appear. */
    val dictationItalic = TextStyle(fontFamily = Sans, fontStyle = FontStyle.Italic, fontSize = 26.sp, lineHeight = 34.sp, lineHeightStyle = trim, platformStyle = noPadding)
    val body = style(Sans, FontWeight.Normal, 17, 25)
    /** Row titles, emphasis. */
    val bodyMedium = style(Sans, FontWeight.SemiBold, 17, 24)
    val secondary = style(Sans, FontWeight.Normal, 15, 21)
    val secondaryMedium = style(Sans, FontWeight.SemiBold, 15, 21)
    val button = style(Sans, FontWeight.SemiBold, 17, 22)
    val caption = style(Sans, FontWeight.Normal, 13, 18)
    val chip = style(Sans, FontWeight.Bold, 13, 17)
    /** Hints in empty sections and the dictation placeholder. */
    val hintItalic = TextStyle(fontFamily = Sans, fontStyle = FontStyle.Italic, fontSize = 20.sp, lineHeight = 28.sp, lineHeightStyle = trim, platformStyle = noPadding)
}

/** Material mapping so M3 components pick the right faces without extra wiring. */
val RaTypography = Typography(
    displayLarge = RaType.display,
    displayMedium = RaType.display,
    displaySmall = RaType.title,
    headlineLarge = RaType.title,
    headlineMedium = RaType.heading,
    headlineSmall = RaType.heading,
    titleLarge = RaType.heading,
    titleMedium = RaType.bodyMedium,
    titleSmall = RaType.secondaryMedium,
    bodyLarge = RaType.body,
    bodyMedium = RaType.secondary,
    bodySmall = RaType.caption,
    labelLarge = RaType.button,
    labelMedium = RaType.secondaryMedium,
    labelSmall = RaType.caption,
)
