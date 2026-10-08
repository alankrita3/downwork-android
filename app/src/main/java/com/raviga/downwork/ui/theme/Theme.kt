package com.raviga.downwork.ui.theme

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val Scheme = lightColorScheme(
    primary = Ink.ink,
    onPrimary = Ink.paper,
    primaryContainer = Ink.wash,
    onPrimaryContainer = Ink.ink,
    secondary = Ink.cobalt,
    onSecondary = Ink.paper,
    secondaryContainer = Ink.wash,
    onSecondaryContainer = Ink.ink,
    tertiary = Ink.cobalt,
    onTertiary = Ink.paper,
    background = Ink.paper,
    onBackground = Ink.ink,
    surface = Ink.paper,
    onSurface = Ink.ink,
    surfaceVariant = Ink.wash,
    onSurfaceVariant = Ink.graphite,
    surfaceContainer = Ink.paper,
    surfaceContainerLow = Ink.paper,
    surfaceContainerLowest = Ink.paper,
    surfaceContainerHigh = Ink.wash,
    surfaceContainerHighest = Ink.wash,
    surfaceTint = Ink.paper,
    inverseSurface = Ink.ink,
    inverseOnSurface = Ink.paper,
    outline = Ink.rule,
    outlineVariant = Ink.rule,
    error = Ink.brick,
    onError = Ink.paper,
    errorContainer = Ink.paper,
    onErrorContainer = Ink.brick,
    scrim = Ink.ink,
)

/** Light only: the brief fixes a white background. */
@Composable
fun DownWorkTheme(content: @Composable () -> Unit) {
    val selection = TextSelectionColors(
        handleColor = Ink.cobalt,
        backgroundColor = Ink.cobalt.copy(alpha = 0.18f),
    )
    MaterialTheme(
        colorScheme = Scheme,
        typography = DwTypography,
        shapes = DwShapes,
    ) {
        CompositionLocalProvider(
            LocalTextSelectionColors provides selection,
            content = content,
        )
    }
}
