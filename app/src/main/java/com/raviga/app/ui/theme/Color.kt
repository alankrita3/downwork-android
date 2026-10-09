package com.raviga.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * "Workshop" colour tokens, mirrored from Raviga iOS/docs/DESIGN.md: warm white paper,
 * near-black ink, teal for anything live, marigold for the moments worth a smile.
 * Light appearance only.
 */
object Ink {
    /** The page. */
    val paper = Color(0xFFFBFAF7)
    /** Tiles, fields and sheets that sit on the page. */
    val surface = Color(0xFFFFFFFF)
    val ink = Color(0xFF17181C)
    val graphite = Color(0xFF4A4B52)
    val ash = Color(0xFF6B6C73)
    /** Hairlines and tile borders. */
    val rule = Color(0xFFECE7DC)
    /** Outline of secondary buttons. */
    val ruleStrong = Color(0xFFD9D3C6)
    /** Chips and quiet fills. */
    val wash = Color(0xFFF3EEE3)
    /** Live state, links, focus, progress. */
    val teal = Color(0xFF0F5B57)
    val tealWash = Color(0xFFE6EFEC)
    val tealInk = Color(0xFF0D3B38)
    /** Highlights: the credits dot, the tier chip, the breakdown bar. */
    val marigold = Color(0xFFF2A33A)
    val marigoldWash = Color(0xFFFCE6BF)
    val marigoldInk = Color(0xFF5C3A00)
    val moss = Color(0xFF1F6F43)
    val amber = Color(0xFF9A5B00)
    val brick = Color(0xFFB3261E)
}
