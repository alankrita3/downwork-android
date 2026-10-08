package com.raviga.downwork.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.raviga.downwork.R

/** Instrument Serif (OFL) for anything with a voice; Roboto (system) for chrome. */
val Serif = FontFamily(
    Font(R.font.instrument_serif_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
)
val Sans = FontFamily.Default

private val trim = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)
private val noPadding = PlatformTextStyle(includeFontPadding = false)

/** Named styles from the design doc, in sp so font scaling works everywhere. */
object DwType {
    val display = TextStyle(fontFamily = Serif, fontSize = 44.sp, lineHeight = 48.sp, lineHeightStyle = trim, platformStyle = noPadding)
    val title = TextStyle(fontFamily = Serif, fontSize = 34.sp, lineHeight = 38.sp, lineHeightStyle = trim, platformStyle = noPadding)
    val heading = TextStyle(fontFamily = Serif, fontSize = 26.sp, lineHeight = 30.sp, lineHeightStyle = trim, platformStyle = noPadding)
    val dictation = TextStyle(fontFamily = Serif, fontSize = 30.sp, lineHeight = 38.sp, lineHeightStyle = trim, platformStyle = noPadding)
    val body = TextStyle(fontFamily = Sans, fontSize = 17.sp, lineHeight = 25.sp, lineHeightStyle = trim, platformStyle = noPadding)
    val secondary = TextStyle(fontFamily = Sans, fontSize = 15.sp, lineHeight = 21.sp, lineHeightStyle = trim, platformStyle = noPadding)
    val button = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 22.sp, lineHeightStyle = trim, platformStyle = noPadding)
    val caption = TextStyle(fontFamily = Sans, fontSize = 13.sp, lineHeight = 18.sp, lineHeightStyle = trim, platformStyle = noPadding)
}

/** Material mapping so M3 components pick the right faces without extra wiring. */
val DwTypography = Typography(
    displayLarge = DwType.display,
    displayMedium = DwType.display,
    displaySmall = DwType.title,
    headlineLarge = DwType.title,
    headlineMedium = DwType.heading,
    headlineSmall = DwType.heading,
    titleLarge = DwType.heading,
    titleMedium = DwType.body.copy(fontWeight = FontWeight.Medium),
    titleSmall = DwType.secondary.copy(fontWeight = FontWeight.Medium),
    bodyLarge = DwType.body,
    bodyMedium = DwType.secondary,
    bodySmall = DwType.caption,
    labelLarge = DwType.button,
    labelMedium = DwType.secondary.copy(fontWeight = FontWeight.Medium),
    labelSmall = DwType.caption,
)
