package com.raviga.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raviga.app.ui.theme.Ra
import com.raviga.app.ui.theme.RaType
import com.raviga.app.ui.theme.Ink

/** Paper everywhere, content capped at a readable width on tablets. */
@Composable
fun ScreenScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    content: @Composable BoxScope.(PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = Ink.paper,
        contentColor = Ink.ink,
        topBar = topBar,
        bottomBar = bottomBar,
        snackbarHost = { if (snackbarHostState != null) RaSnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.fillMaxSize().widthIn(max = Ra.maxContentWidth)) {
                content(padding)
            }
        }
    }
}

/** Back arrow, optional sans title, text actions on the right. 56dp, status bar aware. */
@Composable
fun RaTopBar(
    title: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Ink.paper)
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Ink.ink)
            }
        } else {
            Spacer(Modifier.height(1.dp).padding(start = 12.dp))
        }
        if (title != null) {
            Text(
                title,
                style = RaType.body.copy(fontWeight = FontWeight.Medium),
                color = Ink.ink,
                modifier = Modifier.weight(1f).padding(start = if (onBack != null) 4.dp else 12.dp),
                maxLines = 1,
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        actions()
    }
}

/** Pinned bottom area: hairline above, gutter padding, keyboard and nav bar aware. */
@Composable
fun BottomBar(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Ink.paper)
            .imePadding()
            .navigationBarsPadding(),
    ) {
        Hairline()
        Column(Modifier.padding(horizontal = Ra.gutter, vertical = 12.dp), content = content)
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(Ra.hairline).background(Ink.rule))
}

@Composable
fun RaSnackbarHost(state: SnackbarHostState) {
    SnackbarHost(state) { data ->
        Snackbar(
            snackbarData = data,
            containerColor = Ink.ink,
            contentColor = Ink.paper,
            actionColor = Ink.paper,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(Ra.buttonRadius),
        )
    }
}
