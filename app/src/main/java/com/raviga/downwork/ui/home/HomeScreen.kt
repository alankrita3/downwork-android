package com.raviga.downwork.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.raviga.downwork.data.api.ProjectStatus
import com.raviga.downwork.ui.LocalAppContainer
import com.raviga.downwork.ui.components.BottomBar
import com.raviga.downwork.ui.components.DwRow
import com.raviga.downwork.ui.components.DwTopBar
import com.raviga.downwork.ui.components.ErrorState
import com.raviga.downwork.ui.components.InlineAction
import com.raviga.downwork.ui.components.PrimaryButton
import com.raviga.downwork.ui.components.ProgressRule
import com.raviga.downwork.ui.components.ScreenScaffold
import com.raviga.downwork.ui.components.StatusMark
import com.raviga.downwork.ui.nav.Routes
import com.raviga.downwork.ui.capture.DescribeChooserSheet
import com.raviga.downwork.ui.status.StatusCopy
import com.raviga.downwork.ui.theme.Dw
import com.raviga.downwork.ui.theme.DwType
import com.raviga.downwork.ui.theme.Ink

@Composable
fun HomeScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val vm: HomeViewModel = viewModel { HomeViewModel(container) }
    val summaries by vm.summaries.collectAsStateWithLifecycle()
    val loadedOnce by vm.loadedOnce.collectAsStateWithLifecycle()
    val balance by vm.balance.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val signedOut by vm.signedOut.collectAsStateWithLifecycle()
    var chooser by remember { mutableStateOf(false) }
    if (chooser) {
        DescribeChooserSheet(
            projectTitle = null,
            onPick = { tab -> chooser = false; nav.navigate(Routes.capture(tab = tab)) },
            onDismiss = { chooser = false },
        )
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    // Android 13+: ask for notifications once, after the client has seen the home screen.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !vm.notificationsAsked()) {
            vm.markNotificationsAsked()
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    ScreenScaffold(
        topBar = {
            DwTopBar(
                actions = {
                    InlineAction(
                        text = "$balance ${if (balance == 1) "credit" else "credits"}",
                        onClick = { nav.navigate(Routes.CREDITS) },
                        color = Ink.ink,
                    )
                    IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = Ink.ink)
                    }
                },
            )
        },
        bottomBar = {
            BottomBar {
                PrimaryButton("Describe a project", onClick = { chooser = true })
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (refreshing && summaries.isNotEmpty()) ProgressRule(Modifier.padding(horizontal = Dw.gutter)) else Spacer(Modifier.height(2.dp))
            when {
                error != null && summaries.isEmpty() -> ErrorState(error!!, onRetry = { vm.refresh() })
                summaries.isEmpty() && loadedOnce -> {
                    Column(Modifier.fillMaxSize().padding(horizontal = Dw.gutter)) {
                        Spacer(Modifier.height(40.dp))
                        Text("What should we build?", style = DwType.display, color = Ink.ink)
                        Spacer(Modifier.height(16.dp))
                        Text("No projects yet. Tap the microphone and describe what you want built.", style = DwType.body, color = Ink.graphite)
                        if (signedOut) {
                            Spacer(Modifier.height(24.dp))
                            Text(
                                "This phone was removed from your DownWork account, so it has started fresh. To bring your projects back, use your recovery key.",
                                style = DwType.secondary,
                                color = Ink.graphite,
                            )
                            Row {
                                InlineAction("Use a recovery key", onClick = { vm.dismissSignedOut(); nav.navigate(Routes.RECOVERY) })
                                InlineAction("Dismiss", onClick = { vm.dismissSignedOut() }, color = Ink.graphite)
                            }
                        }
                        if (vm.isDemo) {
                            Spacer(Modifier.height(24.dp))
                            Text("Demo backend: everything works on this phone until the DownWork API is connected.", style = DwType.caption, color = Ink.ash)
                        }
                    }
                }
                summaries.isEmpty() -> Spacer(Modifier.height(1.dp))
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Text(
                            "Your projects",
                            style = DwType.display,
                            color = Ink.ink,
                            modifier = Modifier.padding(horizontal = Dw.gutter).padding(top = 32.dp, bottom = 20.dp),
                        )
                    }
                    items(summaries, key = { it.id }) { s ->
                        val subtitle = buildString {
                            append(StatusCopy.rowLine(s))
                            if (s.unreadComments > 0) append(" · ${s.unreadComments} new")
                        }
                        DwRow(
                            title = s.title.ifBlank { "Untitled project" },
                            subtitle = subtitle,
                            subtitleColor = if (s.unreadComments > 0) Ink.cobalt else Ink.graphite,
                            onClick = { nav.navigate(Routes.forProject(s.id, s.status)) },
                            chevron = false,
                            trailing = { StatusMark(s.status) },
                        )
                    }
                    item { Spacer(Modifier.fillMaxWidth().height(24.dp)) }
                }
            }
        }
    }
}
