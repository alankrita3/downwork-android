package com.raviga.downwork.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** 8dp grid and the handful of fixed sizes from the design doc. */
object Dw {
    val grid = 8.dp
    val gutter = 20.dp
    val rowPadding = 12.dp
    val sectionGap = 32.dp
    val buttonHeight = 52.dp
    val buttonRadius = 12.dp
    val fieldRadius = 10.dp
    val fieldPadding = 14.dp
    val micSize = 72.dp
    val stopGlyph = 20.dp
    val statusMark = 8.dp
    val hairline = 1.dp
    val maxContentWidth = 640.dp
}

val DwShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(Dw.fieldRadius),
    medium = RoundedCornerShape(Dw.buttonRadius),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
