package com.raviga.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** 8dp grid and the handful of fixed sizes from the design doc. */
object Ra {
    val grid = 8.dp
    val gutter = 20.dp
    /** Welcome's wider gutter. */
    val gutterWide = 24.dp
    val rowPadding = 12.dp
    val sectionGap = 32.dp
    /** Space between tiles in a list. */
    val tileGap = 12.dp
    val buttonHeight = 56.dp
    val buttonRadius = 18.dp
    val fieldRadius = 16.dp
    val fieldPadding = 14.dp
    val tileRadius = 24.dp
    val imageRadius = 20.dp
    /** Bottom corners of a full-bleed picture at the top of a screen. */
    val heroRadius = 32.dp
    val micSize = 72.dp
    val stopGlyph = 20.dp
    val statusMark = 8.dp
    val hairline = 1.dp
    val maxContentWidth = 640.dp
}

val RaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(Ra.fieldRadius),
    medium = RoundedCornerShape(Ra.buttonRadius),
    large = RoundedCornerShape(Ra.imageRadius),
    extraLarge = RoundedCornerShape(Ra.tileRadius),
)
