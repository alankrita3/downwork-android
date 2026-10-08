package com.raviga.downwork.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.material3.ripple
import com.raviga.downwork.ui.theme.Dw
import com.raviga.downwork.ui.theme.DwType
import com.raviga.downwork.ui.theme.Ink

private val ButtonShape = RoundedCornerShape(Dw.buttonRadius)

/** Ink fill, paper text. The one action that moves the flow forward. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().height(Dw.buttonHeight),
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Ink.ink,
            contentColor = Ink.paper,
            disabledContainerColor = Ink.ash,
            disabledContentColor = Ink.paper,
        ),
        elevation = null,
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Ink.paper, strokeWidth = 2.dp)
        } else {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(text, style = DwType.button)
        }
    }
}

/** 1px rule outline, ink text. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(Dw.buttonHeight),
        shape = ButtonShape,
        border = BorderStroke(Dw.hairline, if (enabled) Ink.rule else Ink.wash),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink.ink, disabledContentColor = Ink.ash),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = DwType.button)
    }
}

/** Cobalt text, no chrome. */
@Composable
fun TertiaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Ink.cobalt,
    fullWidth: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = (if (fullWidth) modifier.fillMaxWidth() else modifier).height(Dw.buttonHeight),
        shape = ButtonShape,
        colors = ButtonDefaults.textButtonColors(contentColor = color, disabledContentColor = Ink.ash),
    ) {
        Text(text, style = DwType.button)
    }
}

/** Brick text, no chrome. */
@Composable
fun DestructiveButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    TertiaryButton(text, onClick, modifier, enabled, color = Ink.brick)

/** Small cobalt text action used inline next to headings ("Edit", "Regenerate"). */
@Composable
fun InlineAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, color: Color = Ink.cobalt) {
    Text(
        text = text,
        style = DwType.secondary,
        color = if (enabled) color else Ink.ash,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/** 72dp ink circle; while recording the glyph becomes a 20dp paper square. */
@Composable
fun MicButton(recording: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(Dw.micSize)
            .clip(CircleShape)
            .background(if (enabled) Ink.ink else Ink.ash)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = ripple(color = Ink.paper),
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = if (recording) "Stop recording" else "Start recording" },
        contentAlignment = Alignment.Center,
    ) {
        if (recording) {
            Box(Modifier.size(Dw.stopGlyph).clip(RoundedCornerShape(3.dp)).background(Ink.paper))
        } else {
            Icon(icon, contentDescription = null, tint = Ink.paper, modifier = Modifier.size(30.dp))
        }
    }
}
