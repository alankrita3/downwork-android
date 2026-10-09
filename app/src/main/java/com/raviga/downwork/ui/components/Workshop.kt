package com.raviga.downwork.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.raviga.downwork.R
import com.raviga.downwork.ui.theme.Dw
import com.raviga.downwork.ui.theme.DwType
import com.raviga.downwork.ui.theme.Ink

/** The clay illustrations (docs/DESIGN.md, Pictures). Each one belongs to one moment in the flow. */
enum class Picture(@DrawableRes val res: Int) {
    Welcome(R.drawable.illo_welcome),
    HomeEmpty(R.drawable.illo_home_empty),
    Speak(R.drawable.illo_speak),
    Type(R.drawable.illo_type),
    Upload(R.drawable.illo_upload),
    Writing(R.drawable.illo_writing),
    Quote(R.drawable.illo_quote),
    Building(R.drawable.illo_building),
    /** Onboarding step 3 only: clay robots building the app, an engineer with a checklist approving. */
    AIBuild(R.drawable.illo_ai_build),
    Delivered(R.drawable.illo_delivered),
    Credits(R.drawable.illo_credits),
    Privacy(R.drawable.illo_privacy),
}

/**
 * A picture cropped to its frame around a focus point (0,0 keeps the top-left, 0.5,0.5 the
 * middle), never stretched, with rounded corners.
 */
@Composable
fun Illustration(
    picture: Picture,
    modifier: Modifier = Modifier,
    height: Dp? = null,
    radius: Dp = Dw.imageRadius,
    focusX: Float = 0.5f,
    focusY: Float = 0.5f,
) {
    Image(
        painter = painterResource(picture.res),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alignment = BiasAlignment(focusX * 2 - 1, focusY * 2 - 1),
        modifier = modifier
            .fillMaxWidth()
            .then(if (height != null) Modifier.height(height) else Modifier)
            .clip(RoundedCornerShape(radius)),
    )
}

/** A full-bleed picture at the top of a screen, under the status bar, with rounded bottom corners. */
@Composable
fun HeroIllustration(picture: Picture, height: Dp, modifier: Modifier = Modifier, focusY: Float = 0.5f) {
    Image(
        painter = painterResource(picture.res),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alignment = BiasAlignment(0f, focusY * 2 - 1),
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(bottomStart = Dw.heroRadius, bottomEnd = Dw.heroRadius)),
    )
}

/** A whole square picture straight on the paper (the transparent ones: Writing, Credits, Privacy). */
@Composable
fun SpotIllustration(picture: Picture, size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(picture.res),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size),
    )
}

/** White, softly bordered tile on the paper. For choices and grouped facts, not for every block of text. */
fun Modifier.tile(padding: Dp = 16.dp, onClick: (() -> Unit)? = null): Modifier {
    val shape = RoundedCornerShape(Dw.tileRadius)
    return this
        .fillMaxWidth()
        .clip(shape)
        .background(Ink.surface, shape)
        .border(Dw.hairline, Ink.rule, shape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(padding)
}

enum class ChipTone { Plain, Teal, Marigold }

/** Small rounded label: a status, a tier, a promise. Optional 7dp dot or a small icon. */
@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    tone: ChipTone = ChipTone.Plain,
    dot: Color? = null,
    icon: ImageVector? = null,
) {
    val (fg, bg) = when (tone) {
        ChipTone.Plain -> Ink.ink to Ink.wash
        ChipTone.Teal -> Ink.tealInk to Ink.tealWash
        ChipTone.Marigold -> Ink.marigoldInk to Ink.marigoldWash
    }
    Row(
        modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            icon != null -> {
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
            }
            dot != null -> {
                Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(6.dp))
            }
        }
        Text(text, style = DwType.chip, color = fg)
    }
}

/** Chips laid out in rows, wrapping when the width runs out. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipRow(modifier: Modifier = Modifier, perRow: Int = Int.MAX_VALUE, content: @Composable () -> Unit) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = perRow,
    ) { content() }
}

/** The teal note with a lock: what stays private. With a [title], the title leads in bold. */
@Composable
fun PrivacyNote(text: String, modifier: Modifier = Modifier, title: String? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dw.imageRadius))
            .background(Ink.tealWash)
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = Ink.tealInk, modifier = Modifier.padding(top = 3.dp).size(16.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            if (title != null) {
                Text(title, style = DwType.bodyMedium, color = Ink.tealInk)
                Spacer(Modifier.height(4.dp))
            }
            Text(text, style = if (title != null) DwType.secondary else DwType.secondaryMedium, color = Ink.tealInk)
        }
    }
}

/** Said once, warmly, right before the client hands over their idea (Quote). */
@Composable
fun PrivacyPromise(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dw.imageRadius))
            .background(Ink.tealWash)
            .semantics(mergeDescendants = true) {}
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpotIllustration(Picture.Privacy, size = 72.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text("Your idea stays yours", style = DwType.bodyMedium, color = Ink.tealInk)
            Spacer(Modifier.height(6.dp))
            Text(
                "We use your brief only to build this project. We never sell it or use it to train AI, and we delete it from our servers when you accept the delivery.",
                style = DwType.secondary,
                color = Ink.tealInk,
            )
        }
    }
}
